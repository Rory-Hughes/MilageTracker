package com.example.mileagetracker.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.mileagetracker.MainActivity
import com.example.mileagetracker.navigation.Screen

private const val TAG = "DriveTrackingService"
private const val PERCENT_MULTIPLIER = 100
private const val RC_CONTENT = 100
private const val RC_STOP_ACTION = 101

internal fun ensureDriveTrackingNotificationChannel(context: Context) {
    val channel = NotificationChannel(
        DriveTrackingService.CHANNEL_ID,
        "Drive Tracking",
        NotificationManager.IMPORTANCE_LOW,
    ).apply {
        description = "Shows while a trip is being recorded"
    }
    context.getSystemService(NotificationManager::class.java)
        .createNotificationChannel(channel)
}

internal fun buildDriveTrackingNotification(
    context: Context,
    serviceClass: Class<out DriveTrackingService>,
) = NotificationCompat.Builder(context, DriveTrackingService.CHANNEL_ID)
    .setContentTitle("Trip in Progress")
    .setContentText("MileageTracker is recording your drive...")
    .setSmallIcon(android.R.drawable.ic_menu_mylocation)
    .setOngoing(true)
    .setContentIntent(
        PendingIntent.getActivity(
            context,
            RC_CONTENT,
            Intent(context, MainActivity::class.java).apply {
                data = Screen.ManualTrip.DeepLink.toUri()
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    )
    .addAction(
        android.R.drawable.ic_media_pause,
        "Stop Trip",
        PendingIntent.getService(
            context,
            RC_STOP_ACTION,
            Intent(context, serviceClass).apply {
                action = DriveTrackingService.ACTION_MANUAL_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ),
    )
    .build()

internal fun showDuplicateNotification(
    context: Context,
    existingTripId: Int,
    overlapPercent: Float,
    isProbable: Boolean,
) {
    val overlapPct = (overlapPercent * PERCENT_MULTIPLIER).toInt()
    val notification = NotificationCompat.Builder(context, DriveTrackingService.CHANNEL_ID)
        .setContentTitle(if (isProbable) "Duplicate Trip Detected" else "Trip Needs Review")
        .setContentText(
            if (isProbable) {
                "A new trip overlaps trip #$existingTripId by $overlapPct%. Tap to resolve."
            } else {
                "A new trip overlaps trip #$existingTripId by $overlapPct% and was saved for review."
            }
        )
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setAutoCancel(true)
        .build()

    if (!hasNotificationPermission(context)) {
        android.util.Log.w(TAG, "POST_NOTIFICATIONS permission missing; skipping dup notification.")
        return
    }

    try {
        NotificationManagerCompat.from(context)
            .notify(DriveTrackingService.DUPLICATE_NOTIFICATION_ID, notification)
    } catch (error: SecurityException) {
        android.util.Log.w(
            TAG,
            "POST_NOTIFICATIONS permission missing; skipping dup notification: ${error.message}",
            error,
        )
    }
}

internal fun currentBatteryLevel(context: Context): Int? {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return if (level < 0 || scale <= 0) {
        null
    } else {
        level * PERCENT_MULTIPLIER / scale
    }
}

internal fun isPowerSaverActive(context: Context): Boolean =
    (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode ?: false

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
