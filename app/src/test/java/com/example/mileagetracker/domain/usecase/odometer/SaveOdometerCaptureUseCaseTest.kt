package com.example.mileagetracker.domain.usecase.odometer

import com.example.mileagetracker.domain.model.OdometerCapture
import com.example.mileagetracker.domain.repository.OdometerRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaveOdometerCaptureUseCaseTest {

    private lateinit var repo: OdometerRepository
    private lateinit var useCase: SaveOdometerCaptureUseCase

    @Before
    fun setUp() {
        repo = mockk()
        useCase = SaveOdometerCaptureUseCase(repo)
    }

    private fun testCapture(km: Int) = OdometerCapture(
        vehicleId = 1,
        captureDate = 1000L,
        readingKm = km
    )

    @Test
    fun `returns InvalidReading for non-positive km`() = runTest {
        val result = useCase(testCapture(0))
        assertTrue(result is OdometerSaveResult.InvalidReading)
        assertEquals(
            "Odometer reading must be greater than zero.",
            (result as OdometerSaveResult.InvalidReading).reason,
        )
    }

    @Test
    fun `returns Success for normal reading`() = runTest {
        val capture = testCapture(100)
        coEvery { repo.getLatestReading(1) } returns testCapture(90)
        coEvery { repo.saveCapture(capture) } returns capture.copy(id = 5)

        val result = useCase(capture)

        assertTrue(result is OdometerSaveResult.Success)
        assertEquals(5, (result as OdometerSaveResult.Success).capture.id)
    }

    @Test
    fun `returns RegressionWarning for lower reading`() = runTest {
        val capture = testCapture(80)
        val latest = testCapture(100)
        coEvery { repo.getLatestReading(1) } returns latest
        coEvery { repo.saveCapture(capture) } returns capture.copy(id = 6)

        val result = useCase(capture)

        assertTrue(result is OdometerSaveResult.RegressionWarning)
        val warning = result as OdometerSaveResult.RegressionWarning
        assertEquals(6, warning.saved.id)
        assertEquals(100, warning.previous)
    }

    @Test
    fun `returns Success when no previous reading exists`() = runTest {
        val capture = testCapture(100)
        coEvery { repo.getLatestReading(1) } returns null
        coEvery { repo.saveCapture(capture) } returns capture.copy(id = 1)

        val result = useCase(capture)

        assertTrue(result is OdometerSaveResult.Success)
    }
}
