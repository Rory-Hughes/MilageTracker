package com.example.mileagetracker.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.example.mileagetracker.data.repository.SettingsRepositoryImpl
import com.example.mileagetracker.domain.model.CraRateRegion
import com.example.mileagetracker.domain.model.RateMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsRepositoryImplTest {

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SettingsRepositoryImpl

    @Before
    fun setUp() {
        dataStore = mockk()
        repository = SettingsRepositoryImpl(dataStore)
        mockkStatic("androidx.datastore.preferences.core.PreferencesKt")
    }

    @org.junit.After
    fun tearDown() {
        unmockkStatic("androidx.datastore.preferences.core.PreferencesKt")
    }

    @Test
    fun `getRateMode returns default when missing`() = runTest {
        every { dataStore.data } returns flowOf(emptyPreferences())

        val result = repository.getRateMode().first()

        assertEquals(RateMode.CRA_STANDARD, result)
    }

    @Test
    fun `getRateMode returns stored value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[any<Preferences.Key<String>>()] } returns "CUSTOM"
        every { dataStore.data } returns flowOf(prefs)

        val result = repository.getRateMode().first()

        assertEquals(RateMode.CUSTOM, result)
    }

    @Test
    fun `setRateMode updates dataStore`() = runTest {
        val prefs = mockk<MutablePreferences>(relaxed = true)
        coEvery { dataStore.edit(any()) } coAnswers {
            val transform = secondArg<suspend (MutablePreferences) -> Unit>()
            transform(prefs)
            prefs
        }

        repository.setRateMode(RateMode.CUSTOM)

        coVerify { prefs[any<Preferences.Key<String>>()] = "CUSTOM" }
    }

    @Test
    fun `getCraRateRegion returns null when missing`() = runTest {
        every { dataStore.data } returns flowOf(emptyPreferences())
        assertNull(repository.getCraRateRegion().first())
    }

    @Test
    fun `setCraRateRegion updates dataStore`() = runTest {
        val prefs = mockk<MutablePreferences>(relaxed = true)
        coEvery { dataStore.edit(any()) } coAnswers {
            secondArg<suspend (MutablePreferences) -> Unit>().invoke(prefs)
            prefs
        }

        repository.setCraRateRegion(CraRateRegion.TERRITORIES)

        coVerify { prefs[any<Preferences.Key<String>>()] = "TERRITORIES" }
    }

    @Test
    fun `getCustomRateDollars returns default 0_70`() = runTest {
        every { dataStore.data } returns flowOf(emptyPreferences())

        val result = repository.getCustomRateDollars().first()

        assertEquals(0.70f, result)
    }

    @Test
    fun `setCustomRateDollars validates positive rate`() = runTest {
        val error = try {
            repository.setCustomRateDollars(-0.1f)
            null
        } catch (error: IllegalArgumentException) {
            error
        }
        assertNotNull(error)
        assertEquals("Custom rate must be positive; got -0.1", error!!.message)
    }

    @Test
    fun `setCustomRateDollars updates dataStore when valid`() = runTest {
        val prefs = mockk<MutablePreferences>(relaxed = true)
        coEvery { dataStore.edit(any()) } coAnswers {
            secondArg<suspend (MutablePreferences) -> Unit>().invoke(prefs)
            prefs
        }

        repository.setCustomRateDollars(0.85f)

        coVerify { prefs[any<Preferences.Key<Float>>()] = 0.85f }
    }

    @Test
    fun `getDefaultVehicleId returns null when missing`() = runTest {
        every { dataStore.data } returns flowOf(emptyPreferences())

        val result = repository.getDefaultVehicleId().first()

        assertEquals(null, result)
    }

    @Test
    fun `setDefaultVehicleId updates dataStore`() = runTest {
        val prefs = mockk<MutablePreferences>(relaxed = true)
        coEvery { dataStore.edit(any()) } coAnswers {
            secondArg<suspend (MutablePreferences) -> Unit>().invoke(prefs)
            prefs
        }

        repository.setDefaultVehicleId(42)

        coVerify { prefs[any<Preferences.Key<Int>>()] = 42 }
    }

    @Test
    fun `getWeeklySummary returns true by default`() = runTest {
        every { dataStore.data } returns flowOf(emptyPreferences())

        val result = repository.getWeeklySummary().first()

        assertEquals(true, result)
    }

    @Test
    fun `getAutoDetectSensitivity returns default 0_5`() = runTest {
        every { dataStore.data } returns flowOf(emptyPreferences())

        val result = repository.getAutoDetectSensitivity().first()

        assertEquals(0.5f, result)
    }

    @Test
    fun `setAutoDetectSensitivity clamps value`() = runTest {
        val prefs = mockk<MutablePreferences>(relaxed = true)
        coEvery { dataStore.edit(any()) } coAnswers {
            secondArg<suspend (MutablePreferences) -> Unit>().invoke(prefs)
            prefs
        }

        repository.setAutoDetectSensitivity(1.5f)
        coVerify { prefs[any<Preferences.Key<Float>>()] = 1.0f }

        repository.setAutoDetectSensitivity(-0.5f)
        coVerify { prefs[any<Preferences.Key<Float>>()] = 0.0f }
    }

    @Test
    fun `getDisabledRules returns empty set by default`() = runTest {
        every { dataStore.data } returns flowOf(emptyPreferences())
        assertTrue(repository.getDisabledRules().first().isEmpty())
    }

    @Test
    fun `setRuleEnabled updates set in dataStore`() = runTest {
        val prefs = mockk<MutablePreferences>(relaxed = true)
        coEvery { dataStore.edit(any()) } coAnswers {
            secondArg<suspend (MutablePreferences) -> Unit>().invoke(prefs)
            prefs
        }

        // Disable rule -> add to set (no prior set → null)
        every { prefs[any<Preferences.Key<Set<String>>>()] } returns null
        repository.setRuleEnabled("Rule1", false)
        coVerify { prefs[any<Preferences.Key<Set<String>>>()] = setOf("Rule1") }

        // Enable rule -> remove from set
        every { prefs[any<Preferences.Key<Set<String>>>()] } returns setOf("Rule1")
        repository.setRuleEnabled("Rule1", true)
        coVerify { prefs[any<Preferences.Key<Set<String>>>()] = emptySet<String>() }
    }

    @Test
    fun `setCalendarHintsEnabled updates dataStore`() = runTest {
        val prefs = mockk<MutablePreferences>(relaxed = true)
        coEvery { dataStore.edit(any()) } coAnswers {
            secondArg<suspend (MutablePreferences) -> Unit>().invoke(prefs)
            prefs
        }

        repository.setCalendarHintsEnabled(false)

        coVerify { prefs[any<Preferences.Key<Boolean>>()] = false }
    }

    @Test
    fun `setCloudUploadConsentGranted updates version in dataStore`() = runTest {
        val prefs = mockk<MutablePreferences>(relaxed = true)
        coEvery { dataStore.edit(any()) } coAnswers {
            secondArg<suspend (MutablePreferences) -> Unit>().invoke(prefs)
            prefs
        }

        repository.setCloudUploadConsentGranted(true)
        coVerify { prefs[any<Preferences.Key<Int>>()] = 1 } // Assuming CURRENT_VERSION is 1

        repository.setCloudUploadConsentGranted(false)
        coVerify { prefs[any<Preferences.Key<Int>>()] = 0 }
    }
}
