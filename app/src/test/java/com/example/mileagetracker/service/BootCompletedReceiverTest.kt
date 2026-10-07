package com.example.mileagetracker.service

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BootCompletedReceiverTest {

    private lateinit var context: Context
    private lateinit var receiver: BootCompletedReceiver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        receiver = BootCompletedReceiver()
    }

    @Test
    fun `boot completed triggers registration flow when enabled`() {
        prefs()
            .edit()
            .putBoolean(KEY_ENABLED, true)
            .putBoolean(KEY_REGISTERED, true)
            .putString(KEY_FAILURE, "old")
            .commit()

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertEquals(false, prefs().getBoolean(KEY_REGISTERED, true))
        assertEquals(
            "Activity recognition permission is not granted.",
            prefs().getString(KEY_FAILURE, null),
        )
    }

    @Test
    fun `boot completed does not change registration state when automatic tracking is disabled`() {
        prefs()
            .edit()
            .putBoolean(KEY_ENABLED, false)
            .putBoolean(KEY_REGISTERED, true)
            .putString(KEY_FAILURE, "keep-me")
            .commit()

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertEquals(true, prefs().getBoolean(KEY_REGISTERED, false))
        assertEquals("keep-me", prefs().getString(KEY_FAILURE, null))
    }

    @Test
    fun `non boot action does nothing`() {
        prefs()
            .edit()
            .putBoolean(KEY_ENABLED, true)
            .putBoolean(KEY_REGISTERED, true)
            .putString(KEY_FAILURE, "keep-me")
            .commit()

        receiver.onReceive(context, Intent(Intent.ACTION_PACKAGE_REPLACED))

        assertEquals(true, prefs().getBoolean(KEY_REGISTERED, false))
        assertEquals("keep-me", prefs().getString(KEY_FAILURE, null))
    }

    @Test
    fun `null action does nothing`() {
        prefs()
            .edit()
            .putBoolean(KEY_ENABLED, true)
            .putBoolean(KEY_REGISTERED, false)
            .remove(KEY_FAILURE)
            .commit()

        receiver.onReceive(context, Intent())

        assertEquals(false, prefs().getBoolean(KEY_REGISTERED, true))
        assertNull(prefs().getString(KEY_FAILURE, null))
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private companion object {
        const val PREFS = "automatic_tracking_preferences"
        const val KEY_ENABLED = "enabled"
        const val KEY_REGISTERED = "registered"
        const val KEY_FAILURE = "failure"
    }
}
