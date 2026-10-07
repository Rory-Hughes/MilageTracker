package com.example.mileagetracker.ui.integrations

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.data.repository.ApprovalRepository
import com.example.mileagetracker.data.repository.IntegrationRepository
import com.example.mileagetracker.domain.model.IntegrationConfig
import com.example.mileagetracker.domain.model.IntegrationProvider
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.VehicleExpenseRepository
import com.example.mileagetracker.domain.usecase.ExportPayrollSummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class IntegrationsViewModel @Inject constructor(
    private val integrationRepository: IntegrationRepository,
    private val approvalRepository: ApprovalRepository,
    private val driveRepository: DriveRepository,
    private val vehicleExpenseRepository: VehicleExpenseRepository,
    private val exportPayrollSummaryUseCase: ExportPayrollSummaryUseCase,
) : ViewModel() {

    private val _integrations = MutableStateFlow<List<IntegrationConfig>>(emptyList())
    val integrations: StateFlow<List<IntegrationConfig>> = _integrations.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    init {
        loadIntegrations()
    }

    fun loadIntegrations() {
        viewModelScope.launch {
            _integrations.value = integrationRepository.getAllConfigs()
        }
    }

    fun enableCsvExport() {
        viewModelScope.launch {
            val config = IntegrationConfig(
                provider = IntegrationProvider.PAYROLL_CSV,
                accessToken = "",
                connectedAt = System.currentTimeMillis(),
            )
            integrationRepository.saveConfig(config)
            loadIntegrations()
            _syncState.value = SyncState.Connected(IntegrationProvider.PAYROLL_CSV)
        }
    }

    fun disconnect(provider: IntegrationProvider) {
        viewModelScope.launch {
            integrationRepository.revokeConfig(provider)
            loadIntegrations()
            _syncState.value = SyncState.Disconnected(provider)
        }
    }

    fun syncNow(provider: IntegrationProvider) {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing(provider)
            val rows = buildPayrollRowsForCurrentMonth()
            when (val result = exportPayrollSummaryUseCase.execute(provider, rows)) {
                is ExportPayrollSummaryUseCase.ExportResult.CsvReady -> {
                    markLastSynced(provider)
                    loadIntegrations()
                    _syncState.value = SyncState.CsvReady(result.uri)
                }

                is ExportPayrollSummaryUseCase.ExportResult.SyncSuccess -> {
                    markLastSynced(provider)
                    loadIntegrations()
                    _syncState.value = SyncState.SyncSuccess(result.provider, result.recordCount)
                }

                is ExportPayrollSummaryUseCase.ExportResult.Error -> {
                    _syncState.value = SyncState.Error(result.provider, result.message)
                }
            }
        }
    }

    fun setSyncError(provider: IntegrationProvider, message: String) {
        _syncState.value = SyncState.Error(provider, message)
    }

    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }

    private fun markLastSynced(provider: IntegrationProvider) {
        val existing = integrationRepository.getConfig(provider) ?: return
        integrationRepository.saveConfig(existing.copy(lastSyncAt = System.currentTimeMillis()))
    }

    private suspend fun buildPayrollRowsForCurrentMonth(): List<ExportPayrollSummaryUseCase.PayrollRow> {
        val zone = ZoneId.systemDefault()
        val endDate = LocalDate.now(zone)
        val startDate = endDate.withDayOfMonth(1)
        val fromMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMillis = endDate
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli() - 1

        val approved = approvalRepository.getApprovedInRange(fromMillis, toMillis)
        if (approved.isEmpty()) {
            return emptyList()
        }

        val accumulators = linkedMapOf<Long, PayrollAccumulator>()
        for (request in approved) {
            val accumulator = accumulators.getOrPut(request.submittedByUserId) { PayrollAccumulator() }

            request.tripId?.let { tripId ->
                val drive = driveRepository.getDriveById(tripId.toInt())
                if (drive != null) {
                    accumulator.totalKm += drive.distanceKm.toDouble()
                    val reimbursement = drive.reimbursementAmountSnapshot?.toDouble()
                        ?: (drive.distanceKm * drive.appliedRateSnapshot).toDouble()
                    accumulator.reimbursementAmountCad += reimbursement

                    val rateCents = (drive.appliedRateSnapshot * 100f).toInt()
                    if (rateCents > 0) {
                        accumulator.rateCentsTotal += rateCents
                        accumulator.rateCount += 1
                    }
                }
            }

            request.expenseId?.let { expenseId ->
                val expense = vehicleExpenseRepository.getById(expenseId.toInt())
                if (expense?.isBusinessUse == true) {
                    accumulator.reimbursementAmountCad += expense.amountCad
                }
            }
        }

        val periodStart = startDate.toString()
        val periodEnd = endDate.toString()

        return accumulators
            .filterValues { it.totalKm > 0.0 || it.reimbursementAmountCad > 0.0 }
            .map { (submittedByUserId, acc) ->
                val avgRate = if (acc.rateCount > 0) {
                    (acc.rateCentsTotal.toDouble() / acc.rateCount.toDouble()).roundToInt()
                } else {
                    0
                }
                ExportPayrollSummaryUseCase.PayrollRow(
                    employeeName = "Driver $submittedByUserId",
                    periodStart = periodStart,
                    periodEnd = periodEnd,
                    totalKm = acc.totalKm,
                    reimbursementAmountCad = acc.reimbursementAmountCad,
                    policyRateCentsPerKm = avgRate,
                )
            }
    }

    private data class PayrollAccumulator(
        var totalKm: Double = 0.0,
        var reimbursementAmountCad: Double = 0.0,
        var rateCentsTotal: Int = 0,
        var rateCount: Int = 0,
    )

    sealed class SyncState {
        data object Idle : SyncState()
        data class Connected(val provider: IntegrationProvider) : SyncState()
        data class Disconnected(val provider: IntegrationProvider) : SyncState()
        data class Syncing(val provider: IntegrationProvider) : SyncState()
        data class CsvReady(val uri: Uri) : SyncState()
        data class SyncSuccess(val provider: IntegrationProvider, val count: Int) : SyncState()
        data class Error(val provider: IntegrationProvider, val message: String) : SyncState()
    }
}
