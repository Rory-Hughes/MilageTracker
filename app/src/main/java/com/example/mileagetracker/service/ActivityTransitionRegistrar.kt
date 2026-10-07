package com.example.mileagetracker.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity

/**
 * Persists automatic-detection opt-in and repairs Play Services registration.
 */
class ActivityTransitionRegistrar(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun setAutomaticTrackingEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) registerIfEligible() else unregister()
    }

    fun registerIfEligible() {
        if (!preferences.getBoolean(KEY_ENABLED, false)) return
        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACTIVITY_RECOGNITION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            preferences.edit()
                .putBoolean(KEY_REGISTERED, false)
                .putString(KEY_FAILURE, "Activity recognition permission is not granted.")
                .apply()
            return
        }
        ActivityRecognition.getClient(appContext)
            .requestActivityTransitionUpdates(transitionRequest(), pendingIntent())
            .addOnSuccessListener {
                preferences.edit()
                    .putBoolean(KEY_REGISTERED, true)
                    .remove(KEY_FAILURE)
                    .apply()
            }
            .addOnFailureListener { error ->
                preferences.edit()
                    .putBoolean(KEY_REGISTERED, false)
                    .putString(KEY_FAILURE, error.message ?: "Play Services rejected registration.")
                    .apply()
                Log.w(TAG, "Activity transition registration failed: ${error.message}")
            }
    }

    fun unregister() {
        if (!hasActivityRecognitionPermission()) {
            clearRegistrationState()
            return
        }
        try {
            ActivityRecognition.getClient(appContext)
                .removeActivityTransitionUpdates(pendingIntent())
                .addOnCompleteListener {
                    clearRegistrationState()
                }
        } catch (_: SecurityException) {
            clearRegistrationState()
        }
    }

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)

    fun isRegistered(): Boolean = preferences.getBoolean(KEY_REGISTERED, false)

    fun registrationFailure(): String? = preferences.getString(KEY_FAILURE, null)

    private fun clearRegistrationState() {
        preferences.edit()
            .putBoolean(KEY_REGISTERED, false)
            .remove(KEY_FAILURE)
            .apply()
    }

    private fun hasActivityRecognitionPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED

    private fun transitionRequest() = ActivityTransitionRequest(
        listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build(),
        )
    )

    private fun pendingIntent() = PendingIntent.getBroadcast(
        appContext,
        REQUEST_CODE,
        Intent(appContext, ActivityRecognitionReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    private companion object {
        const val TAG = "ActivityTransitionReg"
        const val PREFERENCES = "automatic_tracking_preferences"
        const val KEY_ENABLED = "enabled"
        const val KEY_REGISTERED = "registered"
        const val KEY_FAILURE = "failure"
        const val REQUEST_CODE = 402
    }
}
