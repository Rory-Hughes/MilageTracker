package com.example.mileagetracker.domain.usecase.tracking

import android.util.Log
import com.example.mileagetracker.data.local.TripBufferStore
import com.example.mileagetracker.data.local.entity.BufferedTripEntity
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.RoutePointEntity
import com.example.mileagetracker.domain.usecase.drive.PersistTrackedDriveUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FlushTripBufferUseCaseTest {

    private lateinit var tripBufferStore: TripBufferStore
    private lateinit var persistTrackedDriveUseCase: PersistTrackedDriveUseCase
    private lateinit var useCase: FlushTripBufferUseCase

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        tripBufferStore = mockk(relaxed = true)
        persistTrackedDriveUseCase = mockk()
        useCase = FlushTripBufferUseCase(tripBufferStore, persistTrackedDriveUseCase)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun testBufferedEntity(id: Long) = BufferedTripEntity(
        id = id,
        tripJson = "{}",
        routePointsJson = "[]",
        retryCount = 0,
        createdAt = 1000L,
    )

    @Test
    fun `flushes pending trips successfully`() = runTest {
        val entity = testBufferedEntity(1L)
        val drive = mockk<DriveEntity>(relaxed = true) { every { driverUid } returns "u1" }

        coEvery { tripBufferStore.getPending() } returns listOf(entity)
        coEvery { tripBufferStore.deserializeDrive(any()) } returns drive
        coEvery { tripBufferStore.deserializeRoutePoints(any(), any()) } returns emptyList()
        coEvery { persistTrackedDriveUseCase(any(), any(), any(), any()) } returns 10
        coEvery { tripBufferStore.pendingCount() } returns 0

        val result = useCase()

        assertEquals(1, result.flushed)
        assertEquals(0, result.failed)
        coVerify { persistTrackedDriveUseCase(drive, emptyList(), "u1", emptyList()) }
        coVerify { tripBufferStore.remove(1L) }
    }

    @Test
    fun `handles failure by incrementing retry count`() = runTest {
        val entity = testBufferedEntity(1L)
        coEvery { tripBufferStore.getPending() } returns listOf(entity)
        coEvery { tripBufferStore.deserializeDrive(any()) } throws RuntimeException("JSON error")
        coEvery { tripBufferStore.pendingCount() } returns 1

        val result = useCase()

        assertEquals(0, result.flushed)
        assertEquals(1, result.failed)
        coVerify { tripBufferStore.update(match { it.id == 1L && it.retryCount == 1 }) }
    }

    @Test
    fun `empty buffer returns a zero result and processes nothing`() = runTest {
        coEvery { tripBufferStore.getPending() } returns emptyList()

        val result = useCase()

        assertEquals(FlushResult(flushed = 0, failed = 0, remaining = 0), result)
        coVerify(exactly = 0) { persistTrackedDriveUseCase(any(), any(), any(), any()) }
    }

    @Test
    fun `prunes exhausted rows before processing`() = runTest {
        coEvery { tripBufferStore.getPending() } returns emptyList()

        useCase()

        coVerify { tripBufferStore.pruneExhausted() }
    }

    @Test
    fun `mixed batch counts successes and failures independently`() = runTest {
        val good = testBufferedEntity(1L).copy(tripJson = "GOOD")
        val bad = testBufferedEntity(2L).copy(tripJson = "BAD")
        val drive = mockk<DriveEntity>(relaxed = true) { every { driverUid } returns "u1" }

        coEvery { tripBufferStore.getPending() } returns listOf(good, bad)
        coEvery { tripBufferStore.deserializeDrive("GOOD") } returns drive
        coEvery { tripBufferStore.deserializeRoutePoints(any(), any()) } returns emptyList()
        coEvery { tripBufferStore.deserializeDrive("BAD") } throws RuntimeException("corrupt")
        coEvery { persistTrackedDriveUseCase(any(), any(), any(), any()) } returns 10
        coEvery { tripBufferStore.pendingCount() } returns 1

        val result = useCase()

        assertEquals(1, result.flushed)
        assertEquals(1, result.failed)
        coVerify { tripBufferStore.remove(1L) }
        coVerify { tripBufferStore.update(match { it.id == 2L && it.retryCount == 1 }) }
    }

    @Test
    fun `falls back to SYSTEM actor when buffered drive has a blank uid`() = runTest {
        val entity = testBufferedEntity(1L)
        val drive = mockk<DriveEntity>(relaxed = true) { every { driverUid } returns "" }

        coEvery { tripBufferStore.getPending() } returns listOf(entity)
        coEvery { tripBufferStore.deserializeDrive(any()) } returns drive
        coEvery { tripBufferStore.deserializeRoutePoints(any(), any()) } returns emptyList()
        coEvery { persistTrackedDriveUseCase(any(), any(), any(), any()) } returns 10
        coEvery { tripBufferStore.pendingCount() } returns 0

        useCase()

        coVerify { persistTrackedDriveUseCase(drive, emptyList(), "SYSTEM", emptyList()) }
    }

    @Test
    fun `passes buffered route points into tracked-drive persistence`() = runTest {
        val entity = testBufferedEntity(1L)
        val drive = mockk<DriveEntity>(relaxed = true) { every { driverUid } returns "u1" }
        val point = mockk<RoutePointEntity>(relaxed = true)

        coEvery { tripBufferStore.getPending() } returns listOf(entity)
        coEvery { tripBufferStore.deserializeDrive(any()) } returns drive
        coEvery { tripBufferStore.deserializeRoutePoints(any(), 0) } returns listOf(point)
        coEvery { persistTrackedDriveUseCase(any(), any(), any(), any()) } returns 10
        coEvery { tripBufferStore.pendingCount() } returns 0

        useCase()

        coVerify { persistTrackedDriveUseCase(drive, listOf(point), "u1", emptyList()) }
    }
}
