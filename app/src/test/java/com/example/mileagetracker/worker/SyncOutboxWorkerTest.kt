package com.example.mileagetracker.worker

import android.content.Context
import android.util.Log
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.example.mileagetracker.domain.auth.SignedInUserRequiredException
import com.example.mileagetracker.domain.model.CloudUploadConsentRequiredException
import com.example.mileagetracker.domain.repository.SyncPushResult
import com.example.mileagetracker.domain.repository.SyncRepository
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

class SyncOutboxWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var repository: SyncRepository
    private lateinit var worker: SyncOutboxWorker

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        repository = mockk()
        worker = SyncOutboxWorker(context, workerParams, repository)
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>(), any<Throwable>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `doWork returns success on repo success`() = runTest {
        coEvery { repository.push() } returns SyncPushResult(1, 0, 0)
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork returns success on SignedInUserRequiredException`() = runTest {
        coEvery { repository.push() } throws SignedInUserRequiredException()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork returns success on CloudUploadConsentRequiredException`() = runTest {
        coEvery { repository.push() } throws CloudUploadConsentRequiredException()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork returns retry on unexpected error`() = runTest {
        coEvery { repository.push() } throws RuntimeException("Network error")
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
