package com.example.mileagetracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.local.entity.VehicleExpenseEntity
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
 * Room integration tests for [VehicleExpenseDao] against a real (in-memory) SQLite
 * database. These exercise all-field round-trip, newest-first ordering, vehicle-id
 * scoping, year-range filtering, the COALESCE aggregate (returns 0.0 not null when
 * no rows match), update, per-id deletion, the REPLACE conflict strategy, and the
 * ON DELETE CASCADE from `vehicles`.
 *
 * `vehicle_expenses.vehicle_id` is a hard FK to `vehicles.id`, so each test inserts
 * a parent [VehicleEntity] before inserting expenses.
 */
@RunWith(AndroidJUnit4::class)
class VehicleExpenseDaoTest {

    private lateinit var db: MileageDatabase
    private lateinit var dao: VehicleExpenseDao
    private lateinit var vehicleDao: VehicleDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.vehicleExpenseDao()
        vehicleDao = db.vehicleDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private suspend fun insertVehicle(): Int =
        vehicleDao.insertVehicle(
            VehicleEntity(
                ownerUid = "u1",
                displayName = "Test Car",
                fiscalYear = 2026,
                isActive = true,
            ),
        ).toInt()

    private fun expense(
        id: Int = 0,
        vehicleId: Int,
        categoryKey: String = "fuel",
        amountCad: Double = 50.0,
        date: Long = 1_000L,
        vendorName: String? = null,
        notes: String? = null,
        receiptAttachmentUri: String? = null,
        gstHstAmount: Double? = null,
        isBusinessUse: Boolean = true,
        createdAt: Long = 1_000L,
    ) = VehicleExpenseEntity(
        id = id,
        vehicleId = vehicleId,
        categoryKey = categoryKey,
        amountCad = amountCad,
        date = date,
        vendorName = vendorName,
        notes = notes,
        receiptAttachmentUri = receiptAttachmentUri,
        gstHstAmount = gstHstAmount,
        isBusinessUse = isBusinessUse,
        createdAt = createdAt,
    )

    // ── insert + getById ───────────────────────────────────────────────────────

    @Test
    fun insert_thenGetById_roundTripsAllFields() = runTest {
        val vid = insertVehicle()
        val id = dao.insert(
            expense(
                vehicleId = vid,
                categoryKey = "insurance",
                amountCad = 1_200.50,
                date = 5_000L,
                vendorName = "Intact",
                notes = "Annual policy",
                receiptAttachmentUri = "content://receipts/1",
                gstHstAmount = 60.0,
                isBusinessUse = false,
                createdAt = 6_000L,
            ),
        ).toInt()

        val loaded = dao.getById(id)!!
        assertEquals(vid, loaded.vehicleId)
        assertEquals("insurance", loaded.categoryKey)
        assertEquals(1_200.50, loaded.amountCad, 0.0001)
        assertEquals(5_000L, loaded.date)
        assertEquals("Intact", loaded.vendorName)
        assertEquals("Annual policy", loaded.notes)
        assertEquals("content://receipts/1", loaded.receiptAttachmentUri)
        assertEquals(60.0, loaded.gstHstAmount!!, 0.0001)
        assertEquals(false, loaded.isBusinessUse)
        assertEquals(6_000L, loaded.createdAt)
    }

    @Test
    fun getById_returnsNull_forUnknownId() = runTest {
        assertNull(dao.getById(999))
    }

    // ── getForVehicle: newest-first, vehicle scoping ──────────────────────────

    @Test
    fun getForVehicle_ordersNewestDateFirst() = runTest {
        val vid = insertVehicle()
        dao.insert(expense(vehicleId = vid, date = 1_000L))
        dao.insert(expense(vehicleId = vid, date = 3_000L))
        dao.insert(expense(vehicleId = vid, date = 2_000L))

        val dates = dao.getForVehicle(vid).first().map { it.date }
        assertEquals(listOf(3_000L, 2_000L, 1_000L), dates)
    }

    @Test
    fun getForVehicle_scopesToRequestedVehicle() = runTest {
        val v1 = insertVehicle()
        val v2 = insertVehicle()
        dao.insert(expense(vehicleId = v1, date = 1_000L))
        dao.insert(expense(vehicleId = v2, date = 2_000L))

        assertEquals(1, dao.getForVehicle(v1).first().size)
        assertTrue(dao.getForVehicle(v1).first().all { it.vehicleId == v1 })
    }

