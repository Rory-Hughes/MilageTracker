package com.example.mileagetracker.worker

import com.example.mileagetracker.data.local.dao.VehicleDao
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.model.OdometerBoundaryType
import com.example.mileagetracker.domain.model.OdometerCapture
import com.example.mileagetracker.domain.repository.OdometerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class OdometerEvidenceReminderCheckerTest {

    private lateinit var vehicleDao: VehicleDao
    private lateinit var odometerRepository: OdometerRepository
    private lateinit var checker: OdometerEvidenceReminderChecker

    @Before
    fun setUp() {
        vehicleDao = mockk()
        odometerRepository = mockk()
        checker = OdometerEvidenceReminderChecker(vehicleDao, odometerRepository)
    }

    @Test
    fun `returns zero outside January`() = runTest {
        val result = checker.countVehiclesMissingOpeningEvidence(LocalDate.of(2026, 2, 1))

        assertEquals(0, result)
        coVerify(exactly = 0) { vehicleDao.getActiveVehiclesForFiscalYearOnce(any()) }
    }

    @Test
    fun `counts active vehicles missing January opening evidence`() = runTest {
        coEvery { vehicleDao.getActiveVehiclesForFiscalYearOnce(2026) } returns listOf(
            vehicle(id = 1),
            vehicle(id = 2),
        )
        coEvery {
            odometerRepository.getOpeningEvidenceForVehicleYear(1, 2026, any(), any())
        } returns capture(vehicleId = 1)
        coEvery {
            odometerRepository.getOpeningEvidenceForVehicleYear(2, 2026, any(), any())
        } returns null

        val result = checker.countVehiclesMissingOpeningEvidence(LocalDate.of(2026, 1, 5))

        assertEquals(1, result)
    }

    @Test
    fun `prior year closing returned by repository satisfies January evidence`() = runTest {
        coEvery { vehicleDao.getActiveVehiclesForFiscalYearOnce(2026) } returns listOf(vehicle(id = 1))
        coEvery {
            odometerRepository.getOpeningEvidenceForVehicleYear(1, 2026, any(), any())
        } returns capture(
            vehicleId = 1,
            taxYear = 2025,
            boundaryType = OdometerBoundaryType.CLOSING,
        )

        val result = checker.countVehiclesMissingOpeningEvidence(LocalDate.of(2026, 1, 10))

        assertEquals(0, result)
    }

    private fun vehicle(id: Int) = VehicleEntity(
        id = id,
        ownerUid = "u1",
        displayName = "Car $id",
        fiscalYear = 2026,
    )

    private fun capture(
        vehicleId: Int,
        taxYear: Int = 2026,
        boundaryType: OdometerBoundaryType = OdometerBoundaryType.OPENING,
    ) = OdometerCapture(
        id = 1,
        vehicleId = vehicleId,
        captureDate = 1_767_225_600_000L,
        readingKm = 10_000,
        taxYear = taxYear,
        boundaryType = boundaryType,
    )
}
