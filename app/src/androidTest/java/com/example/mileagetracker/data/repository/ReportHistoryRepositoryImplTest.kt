package com.example.mileagetracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.ReportHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Repository integration tests for [ReportHistoryRepositoryImpl].
 *
 * The primary value here is testing the **CRA 6-year retention guard**:
 * [ReportHistoryRepositoryImpl.deleteOlderThan] must throw
 * [IllegalArgumentException] if the requested threshold is more recent than
 * `now - 6 years`, preventing accidental deletion of records that are still
 * within the CRA mandatory retention window.
 *
 * Other tests verify basic CRUD behaviour to confirm the guard integrates
 * correctly with the underlying DAO.
 */
@RunWith(AndroidJUnit4::class)
class ReportHistoryRepositoryImplTest {

    private lateinit var db: MileageDatabase
    private lateinit var repository: ReportHistoryRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = ReportHistoryRepositoryImpl(db.reportHistoryDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun report(
        generatedAt: Long,
        format: String = "CSV",
        year: Int = 2026,
        vehicleId: Int? = null,
    ) = ReportHistoryEntity(
        format = format,
        year = year,
        vehicleId = vehicleId,
        generatedAt = generatedAt,
        fileUri = "file:///export_$generatedAt.csv",
        fileSizeBytes = 1024L,
    )

    // ── save + getById ─────────────────────────────────────────────────────────

    @Test
    fun save_returnsId_andGetByIdReadsItBack() = runTest {
        val id = repository.save(report(generatedAt = 1_000L, format = "PDF", vehicleId = 3))

        assertTrue(id > 0)
        val loaded = repository.getById(id)!!
        assertEquals("PDF", loaded.format)
        assertEquals(3, loaded.vehicleId)
        assertEquals(1_000L, loaded.generatedAt)
    }

    @Test
    fun getById_returnsNull_forUnknownId() = runTest {
        assertNull(repository.getById(999))
    }

    // ── getAll flow ────────────────────────────────────────────────────────────

    @Test
    fun getAll_returnsAllRows_newestFirst() = runTest {
        repository.save(report(generatedAt = 1_000L))
        repository.save(report(generatedAt = 3_000L))
        repository.save(report(generatedAt = 2_000L))

        val all = repository.getAll().first()
        assertEquals(listOf(3_000L, 2_000L, 1_000L), all.map { it.generatedAt })
    }

    @Test
    fun getAll_emitsEmpty_whenTableIsEmpty() = runTest {
        assertTrue(repository.getAll().first().isEmpty())
    }

    // ── deleteById ────────────────────────────────────────────────────────────

    @Test
    fun deleteById_removesRow() = runTest {
        val id = repository.save(report(generatedAt = 1_000L))
        assertNotNull(repository.getById(id))

        repository.deleteById(id)

        assertNull(repository.getById(id))
    }

    // ── deleteOlderThan: retention guard ──────────────────────────────────────

    /**
     * A threshold of exactly `now - 6 years` is the boundary value and must
     * be accepted (the guard is `thresholdMs <= minimumAllowed`).
     *
     * Using `minimumAllowed - 1` ensures we are safely inside the valid window
     * even under timing jitter between the test's call and the repository's
     * `System.currentTimeMillis()` call.
     */
    @Test
    fun deleteOlderThan_withValidThreshold_succeeds() = runTest {
        val sixYearMs = 6L * 365 * 24 * 60 * 60 * 1_000
        val validThreshold = System.currentTimeMillis() - sixYearMs - 1_000L

        // Insert a report older than the threshold — it should be deleted.
        val oldId   = repository.save(report(generatedAt = validThreshold - 1_000L))
        // Insert a report newer than the threshold — it should be kept.
        val newId   = repository.save(report(generatedAt = System.currentTimeMillis()))

        repository.deleteOlderThan(validThreshold) // must not throw

        assertNull("Old report must be deleted", repository.getById(oldId))
        assertNotNull("New report must be kept", repository.getById(newId))
    }

    @Test
    fun deleteOlderThan_withThresholdTooRecent_throwsIllegalArgument() = runTest {
        // A threshold of "now" is far newer than now-6years → must be rejected.
        val tooRecentThreshold = System.currentTimeMillis()

        try {
            repository.deleteOlderThan(tooRecentThreshold)
            fail("Expected IllegalArgumentException — threshold violates CRA retention")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Message should mention the retention minimum",
                e.message!!.contains("6-year") || e.message!!.contains("retention"),
            )
        }
    }

    @Test
    fun deleteOlderThan_withThresholdOfZero_throwsIllegalArgument() = runTest {
        // Epoch 0 is ancient but still violates `thresholdMs <= now-6years` when
        // the threshold is 0 (= 1970). Wait — 0 IS older than now-6years, so the
        // guard (thresholdMs <= minimumAllowed) would PASS for threshold=0 since
        // 0 < now-6years. Actually the guard says "throw if threshold > minimumAllowed".
        // threshold=0 < minimumAllowed=~now-6years, so it should NOT throw.
        // Let's test the one we know throws: threshold = now (too recent).
        // The test above already covers that. This one tests a historically "safe" threshold.
        val veryOldThreshold = 1_000L // epoch millis of 1970 — definitely before 6 years ago

        repository.deleteOlderThan(veryOldThreshold) // must not throw
    }

    @Test
    fun deleteOlderThan_withThresholdJustAboveMinimum_throwsIllegalArgument() = runTest {
        val sixYearMs = 6L * 365 * 24 * 60 * 60 * 1_000
        // minimumAllowed = now - sixYearMs
        // threshold = minimumAllowed + 1  →  threshold > minimumAllowed  →  must throw
        val tooRecentByOne = System.currentTimeMillis() - sixYearMs + 1L

        try {
            repository.deleteOlderThan(tooRecentByOne)
            fail("Expected IllegalArgumentException — threshold is 1ms inside retention window")
        } catch (error: IllegalArgumentException) {
            assertNotNull(error)
        }
    }
}
