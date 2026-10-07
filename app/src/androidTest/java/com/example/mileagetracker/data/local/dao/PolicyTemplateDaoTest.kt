package com.example.mileagetracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.PolicyTemplateEntity
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
 * Room integration tests for [PolicyTemplateDao] against a real (in-memory)
 * SQLite database. These exercise string-PK semantics, name-ASC ordering,
 * round-trip of [eligibleCategories] via [StringListConverter], the REPLACE
 * upsert, and deletion by string id.
 */
@RunWith(AndroidJUnit4::class)
class PolicyTemplateDaoTest {

    private lateinit var db: MileageDatabase
    private lateinit var dao: PolicyTemplateDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.policyTemplateDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun template(
        id: String = "tmpl-1",
        name: String = "CRA Standard",
        rateMode: String = "CRA_STANDARD",
        customRateCentsPerKm: Int? = null,
        eligibleCategories: List<String> = listOf("BUSINESS"),
        excludeCommute: Boolean = false,
        requirePurpose: Boolean = false,
        requireAttachment: Boolean = false,
        maxDailyKm: Int? = null,
    ) = PolicyTemplateEntity(
        id = id,
        name = name,
        rateMode = rateMode,
        customRateCentsPerKm = customRateCentsPerKm,
        eligibleCategories = eligibleCategories,
        excludeCommute = excludeCommute,
        requirePurpose = requirePurpose,
        requireAttachment = requireAttachment,
        maxDailyKm = maxDailyKm,
    )

    // ── insertOrReplace + getById ──────────────────────────────────────────────

    @Test
    fun insertOrReplace_thenGetById_roundTripsAllFields() = runTest {
        dao.insertOrReplace(
            template(
                id = "tmpl-full",
                name = "Company Policy",
                rateMode = "CUSTOM_FLAT",
                customRateCentsPerKm = 58,
                eligibleCategories = listOf("BUSINESS", "MEDICAL"),
                excludeCommute = true,
                requirePurpose = true,
                requireAttachment = true,
                maxDailyKm = 300,
            ),
        )

        val loaded = dao.getById("tmpl-full")!!
        assertEquals("Company Policy", loaded.name)
        assertEquals("CUSTOM_FLAT", loaded.rateMode)
        assertEquals(58, loaded.customRateCentsPerKm)
        assertEquals(listOf("BUSINESS", "MEDICAL"), loaded.eligibleCategories)
        assertEquals(true, loaded.excludeCommute)
        assertEquals(true, loaded.requirePurpose)
        assertEquals(true, loaded.requireAttachment)
        assertEquals(300, loaded.maxDailyKm)
    }

    @Test
    fun getById_returnsNull_forUnknownId() = runTest {
        assertNull(dao.getById("does-not-exist"))
    }

    @Test
    fun insertOrReplace_withNullableFields_persistsNulls() = runTest {
        dao.insertOrReplace(
            template(
                id = "tmpl-nullable",
                customRateCentsPerKm = null,
                maxDailyKm = null,
            ),
        )

        val loaded = dao.getById("tmpl-nullable")!!
        assertNull(loaded.customRateCentsPerKm)
        assertNull(loaded.maxDailyKm)
    }

    // ── eligibleCategories type converter ─────────────────────────────────────

    @Test
    fun eligibleCategories_multipleValues_roundTripViaConverter() = runTest {
        val categories = listOf("BUSINESS", "MEDICAL", "MOVING")
        dao.insertOrReplace(template(id = "tmpl-multi", eligibleCategories = categories))

        assertEquals(categories, dao.getById("tmpl-multi")!!.eligibleCategories)
    }

    @Test
    fun eligibleCategories_emptyList_roundTripViaConverter() = runTest {
        dao.insertOrReplace(template(id = "tmpl-empty", eligibleCategories = emptyList()))

        assertEquals(emptyList<String>(), dao.getById("tmpl-empty")!!.eligibleCategories)
    }

    // ── getAll: name ASC ordering ─────────────────────────────────────────────

    @Test
    fun getAll_ordersNameAscending() = runTest {
        dao.insertOrReplace(template(id = "z", name = "Zeta"))
        dao.insertOrReplace(template(id = "a", name = "Alpha"))
        dao.insertOrReplace(template(id = "m", name = "Mu"))

        val names = dao.getAll().first().map { it.name }
        assertEquals(listOf("Alpha", "Mu", "Zeta"), names)
    }

    @Test
    fun getAll_emitsEmpty_whenTableIsEmpty() = runTest {
        assertTrue(dao.getAll().first().isEmpty())
    }

    // ── deleteById ────────────────────────────────────────────────────────────

    @Test
    fun deleteById_removesRow_andGetByIdReturnsNull() = runTest {
        dao.insertOrReplace(template(id = "to-delete"))
        dao.insertOrReplace(template(id = "to-keep"))

        dao.deleteById("to-delete")

        assertNull(dao.getById("to-delete"))
        val remaining = dao.getAll().first()
        assertEquals(1, remaining.size)
        assertEquals("to-keep", remaining.single().id)
    }

    @Test
    fun deleteById_onUnknownId_isNoOp() = runTest {
        dao.insertOrReplace(template(id = "exists"))
        dao.deleteById("not-there")
        assertEquals(1, dao.getAll().first().size)
    }

    // ── REPLACE conflict strategy (string PK) ─────────────────────────────────

    @Test
    fun insertOrReplace_withSameStringId_replacesRow() = runTest {
        dao.insertOrReplace(template(id = "shared-id", name = "Before", rateMode = "CRA_STANDARD"))
        dao.insertOrReplace(template(id = "shared-id", name = "After",  rateMode = "CUSTOM_FLAT"))

        val all = dao.getAll().first()
        assertEquals(1, all.size)
        assertEquals("After", all.single().name)
        assertEquals("CUSTOM_FLAT", all.single().rateMode)
    }
}
