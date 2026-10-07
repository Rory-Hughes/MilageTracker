package com.example.mileagetracker.ui.viewmodel

import android.content.Context
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.AppLanguage
import com.example.mileagetracker.domain.model.CraRateDiagnostics
import com.example.mileagetracker.domain.model.CraRateRegion
import com.example.mileagetracker.domain.model.RateMode
import com.example.mileagetracker.domain.repository.SettingsRepository
import com.example.mileagetracker.domain.usecase.localization.GetLanguagePreferenceUseCase
import com.example.mileagetracker.domain.usecase.localization.ResetLanguagePreferenceUseCase
import com.example.mileagetracker.domain.usecase.localization.SetLanguagePreferenceUseCase
import com.example.mileagetracker.domain.usecase.rate.GetCraRateDiagnosticsUseCase
import com.example.mileagetracker.domain.usecase.vehicle.GetActiveVehiclesUseCase
import com.example.mileagetracker.domain.usecase.vehicle.SetDefaultVehicleUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var viewModel: SettingsViewModel
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var getActiveVehiclesUseCase: GetActiveVehiclesUseCase
    private lateinit var setDefaultVehicleUseCase: SetDefaultVehicleUseCase
    private lateinit var getLanguagePreferenceUseCase: GetLanguagePreferenceUseCase
    private lateinit var setLanguagePreferenceUseCase: SetLanguagePreferenceUseCase
    private lateinit var resetLanguagePreferenceUseCase: ResetLanguagePreferenceUseCase
    private lateinit var getCraRateDiagnosticsUseCase: GetCraRateDiagnosticsUseCase
    private lateinit var currentUserProvider: CurrentUserProvider
    private lateinit var context: Context

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = mockk(relaxed = true)
        getActiveVehiclesUseCase = mockk(relaxed = true)
        setDefaultVehicleUseCase = mockk(relaxed = true)
        getLanguagePreferenceUseCase = mockk(relaxed = true)
        setLanguagePreferenceUseCase = mockk(relaxed = true)
        resetLanguagePreferenceUseCase = mockk(relaxed = true)
        getCraRateDiagnosticsUseCase = mockk(relaxed = true)
        currentUserProvider = mockk()
        context = mockk(relaxed = true)

        every { currentUserProvider.requireUid() } returns "u1"
        every { getLanguagePreferenceUseCase() } returns flowOf(AppLanguage.ENGLISH)
        every { settingsRepository.getRateMode() } returns flowOf(RateMode.CRA_STANDARD)
        every { settingsRepository.getCustomRateDollars() } returns flowOf(0.70f)
        every { settingsRepository.getDefaultVehicleId() } returns flowOf(null)
        every { settingsRepository.getTripStartReminder() } returns flowOf(true)
        every { settingsRepository.getWeeklySummary() } returns flowOf(true)
        every { settingsRepository.getOdometerPhotoReminder() } returns flowOf(true)
        every { settingsRepository.getAutoDetectSensitivity() } returns flowOf(0.5f)
        every { settingsRepository.getCalendarHintsEnabled() } returns flowOf(true)
        every { settingsRepository.getCraRateRegion() } returns flowOf(CraRateRegion.PROVINCES)
        every { settingsRepository.getCloudUploadConsentVersion() } returns flowOf(0)
        every { getActiveVehiclesUseCase(any(), any()) } returns flowOf(emptyList())
        coEvery { getCraRateDiagnosticsUseCase() } returns CraRateDiagnostics(
            currentYear = 2026,
            catalogStartYear = 2009,
            catalogEndYear = 2026,
            seededYears = (2009..2026).toList(),
            unseededYears = emptyList(),
            sourceUrl = "https://example.com",
        )

        viewModel = SettingsViewModel(
            settingsRepository,
            getActiveVehiclesUseCase,
            setDefaultVehicleUseCase,
            getLanguagePreferenceUseCase,
            setLanguagePreferenceUseCase,
            resetLanguagePreferenceUseCase,
            getCraRateDiagnosticsUseCase,
            currentUserProvider,
            context
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setRateMode calls repository`() = runTest {
        viewModel.setRateMode(RateMode.CUSTOM)
        advanceUntilIdle()
        coVerify { settingsRepository.setRateMode(RateMode.CUSTOM) }
    }

    @Test
    fun `onCustomRateInputChanged updates state and validates`() = runTest {
        val collector = backgroundScope.launch { viewModel.settingsState.collect {} }
        viewModel.onCustomRateInputChanged("abc")
        advanceUntilIdle()
        assertEquals("abc", viewModel.settingsState.value.customRateInput)
        assertEquals("Must be a number (e.g. 0.70)", viewModel.settingsState.value.customRateError)

        viewModel.onCustomRateInputChanged("0.55")
        advanceUntilIdle()
        assertEquals("0.55", viewModel.settingsState.value.customRateInput)
        assertNull(viewModel.settingsState.value.customRateError)
        collector.cancel()
    }

    @Test
    fun `applyCustomRate calls repository if valid`() = runTest {
        viewModel.onCustomRateInputChanged("0.65")
        viewModel.applyCustomRate()
        advanceUntilIdle()
        coVerify { settingsRepository.setCustomRateDollars(0.65f) }
    }

    @Test
    fun `applyCustomRate does not call repository if invalid`() = runTest {
        viewModel.onCustomRateInputChanged("-1.0")
        viewModel.applyCustomRate()
        advanceUntilIdle()
        coVerify(exactly = 0) { settingsRepository.setCustomRateDollars(any()) }
    }

    @Test
    fun `setDefaultVehicle calls repository and use case`() = runTest {
        viewModel.setDefaultVehicle(5)
        advanceUntilIdle()
        coVerify { settingsRepository.setDefaultVehicleId(5) }
        coVerify { setDefaultVehicleUseCase(5, "u1", any()) }
    }

    @Test
    fun `setOdometerPhotoReminder calls repository`() = runTest {
        viewModel.setOdometerPhotoReminder(false)
        advanceUntilIdle()
        coVerify { settingsRepository.setOdometerPhotoReminder(false) }
    }

    @Test
    fun `craRateDiagnostics loads during init`() = runTest {
        advanceUntilIdle()

        assertEquals(2026, viewModel.craRateDiagnosticsState.value.diagnostics?.currentYear)
        assertNull(viewModel.craRateDiagnosticsState.value.error)
    }
}
