package com.example.mileagetracker.domain.usecase

import android.net.Uri
import com.example.mileagetracker.data.remote.integrations.PayrollExportService
import com.example.mileagetracker.domain.model.IntegrationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ExportPayrollSummaryUseCaseTest {

    private lateinit var payrollExportService: PayrollExportService
    private lateinit var useCase: ExportPayrollSummaryUseCase

    @Before
    fun setUp() {
        payrollExportService = mockk()
        useCase = ExportPayrollSummaryUseCase(payrollExportService)
    }

    @Test
    fun `execute with empty rows returns Error`() = runTest {
        val result = useCase.execute(IntegrationProvider.PAYROLL_CSV, emptyList())
        assertTrue(result is ExportPayrollSummaryUseCase.ExportResult.Error)
        assertEquals(
            "No approved records found for the selected period.",
            (result as ExportPayrollSummaryUseCase.ExportResult.Error).message
        )
    }

    @Test
    fun `execute with PAYROLL_CSV returns CsvReady`() = runTest {
        val mockUri = mockk<Uri>()
        val row = ExportPayrollSummaryUseCase.PayrollRow(
            employeeName = "John Doe",
            periodStart = "2025-01-01",
            periodEnd = "2025-01-31",
            totalKm = 100.0,
            reimbursementAmountCad = 70.0,
            policyRateCentsPerKm = 70
        )

        every { payrollExportService.generateCsv(any()) } returns mockUri

        val result = useCase.execute(IntegrationProvider.PAYROLL_CSV, listOf(row))

        assertTrue(result is ExportPayrollSummaryUseCase.ExportResult.CsvReady)
        assertEquals(mockUri, (result as ExportPayrollSummaryUseCase.ExportResult.CsvReady).uri)
    }

    @Test
    fun `execute with QUICKBOOKS returns Error`() = runTest {
        val row = ExportPayrollSummaryUseCase.PayrollRow("A", "B", "C", 1.0, 1.0, 1)
        val result = useCase.execute(IntegrationProvider.QUICKBOOKS, listOf(row))
        assertTrue(result is ExportPayrollSummaryUseCase.ExportResult.Error)
        assertTrue((result as ExportPayrollSummaryUseCase.ExportResult.Error).message.contains("sync is disabled"))
    }

    @Test
    fun `execute with XERO returns Error`() = runTest {
        val row = ExportPayrollSummaryUseCase.PayrollRow("A", "B", "C", 1.0, 1.0, 1)
        val result = useCase.execute(IntegrationProvider.XERO, listOf(row))

        assertTrue(result is ExportPayrollSummaryUseCase.ExportResult.Error)
        result as ExportPayrollSummaryUseCase.ExportResult.Error
        assertEquals(IntegrationProvider.XERO, result.provider)
        assertTrue(result.message.contains("sync is disabled"))
    }

    @Test
    fun `empty rows returns provider scoped error for QUICKBOOKS`() = runTest {
        val result = useCase.execute(IntegrationProvider.QUICKBOOKS, emptyList())

        assertTrue(result is ExportPayrollSummaryUseCase.ExportResult.Error)
        result as ExportPayrollSummaryUseCase.ExportResult.Error
        assertEquals(IntegrationProvider.QUICKBOOKS, result.provider)
        assertEquals("No approved records found for the selected period.", result.message)
    }

    @Test
    fun `empty rows returns provider scoped error for XERO`() = runTest {
        val result = useCase.execute(IntegrationProvider.XERO, emptyList())

        assertTrue(result is ExportPayrollSummaryUseCase.ExportResult.Error)
        result as ExportPayrollSummaryUseCase.ExportResult.Error
        assertEquals(IntegrationProvider.XERO, result.provider)
        assertEquals("No approved records found for the selected period.", result.message)
    }

    @Test
    fun `csv export maps row fields into payroll export service payload`() = runTest {
        val mockUri = mockk<Uri>()
        val slot = slot<List<PayrollExportService.PayrollRow>>()
        every { payrollExportService.generateCsv(capture(slot)) } returns mockUri
        val row = ExportPayrollSummaryUseCase.PayrollRow(
            employeeName = "Alice",
            periodStart = "2026-01-01",
            periodEnd = "2026-01-31",
            totalKm = 321.45,
            reimbursementAmountCad = 222.10,
            policyRateCentsPerKm = 69,
        )

        useCase.execute(IntegrationProvider.PAYROLL_CSV, listOf(row))

        assertEquals(1, slot.captured.size)
        val sent = slot.captured.single()
        assertEquals("Alice", sent.employeeName)
        assertEquals("2026-01-01", sent.periodStart)
        assertEquals("2026-01-31", sent.periodEnd)
        assertEquals(321.45, sent.totalKm, 0.0001)
        assertEquals(222.10, sent.reimbursementAmountCad, 0.0001)
        assertEquals(69, sent.policyRateCentsPerKm)
    }

    @Test
    fun `csv export preserves row order when sending to service`() = runTest {
        val mockUri = mockk<Uri>()
        val slot = slot<List<PayrollExportService.PayrollRow>>()
        every { payrollExportService.generateCsv(capture(slot)) } returns mockUri
        val rows = listOf(
            ExportPayrollSummaryUseCase.PayrollRow("First", "2026-01-01", "2026-01-31", 1.0, 2.0, 3),
            ExportPayrollSummaryUseCase.PayrollRow("Second", "2026-01-01", "2026-01-31", 4.0, 5.0, 6),
        )

        useCase.execute(IntegrationProvider.PAYROLL_CSV, rows)

        assertEquals(listOf("First", "Second"), slot.captured.map { it.employeeName })
    }

    @Test
    fun `csv export invokes service exactly once`() = runTest {
        every { payrollExportService.generateCsv(any()) } returns mockk()
        val rows = listOf(
            ExportPayrollSummaryUseCase.PayrollRow("A", "2026-01-01", "2026-01-31", 1.0, 1.0, 1),
        )

        useCase.execute(IntegrationProvider.PAYROLL_CSV, rows)

        verify(exactly = 1) { payrollExportService.generateCsv(any()) }
    }

    @Test
    fun `quickbooks path does not invoke csv service`() = runTest {
        val rows = listOf(
            ExportPayrollSummaryUseCase.PayrollRow("A", "2026-01-01", "2026-01-31", 1.0, 1.0, 1),
        )

        useCase.execute(IntegrationProvider.QUICKBOOKS, rows)

        verify(exactly = 0) { payrollExportService.generateCsv(any()) }
    }

    @Test
    fun `xero path does not invoke csv service`() = runTest {
        val rows = listOf(
            ExportPayrollSummaryUseCase.PayrollRow("A", "2026-01-01", "2026-01-31", 1.0, 1.0, 1),
        )

        useCase.execute(IntegrationProvider.XERO, rows)

        verify(exactly = 0) { payrollExportService.generateCsv(any()) }
    }

    @Test
    fun `csv path propagates export service failures`() = runTest {
        every { payrollExportService.generateCsv(any()) } throws IllegalStateException("disk write failed")
        val rows = listOf(
            ExportPayrollSummaryUseCase.PayrollRow("A", "2026-01-01", "2026-01-31", 1.0, 1.0, 1),
        )

        try {
            useCase.execute(IntegrationProvider.PAYROLL_CSV, rows)
            fail("Expected IllegalStateException")
        } catch (error: IllegalStateException) {
            assertEquals("disk write failed", error.message)
        }
    }

    @Test
    fun `quickbooks error message references quickbooks label`() = runTest {
        val rows = listOf(
            ExportPayrollSummaryUseCase.PayrollRow("A", "2026-01-01", "2026-01-31", 1.0, 1.0, 1),
        )
        val result = useCase.execute(IntegrationProvider.QUICKBOOKS, rows)

        assertTrue(result is ExportPayrollSummaryUseCase.ExportResult.Error)
        result as ExportPayrollSummaryUseCase.ExportResult.Error
        assertTrue(result.message.contains("QuickBooks"))
        assertFalse(result.message.contains("Xero"))
    }
}
