package com.example.mileagetracker.domain.usecase.tracking

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.mileagetracker.domain.model.PermissionDiagnostic
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ValidateTrackingPermissionsUseCaseTest {

    private lateinit var context: Context
    private lateinit var powerManager: PowerManager
    private lateinit var preferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var useCase: ValidateTrackingPermissionsUseCase

    @Before
    fun setUp() {
        context = mockk()
        powerManager = mockk()
        preferences = mockk()
        editor = mockk()

        useCase = ValidateTrackingPermissionsUseCase(context)

        every { context.applicationContext } returns context
        every { context.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { context.packageName } returns "com.test.app"
        every {
            context.getSharedPreferences("automatic_tracking_preferences", Context.MODE_PRIVATE)
        } returns preferences
        every { preferences.getBoolean(any(), any()) } returns false
        every { preferences.getString(any(), any()) } returns null
        every { preferences.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.apply() } returns Unit
        
        mockkStatic(ContextCompat::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(ContextCompat::class)
    }

    @Test
    fun `returns AllClear when all permissions and exemptions granted`() {
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        
        every { powerManager.isIgnoringBatteryOptimizations("com.test.app") } returns true
        
        // Note: Build.VERSION.SDK_INT is hard to mock, usually we test logic around it.
        // On standard JVM test it's often 0 or whatever the test framework sets.
        
        val result = useCase()

        assertEquals(listOf(PermissionDiagnostic.AllClear), result)
    }

    @Test
    fun `returns issues when permissions are missing`() {
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } returns PackageManager.PERMISSION_DENIED
        
        every { powerManager.isIgnoringBatteryOptimizations("com.test.app") } returns false

        val result = useCase()

        assertTrue(result.contains(PermissionDiagnostic.BackgroundLocationMissing))
        assertTrue(result.contains(PermissionDiagnostic.BatteryOptimizationActive))
        assertEquals(2, result.size)
    }

    @Test
    fun `returns activity transition registration failure when automatic tracking is enabled`() {
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        every { powerManager.isIgnoringBatteryOptimizations("com.test.app") } returns true
        every { preferences.getBoolean("enabled", false) } returns true
        every { preferences.getString("failure", null) } returns "Play Services rejected registration."

        val result = useCase()

        assertEquals(
            listOf(
                PermissionDiagnostic.ActivityTransitionRegistrationFailed(
                    "Play Services rejected registration."
                )
            ),
            result,
        )
    }

    @Test
    fun `orders diagnostics with transition failure after core permission checks`() {
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } returns PackageManager.PERMISSION_DENIED
        every { powerManager.isIgnoringBatteryOptimizations("com.test.app") } returns false
        every { preferences.getBoolean("enabled", false) } returns true
        every { preferences.getString("failure", null) } returns "Activity transitions unavailable."

        val result = useCase()

        assertEquals(
            listOf(
                PermissionDiagnostic.BackgroundLocationMissing,
                PermissionDiagnostic.BatteryOptimizationActive,
                PermissionDiagnostic.ActivityTransitionRegistrationFailed(
                    "Activity transitions unavailable."
                ),
            ),
            result,
        )
    }
}
