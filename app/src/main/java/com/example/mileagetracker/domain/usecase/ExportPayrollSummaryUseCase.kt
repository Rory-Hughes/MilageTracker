package com.example.mileagetracker.domain.usecase

import android.net.Uri
import com.example.mileagetracker.data.remote.integrations.PayrollExportService
import com.example.mileagetracker.domain.model.IntegrationProvider
import javax.inject.Inject

/**
 * Aggregates approved trips and expenses for a period and exports them through
 * production-safe solo-MVP channels.
 */
class ExportPayrollSummaryUseCase @Inject constructor(
    private val payrollExportService: PayrollExportService,
) {

    data class PayrollRow(
        val employeeName: String,
        val periodStart: String,
        val periodEnd: String,
        val totalKm: Double,
        val reimbursementAmountCad: Double,
        val policyRateCentsPerKm: Int,
    )

    sealed class ExportResult {
        data class CsvReady(val uri: Uri) : ExportResult()
        data class SyncSuccess(val provider: IntegrationProvider, val recordCount: Int) : ExportResult()
        data class Error(val provider: IntegrationProvider, val message: String) : ExportResult()
    }

    suspend fun execute(
        provider: IntegrationProvider,
        rows: List<PayrollRow>,
    ): ExportResult {
        if (rows.isEmpty()) {
            return ExportResult.Error(provider, "No approved records found for the selected period.")
        }

        return when (provider) {
            IntegrationProvider.PAYROLL_CSV -> {
                val uri = payrollExportService.generateCsv(
                    rows.map {
                        PayrollExportService.PayrollRow(
                            employeeName = it.employeeName,
                            periodStart = it.periodStart,
                            periodEnd = it.periodEnd,
                            totalKm = it.totalKm,
                            reimbursementAmountCad = it.reimbursementAmountCad,
                            policyRateCentsPerKm = it.policyRateCentsPerKm,
                        )
                    }
                )
                ExportResult.CsvReady(uri)
            }

            IntegrationProvider.QUICKBOOKS,
            IntegrationProvider.XERO -> ExportResult.Error(
                provider = provider,
                message = "Direct ${provider.label()} sync is disabled in the mobile app. " +
                    "Use payroll CSV until a secure server-side broker or PKCE flow is configured.",
            )
        }
    }

    private fun IntegrationProvider.label(): String =
        when (this) {
            IntegrationProvider.QUICKBOOKS -> "QuickBooks"
            IntegrationProvider.XERO -> "Xero"
            IntegrationProvider.PAYROLL_CSV -> "Payroll CSV"
        }
}
