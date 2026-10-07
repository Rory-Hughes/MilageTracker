package com.example.mileagetracker.domain.usecase.drive

import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.domain.repository.DriveRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Returns a live [Flow] of unclassified active drives for [driverUid],
 * ordered newest-first.
 *
 * ## Why a use case for a thin wrapper?
 * Even a simple read benefits from a named use case:
 * - The ViewModel imports `GetUnclassifiedDrivesUseCase`, not
 *   `DriveRepository` — the dependency is self-documenting.
 * - Future phases can inject a classifier here (e.g. auto-suggest logic)
 *   without changing the ViewModel contract.
 * - Unit tests mock the use case, not the repository, keeping test
 *   setup minimal.
 *
 * This use case is deliberately stateless: it holds no data, caches
 * nothing, and delegates entirely to the repository [Flow].  The
 * ViewModel is responsible for collecting and presenting the stream.
 */
class GetUnclassifiedDrivesUseCase @Inject constructor(
    private val driveRepository: DriveRepository,
) {
    /**
     * @param driverUid the UID of the driver whose unclassified drives
     *                  should be returned.
     * @return a cold [Flow] that emits the current list and re-emits
     *         whenever the underlying data changes.
     */
    operator fun invoke(driverUid: String): Flow<List<DriveEntity>> =
        driveRepository.getUnclassifiedDrives(driverUid)
}
