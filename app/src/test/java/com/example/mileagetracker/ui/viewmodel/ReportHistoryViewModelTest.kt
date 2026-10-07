package com.example.mileagetracker.ui.viewmodel

import android.net.Uri
import android.util.Log
import com.example.mileagetracker.data.local.entity.ReportHistoryEntity
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.repository.ReportHistoryRepository
import com.example.mileagetracker.domain.repository.VehicleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportHistoryViewModelTest {

    private lateinit var viewModel: ReportHistoryViewModel
    private lateinit var repository: ReportHistoryRepository
    private lateinit var vehicleRepository: VehicleRepository
    private lateinit var currentUserProvider: CurrentUserProvider
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Uri::class)
        mockkStatic(Log::class)

        val parsedUri = mockk<Uri>()
        every { parsedUri.path } returns "C:\\tmp\\report.pdf"
        every { Uri.parse(any()) } returns parsedUri
        every { Log.e(any(), any(), any()) } returns 0

        repository = mockk()
        vehicleRepository = mockk()
        currentUserProvider = mockk()
        every { currentUserProvider.requireUid() } returns "u1"
        every { repository.getAll() } returns flowOf(emptyList())
        every { vehicleRepository.getAllVehiclesForUser("u1") } returns flowOf(emptyList())

        viewModel = ReportHistoryViewModel(repository, vehicleRepository, currentUserProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Uri::class)
        unmockkStatic(Log::class)
    }

    private fun testReport(id: Int, year: Int, generatedAt: Long, vehicleId: Int? = 1) = ReportHistoryEntity(
        id = id,
        year = year,
        format = "PDF",
        vehicleId = vehicleId,
        generatedAt = generatedAt,
        fileUri = "file:///tmp/report_$id.pdf",
        fileSizeBytes = 1024L,
    )

    @Test
    fun `initializes and groups reports by year descending with vehicle labels`() = runTest {
        val reports = listOf(
            testReport(1, 2024, 1000L, vehicleId = null),
            testReport(2, 2025, 2000L, vehicleId = 2),
            testReport(3, 2024, 1500L, vehicleId = 3),
        )
        val vehicles = listOf(
            VehicleEntity(id = 2, ownerUid = "u1", displayName = "Sedan", fiscalYear = 2025),
            VehicleEntity(
                id = 3,
                ownerUid = "u1",
                displayName = "",
                make = "Ford",
                model = "Escape",
                fiscalYear = 2024
            ),
        )
        every { repository.getAll() } returns flowOf(reports)
        every { vehicleRepository.getAllVehiclesForUser("u1") } returns flowOf(vehicles)

        viewModel = ReportHistoryViewModel(repository, vehicleRepository, currentUserProvider)
        advanceUntilIdle()

        val grouped = viewModel.groupedReports.value
        assertEquals(2, grouped.size)
        assertTrue(grouped.keys.first() == 2025)
        assertEquals("Sedan", grouped[2025]?.single()?.vehicleLabel)
        assertEquals("All vehicles", grouped[2024]?.first()?.vehicleLabel)
        assertEquals("Ford Escape", grouped[2024]?.last()?.vehicleLabel)
    }

    @Test
    fun `deleteReport blocks within 6 year window`() = runTest {
        val recentReport = testReport(1, 2025, System.currentTimeMillis() - 1000L)

        viewModel.deleteReport(recentReport)

        assertNotNull(viewModel.retentionWarning.value)
        assertEquals(recentReport, viewModel.retentionWarning.value)
        coVerify(exactly = 0) { repository.deleteById(any()) }
    }

    @Test
    fun `deleteReport proceeds outside 6 year window`() = runTest {
        val sixYearsAndADay = 6L * 365 * 24 * 60 * 60 * 1000L + (24 * 60 * 60 * 1000L)
        val oldReport = testReport(2, 2018, System.currentTimeMillis() - sixYearsAndADay)

        coEvery { repository.deleteById(2) } returns Unit

        viewModel.deleteReport(oldReport)
        advanceUntilIdle()

        assertNull(viewModel.retentionWarning.value)
        coVerify(exactly = 1) { repository.deleteById(2) }
    }

    @Test
    fun `clearRetentionWarning resets state`() = runTest {
        val recentReport = testReport(1, 2025, System.currentTimeMillis())
        viewModel.deleteReport(recentReport)
        assertNotNull(viewModel.retentionWarning.value)

        viewModel.clearRetentionWarning()
        assertNull(viewModel.retentionWarning.value)
    }
}
