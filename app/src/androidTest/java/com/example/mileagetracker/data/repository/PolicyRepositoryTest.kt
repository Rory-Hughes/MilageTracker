package com.example.mileagetracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.domain.model.PolicyTemplate
import com.example.mileagetracker.domain.model.RateMode
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
 * Repository integration tests for [PolicyRepository] against a real
 * (in-memory) SQLite database.
 *
 * These tests go beyond what [PolicyTemplateDaoTest] covers at the DAO level by
 * verifying the full entity ↔ domain mapping round-trip through the repository:
 *
 * - **String PK**: [PolicyTemplate.id] is a caller-supplied UUID string; the
 *   repository must preserve it exactly.
 * - **`eligibleCategories` round-trip**: the list is stored via [StringListConverter]
 *   (comma-separated); multi-value and empty lists must survive the round-trip.
 * - **REPLACE (upsert)**: `savePolicy` on an existing id updates the row, it
 *   does not create a duplicate.
 * - **`RateMode.CUSTOM`** with a non-null `customRateCentsPerKm` persists and
 *   maps back correctly.
 * - **`observePolicies`** emits the full list ordered by name ASC.
 */
@RunWith(AndroidJUnit4::class)
class PolicyRepositoryTest {

    private lateinit var db: MileageDatabase
    private lateinit var repository: PolicyRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = PolicyRepository(db.policyTemplateDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun policy(
        id: String = "policy-001",
        name: String = "Standard CRA",
        rateMode: RateMode = RateMode.CRA_STANDARD,
        customRateCentsPerKm: Int? = null,
        eligibleCategories: List<String> = listOf("BUSINESS"),
        excludeCommute: Boolean = true,
        requirePurpose: Boolean = false,
        requireAttachment: Boolean = false,
        maxDailyKm: Int? = null,
    ) = PolicyTemplate(
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

    // ── savePolicy + getById ───────────────────────────────────────────────────

    @Test
    fun savePolicy_stringPkPreserved_andGetByIdReadsItBack() = runTest {
        repository.savePolicy(policy(id = "uuid-abc-123", name = "My Policy"))

        val loaded = repository.getById("uuid-abc-123")!!
        assertEquals("uuid-abc-123", loaded.id)
        assertEquals("My Policy", loaded.name)
    }

    @Test
    fun getById_returnsNull_forUnknownId() = runTest {
        assertNull(repository.getById("does-not-exist"))
    }

    // ── all fields round-trip ─────────────────────────────────────────────────

    @Test
    fun savePolicy_allFields_roundTripThroughDomainMapping() = runTest {
        repository.savePolicy(
            policy(
                id                  = "full-round-trip",
                name                = "Enterprise Reimbursement",
                rateMode            = RateMode.CRA_STANDARD,
                customRateCentsPerKm = null,
                eligibleCategories  = listOf("BUSINESS", "MEDICAL"),
                excludeCommute      = false,
                requirePurpose      = true,
                requireAttachment   = true,
                maxDailyKm         = 200,
            ),
        )

        val loaded = repository.getById("full-round-trip")!!
        assertEquals("Enterprise Reimbursement", loaded.name)
        assertEquals(RateMode.CRA_STANDARD, loaded.rateMode)
        assertNull(loaded.customRateCentsPerKm)
        assertEquals(listOf("BUSINESS", "MEDICAL"), loaded.eligibleCategories)
        assertEquals(false, loaded.excludeCommute)
        assertEquals(true, loaded.requirePurpose)
        assertEquals(true, loaded.requireAttachment)
        assertEquals(200, loaded.maxDailyKm)
    }

    // ── RateMode.CUSTOM with customRateCentsPerKm ─────────────────────────────

    @Test
    fun savePolicy_customRateMode_persistsRateCentsPerKm() = runTest {
        repository.savePolicy(
            policy(
                id                  = "custom-rate",
                rateMode            = RateMode.CUSTOM,
                customRateCentsPerKm = 85,
            ),
        )

        val loaded = repository.getById("custom-rate")!!
        assertEquals(RateMode.CUSTOM, loaded.rateMode)
        assertEquals(85, loaded.customRateCentsPerKm)
    }

    // ── eligibleCategories: StringListConverter round-trips ───────────────────

    @Test
    fun savePolicy_multiValueEligibleCategories_roundTrip() = runTest {
        val categories = listOf("BUSINESS", "MEDICAL", "CHARITY")
        repository.savePolicy(policy(id = "multi-cat", eligibleCategories = categories))

        assertEquals(categories, repository.getById("multi-cat")!!.eligibleCategories)
    }

    @Test
    fun savePolicy_emptyEligibleCategories_roundTrip() = runTest {
        repository.savePolicy(policy(id = "empty-cat", eligibleCategories = emptyList()))

        assertTrue(repository.getById("empty-cat")!!.eligibleCategories.isEmpty())
    }

    // ── savePolicy acts as upsert (REPLACE) ───────────────────────────────────

    @Test
    fun savePolicy_existingId_replacesRow_noduplicateCreated() = runTest {
        repository.savePolicy(policy(id = "upsert-id", name = "Original"))
        repository.savePolicy(policy(id = "upsert-id", name = "Updated"))

        val all = repository.observePolicies().first()
        assertEquals(1, all.size)
        assertEquals("Updated", all.single().name)
    }

    // ── deletePolicy ──────────────────────────────────────────────────────────

    @Test
    fun deletePolicy_removesRow_getByIdReturnsNull() = runTest {
        repository.savePolicy(policy(id = "to-delete"))
        repository.deletePolicy("to-delete")

        assertNull(repository.getById("to-delete"))
    }

    @Test
    fun deletePolicy_unknownId_isANoop() = runTest {
        repository.savePolicy(policy(id = "keeper"))
        repository.deletePolicy("nonexistent-id")

        assertEquals(1, repository.observePolicies().first().size)
    }

    // ── observePolicies: ordering ─────────────────────────────────────────────

    @Test
    fun observePolicies_emitsAllPolicies_orderedByNameAsc() = runTest {
        repository.savePolicy(policy(id = "id-z", name = "Zulu Policy"))
        repository.savePolicy(policy(id = "id-a", name = "Alpha Policy"))
        repository.savePolicy(policy(id = "id-m", name = "Mike Policy"))

        val list = repository.observePolicies().first()
        assertEquals(3, list.size)
        assertEquals(listOf("Alpha Policy", "Mike Policy", "Zulu Policy"), list.map { it.name })
    }

    @Test
    fun observePolicies_emitsEmpty_whenNoRowsExist() = runTest {
        assertTrue(repository.observePolicies().first().isEmpty())
    }
}
