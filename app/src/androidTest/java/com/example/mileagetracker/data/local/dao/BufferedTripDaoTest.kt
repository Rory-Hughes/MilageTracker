package com.example.mileagetracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.BufferedTripEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room integration tests for [BufferedTripDao] against a real (in-memory) SQLite
 * database. These exercise the actual SQL — FIFO ordering, retry-count persistence,
 * exhausted-row pruning, selective deletion by id, the REPLACE conflict strategy,
 * and the diagnostic count query.
 */
@RunWith(AndroidJUnit4::class)
class BufferedTripDaoTest {

    private lateinit var db: MileageDatabase
    private lateinit var dao: BufferedTripDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.bufferedTripDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun buffered(
        id: Long = 0,
        tripJson: String = """{"id":1}""",
        routePointsJson: String = "[]",
        createdAt: Long = 1_000L,
        retryCount: Int = 0,
    ) = BufferedTripEntity(
        id = id,
        tripJson = tripJson,
        routePointsJson = routePointsJson,
        createdAt = createdAt,
        retryCount = retryCount,
    )

    // ── insert + read-back ─────────────────────────────────────────────────────

    @Test
    fun insert_thenGetAll_roundTripsAllFields() = runTest {
        dao.insert(
            buffered(
                tripJson = """{"id":42,"distance":15.5}""",
                routePointsJson = """[{"lat":45.0,"lng":-66.0}]""",
                createdAt = 2_000L,
                retryCount = 2,
            ),
        )

        val loaded = dao.getAll().single()
        assertEquals("""{"id":42,"distance":15.5}""", loaded.tripJson)
        assertEquals("""[{"lat":45.0,"lng":-66.0}]""", loaded.routePointsJson)
        assertEquals(2_000L, loaded.createdAt)
        assertEquals(2, loaded.retryCount)
    }

    // ── FIFO ordering ─────────────────────────────────────────────────────────

    @Test
    fun getAll_ordersOldestFirst_forFifoFlushing() = runTest {
        dao.insert(buffered(createdAt = 3_000L, tripJson = """{"seq":3}"""))
        dao.insert(buffered(createdAt = 1_000L, tripJson = """{"seq":1}"""))
        dao.insert(buffered(createdAt = 2_000L, tripJson = """{"seq":2}"""))

        val order = dao.getAll().map { it.createdAt }
        assertEquals(listOf(1_000L, 2_000L, 3_000L), order)
    }

    // ── deleteById ────────────────────────────────────────────────────────────

    @Test
    fun deleteById_removesOnlyThatRow() = runTest {
        val id = dao.insert(buffered(tripJson = """{"seq":1}"""))
        dao.insert(buffered(tripJson = """{"seq":2}"""))

        dao.deleteById(id)

        val remaining = dao.getAll()
        assertEquals(1, remaining.size)
        assertEquals("""{"seq":2}""", remaining.single().tripJson)
    }

    @Test
    fun deleteById_onUnknownId_isNoOp() = runTest {
        dao.insert(buffered())

        dao.deleteById(999L) // no-op, should not throw

        assertEquals(1, dao.getAll().size)
    }

    // ── update (retry count) ──────────────────────────────────────────────────

    @Test
    fun update_persistsIncrementedRetryCount() = runTest {
        val id = dao.insert(buffered(retryCount = 0))
        val row = dao.getAll().single()

        dao.update(row.copy(retryCount = 3))

        assertEquals(3, dao.getAll().single().retryCount)
        assertEquals(id, dao.getAll().single().id)
    }

    // ── deleteExhausted ───────────────────────────────────────────────────────

    @Test
    fun deleteExhausted_removesRowsAtOrAboveThreshold_keepsRowsBelow() = runTest {
        dao.insert(buffered(createdAt = 1_000L, retryCount = 4)) // below MAX (5) → keep
        dao.insert(buffered(createdAt = 2_000L, retryCount = 5)) // at MAX → remove
        dao.insert(buffered(createdAt = 3_000L, retryCount = 6)) // above MAX → remove

        dao.deleteExhausted(maxRetries = BufferedTripEntity.MAX_RETRY_COUNT)

        val remaining = dao.getAll()
        assertEquals(1, remaining.size)
        assertEquals(4, remaining.single().retryCount)
    }

    @Test
    fun deleteExhausted_whenNoneExhausted_isNoOp() = runTest {
        dao.insert(buffered(retryCount = 0))
        dao.insert(buffered(retryCount = 1))

        dao.deleteExhausted(maxRetries = BufferedTripEntity.MAX_RETRY_COUNT)

        assertEquals(2, dao.getAll().size)
    }

    // ── count ─────────────────────────────────────────────────────────────────

    @Test
    fun count_returnsZero_whenTableIsEmpty() = runTest {
        assertEquals(0, dao.count())
    }

    @Test
    fun count_reflectsInsertAndDelete() = runTest {
        val id = dao.insert(buffered())
        dao.insert(buffered())
        assertEquals(2, dao.count())

        dao.deleteById(id)
        assertEquals(1, dao.count())
    }

    // ── REPLACE conflict strategy ─────────────────────────────────────────────

    @Test
    fun insert_withSamePrimaryKey_replacesExistingRow() = runTest {
        dao.insert(buffered(id = 10L, retryCount = 0, tripJson = """{"v":1}"""))
        dao.insert(buffered(id = 10L, retryCount = 3, tripJson = """{"v":2}"""))

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("""{"v":2}""", all.single().tripJson)
        assertEquals(3, all.single().retryCount)
    }
}
