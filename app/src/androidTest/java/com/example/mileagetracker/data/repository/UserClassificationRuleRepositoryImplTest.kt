package com.example.mileagetracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.UserClassificationRuleEntity
import com.example.mileagetracker.domain.auth.CurrentUser
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.UserRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Repository integration tests for [UserClassificationRuleRepositoryImpl] against
 * a real (in-memory) SQLite database.
 *
 * Beyond what [UserClassificationRuleDaoTest] covers, these tests verify:
 *
 * - **`save` with id=0**: the returned [UserRule] carries the DB-assigned id (> 0).
 * - **`save` with id>0 (update)**: the REPLACE strategy fires; the returned rule
 *   preserves the caller's id.
 * - **Domain mapping**: `observeAll` and `getEnabledRules` emit [UserRule] objects
 *   with the correct [TripCategory] resolved from the stored string.
 * - **`runCatching` fallback**: an entity with an unrecognised `category` string
 *   (e.g., from a rolled-back feature branch) resolves to [TripCategory.CUSTOM]
 *   rather than crashing.
 */
@RunWith(AndroidJUnit4::class)
class UserClassificationRuleRepositoryImplTest {
    private companion object {
        const val OWNER_UID = "u1"
        const val OTHER_UID = "u2"
    }

    private lateinit var db: MileageDatabase
    private lateinit var repository: UserClassificationRuleRepositoryImpl
    private val currentUserProvider = object : CurrentUserProvider {
        override fun currentUser() = CurrentUser(OWNER_UID, "u1@example.com", null, null)
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = UserClassificationRuleRepositoryImpl(
            db.userClassificationRuleDao(),
            currentUserProvider,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun rule(
        id: Int = 0,
        name: String = "Rule",
        keyword: String = "office",
        category: TripCategory = TripCategory.BUSINESS,
        confidence: Float = 0.75f,
        priority: Int = 50,
        isEnabled: Boolean = true,
    ) = UserRule(
        id = id,
        name = name,
        keyword = keyword,
        category = category,
        confidence = confidence,
        priority = priority,
        isEnabled = isEnabled,
        createdAt = 1_000L,
    )

    // ── save: new rule (id = 0) ────────────────────────────────────────────────

    @Test
    fun save_withIdZero_returnsRuleWithAssignedId() = runTest {
        val saved = repository.save(rule(id = 0, name = "Airport"))

        assertTrue("Assigned id must be > 0", saved.id > 0)
        assertEquals("Airport", saved.name)
    }

    @Test
    fun save_withIdZero_canBeRetrievedViaObserveAll() = runTest {
        val saved = repository.save(rule(id = 0, name = "Hospital", category = TripCategory.MEDICAL))

        val all = repository.observeAll().first()
        assertEquals(1, all.size)
        assertEquals(saved.id, all.single().id)
        assertEquals(TripCategory.MEDICAL, all.single().category)
    }

    // ── save: update existing rule (id > 0) ───────────────────────────────────

    @Test
    fun save_withExistingId_replacesRuleAndPreservesId() = runTest {
        val original = repository.save(rule(id = 0, name = "Original", confidence = 0.75f))

        val updated = repository.save(
            original.copy(name = "Updated", confidence = 0.90f),
        )

        assertEquals(original.id, updated.id)
        val all = repository.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Updated", all.single().name)
        assertEquals(0.90f, all.single().confidence)
    }

    // ── deleteById ────────────────────────────────────────────────────────────

    @Test
    fun deleteById_removesRule_observeAllEmitsEmpty() = runTest {
        val saved = repository.save(rule())

        repository.deleteById(saved.id)

        assertTrue(repository.observeAll().first().isEmpty())
    }

    // ── observeAll: domain mapping and ordering ────────────────────────────────

    @Test
    fun observeAll_ordersByPriorityThenName_asDomainObjects() = runTest {
        repository.save(rule(name = "Zulu",  priority = 10, category = TripCategory.BUSINESS))
        repository.save(rule(name = "Alpha", priority = 10, category = TripCategory.PERSONAL))
        repository.save(rule(name = "Gamma", priority = 5,  category = TripCategory.MEDICAL))

        val all = repository.observeAll().first()
        assertEquals(listOf("Gamma", "Alpha", "Zulu"), all.map { it.name })
        assertEquals(TripCategory.MEDICAL,  all[0].category)
        assertEquals(TripCategory.PERSONAL, all[1].category)
        assertEquals(TripCategory.BUSINESS, all[2].category)
    }

    // ── getEnabledRules ───────────────────────────────────────────────────────

    @Test
    fun getEnabledRules_returnsOnlyEnabledRules_asDomainObjects() = runTest {
        repository.save(rule(name = "Active",   isEnabled = true,  priority = 10))
        repository.save(rule(name = "Inactive", isEnabled = false, priority = 1))

        val enabled = repository.getEnabledRules()

        assertEquals(1, enabled.size)
        assertEquals("Active", enabled.single().name)
        assertTrue(enabled.single().isEnabled)
        assertEquals(10, enabled.single().priority)
    }

    // ── toDomain: runCatching fallback for unknown category ───────────────────

    @Test
    fun toDomain_unknownCategoryString_fallsBackToCustom() = runTest {
        // Insert a raw entity with an unrecognised category string directly via
        // the DAO — simulates a legacy row from a feature branch with extra enum values.
        db.userClassificationRuleDao().upsert(
            UserClassificationRuleEntity(
                ownerUid = OWNER_UID,
                name = "Legacy Rule",
                keyword = "depot",
                category = "CHARITY_DRIVE",  // not a valid TripCategory
                confidence = 0.80f,
                priority = 30,
                isEnabled = true,
                createdAt = 1_000L,
            ),
        )

        val all = repository.observeAll().first()
        assertEquals(1, all.size)
        // runCatching catches the IllegalArgumentException from TripCategory.valueOf
        // and falls back to TripCategory.CUSTOM.
        assertEquals(TripCategory.CUSTOM, all.single().category)
        assertEquals("Legacy Rule", all.single().name)
    }

    @Test
    fun toDomain_unknownCategoryString_doesNotPreventOtherRulesFromLoading() = runTest {
        db.userClassificationRuleDao().upsert(
            UserClassificationRuleEntity(
                ownerUid = OWNER_UID,
                name = "Bad Rule",
                keyword = "bad",
                category = "NONEXISTENT",
                confidence = 0.75f,
                priority = 50,
                isEnabled = true,
                createdAt = 1_000L,
            ),
        )
        repository.save(rule(name = "Good Rule", category = TripCategory.BUSINESS))

        val all = repository.observeAll().first()
        assertEquals(2, all.size)
        val categories = all.map { it.category }.toSet()
        assertTrue(categories.contains(TripCategory.CUSTOM))
        assertTrue(categories.contains(TripCategory.BUSINESS))
    }

    @Test
    fun observeAll_filtersOutOtherOwnersRules() = runTest {
        db.userClassificationRuleDao().upsert(
            UserClassificationRuleEntity(
                ownerUid = OTHER_UID,
                name = "Other Owner",
                keyword = "other",
                category = TripCategory.PERSONAL.name,
                confidence = 0.60f,
                priority = 10,
                isEnabled = true,
                createdAt = 1_000L,
            ),
        )
        repository.save(rule(name = "Current Owner"))

        val all = repository.observeAll().first()

        assertEquals(listOf("Current Owner"), all.map { it.name })
    }

    // ── full round-trip ───────────────────────────────────────────────────────

    @Test
    fun save_allFields_roundTripThroughDomainMapping() = runTest {
        val saved = repository.save(
            rule(
                name = "Medical Clinic",
                keyword = "clinic",
                category = TripCategory.MEDICAL,
                confidence = 0.85f,
                priority = 20,
                isEnabled = false,
            ),
        )

        val loaded = repository.observeAll().first().single()
        assertEquals(saved.id, loaded.id)
        assertEquals("Medical Clinic", loaded.name)
        assertEquals("clinic", loaded.keyword)
        assertEquals(TripCategory.MEDICAL, loaded.category)
        assertEquals(0.85f, loaded.confidence)
        assertEquals(20, loaded.priority)
        assertEquals(false, loaded.isEnabled)
    }
}
