package com.example.mileagetracker.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.domain.model.CloudUploadConsent
import com.example.mileagetracker.domain.model.CraRateRegion
import com.example.mileagetracker.domain.model.RateMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented tests for [SettingsRepositoryImpl] backed by a real DataStore
 * writing to a temporary file in the device's cache directory.
 *
 * Each test uses a fresh DataStore file (unique name per test class instance)
 * so there is no state bleed between tests. The scope is cancelled and the
 * file is deleted in [tearDown].
 *
 * Key behaviours under test:
 *
 * - **Default values**: all settings emit the documented defaults before any
 *   write has been performed.
 * - **`setCustomRateDollars` validation**: throws [IllegalArgumentException]
 *   for any rate ≤ 0.
 * - **`setAutoDetectSensitivity` coercion**: values outside `[0f, 1f]` are
 *   clamped to the nearest boundary.
 * - **`setDefaultVehicleId(null)` sentinel**: stores −1 internally; reading
 *   back via `getDefaultVehicleId()` maps −1 back to `null`.
 * - **`setRuleEnabled` set management**: enabling a rule removes it from
 *   the disabled set; disabling adds it.
 * - **`getRateMode` `runCatching` fallback**: a corrupt/unknown string stored
 *   directly in DataStore maps to [RateMode.CRA_STANDARD] without crashing.
 * - **Cloud upload consent**: `setCloudUploadConsentGranted(true)` stores
 *   [CloudUploadConsent.CURRENT_VERSION]; `false` stores 0.
 */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryImplTest {

    private lateinit var repository: SettingsRepositoryImpl
    private lateinit var testFile: File
    private lateinit var testScope: CoroutineScope

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Unique file name per test instance avoids DataStore singleton conflicts.
        testFile = File(context.cacheDir, "test_settings_${System.nanoTime()}.preferences_pb")
        testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(
            scope       = testScope,
            produceFile = { testFile },
        )
        repository = SettingsRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        testScope.cancel()
        testFile.delete()
    }

    // ── RateMode ───────────────────────────────────────────────────────────────

    @Test
    fun getRateMode_default_isCraStandard() = runTest {
        assertEquals(RateMode.CRA_STANDARD, repository.getRateMode().first())
    }

    @Test
    fun setRateMode_persists_andEmitsNewValue() = runTest {
        repository.setRateMode(RateMode.CUSTOM)

        assertEquals(RateMode.CUSTOM, repository.getRateMode().first())
    }

    @Test
    fun getRateMode_unknownStoredValue_fallsBackToCraStandard() = runTest {
        // Write an invalid string directly, bypassing the type-safe API.
        // This simulates a preference from a rolled-back feature branch.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val corruptFile = File(context.cacheDir, "corrupt_${System.nanoTime()}.preferences_pb")
        val corruptScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val corruptStore = PreferenceDataStoreFactory.create(
                scope       = corruptScope,
                produceFile = { corruptFile },
            )
            corruptStore.edit { it[stringPreferencesKey("rate_mode")] = "LEGACY_CRA_PLUS" }

            val corruptRepo = SettingsRepositoryImpl(corruptStore)
            // runCatching inside getRateMode maps unknown enum to CRA_STANDARD.
            assertEquals(RateMode.CRA_STANDARD, corruptRepo.getRateMode().first())
        } finally {
            corruptScope.cancel()
            corruptFile.delete()
        }
    }

    // ── CraRateRegion ─────────────────────────────────────────────────────────

    @Test
    fun getCraRateRegion_default_isNull() = runTest {
        assertNull(repository.getCraRateRegion().first())
    }

    @Test
    fun setCraRateRegion_persists_andEmitsNewValue() = runTest {
        repository.setCraRateRegion(CraRateRegion.TERRITORIES)

        assertEquals(CraRateRegion.TERRITORIES, repository.getCraRateRegion().first())
    }

    // ── Custom rate ───────────────────────────────────────────────────────────

    @Test
    fun getCustomRateDollars_default_is0_70() = runTest {
        assertEquals(0.70f, repository.getCustomRateDollars().first())
    }

    @Test
    fun setCustomRateDollars_persists_positiveRate() = runTest {
        repository.setCustomRateDollars(0.85f)

        assertEquals(0.85f, repository.getCustomRateDollars().first())
    }

    @Test
    fun setCustomRateDollars_throwsForZero() = runTest {
        try {
            repository.setCustomRateDollars(0f)
            fail("Expected IllegalArgumentException for rate = 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("positive") || e.message!!.contains("0"))
        }
    }

    @Test
    fun setCustomRateDollars_throwsForNegativeRate() = runTest {
        val error = try {
            repository.setCustomRateDollars(-0.50f)
            fail("Expected IllegalArgumentException for negative rate")
            null
        } catch (error: IllegalArgumentException) {
            error
        }
        assertNotNull(error)
    }

    // ── Default vehicle ID ────────────────────────────────────────────────────

    @Test
    fun getDefaultVehicleId_default_isNull() = runTest {
        assertNull(repository.getDefaultVehicleId().first())
    }

    @Test
    fun setDefaultVehicleId_positiveId_persists() = runTest {
        repository.setDefaultVehicleId(5)

        assertEquals(5, repository.getDefaultVehicleId().first())
    }

    @Test
    fun setDefaultVehicleId_null_storesSentinel_readsBackAsNull() = runTest {
        repository.setDefaultVehicleId(3)        // set a real vehicle first
        repository.setDefaultVehicleId(null)     // then clear it

        // Internal sentinel (-1) must map back to null in the public API.
        assertNull(repository.getDefaultVehicleId().first())
    }

    // ── Auto-detect sensitivity coercion ──────────────────────────────────────

    @Test
    fun getAutoDetectSensitivity_default_is0_5() = runTest {
        assertEquals(0.5f, repository.getAutoDetectSensitivity().first())
    }

    @Test
    fun setAutoDetectSensitivity_inRange_persists() = runTest {
        repository.setAutoDetectSensitivity(0.8f)

        assertEquals(0.8f, repository.getAutoDetectSensitivity().first())
    }

    @Test
    fun setAutoDetectSensitivity_aboveOne_clampedToOne() = runTest {
        repository.setAutoDetectSensitivity(1.5f)

        assertEquals(1.0f, repository.getAutoDetectSensitivity().first())
    }

    @Test
    fun setAutoDetectSensitivity_belowZero_clampedToZero() = runTest {
        repository.setAutoDetectSensitivity(-0.1f)

        assertEquals(0.0f, repository.getAutoDetectSensitivity().first())
    }

    // ── Notification flags ────────────────────────────────────────────────────

    @Test
    fun getTripStartReminder_default_isTrue() = runTest {
        assertTrue(repository.getTripStartReminder().first())
    }

    @Test
    fun setTripStartReminder_persists() = runTest {
        repository.setTripStartReminder(false)

        assertFalse(repository.getTripStartReminder().first())
    }

    @Test
    fun getWeeklySummary_default_isTrue() = runTest {
        assertTrue(repository.getWeeklySummary().first())
    }

    @Test
    fun setWeeklySummary_persists() = runTest {
        repository.setWeeklySummary(false)

        assertFalse(repository.getWeeklySummary().first())
    }

    // ── Classification rule toggles ───────────────────────────────────────────

    @Test
    fun getDisabledRules_default_isEmpty() = runTest {
        assertTrue(repository.getDisabledRules().first().isEmpty())
    }

    @Test
    fun setRuleEnabled_false_addsRuleToDisabledSet() = runTest {
        repository.setRuleEnabled("HomeOfficeRule", enabled = false)

        assertTrue(repository.getDisabledRules().first().contains("HomeOfficeRule"))
    }

    @Test
    fun setRuleEnabled_true_removesRuleFromDisabledSet() = runTest {
        repository.setRuleEnabled("HomeOfficeRule", enabled = false)
        repository.setRuleEnabled("HomeOfficeRule", enabled = true)

        assertFalse(repository.getDisabledRules().first().contains("HomeOfficeRule"))
    }

    @Test
    fun setRuleEnabled_multipleRules_managedIndependently() = runTest {
        repository.setRuleEnabled("RuleA", enabled = false)
        repository.setRuleEnabled("RuleB", enabled = false)
        repository.setRuleEnabled("RuleA", enabled = true) // re-enable only A

        val disabled = repository.getDisabledRules().first()
        assertFalse("RuleA should be re-enabled", disabled.contains("RuleA"))
        assertTrue("RuleB should still be disabled", disabled.contains("RuleB"))
    }

    // ── Calendar hints ────────────────────────────────────────────────────────

    @Test
    fun getCalendarHintsEnabled_default_isTrue() = runTest {
        assertTrue(repository.getCalendarHintsEnabled().first())
    }

    @Test
    fun setCalendarHintsEnabled_false_persists() = runTest {
        repository.setCalendarHintsEnabled(false)

        assertFalse(repository.getCalendarHintsEnabled().first())
    }

    // ── Cloud upload consent ──────────────────────────────────────────────────

    @Test
    fun getCloudUploadConsentVersion_default_isZero() = runTest {
        assertEquals(0, repository.getCloudUploadConsentVersion().first())
    }

    @Test
    fun setCloudUploadConsentGranted_true_storesCurrentVersion() = runTest {
        repository.setCloudUploadConsentGranted(true)

        assertEquals(
            CloudUploadConsent.CURRENT_VERSION,
            repository.getCloudUploadConsentVersion().first(),
        )
    }

    @Test
    fun setCloudUploadConsentGranted_false_storesZero() = runTest {
        repository.setCloudUploadConsentGranted(true)   // grant first
        repository.setCloudUploadConsentGranted(false)  // then revoke

        assertEquals(0, repository.getCloudUploadConsentVersion().first())
    }
}
