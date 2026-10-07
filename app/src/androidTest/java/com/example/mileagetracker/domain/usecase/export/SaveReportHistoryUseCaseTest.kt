package com.example.mileagetracker.domain.usecase.export

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.repository.ReportHistoryRepositoryImpl
import com.example.mileagetracker.domain.model.ExportFormat
import com.example.mileagetracker.domain.model.ExportResult
import com.example.mileagetracker.domain.model.LogbookExportRequest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [SaveReportHistoryUseCase] against a real in-memory Room DB.
 *
 * The main value here is verifying the mapping from [ExportResult.Success] and
 * [LogbookExportRequest] into the persisted [ReportHistoryEntity], including
 * the generated-at timestamp set by the use case itself.
 */
@RunWith(AndroidJUnit4::class)
class SaveReportHistoryUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var repository: ReportHistoryRepositoryImpl
    private lateinit var useCase: SaveReportHistoryUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        repository = ReportHistoryRepositoryImpl(db.reportHistoryDao())
        useCase = SaveReportHistoryUseCase(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun invoke_persistsMappedFieldsAndGeneratedAt() = runTest {
        val result = ExportResult.Success(
            fileUri = Uri.parse("file:///exports/logbook-2026.pdf"),
            format = ExportFormat.PDF,
            fileSizeBytes = 4_096L,
        )
        val request = LogbookExportRequest(
            vehicleId = 42,
            taxYear = 2026,
            format = ExportFormat.PDF,
        )
        val before = System.currentTimeMillis()

        val historyId = useCase(result, request)

        val after = System.currentTimeMillis()
        val saved = repository.getById(historyId)!!
        assertEquals("PDF", saved.format)
        assertEquals(2026, saved.year)
        assertEquals(42, saved.vehicleId)
        assertEquals("file:///exports/logbook-2026.pdf", saved.fileUri)
        assertEquals(4_096L, saved.fileSizeBytes)
        assertTrue(saved.generatedAt in before..after)
    }

    @Test
    fun invoke_withNullVehicleId_persistsNullVehicleScope() = runTest {
        val result = ExportResult.Success(
            fileUri = Uri.parse("file:///exports/logbook-all.csv"),
            format = ExportFormat.CSV,
            fileSizeBytes = 2_048L,
        )
        val request = LogbookExportRequest(
            vehicleId = null,
            taxYear = 2025,
            format = ExportFormat.CSV,
        )

        val historyId = useCase(result, request)

        val saved = repository.getById(historyId)!!
        assertEquals("CSV", saved.format)
        assertEquals(2025, saved.year)
        assertNull(saved.vehicleId)
        assertEquals("file:///exports/logbook-all.csv", saved.fileUri)
        assertEquals(2_048L, saved.fileSizeBytes)
    }
}
