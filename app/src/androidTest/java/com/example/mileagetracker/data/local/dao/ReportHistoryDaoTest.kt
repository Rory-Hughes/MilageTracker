package com.example.mileagetracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.ReportHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room integration tests for [ReportHistoryDao] against a real (in-memory) SQLite
 * database. These exercise newest-first ordering, single-row lookup, per-id deletion,
 * threshold-based bulk deletion (used by [ReportHistoryCleanupWorker]), the REPLACE
 * conflict strategy, and nullable [vehicleId].
 */
@RunWith(AndroidJUnit4::class)
class ReportHistoryDaoTest {

    private lateinit var db: MileageDatabase
    private lateinit var dao: ReportHistoryDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.reportHistoryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun report(
        id: Int = 0,
        format: String = "CSV",
        year: Int = 2026,
        vehicleId: Int? = null,
        generatedAt: Long = 1_000L,
        fileUri: String = "file:///export.csv",
        fileSizeBytes: Long = 1024L,
    ) = ReportHistoryEntity(
        id = id,
        format = format,
        year = year,
        vehicleId = vehicleId,
        generatedAt = generatedAt,
        fileUri = fileUri,
        fileSizeBytes = fileSizeBytes,
    )

    // ── insert + read-back ─────────────────────────────────────────────────────

    @Test
    fun insert_thenGetById_roundTripsAllFields() = runTest {
        val id = dao.insert(
            report(
                format = "PDF",
                year = 2025,
                vehicleId = 7,
                generatedAt = 5_000L,
                fileUri = "file:///report.pdf",
                fileSizeBytes = 204_800L,
            ),
        ).toInt()

        val loaded = dao.getById(id)!!
        assertEquals("PDF", loaded.format)
        assertEquals(2025, loaded.year)
        assertEquals(7, loaded.vehicleId)
        assertEquals(5_000L, loaded.generatedAt)
        assertEquals("file:///report.pdf", loaded.fileUri)
        assertEquals(204_800L, loaded.fileSizeBytes)
    }

    @Test
    fun insert_withNullVehicleId_persistsNull() = runTest {
        val id = dao.insert(report(vehicleId = null)).toInt()
        assertNull(dao.getById(id)!!.vehicleId)
    }

    @Test
    fun getById_returnsNull_forUnknownId() = runTest {
        assertNull(dao.getById(999))
    }

    // ── getAll: newest-first ordering ─────────────────────────────────────────

    @Test
    fun getAll_ordersNewestGeneratedAtFirst() = runTest {
        dao.insert(report(generatedAt = 1_000L))
        dao.insert(report(generatedAt = 3_000L))
        dao.insert(report(generatedAt = 2_000L))

        val order = dao.getAll().first().map { it.generatedAt }
        assertEquals(listOf(3_000L, 2_000L, 1_000L), order)
    }

    @Test
    fun getAll_emitsEmpty_whenTableIsEmpty() = runTest {
        assertTrue(dao.getAll().first().isEmpty())
    }

    // ── deleteById ────────────────────────────────────────────────────────────

    @Test
    fun deleteById_removesOnlyThatRow() = runTest {
        val id = dao.insert(report(generatedAt = 1_000L)).toInt()
        dao.insert(report(generatedAt = 2_000L))

        dao.deleteById(id)

        val remaining = dao.getAll().first()
        assertEquals(1, remaining.size)
        assertEquals(2_000L, remaining.single().generatedAt)
    }

    @Test
    fun deleteById_onUnknownId_isNoOp() = runTest {
        dao.insert(report())
        dao.deleteById(999)
        assertEquals(1, dao.getAll().first().size)
    }

    // ── deleteOlderThan ───────────────────────────────────────────────────────

    @Test
    fun deleteOlderThan_removesRowsStrictlyBeforeThreshold() = runTest {
        dao.insert(report(generatedAt = 999L))  // strictly before threshold → delete
        dao.insert(report(generatedAt = 1_000L)) // at threshold → keep (strict less-than)
        dao.insert(report(generatedAt = 2_000L)) // after threshold → keep

        dao.deleteOlderThan(thresholdMs = 1_000L)

        val remaining = dao.getAll().first().map { it.generatedAt }
        assertEquals(listOf(2_000L, 1_000L), remaining)
    }

    @Test
    fun deleteOlderThan_whenNoneOlderThanThreshold_isNoOp() = runTest {
        dao.insert(report(generatedAt = 5_000L))

        dao.deleteOlderThan(thresholdMs = 1_000L)

        assertEquals(1, dao.getAll().first().size)
    }

    @Test
    fun deleteOlderThan_removesAllRows_whenThresholdIsMaxLong() = runTest {
        dao.insert(report(generatedAt = 1_000L))
        dao.insert(report(generatedAt = 2_000L))

        dao.deleteOlderThan(thresholdMs = Long.MAX_VALUE)

        assertTrue(dao.getAll().first().isEmpty())
    }

    // ── REPLACE conflict strategy ─────────────────────────────────────────────

    @Test
    fun insert_withSamePrimaryKey_replacesRow() = runTest {
        dao.insert(report(id = 5, format = "CSV", generatedAt = 1_000L))
        dao.insert(report(id = 5, format = "PDF", generatedAt = 2_000L))

        val all = dao.getAll().first()
        assertEquals(1, all.size)
        assertEquals("PDF", all.single().format)
    }
}
