package com.example.mileagetracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

class ActivityRecognitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return

        val result = ActivityTransitionResult.extractResult(intent) ?: return

        for (event in result.transitionEvents) {
            val dispatcher = TrackingCommandDispatcher(context.applicationContext)

            when (
                // User just started driving - start the foreground service
                event.activityType
            ) {
                DetectedActivity.IN_VEHICLE if event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                    dispatcher.startAutomatic()
                }

                // User just stopped driving - stop the foreground service
                DetectedActivity.IN_VEHICLE if event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                    dispatcher.stop()
                }
            }
        }
    }
}
