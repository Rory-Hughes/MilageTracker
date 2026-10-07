package com.example.mileagetracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.local.entity.VehicleExpenseEntity
import com.example.mileagetracker.domain.model.ExpenseCategory
import com.example.mileagetracker.domain.model.VehicleExpense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Repository integration tests for [VehicleExpenseRepositoryImpl] against a
 * real (in-memory) SQLite database.
 *
 * Beyond what [VehicleExpenseDaoTest] already covers, these tests verify:
 *
 * - **`save`** returns the saved [VehicleExpense] with the DB-assigned id.
 * - **Domain mapping**: all fields, including the sealed [ExpenseCategory]
 *   hierarchy, survive the entity ↔ domain round-trip.
 * - **`ExpenseCategory.Other` round-trip**: `"Other:Car wash"` key persists
 *   and reconstitutes as `Other("Car wash")`.
 * - **Unknown category fallback**: an unrecognised `categoryKey` in the DB
 *   maps to `Other(key)` via [ExpenseCategory.fromKey] rather than crashing.
 * - **`update` return value**: returns `true` when a row is affected,
 *   `false` when the id is unknown.
 * - **`getTotalForVehicleAndYear`**: `COALESCE(SUM, 0.0)` returns `0.0` for
 *   an empty range; sums correctly within the window.
 *
 * `vehicle_expenses.vehicle_id` is a hard FK to `vehicles.id`, so a parent
 * vehicle is inserted in [setUp].
 */
@RunWith(AndroidJUnit4::class)
class VehicleExpenseRepositoryImplTest {

    private lateinit var db: MileageDatabase
    private lateinit var repository: VehicleExpenseRepositoryImpl
    private var vehicleId: Int = 0

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        repository = VehicleExpenseRepositoryImpl(db.vehicleExpenseDao())

        vehicleId = db.vehicleDao().insertVehicle(
            VehicleEntity(
                ownerUid    = "u1",
                displayName = "Test Car",
                fiscalYear  = 2026,
                isActive    = true,
            ),
        ).toInt()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun expense(
        date: Long = 1_000L,
        amountCad: Double = 50.0,
        category: ExpenseCategory = ExpenseCategory.Fuel,
        vendorName: String? = null,
        notes: String? = null,
        gstHstAmount: Double? = null,
        isBusinessUse: Boolean = true,
    ) = VehicleExpense(
        vehicleId    = vehicleId,
        category     = category,
        amountCad    = amountCad,
        date         = date,
        vendorName   = vendorName,
        notes        = notes,
        gstHstAmount = gstHstAmount,
        isBusinessUse = isBusinessUse,
        createdAt    = 1_000L,
    )

    // ── save: auto-assigned id ─────────────────────────────────────────────────

    @Test
    fun save_returnsExpense_withAssignedId() = runTest {
        val saved = repository.save(expense(amountCad = 75.50))

        assertTrue("Assigned id must be > 0", saved.id > 0)
        assertEquals(75.50, saved.amountCad, 0.001)
        assertEquals(vehicleId, saved.vehicleId)
    }

    @Test
    fun save_allFields_roundTripThroughDomainMapping() = runTest {
        val saved = repository.save(
            expense(
                date         = 2_000L,
                amountCad    = 123.45,
                category     = ExpenseCategory.Maintenance,
                vendorName   = "Midas",
                notes        = "Oil change",
                gstHstAmount = 16.05,
                isBusinessUse = false,
            ),
        )

        val loaded = repository.getById(saved.id)!!
        assertEquals(saved.id, loaded.id)
        assertEquals(2_000L, loaded.date)
        assertEquals(123.45, loaded.amountCad, 0.001)
        assertEquals(ExpenseCategory.Maintenance, loaded.category)
        assertEquals("Midas", loaded.vendorName)
        assertEquals("Oil change", loaded.notes)
        assertEquals(16.05, loaded.gstHstAmount!!, 0.001)
        assertEquals(false, loaded.isBusinessUse)
    }

    // ── ExpenseCategory.Other round-trip ──────────────────────────────────────

    @Test
    fun save_otherCategory_roundTrips_withLabel() = runTest {
        val saved = repository.save(expense(category = ExpenseCategory.Other("Car wash")))

        val loaded = repository.getById(saved.id)!!
        val cat = loaded.category
        assertTrue(cat is ExpenseCategory.Other)
        assertEquals("Car wash", (cat as ExpenseCategory.Other).label)
    }

