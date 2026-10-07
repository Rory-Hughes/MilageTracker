package com.example.mileagetracker.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomaticTrackingController @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val registrar = ActivityTransitionRegistrar(context)

    fun setEnabled(enabled: Boolean) {
        registrar.setAutomaticTrackingEnabled(enabled)
    }
}
