package com.example.mileagetracker.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import io.mockk.slot
import java.lang.Exception

class ActivityTransitionRegistrarTest {

    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var registrar: ActivityTransitionRegistrar
    private lateinit var recognitionClient: ActivityRecognitionClient

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        preferences = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        recognitionClient = mockk(relaxed = true)

        every { context.applicationContext } returns context
        every {
            context.getSharedPreferences("automatic_tracking_preferences", Context.MODE_PRIVATE)
        } returns preferences

        every { preferences.getBoolean(any(), any()) } answers { secondArg() }
        every { preferences.getString(any(), any()) } answers { secondArg() }
        every { preferences.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.apply() } returns Unit

        mockkStatic(ContextCompat::class)
        mockkStatic(ActivityRecognition::class)
        mockkStatic(PendingIntent::class)
        mockkStatic(Log::class)
        every { ActivityRecognition.getClient(context) } returns recognitionClient
        every { PendingIntent.getBroadcast(any(), any(), any<Intent>(), any()) } returns mockk()
        every { Log.w(any(), any<String>()) } returns 0

        registrar = ActivityTransitionRegistrar(context)
    }

    @After
    fun tearDown() {
        unmockkStatic(ContextCompat::class)
        unmockkStatic(ActivityRecognition::class)
        unmockkStatic(PendingIntent::class)
        unmockkStatic(Log::class)
    }

    @Test
    fun `registerIfEligible success updates state`() {
        every { preferences.getBoolean("enabled", false) } returns true
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.ACTIVITY_RECOGNITION)
        } returns PackageManager.PERMISSION_GRANTED
        
        val task = mockk<Task<Void>>()
        every { recognitionClient.requestActivityTransitionUpdates(any(), any()) } returns task
        
        val successSlot = slot<OnSuccessListener<Void>>()
        every { task.addOnSuccessListener(capture(successSlot)) } returns task
        every { task.addOnFailureListener(any()) } returns task

        registrar.registerIfEligible()
        
        successSlot.captured.onSuccess(null)

        verify { editor.putBoolean("registered", true) }
        verify { editor.remove("failure") }
        verify { editor.apply() }
    }

    @Test
    fun `registerIfEligible failure updates state`() {
        every { preferences.getBoolean("enabled", false) } returns true
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.ACTIVITY_RECOGNITION)
        } returns PackageManager.PERMISSION_GRANTED
        
        val task = mockk<Task<Void>>()
        every { recognitionClient.requestActivityTransitionUpdates(any(), any()) } returns task
        
        val failureSlot = slot<OnFailureListener>()
        every { task.addOnSuccessListener(any()) } returns task
        every { task.addOnFailureListener(capture(failureSlot)) } returns task

        registrar.registerIfEligible()
        
        failureSlot.captured.onFailure(Exception("Play Services error"))

        verify { editor.putBoolean("registered", false) }
        verify { editor.putString("failure", "Play Services error") }
        verify { editor.apply() }
    }

    @Test
    fun `unregister with permission calls Play Services and clears state`() {
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.ACTIVITY_RECOGNITION)
        } returns PackageManager.PERMISSION_GRANTED
        
        val task = mockk<Task<Void>>()
        every { recognitionClient.removeActivityTransitionUpdates(any()) } returns task
        
        val completeSlot = slot<OnCompleteListener<Void>>()
        every { task.addOnCompleteListener(capture(completeSlot)) } returns task

        registrar.unregister()
        
        completeSlot.captured.onComplete(task)

        verify { recognitionClient.removeActivityTransitionUpdates(any()) }
        verify { editor.putBoolean("registered", false) }
        verify { editor.apply() }
    }

    @Test
    fun `registerIfEligible returns early when automatic tracking is disabled`() {
        every { preferences.getBoolean("enabled", false) } returns false

        registrar.registerIfEligible()

        verify(exactly = 0) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
        }
        verify(exactly = 0) { editor.putBoolean("registered", any()) }
    }

    @Test
    fun `registerIfEligible stores failure when activity recognition permission is missing`() {
        every { preferences.getBoolean("enabled", false) } returns true
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
        } returns PackageManager.PERMISSION_DENIED

        registrar.registerIfEligible()

        verify { editor.putBoolean("registered", false) }
        verify { editor.putString("failure", "Activity recognition permission is not granted.") }
        verify { editor.apply() }
    }

    @Test
    fun `setAutomaticTrackingEnabled false disables feature and clears registration state`() {
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
        } returns PackageManager.PERMISSION_DENIED

        registrar.setAutomaticTrackingEnabled(false)

        verify { editor.putBoolean("enabled", false) }
        verify { editor.putBoolean("registered", false) }
        verify { editor.remove("failure") }
        verify { editor.apply() }
    }

    @Test
    fun `setAutomaticTrackingEnabled true records opt-in and captures permission failure`() {
        every { preferences.getBoolean("enabled", false) } returns true
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
        } returns PackageManager.PERMISSION_DENIED

        registrar.setAutomaticTrackingEnabled(true)

        verify { editor.putBoolean("enabled", true) }
        verify { editor.putBoolean("registered", false) }
        verify { editor.putString("failure", "Activity recognition permission is not granted.") }
        verify { editor.apply() }
    }

    @Test
    fun `status getters return stored registration flags and message`() {
        every { preferences.getBoolean("enabled", false) } returns true
        every { preferences.getBoolean("registered", false) } returns true
        every { preferences.getString("failure", null) } returns "Play Services rejected registration."

        assertEquals(true, registrar.isEnabled())
        assertEquals(true, registrar.isRegistered())
        assertEquals("Play Services rejected registration.", registrar.registrationFailure())
    }
}
