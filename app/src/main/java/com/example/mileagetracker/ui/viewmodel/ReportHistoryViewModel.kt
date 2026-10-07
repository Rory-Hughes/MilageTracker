package com.example.mileagetracker.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.data.local.entity.ReportHistoryEntity
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.displayLabel
import com.example.mileagetracker.domain.repository.ReportHistoryRepository
import com.example.mileagetracker.domain.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ReportHistoryViewModel @Inject constructor(
    private val repository: ReportHistoryRepository,
    private val vehicleRepository: VehicleRepository,
    currentUserProvider: CurrentUserProvider,
) : ViewModel() {

    private val ownerUid = currentUserProvider.requireUid()

    private val _groupedReports =
        MutableStateFlow<Map<Int, List<ReportHistoryListItem>>>(emptyMap())
    val groupedReports: StateFlow<Map<Int, List<ReportHistoryListItem>>> =
        _groupedReports.asStateFlow()

    private val _retentionWarning = MutableStateFlow<ReportHistoryEntity?>(null)
    val retentionWarning: StateFlow<ReportHistoryEntity?> = _retentionWarning.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.getAll(),
                vehicleRepository.getAllVehiclesForUser(ownerUid),
            ) { reports, vehicles ->
                val vehiclesById = vehicles.associateBy(VehicleEntity::id)
                reports
                    .groupBy { it.year }
                    .toSortedMap(compareByDescending { it })
                    .mapValues { (_, yearReports) ->
                        yearReports.map { report ->
                            ReportHistoryListItem(
                                report = report,
                                vehicleLabel = resolveVehicleLabel(report, vehiclesById),
                            )
                        }
                    }
            }.collect { grouped ->
                _groupedReports.value = grouped
            }
        }
    }

    fun reshare(context: Context, report: ReportHistoryEntity) {
        buildShareIntent(context, report)?.let { shareIntent ->
            context.startActivity(
                Intent.createChooser(shareIntent, "Share ${report.format} Report")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun deleteReport(report: ReportHistoryEntity) {
        val retentionCutoff = System.currentTimeMillis() - SIX_YEAR_MS
        if (report.generatedAt > retentionCutoff) {
            _retentionWarning.value = report
            return
        }

        viewModelScope.launch {
            runCatching {
                report.fileUri.toUri().path?.let { path ->
                    val file = File(path)
                    if (file.exists()) file.delete()
                }
                repository.deleteById(report.id)
            }.onFailure { error ->
                Log.e(TAG, "deleteReport failed for id=${report.id}: ${error.message}", error)
            }
        }
    }

    fun clearRetentionWarning() {
        _retentionWarning.value = null
    }

    private fun resolveVehicleLabel(
        report: ReportHistoryEntity,
        vehiclesById: Map<Int, VehicleEntity>,
    ): String = when (val vehicleId = report.vehicleId) {
        null -> "All vehicles"
        else -> vehiclesById[vehicleId]?.displayLabel(vehicleId) ?: "Vehicle $vehicleId"
    }

    private fun buildShareIntent(
        context: Context,
        report: ReportHistoryEntity,
    ): Intent? {
        val file = report.fileUri.toUri().path?.let(::File)
        val contentUri = file
            ?.takeIf { shareableFile ->
                if (shareableFile.exists()) {
                    true
                } else {
                    Log.w(TAG, "reshare: cached file no longer exists at ${shareableFile.absolutePath}")
                    false
                }
            }
            ?.let { shareableFile -> createContentUri(context, shareableFile) }

        if (file == null) {
            Log.w(TAG, "reshare: cannot parse fileUri ${report.fileUri}")
        }

        return contentUri?.let { uri ->
            Intent(Intent.ACTION_SEND).apply {
                type = shareMimeType(report)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private fun createContentUri(
        context: Context,
        file: File,
    ): Uri? = try {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file,
        )
    } catch (e: IllegalArgumentException) {
        Log.e(TAG, "reshare: FileProvider failed: ${e.message}", e)
        null
    }

    private fun shareMimeType(report: ReportHistoryEntity): String = when (report.format.uppercase()) {
        "PDF" -> "application/pdf"
        "CSV" -> "text/csv"
        else -> "*/*"
    }

    private companion object {
        const val TAG = "ReportHistoryViewModel"
        const val SIX_YEAR_MS = 6L * 365 * 24 * 60 * 60 * 1_000L
    }
}

data class ReportHistoryListItem(
    val report: ReportHistoryEntity,
    val vehicleLabel: String,
)
