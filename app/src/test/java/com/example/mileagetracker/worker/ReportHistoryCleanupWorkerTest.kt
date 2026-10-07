package com.example.mileagetracker.worker

import android.content.Context
import android.util.Log
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.example.mileagetracker.domain.repository.ReportHistoryRepository
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

class ReportHistoryCleanupWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var repository: ReportHistoryRepository
    private lateinit var worker: ReportHistoryCleanupWorker

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        repository = mockk()
        worker = ReportHistoryCleanupWorker(context, workerParams, repository)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `doWork returns success on repository success`() = runTest {
        coEvery { repository.deleteOlderThan(any()) } returns Unit
        
        val result = worker.doWork()
        
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork returns failure on IllegalArgumentException`() = runTest {
        coEvery { repository.deleteOlderThan(any()) } throws IllegalArgumentException("Too recent")
        
        val result = worker.doWork()
        
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `doWork returns retry on unexpected exception`() = runTest {
        coEvery { repository.deleteOlderThan(any()) } throws Exception("DB Error")
        
        val result = worker.doWork()
        
        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