    // ── getById ────────────────────────────────────────────────────────────────

    @Test
    fun getById_returnsNull_forUnknownId() = runTest {
        assertNull(repository.getById(999))
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    fun update_returnsTrue_andPersistsChangedFields() = runTest {
        val saved = repository.save(expense(amountCad = 50.0))

        val result = repository.update(saved.copy(amountCad = 99.0, vendorName = "Shell"))

        assertTrue(result)
        val loaded = repository.getById(saved.id)!!
        assertEquals(99.0, loaded.amountCad, 0.001)
        assertEquals("Shell", loaded.vendorName)
    }

    @Test
    fun update_returnsFalse_forUnknownId() = runTest {
        val nonExistent = expense().copy(id = 999)

        assertFalse(repository.update(nonExistent))
    }

    // ── deleteById ────────────────────────────────────────────────────────────

    @Test
    fun deleteById_removesRow_getByIdReturnsNull() = runTest {
        val saved = repository.save(expense())

        repository.deleteById(saved.id)

        assertNull(repository.getById(saved.id))
    }

    // ── observeForVehicle ─────────────────────────────────────────────────────

    @Test
    fun observeForVehicle_emitsDomainObjects_forVehicle() = runTest {
        repository.save(expense(date = 1_000L, category = ExpenseCategory.Insurance))
        repository.save(expense(date = 3_000L, category = ExpenseCategory.Parking))
        repository.save(expense(date = 2_000L, category = ExpenseCategory.Tolls))

        val list = repository.observeForVehicle(vehicleId).first()
        assertEquals(3, list.size)
        assertTrue(list.all { it.vehicleId == vehicleId })
    }

    @Test
    fun observeForVehicle_emitsEmpty_whenNoExpenses() = runTest {
        assertTrue(repository.observeForVehicle(vehicleId).first().isEmpty())
    }

    // ── observeForVehicleInYear ───────────────────────────────────────────────

    @Test
    fun observeForVehicleInYear_returnsOnlyExpensesWithinWindow() = runTest {
        repository.save(expense(date = 999L))    // before window — excluded
        repository.save(expense(date = 1_000L))  // at lower bound — included
        repository.save(expense(date = 2_500L))  // inside window
        repository.save(expense(date = 3_001L))  // after window — excluded

        val list = repository.observeForVehicleInYear(
            vehicleId, fromMillis = 1_000L, toMillis = 3_000L,
        ).first()

        assertEquals(2, list.size)
        assertTrue(list.all { it.date in 1_000L..3_000L })
    }

    // ── getTotalForVehicleAndYear: COALESCE(SUM, 0.0) ─────────────────────────

    @Test
    fun getTotalForVehicleAndYear_returns0_forEmptyRange() = runTest {
        // No expenses at all → COALESCE must return 0.0, not null.
        assertEquals(0.0, repository.getTotalForVehicleAndYear(vehicleId, 0L, 1_000L), 0.001)
    }

    @Test
    fun getTotalForVehicleAndYear_sumsExpensesInWindow() = runTest {
        repository.save(expense(date = 1_000L, amountCad = 100.0))
        repository.save(expense(date = 2_000L, amountCad = 200.0))
        repository.save(expense(date = 5_000L, amountCad = 999.0)) // outside window

        val total = repository.getTotalForVehicleAndYear(vehicleId, fromMillis = 500L, toMillis = 3_000L)

        assertEquals(300.0, total, 0.001)
    }

    // ── ExpenseCategory.fromKey fallback ──────────────────────────────────────

    @Test
    fun toDomain_unknownCategoryKey_fallsBackToOther() = runTest {
        // Bypass the repository to insert a raw entity with an unrecognised key.
        db.vehicleExpenseDao().insert(
            VehicleExpenseEntity(
                vehicleId   = vehicleId,
                categoryKey = "CarWash",  // no matching ExpenseCategory
                amountCad   = 25.0,
                date        = 1_000L,
                createdAt   = 1_000L,
            ),
        )

        val list = repository.observeForVehicle(vehicleId).first()
        assertEquals(1, list.size)
        // fromKey falls back to Other(key) — must not crash.
        assertTrue(list.single().category is ExpenseCategory.Other)
    }
}
