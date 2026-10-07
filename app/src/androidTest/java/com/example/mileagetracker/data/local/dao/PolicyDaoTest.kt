package com.example.mileagetracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.PolicyEntity
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
 * Room integration tests for [PolicyDao] against a real (in-memory) SQLite database.
 * Exercises insert, update, single-row and all-row reactive reads, REPLACE conflict
 * strategy, and nullable two-tier rate fields.
 */
@RunWith(AndroidJUnit4::class)
class PolicyDaoTest {

    private lateinit var db: MileageDatabase
    private lateinit var dao: PolicyDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.policyDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun policy(
        id: Int = 0,
        policyName: String = "CRA Standard 2026",
        policyType: String = "CRA_Standard",
        baseRate: Float = 0.74f,
        secondaryRate: Float? = 0.68f,
        thresholdKm: Float? = 5_000f,
    ) = PolicyEntity(
        id = id,
        policyName = policyName,
        policyType = policyType,
        baseRate = baseRate,
        secondaryRate = secondaryRate,
        thresholdKm = thresholdKm,
    )

    // ── insert + read-back ─────────────────────────────────────────────────────

    @Test
    fun insertPolicy_returnsId_andGetByIdEmitsRow() = runTest {
        val id = dao.insertPolicy(policy(policyName = "CRA Standard")).toInt()
        assertTrue(id > 0)

        val loaded = dao.getPolicyById(id).first()
        assertEquals("CRA Standard", loaded.policyName)
        assertEquals(0.74f, loaded.baseRate)
    }

    @Test
    fun insertPolicy_roundTripsAllFields_includingTwoTierRates() = runTest {
        val id = dao.insertPolicy(
            policy(
                policyName = "Acme Corp",
                policyType = "Custom_Company",
                baseRate = 0.55f,
                secondaryRate = 0.48f,
                thresholdKm = 3_000f,
            ),
        ).toInt()

        val loaded = dao.getPolicyById(id).first()
        assertEquals("Acme Corp", loaded.policyName)
        assertEquals("Custom_Company", loaded.policyType)
        assertEquals(0.55f, loaded.baseRate)
        assertEquals(0.48f, loaded.secondaryRate)
        assertEquals(3_000f, loaded.thresholdKm)
    }

    @Test
    fun insertPolicy_withNullTieredFields_persistsNulls() = runTest {
        val id = dao.insertPolicy(
            policy(secondaryRate = null, thresholdKm = null),
        ).toInt()

        val loaded = dao.getPolicyById(id).first()
        assertNull(loaded.secondaryRate)
        assertNull(loaded.thresholdKm)
    }

    // ── updatePolicy ──────────────────────────────────────────────────────────

    @Test
    fun updatePolicy_persistsChangedRate() = runTest {
        val id = dao.insertPolicy(policy(baseRate = 0.70f)).toInt()
        val existing = dao.getPolicyById(id).first()

        dao.updatePolicy(existing.copy(baseRate = 0.77f, policyName = "Updated Name"))

        val updated = dao.getPolicyById(id).first()
        assertEquals(0.77f, updated.baseRate)
        assertEquals("Updated Name", updated.policyName)
        assertEquals(id, updated.id)
    }

    // ── getAllPolicies ─────────────────────────────────────────────────────────

    @Test
    fun getAllPolicies_returnsAllRows() = runTest {
        dao.insertPolicy(policy(policyName = "Alpha"))
        dao.insertPolicy(policy(policyName = "Beta"))
        dao.insertPolicy(policy(policyName = "Gamma"))

        val all = dao.getAllPolicies().first()
        assertEquals(3, all.size)
    }

    @Test
    fun getAllPolicies_emitsEmpty_whenTableIsEmpty() = runTest {
        assertTrue(dao.getAllPolicies().first().isEmpty())
    }

    // ── REPLACE conflict strategy ─────────────────────────────────────────────

    @Test
    fun insertPolicy_withSamePrimaryKey_replacesRow() = runTest {
        dao.insertPolicy(policy(id = 10, policyName = "Old", baseRate = 0.70f))
        dao.insertPolicy(policy(id = 10, policyName = "New", baseRate = 0.77f))

        val all = dao.getAllPolicies().first()
        assertEquals(1, all.size)
        assertEquals("New", all.single().policyName)
        assertEquals(0.77f, all.single().baseRate)
    }
}
