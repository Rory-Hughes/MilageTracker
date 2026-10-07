package com.example.mileagetracker.service

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.example.mileagetracker.domain.model.DetectionSource
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ActivityRecognitionReceiverTest {

    private lateinit var receiver: ActivityRecognitionReceiver
    private lateinit var context: RecordingContext

    @Before
    fun setUp() {
        receiver = ActivityRecognitionReceiver()
        context = RecordingContext(ApplicationProvider.getApplicationContext())
        mockkStatic(ActivityTransitionResult::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(ActivityTransitionResult::class)
    }

    @Test
    fun `onReceive ignores intents without transition result`() {
        val intent = Intent("no-result")
        every { ActivityTransitionResult.hasResult(intent) } returns false

        receiver.onReceive(context, intent)

        assertNull(context.lastForegroundServiceIntent)
        assertNull(context.lastBackgroundServiceIntent)
    }

    @Test
    fun `onReceive ignores null extracted transition result`() {
        val intent = Intent("activity-transition")
        every { ActivityTransitionResult.hasResult(intent) } returns true
        every { ActivityTransitionResult.extractResult(intent) } returns null

        receiver.onReceive(context, intent)

        assertNull(context.lastForegroundServiceIntent)
        assertNull(context.lastBackgroundServiceIntent)
    }

    @Test
    fun `onReceive starts automatic tracking on in vehicle enter`() {
        val intent = Intent("activity-transition")
        val result = ActivityTransitionResult(
            listOf(
                ActivityTransitionEvent(
                    DetectedActivity.IN_VEHICLE,
                    ActivityTransition.ACTIVITY_TRANSITION_ENTER,
                    1L,
                )
            )
        )
        every { ActivityTransitionResult.hasResult(intent) } returns true
        every { ActivityTransitionResult.extractResult(intent) } returns result

        receiver.onReceive(context, intent)

        assertEquals(
            DriveTrackingService.ACTION_AUTO_START,
            context.lastForegroundServiceIntent?.action,
        )
        assertEquals(
            DetectionSource.AUTO.name,
            context.lastForegroundServiceIntent?.getStringExtra(DriveTrackingService.EXTRA_DETECTION_SOURCE),
        )
    }

    @Test
    fun `onReceive stops tracking on in vehicle exit`() {
        val intent = Intent("activity-transition")
        val result = ActivityTransitionResult(
            listOf(
                ActivityTransitionEvent(
                    DetectedActivity.IN_VEHICLE,
                    ActivityTransition.ACTIVITY_TRANSITION_EXIT,
                    2L,
                )
            )
        )
        every { ActivityTransitionResult.hasResult(intent) } returns true
        every { ActivityTransitionResult.extractResult(intent) } returns result

        receiver.onReceive(context, intent)

        assertEquals(
            DriveTrackingService.ACTION_AUTO_STOP,
            context.lastBackgroundServiceIntent?.action,
        )
    }

    @Test
    fun `onReceive ignores non vehicle activity transitions`() {
        val intent = Intent("activity-transition")
        val result = ActivityTransitionResult(
            listOf(
                ActivityTransitionEvent(
                    DetectedActivity.ON_BICYCLE,
                    ActivityTransition.ACTIVITY_TRANSITION_ENTER,
                    3L,
                )
            )
        )
        every { ActivityTransitionResult.hasResult(intent) } returns true
        every { ActivityTransitionResult.extractResult(intent) } returns result

        receiver.onReceive(context, intent)

        assertNull(context.lastForegroundServiceIntent)
        assertNull(context.lastBackgroundServiceIntent)
    }

    @Test
    fun `onReceive processes mixed events and dispatches both start and stop`() {
        val intent = Intent("activity-transition")
        val result = ActivityTransitionResult(
            listOf(
                ActivityTransitionEvent(
                    DetectedActivity.IN_VEHICLE,
                    ActivityTransition.ACTIVITY_TRANSITION_ENTER,
                    4L,
                ),
                ActivityTransitionEvent(
                    DetectedActivity.IN_VEHICLE,
                    ActivityTransition.ACTIVITY_TRANSITION_EXIT,
                    5L,
                ),
            )
        )
        every { ActivityTransitionResult.hasResult(intent) } returns true
        every { ActivityTransitionResult.extractResult(intent) } returns result

        receiver.onReceive(context, intent)

        assertEquals(1, context.startForegroundServiceCallCount)
        assertEquals(1, context.startServiceCallCount)
        assertEquals(DriveTrackingService.ACTION_AUTO_STOP, context.lastBackgroundServiceIntent?.action)
    }

    private class RecordingContext(
        base: Context,
    ) : ContextWrapper(base) {
        var lastForegroundServiceIntent: Intent? = null
        var lastBackgroundServiceIntent: Intent? = null
        var startForegroundServiceCallCount: Int = 0
        var startServiceCallCount: Int = 0

        override fun getApplicationContext(): Context = this

        override fun startForegroundService(service: Intent?): ComponentName? {
            startForegroundServiceCallCount += 1
            lastForegroundServiceIntent = service
            return null
        }

        override fun startService(service: Intent?): ComponentName? {
            startServiceCallCount += 1
            lastBackgroundServiceIntent = service
            return null
        }
    }
}
