package com.example.mileagetracker.worker

import android.content.Context
import android.util.Log
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.example.mileagetracker.domain.usecase.tracking.FlushResult
import com.example.mileagetracker.domain.usecase.tracking.FlushTripBufferUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TripBufferFlushWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var useCase: FlushTripBufferUseCase
    private lateinit var worker: TripBufferFlushWorker

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        useCase = mockk()
        worker = TripBufferFlushWorker(context, workerParams, useCase)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `doWork returns success when use case completes`() = runTest {
        coEvery { useCase() } returns FlushResult(1, 0, 0)
        
        val result = worker.doWork()
        
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork returns retry on exception`() = runTest {
        coEvery { useCase() } throws RuntimeException("DB Error")
        
        val result = worker.doWork()
        
        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
