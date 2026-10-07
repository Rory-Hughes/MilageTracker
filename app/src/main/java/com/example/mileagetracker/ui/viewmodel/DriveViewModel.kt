package com.example.mileagetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.CraRateRegion
import com.example.mileagetracker.domain.model.RateMode
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.SettingsRepository
import com.example.mileagetracker.domain.usecase.drive.ClassifyTripUseCase
import com.example.mileagetracker.domain.usecase.drive.GetUnclassifiedDrivesUseCase
import com.example.mileagetracker.domain.usecase.drive.ResolveDriveLocationsUseCase
import com.example.mileagetracker.domain.usecase.drive.UpdateDriveUseCase
import com.example.mileagetracker.domain.usecase.rate.CalculateReimbursementUseCase
import com.example.mileagetracker.domain.usecase.rate.GetCraRateForYearUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class DriveViewModel @Inject constructor(
    private val getUnclassifiedDrivesUseCase: GetUnclassifiedDrivesUseCase,
    private val updateDriveUseCase: UpdateDriveUseCase,
    private val resolveDriveLocationsUseCase: ResolveDriveLocationsUseCase,
    private val classifyTripUseCase: ClassifyTripUseCase,
    private val calculateReimbursementUseCase: CalculateReimbursementUseCase,
    private val getCraRateForYearUseCase: GetCraRateForYearUseCase,
    private val driveRepository: DriveRepository,
    settingsRepository: SettingsRepository,
    currentUserProvider: CurrentUserProvider,
) : ViewModel() {

    private val driverUid: String =
        currentUserProvider.requireUid()
    private val locationEnrichmentInFlight = mutableSetOf<Int>()

    val craRateRegion: StateFlow<CraRateRegion?> =
        settingsRepository.getCraRateRegion()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    val rateMode: StateFlow<RateMode> =
        settingsRepository.getRateMode()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = RateMode.CRA_STANDARD,
            )

    // ───────────────────────────────────────────────────────────────────────────
    // Unclassified drives
    // ───────────────────────────────────────────────────────────────────────────

    val unclassifiedUiState: StateFlow<UnclassifiedUiState> =
        getUnclassifiedDrivesUseCase(driverUid)
            .onEach(::enqueueCoordinateLocationBackfill)
            .map<List<DriveEntity>, UnclassifiedUiState> { drives ->
                UnclassifiedUiState.Success(drives)
            }
            .catch { e ->
                emit(UnclassifiedUiState.Error(e.message ?: "Unknown error loading drives"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UnclassifiedUiState.Loading,
            )

    // ───────────────────────────────────────────────────────────────────────────
    // All drives (newest first) — single shared source for recent trips and YTD
    // totals.  Both flows below derive from this one StateFlow so the underlying
    // Room query is collected only once instead of three times.
    // ───────────────────────────────────────────────────────────────────────────

    private val allDrives: StateFlow<List<DriveEntity>> =
        driveRepository.getAllDrives(driverUid)
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    // ───────────────────────────────────────────────────────────────────────────
    // Recent trips — 3 most recent active trips of any category
    // ───────────────────────────────────────────────────────────────────────────

    val recentTrips: StateFlow<List<DriveEntity>> =
        allDrives
            .map { drives -> drives.take(3) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    // ───────────────────────────────────────────────────────────────────────────
    // YTD dashboard KPIs
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * YTD business kilometres from the database aggregate query.
     * Returns 0f when no qualifying rows exist.
     */
    val ytdBusinessDistance: StateFlow<Float> =
        driveRepository
            .getYearToDateBusinessDistance(
                driverUid = driverUid,
                fromMillis = yearStartMillis(),
                toMillis = yearEndMillis(),
            )
            .map { it ?: 0f }
            .catch { emit(0f) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0f,
            )

    /**
     * YTD CRA prescribed-allowance estimate in CAD.
     *
     * Computed **live** from [ytdBusinessDistance] by running the two-tier CRA
     * formula once for the whole year via [CalculateReimbursementUseCase].
     *
     * This deliberately does **not** sum per-trip
     * [DriveEntity.reimbursementAmountSnapshot] values. Those snapshots are
     * frozen at classification time with whatever YTD position the trip had
     * then, so they drift whenever trips are classified out of order, edited,
     * deleted, or written by a path that forgets to set them — making the
     * summed total unreliable. Computing from the authoritative business-km
     * aggregate keeps the dollar figure consistent with the "Business km"
     * number shown right beside it.
     *
     * Re-evaluates when either the business distance or the CRA region
     * changes. Returns 0 when the region is unset or no rate is seeded for the
     * year; the dashboard gates display on those conditions separately.
     */
    val ytdDeductions: StateFlow<Float> =
        combine(ytdBusinessDistance, craRateRegion) { businessKm, _ -> businessKm }
            .map { businessKm ->
                if (businessKm <= 0f) {
                    0f
                } else {
                    runCatching {
                        calculateReimbursementUseCase(
                            taxYear = currentYear(),
                            newDistanceKm = businessKm,
                        ).amountDollars.toFloat()
                    }.getOrDefault(0f)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0f,
            )

    // ───────────────────────────────────────────────────────────────────────────
    // Classification action state
    // ───────────────────────────────────────────────────────────────────────────

    private val _classifyState = MutableStateFlow<ClassifyState>(ClassifyState.Idle)
    val classifyState: StateFlow<ClassifyState> = _classifyState.asStateFlow()

    // ───────────────────────────────────────────────────────────────────────────
    // Public actions
    // ───────────────────────────────────────────────────────────────────────────

    fun classifyDrive(drive: DriveEntity, isBusiness: Boolean) {
        _classifyState.value = ClassifyState.Loading
        viewModelScope.launch {
            runCatching {
                if (isBusiness) classifyAsBusiness(drive) else classifyAsPersonal(drive)
            }.onSuccess {
                _classifyState.value = ClassifyState.Idle
            }.onFailure { error ->
                _classifyState.value = ClassifyState.Error(error.message ?: "Classification failed")
            }
        }
    }

    fun clearClassifyError() {
        _classifyState.value = ClassifyState.Idle
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Private classification helpers
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Delegate both swipe paths to [ClassifyTripUseCase] — the single source
     * of truth for category + CRA snapshot writes. Earlier this VM
     * reimplemented the math inline and (a) stored the total reimbursement
     * in [DriveEntity.appliedRateSnapshot] (it's supposed to be $/km) and
     * (b) never wrote [DriveEntity.reimbursementAmountSnapshot] at all,
     * making the dashboard YTD total undercount swipe-classified trips.
     */
    private suspend fun classifyAsBusiness(drive: DriveEntity) {
        classifyTripUseCase(
            driveId = drive.id,
            category = TripCategory.BUSINESS,
            purposeText = drive.purposeText,
            actorUid = driverUid,
        )
    }

    private suspend fun classifyAsPersonal(drive: DriveEntity) {
        classifyTripUseCase(
            driveId = drive.id,
            category = TripCategory.PERSONAL,
            purposeText = drive.purposeText,
            actorUid = driverUid,
        )
    }

    private fun enqueueCoordinateLocationBackfill(drives: List<DriveEntity>) {
        drives
            .filter(resolveDriveLocationsUseCase::requiresResolution)
            .forEach { drive ->
                synchronized(locationEnrichmentInFlight) {
                    if (!locationEnrichmentInFlight.add(drive.id)) return@forEach
                }
                viewModelScope.launch {
                    try {
                        val resolvedDrive = resolveDriveLocationsUseCase(drive)
                        if (resolvedDrive != drive) {
                            updateDriveUseCase(updated = resolvedDrive, actorUid = driverUid)
                        }
                    } finally {
                        synchronized(locationEnrichmentInFlight) {
                            locationEnrichmentInFlight.remove(drive.id)
                        }
                    }
                }
            }
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Helpers
    // ───────────────────────────────────────────────────────────────────────────

    private fun currentYear(): Int = LocalDate.now().year

    private fun yearStartMillis(): Long =
        LocalDate.of(currentYear(), 1, 1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun yearEndMillis(): Long =
        LocalDate.of(currentYear(), 12, 31)
            .atTime(23, 59, 59, 999_000_000)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}

// ───────────────────────────────────────────────────────────────────────────
// UI state types
// ───────────────────────────────────────────────────────────────────────────

sealed class UnclassifiedUiState {
    object Loading : UnclassifiedUiState()
    data class Success(val drives: List<DriveEntity>) : UnclassifiedUiState()
    data class Error(val message: String) : UnclassifiedUiState()
}

sealed class ClassifyState {
    object Idle : ClassifyState()
    object Loading : ClassifyState()
    data class Error(val message: String) : ClassifyState()
}
