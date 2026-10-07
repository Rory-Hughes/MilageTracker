package com.example.mileagetracker.ui.viewmodel

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.mileagetracker.domain.model.PermissionDiagnostic
import com.example.mileagetracker.domain.usecase.tracking.ValidateTrackingPermissionsUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingDiagnosticsViewModelTest {

    private lateinit var viewModel: TrackingDiagnosticsViewModel
    private lateinit var validateTrackingPermissionsUseCase: ValidateTrackingPermissionsUseCase
    private lateinit var context: Context
    private lateinit var dataStore: DataStore<Preferences>
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        validateTrackingPermissionsUseCase = mockk()
        context = mockk(relaxed = true)
        dataStore = mockk(relaxed = true)

        // Mock context.diagPrefs
        // This is tricky because it's an extension property. 
        // We might need to mock the DataStore returned by the extension.
        // But the extension is private and uses preferencesDataStore.
        
        every { validateTrackingPermissionsUseCase() } returns listOf(PermissionDiagnostic.AllClear)

        viewModel = TrackingDiagnosticsViewModel(validateTrackingPermissionsUseCase, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initializes with diagnostics`() = runTest {
        advanceUntilIdle()
        assertEquals(listOf(PermissionDiagnostic.AllClear), viewModel.diagnostics.value)
    }

    @Test
    fun `refresh updates diagnostics`() = runTest {
        val issue = PermissionDiagnostic.BackgroundLocationMissing
        every { validateTrackingPermissionsUseCase() } returns listOf(issue)
        
        viewModel.refresh()
        advanceUntilIdle()
        
        assertEquals(listOf(issue), viewModel.diagnostics.value)
    }
}