    @Test
    fun getForVehicle_returnsEmpty_forVehicleWithNoExpenses() = runTest {
        val vid = insertVehicle()
        assertTrue(dao.getForVehicle(vid).first().isEmpty())
    }

    // ── getForVehicleInYear ───────────────────────────────────────────────────

    @Test
    fun getForVehicleInYear_appliesInclusiveBounds_newestFirst() = runTest {
        val vid = insertVehicle()
        dao.insert(expense(vehicleId = vid, date = 999L,   amountCad = 10.0)) // before window
        dao.insert(expense(vehicleId = vid, date = 1_000L, amountCad = 20.0)) // at lower bound
        dao.insert(expense(vehicleId = vid, date = 2_000L, amountCad = 30.0)) // upper bound
        dao.insert(expense(vehicleId = vid, date = 2_001L, amountCad = 40.0)) // after window

        val results = dao.getForVehicleInYear(vid, fromMillis = 1_000L, toMillis = 2_000L).first()

        assertEquals(listOf(2_000L, 1_000L), results.map { it.date }) // DESC
        assertEquals(listOf(30.0, 20.0), results.map { it.amountCad })
    }

    // ── getTotalForVehicleAndYear ─────────────────────────────────────────────

    @Test
    fun getTotalForVehicleAndYear_sumsAmountsInWindow() = runTest {
        val vid = insertVehicle()
        dao.insert(expense(vehicleId = vid, date = 1_000L, amountCad = 100.0))
        dao.insert(expense(vehicleId = vid, date = 2_000L, amountCad = 200.0))
        dao.insert(expense(vehicleId = vid, date = 2_001L, amountCad = 999.0)) // outside
        dao.insert(expense(vehicleId = vid, date = 999L,   amountCad = 999.0)) // outside

        val total = dao.getTotalForVehicleAndYear(vid, fromMillis = 1_000L, toMillis = 2_000L)

        assertEquals(300.0, total, 0.001)
    }

    @Test
    fun getTotalForVehicleAndYear_returnsZero_viaCoalesce_whenNoMatchingRows() = runTest {
        val vid = insertVehicle()

        val total = dao.getTotalForVehicleAndYear(vid, fromMillis = 1_000L, toMillis = 2_000L)

        // COALESCE(SUM(...), 0.0) — callers must distinguish 0 from "no data"
        // via the expense list; the aggregate never returns null.
        assertEquals(0.0, total, 0.0)
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    fun update_persistsChangedAmountAndCategory() = runTest {
        val vid = insertVehicle()
        val id = dao.insert(expense(vehicleId = vid, amountCad = 50.0, categoryKey = "fuel")).toInt()
        val existing = dao.getById(id)!!

        val rowsUpdated = dao.update(existing.copy(amountCad = 75.0, categoryKey = "maintenance"))

        assertEquals(1, rowsUpdated)
        val updated = dao.getById(id)!!
        assertEquals(75.0, updated.amountCad, 0.001)
        assertEquals("maintenance", updated.categoryKey)
    }

    // ── deleteById ────────────────────────────────────────────────────────────

    @Test
    fun deleteById_removesOnlyThatRow() = runTest {
        val vid = insertVehicle()
        val id = dao.insert(expense(vehicleId = vid, date = 1_000L)).toInt()
        dao.insert(expense(vehicleId = vid, date = 2_000L))

        dao.deleteById(id)

        assertEquals(1, dao.getForVehicle(vid).first().size)
        assertNull(dao.getById(id))
    }

    // ── REPLACE conflict strategy ─────────────────────────────────────────────

    @Test
    fun insert_withSamePrimaryKey_replacesRow() = runTest {
        val vid = insertVehicle()
        dao.insert(expense(id = 10, vehicleId = vid, amountCad = 50.0))
        dao.insert(expense(id = 10, vehicleId = vid, amountCad = 99.0))

        val all = dao.getForVehicle(vid).first()
        assertEquals(1, all.size)
        assertEquals(99.0, all.single().amountCad, 0.001)
    }

    // ── ON DELETE CASCADE ─────────────────────────────────────────────────────

    @Test
    fun deletingVehicle_cascadeDeletesItsExpenses() = runTest {
        val v1 = insertVehicle()
        val v2 = insertVehicle()
        dao.insert(expense(vehicleId = v1, date = 1_000L))
        dao.insert(expense(vehicleId = v2, date = 2_000L))

        vehicleDao.deleteVehicle(vehicleDao.getVehicleById(v1)!!)

        assertTrue(dao.getForVehicle(v1).first().isEmpty())
        assertEquals(1, dao.getForVehicle(v2).first().size)
    }
}
