package com.example.mileagetracker.ui.viewmodel

import android.net.Uri
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.ExportFormat
import com.example.mileagetracker.domain.model.ExportResult
import com.example.mileagetracker.domain.usecase.export.ExportLogbookToCsvUseCase
import com.example.mileagetracker.domain.usecase.export.ExportLogbookToPdfUseCase
import com.example.mileagetracker.domain.usecase.export.SaveReportHistoryUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {

    private lateinit var viewModel: ExportViewModel
    private lateinit var exportPdf: ExportLogbookToPdfUseCase
    private lateinit var exportCsv: ExportLogbookToCsvUseCase
    private lateinit var saveReportHistory: SaveReportHistoryUseCase
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Uri::class)
        exportPdf = mockk()
        exportCsv = mockk()
        saveReportHistory = mockk()
        viewModel = ExportViewModel(exportPdf, exportCsv, saveReportHistory)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Uri::class)
    }

    @Test
    fun `initial state is Idle`() = runTest {
        assertEquals(ExportUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `exportLogbook PDF success updates state to Success`() = runTest {
        val mockUri = mockk<Uri>()
        every { Uri.parse("file:///tmp/report.pdf") } returns mockUri
        every { mockUri.toString() } returns "file:///tmp/report.pdf"

        val result = ExportResult.Success(
            fileUri = mockUri,
            format = ExportFormat.PDF,
            fileSizeBytes = 1024L,
        )
        coEvery { exportPdf(any()) } returns result
        coEvery { saveReportHistory(result, any()) } returns 7

        viewModel.exportLogbook(
            vehicleId = 1,
            taxYear = 2025,
            format = ExportFormat.PDF,
            categories = setOf(TripCategory.BUSINESS, TripCategory.MEDICAL),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ExportUiState.Success)
        val success = state as ExportUiState.Success
        assertEquals(mockUri, success.fileUri)
        assertEquals(7, success.historyRecordId)
        assertEquals("application/pdf", success.mimeType)
        coVerify(exactly = 1) {
            exportPdf(match { request ->
                request.categories == setOf(TripCategory.BUSINESS, TripCategory.MEDICAL)
            })
        }
    }

    @Test
    fun `exportLogbook CSV failure updates state to Error`() = runTest {
        coEvery { exportCsv(any()) } returns ExportResult.Failure("Disk full")

        viewModel.exportLogbook(
            vehicleId = null,
            taxYear = 2024,
            format = ExportFormat.CSV,
            categories = setOf(TripCategory.BUSINESS),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ExportUiState.Error)
        assertEquals("Disk full", (state as ExportUiState.Error).message)
    }

    @Test
    fun `onShareConsumed resets state to Idle`() = runTest {
        val mockUri = mockk<Uri>()
        val result = ExportResult.Success(mockUri, ExportFormat.PDF, 100L)
        coEvery { exportPdf(any()) } returns result
        coEvery { saveReportHistory(any(), any()) } returns 1

        viewModel.exportLogbook(1, 2025)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ExportUiState.Success)

        viewModel.onShareConsumed()
        assertEquals(ExportUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `clearError resets state to Idle`() = runTest {
        coEvery { exportPdf(any()) } returns ExportResult.Failure("Error")
        viewModel.exportLogbook(1, 2025)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ExportUiState.Error)

        viewModel.clearError()
        assertEquals(ExportUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `exportLogbook updates state to Exporting initially`() = runTest {
        coEvery { exportPdf(any()) } coAnswers {
            assertTrue(viewModel.uiState.value is ExportUiState.Exporting)
            ExportResult.Failure("abort")
        }

        viewModel.exportLogbook(vehicleId = 1, taxYear = 2025, format = ExportFormat.PDF)
        advanceUntilIdle()
    }
}
