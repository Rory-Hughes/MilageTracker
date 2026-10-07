package com.example.mileagetracker.util

import android.content.Context
import com.example.mileagetracker.service.ActivityTransitionRegistrar

fun registerActivityTransitions(context: Context) {
    ActivityTransitionRegistrar(context).setAutomaticTrackingEnabled(true)
}
