package com.example.mileagetracker.repository

import android.content.Context
import com.example.mileagetracker.data.local.dao.OdometerCaptureDao
import com.example.mileagetracker.data.local.entity.OdometerCaptureEntity
import com.example.mileagetracker.data.repository.OdometerRepositoryImpl
import com.example.mileagetracker.domain.model.OdometerBoundaryType
import com.example.mileagetracker.domain.model.OdometerCapture
import com.example.mileagetracker.domain.model.OdometerCaptureType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OdometerRepositoryImplTest {

    private lateinit var dao: OdometerCaptureDao
    private lateinit var context: Context
    private lateinit var repository: OdometerRepositoryImpl

    @Before
    fun setUp() {
        dao = mockk()
        context = mockk(relaxed = true)
        repository = OdometerRepositoryImpl(dao, context)
    }

    private fun testCapture(
        id: Int = 0,
        vehicleId: Int = 1,
        readingKm: Int = 100,
        photoUri: String? = null
    ) = OdometerCapture(
        id = id,
        vehicleId = vehicleId,
        readingKm = readingKm,
        captureDate = 1000L,
        photoUri = photoUri,
        captureType = OdometerCaptureType.MANUAL,
        notes = "Test",
        createdAt = 1000L
    )

    @Test
    fun `saveCapture with null photoUri delegates to dao`() = runTest {
        val capture = testCapture(photoUri = null)
        coEvery { dao.insert(any()) } returns 5L

        val result = repository.saveCapture(capture)

        assertEquals(5, result.id)
        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test
    fun `observeCapturesForVehicle maps dao results to domain`() = runTest {
        val entity = OdometerCaptureEntity(
            id = 1,
            vehicleId = 1,
            captureDate = 1000L,
            readingKm = 100,
            photoUri = null,
            captureType = "MANUAL",
            notes = "Test",
            createdAt = 1000L
        )
        every { dao.getForVehicle(1) } returns flowOf(listOf(entity))

        val result = repository.observeCapturesForVehicle(1).first()

        assertEquals(1, result.size)
        assertEquals(1, result[0].id)
        assertEquals(OdometerCaptureType.MANUAL, result[0].captureType)
    }

    @Test
    fun `getLatestReading returns domain object`() = runTest {
        val entity = OdometerCaptureEntity(
            id = 2,
            vehicleId = 1,
            captureDate = 2000L,
            readingKm = 200,
            photoUri = null,
            captureType = "AUTO",
            notes = "Latest",
            createdAt = 2000L
        )
        coEvery { dao.getLatestForVehicle(1) } returns entity

        val result = repository.getLatestReading(1)

        assertEquals(2, result?.id)
        assertEquals("Latest", result?.notes)
    }

    @Test
    fun `getFirstCaptureForVehicleInYear delegates to dao`() = runTest {
        coEvery { dao.getFirstCaptureForVehicleInYear(1, 0L, 100L) } returns null

        val result = repository.getFirstCaptureForVehicleInYear(1, 0L, 100L)

        assertEquals(null, result)
        coVerify(exactly = 1) { dao.getFirstCaptureForVehicleInYear(1, 0L, 100L) }
    }

    @Test
    fun `getLastCaptureForVehicleInYear delegates to dao`() = runTest {
        coEvery { dao.getLastCaptureForVehicleInYear(1, 0L, 100L) } returns null

        val result = repository.getLastCaptureForVehicleInYear(1, 0L, 100L)

        assertEquals(null, result)
        coVerify(exactly = 1) { dao.getLastCaptureForVehicleInYear(1, 0L, 100L) }
    }

    @Test
    fun `opening evidence prefers current year opening capture`() = runTest {
        val opening = testEntity(
            readingKm = 1_000,
            taxYear = 2026,
            boundaryType = OdometerBoundaryType.OPENING.name,
        )
        coEvery { dao.getOpeningCaptureForVehicleYear(1, 2026) } returns opening

        val result = repository.getOpeningEvidenceForVehicleYear(1, 2026, 0L, 100L)

        assertEquals(1_000, result?.readingKm)
        assertEquals(OdometerBoundaryType.OPENING, result?.boundaryType)
        coVerify(exactly = 0) { dao.getClosingCaptureForVehicleYear(1, 2025) }
        coVerify(exactly = 0) { dao.getFirstOtherCaptureForVehicleYear(any(), any(), any(), any()) }
    }

    @Test
    fun `opening evidence uses prior year closing when opening missing`() = runTest {
        val closing = testEntity(
            readingKm = 2_000,
            taxYear = 2025,
            boundaryType = OdometerBoundaryType.CLOSING.name,
        )
        coEvery { dao.getOpeningCaptureForVehicleYear(1, 2026) } returns null
        coEvery { dao.getClosingCaptureForVehicleYear(1, 2025) } returns closing

        val result = repository.getOpeningEvidenceForVehicleYear(1, 2026, 0L, 100L)

        assertEquals(2_000, result?.readingKm)
        assertEquals(2025, result?.taxYear)
        assertEquals(OdometerBoundaryType.CLOSING, result?.boundaryType)
        coVerify(exactly = 0) { dao.getFirstOtherCaptureForVehicleYear(any(), any(), any(), any()) }
    }

    @Test
    fun `opening evidence falls back to first other capture in year`() = runTest {
        val fallback = testEntity(
            readingKm = 3_000,
            taxYear = 2026,
            boundaryType = OdometerBoundaryType.OTHER.name,
        )
        coEvery { dao.getOpeningCaptureForVehicleYear(1, 2026) } returns null
        coEvery { dao.getClosingCaptureForVehicleYear(1, 2025) } returns null
        coEvery { dao.getFirstOtherCaptureForVehicleYear(1, 2026, 0L, 100L) } returns fallback

        val result = repository.getOpeningEvidenceForVehicleYear(1, 2026, 0L, 100L)

        assertEquals(3_000, result?.readingKm)
        coVerify { dao.getFirstOtherCaptureForVehicleYear(1, 2026, 0L, 100L) }
    }

    @Test
    fun `closing evidence prefers current year closing capture`() = runTest {
        val closing = testEntity(
            readingKm = 4_000,
            taxYear = 2026,
            boundaryType = OdometerBoundaryType.CLOSING.name,
        )
        coEvery { dao.getClosingCaptureForVehicleYear(1, 2026) } returns closing

        val result = repository.getClosingEvidenceForVehicleYear(1, 2026, 0L, 100L)

        assertEquals(4_000, result?.readingKm)
        assertEquals(OdometerBoundaryType.CLOSING, result?.boundaryType)
        coVerify(exactly = 0) { dao.getLastOtherCaptureForVehicleYear(any(), any(), any(), any()) }
    }

    @Test
    fun `closing evidence falls back to last other capture in year`() = runTest {
        val fallback = testEntity(
            readingKm = 5_000,
            taxYear = 2026,
            boundaryType = OdometerBoundaryType.OTHER.name,
        )
        coEvery { dao.getClosingCaptureForVehicleYear(1, 2026) } returns null
        coEvery { dao.getLastOtherCaptureForVehicleYear(1, 2026, 0L, 100L) } returns fallback

        val result = repository.getClosingEvidenceForVehicleYear(1, 2026, 0L, 100L)

        assertEquals(5_000, result?.readingKm)
        coVerify { dao.getLastOtherCaptureForVehicleYear(1, 2026, 0L, 100L) }
    }

    private fun testEntity(
        id: Int = 1,
        vehicleId: Int = 1,
        readingKm: Int,
        taxYear: Int,
        boundaryType: String,
    ) = OdometerCaptureEntity(
        id = id,
        vehicleId = vehicleId,
        captureDate = 1000L,
        readingKm = readingKm,
        taxYear = taxYear,
        photoUri = null,
        captureType = "MANUAL",
        boundaryType = boundaryType,
        notes = "Test",
        createdAt = 1000L,
    )
}
