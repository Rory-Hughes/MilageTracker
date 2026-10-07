package com.example.mileagetracker.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.mileagetracker.domain.model.DetectionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class TrackingCommandDispatcherTest {

    private lateinit var context: RecordingContext
    private lateinit var dispatcher: TrackingCommandDispatcher

    @Before
    fun setUp() {
        context = RecordingContext(ApplicationProvider.getApplicationContext())
        dispatcher = TrackingCommandDispatcher(context)
    }

    @Test
    fun `startManual sends foreground command with manual action and source`() {
        dispatcher.startManual()

        assertNotNull(context.lastForegroundServiceIntent)
        assertEquals(DriveTrackingService.ACTION_MANUAL_START, context.lastForegroundServiceIntent?.action)
        assertEquals(
            DetectionSource.MANUAL.name,
            context.lastForegroundServiceIntent?.getStringExtra(DriveTrackingService.EXTRA_DETECTION_SOURCE),
        )
    }

    @Test
    fun `startAutomatic sends foreground command with auto action and source`() {
        dispatcher.startAutomatic()

        assertNotNull(context.lastForegroundServiceIntent)
        assertEquals(DriveTrackingService.ACTION_AUTO_START, context.lastForegroundServiceIntent?.action)
        assertEquals(
            DetectionSource.AUTO.name,
            context.lastForegroundServiceIntent?.getStringExtra(DriveTrackingService.EXTRA_DETECTION_SOURCE),
        )
    }

    @Test
    fun `stop sends background service command with auto stop action`() {
        dispatcher.stop()

        assertNotNull(context.lastBackgroundServiceIntent)
        assertEquals(DriveTrackingService.ACTION_AUTO_STOP, context.lastBackgroundServiceIntent?.action)
    }

    @Test
    fun `stopManual sends background service command with manual stop action`() {
        dispatcher.stopManual()

        assertNotNull(context.lastBackgroundServiceIntent)
        assertEquals(DriveTrackingService.ACTION_MANUAL_STOP, context.lastBackgroundServiceIntent?.action)
    }

    @Test
    fun `stop swallows service start failure`() {
        context.throwOnStartService = true

        dispatcher.stop()

        assertEquals(1, context.startServiceCallCount)
    }

    private class RecordingContext(
        base: Context,
    ) : ContextWrapper(base) {
        var lastForegroundServiceIntent: Intent? = null
        var lastBackgroundServiceIntent: Intent? = null
        var throwOnStartService: Boolean = false
        var startServiceCallCount: Int = 0

        override fun startForegroundService(service: Intent?): ComponentName? {
            lastForegroundServiceIntent = service
            return null
        }

        override fun startService(service: Intent?): ComponentName? {
            startServiceCallCount += 1
            if (throwOnStartService) {
                throw IllegalStateException("not running")
            }
            lastBackgroundServiceIntent = service
            return null
        }
    }
}
