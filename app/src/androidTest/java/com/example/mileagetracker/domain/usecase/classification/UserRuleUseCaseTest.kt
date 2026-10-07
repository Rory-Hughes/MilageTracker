package com.example.mileagetracker.domain.usecase.classification

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.repository.UserClassificationRuleRepositoryImpl
import com.example.mileagetracker.domain.auth.CurrentUser
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.UserRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [SaveUserRuleUseCase] and [DeleteUserRuleUseCase]
 * against a real (in-memory) Room DB.
 *
 * ## SaveUserRuleUseCase — behaviour under test
 * - **Validation**: blank name, blank keyword, confidence outside `[0f, 1f]`,
 *   and non-positive priority each return [SaveUserRuleResult.ValidationError].
 * - **Happy path**: rule persisted, returned with a positive db-generated id.
 * - **Upsert**: saving a rule whose id > 0 updates the existing row.
 *
 * ## DeleteUserRuleUseCase — behaviour under test
 * - Deleting a rule by id removes it from the repository.
 * - Other rules in the table are unaffected.
 * - Deleting a non-existent id is a silent no-op (no exception).
 */
@RunWith(AndroidJUnit4::class)
class UserRuleUseCaseTest {
    private companion object {
        const val OWNER_UID = "u1"
    }

    private lateinit var db:        MileageDatabase
    private lateinit var repo:      UserClassificationRuleRepositoryImpl
    private lateinit var saveCase:  SaveUserRuleUseCase
    private lateinit var deleteCase: DeleteUserRuleUseCase
    private val currentUserProvider = object : CurrentUserProvider {
        override fun currentUser() = CurrentUser(OWNER_UID, "u1@example.com", null, null)
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        repo       = UserClassificationRuleRepositoryImpl(db.userClassificationRuleDao(), currentUserProvider)
        saveCase   = SaveUserRuleUseCase(repo)
        deleteCase = DeleteUserRuleUseCase(repo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────────

    private fun rule(
        name:       String  = "Client ABC",
        keyword:    String  = "ABC",
        confidence: Float   = 0.8f,
        priority:   Int     = 50,
    ) = UserRule(
        name       = name,
        keyword    = keyword,
        category   = TripCategory.BUSINESS,
        confidence = confidence,
        priority   = priority,
    )

    // ── SaveUserRuleUseCase — Validation ──────────────────────────────────────────

    @Test
    fun save_blankName_returnsValidationError() = runTest {
        val result = saveCase(rule(name = "  "))
        assertTrue(result is SaveUserRuleResult.ValidationError)
    }

    @Test
    fun save_emptyKeyword_returnsValidationError() = runTest {
        val result = saveCase(rule(keyword = ""))
        assertTrue(result is SaveUserRuleResult.ValidationError)
    }

    @Test
    fun save_confidenceBelowZero_returnsValidationError() = runTest {
        val result = saveCase(rule(confidence = -0.1f))
        assertTrue(result is SaveUserRuleResult.ValidationError)
    }

    @Test
    fun save_confidenceAboveOne_returnsValidationError() = runTest {
        val result = saveCase(rule(confidence = 1.01f))
        assertTrue(result is SaveUserRuleResult.ValidationError)
    }

    @Test
    fun save_zeroPriority_returnsValidationError() = runTest {
        val result = saveCase(rule(priority = 0))
        assertTrue(result is SaveUserRuleResult.ValidationError)
    }

    @Test
    fun save_negativePriority_returnsValidationError() = runTest {
        val result = saveCase(rule(priority = -1))
        assertTrue(result is SaveUserRuleResult.ValidationError)
    }

    @Test
    fun save_validationError_nothingWrittenToDb() = runTest {
        saveCase(rule(name = ""))

        assertTrue(repo.observeAll().first().isEmpty())
    }

    // ── SaveUserRuleUseCase — Happy path ──────────────────────────────────────────

    @Test
    fun save_validRule_returnsSuccess() = runTest {
        val result = saveCase(rule())
        assertTrue(result is SaveUserRuleResult.Success)
    }

    @Test
    fun save_validRule_savedRuleHasPositiveId() = runTest {
        val result = saveCase(rule()) as SaveUserRuleResult.Success
        assertTrue("Saved id must be > 0", result.rule.id > 0)
    }

    @Test
    fun save_validRule_savedRuleFieldsMatchInput() = runTest {
        val input  = rule(name = "Doctor Visit", keyword = "hospital", confidence = 0.9f)
        val result = saveCase(input) as SaveUserRuleResult.Success

        assertEquals("Doctor Visit", result.rule.name)
        assertEquals("hospital",     result.rule.keyword)
        assertEquals(0.9f,           result.rule.confidence)
    }

    @Test
    fun save_validRule_rowPersistedInDb() = runTest {
        saveCase(rule(name = "My Rule"))

        val stored = repo.observeAll().first()
        assertEquals(1, stored.size)
        assertEquals("My Rule", stored.single().name)
    }

    @Test
    fun save_confidenceAtZero_isValid() = runTest {
        val result = saveCase(rule(confidence = 0f))
        assertTrue(result is SaveUserRuleResult.Success)
    }

    @Test
    fun save_confidenceAtOne_isValid() = runTest {
        val result = saveCase(rule(confidence = 1f))
        assertTrue(result is SaveUserRuleResult.Success)
    }

    @Test
    fun save_priorityOne_isValid() = runTest {
        val result = saveCase(rule(priority = 1))
        assertTrue(result is SaveUserRuleResult.Success)
    }

    // ── SaveUserRuleUseCase — Upsert ──────────────────────────────────────────────

    @Test
    fun save_existingRuleId_updatesRow() = runTest {
        val first  = (saveCase(rule(name = "Original")) as SaveUserRuleResult.Success).rule
        val updated = first.copy(name = "Updated")

        saveCase(updated)

        val all = repo.observeAll().first()
        assertEquals("Only one row after upsert", 1, all.size)
        assertEquals("Updated", all.single().name)
    }

    // ── DeleteUserRuleUseCase ─────────────────────────────────────────────────────

    @Test
    fun delete_existingRule_removedFromDb() = runTest {
        val saved = (saveCase(rule()) as SaveUserRuleResult.Success).rule

        deleteCase(saved.id)

        assertTrue(repo.observeAll().first().isEmpty())
    }

    @Test
    fun delete_nonExistentId_doesNotThrow() = runTest {
        deleteCase(9999) // must not throw
    }

    @Test
    fun delete_oneOfTwo_otherRuleUnaffected() = runTest {
        val r1 = (saveCase(rule(name = "Rule A", keyword = "A")) as SaveUserRuleResult.Success).rule
        val r2 = (saveCase(rule(name = "Rule B", keyword = "B")) as SaveUserRuleResult.Success).rule

        deleteCase(r1.id)

        val remaining = repo.observeAll().first()
        assertEquals(1, remaining.size)
        assertEquals(r2.id, remaining.single().id)
        assertEquals("Rule B", remaining.single().name)
    }

    @Test
    fun delete_thenSaveNew_dbHasOnlyNewRule() = runTest {
        val saved = (saveCase(rule(name = "Old Rule")) as SaveUserRuleResult.Success).rule
        deleteCase(saved.id)

        saveCase(rule(name = "New Rule"))

        val all = repo.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("New Rule", all.single().name)
    }

    // ── Boundary: confidence and priority at boundary values ──────────────────────

    @Test
    fun save_twoRulesWithDifferentPriorities_bothPersisted() = runTest {
        saveCase(rule(name = "High priority", priority = 1))
        saveCase(rule(name = "Low priority",  priority = 100))

        val enabled = repo.getEnabledRules()
        assertEquals(2, enabled.size)
        assertFalse(enabled.none { it.name == "High priority" })
        assertFalse(enabled.none { it.name == "Low priority" })
    }
}
