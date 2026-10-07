package com.example.mileagetracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.UserClassificationRuleEntity
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
 * Room integration tests for [UserClassificationRuleDao] against a real (in-memory)
 * SQLite database. These exercise the actual SQL — the REPLACE upsert semantics,
 * priority-then-name ordering, the enabled-only filter, and hard deletion.
 */
@RunWith(AndroidJUnit4::class)
class UserClassificationRuleDaoTest {
    private companion object {
        const val OWNER_UID = "u1"
        const val OTHER_UID = "u2"
    }

    private lateinit var db: MileageDatabase
    private lateinit var dao: UserClassificationRuleDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.userClassificationRuleDao()
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
        category: String = "BUSINESS",
        confidence: Float = 0.75f,
        priority: Int = 50,
        isEnabled: Boolean = true,
        createdAt: Long = 1_000L,
        ownerUid: String = OWNER_UID,
    ) = UserClassificationRuleEntity(
        id = id,
        ownerUid = ownerUid,
        name = name,
        keyword = keyword,
        category = category,
        confidence = confidence,
        priority = priority,
        isEnabled = isEnabled,
        createdAt = createdAt,
    )

    // ── upsert ────────────────────────────────────────────────────────────────

    @Test
    fun upsert_insertsNewRow_andReturnsRowId() = runTest {
        val rowId = dao.upsert(rule(name = "Commute"))

        assertTrue(rowId > 0)
        val all = dao.observeAll(OWNER_UID).first()
        assertEquals(1, all.size)
        assertEquals("Commute", all.single().name)
    }

    @Test
    fun upsert_withExistingId_replacesRow() = runTest {
        val id = dao.upsert(rule(name = "Old", keyword = "airport")).toInt()

        dao.upsert(rule(id = id, name = "New", keyword = "hospital"))

        val all = dao.observeAll(OWNER_UID).first()
        assertEquals(1, all.size)
        assertEquals("New", all.single().name)
        assertEquals("hospital", all.single().keyword)
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    fun update_persistsChangedFields() = runTest {
        val id = dao.upsert(rule(name = "Original", confidence = 0.75f, isEnabled = true)).toInt()
        val existing = dao.observeAll(OWNER_UID).first().single()

        dao.update(existing.copy(name = "Renamed", confidence = 0.9f, isEnabled = false))

        val updated = dao.observeAll(OWNER_UID).first().single()
        assertEquals("Renamed", updated.name)
        assertEquals(0.9f, updated.confidence)
        assertEquals(false, updated.isEnabled)
    }

    // ── deleteById ────────────────────────────────────────────────────────────

    @Test
    fun deleteById_removesOnlyThatRule() = runTest {
        val id = dao.upsert(rule(name = "ToDelete")).toInt()
        dao.upsert(rule(name = "ToKeep"))

        dao.deleteById(OWNER_UID, id)

        val remaining = dao.observeAll(OWNER_UID).first()
        assertEquals(1, remaining.size)
        assertEquals("ToKeep", remaining.single().name)
    }

    @Test
    fun deleteById_onUnknownId_isNoOp() = runTest {
        dao.upsert(rule())

        dao.deleteById(OWNER_UID, 999)

        assertEquals(1, dao.observeAll(OWNER_UID).first().size)
    }

    // ── observeAll: ordering ──────────────────────────────────────────────────

    @Test
    fun observeAll_ordersByPriorityAsc_thenNameAsc() = runTest {
        dao.upsert(rule(name = "Zulu",  priority = 10))
        dao.upsert(rule(name = "Alpha", priority = 10))
        dao.upsert(rule(name = "Gamma", priority = 5))
        dao.upsert(rule(name = "Beta",  priority = 20))

        val names = dao.observeAll(OWNER_UID).first().map { it.name }

        // priority 5 first, then priority 10 (Alpha before Zulu), then priority 20.
        assertEquals(listOf("Gamma", "Alpha", "Zulu", "Beta"), names)
    }

    @Test
    fun observeAll_includesDisabledRules() = runTest {
        dao.upsert(rule(name = "Enabled",  isEnabled = true))
        dao.upsert(rule(name = "Disabled", isEnabled = false))

        val all = dao.observeAll(OWNER_UID).first()
        assertEquals(2, all.size)
    }

    // ── getEnabledRules ───────────────────────────────────────────────────────

    @Test
    fun getEnabledRules_excludesDisabledRows_ordersByPriorityAsc() = runTest {
        dao.upsert(rule(name = "HighPri",  priority = 10, isEnabled = true))
        dao.upsert(rule(name = "LowPri",   priority = 90, isEnabled = true))
        dao.upsert(rule(name = "Disabled", priority = 1,  isEnabled = false))

        val enabled = dao.getEnabledRules(OWNER_UID)

        assertEquals(2, enabled.size)
        assertEquals(listOf("HighPri", "LowPri"), enabled.map { it.name })
        assertTrue(enabled.all { it.isEnabled })
    }

    @Test
    fun getEnabledRules_returnsEmpty_whenAllDisabled() = runTest {
        dao.upsert(rule(isEnabled = false))

        assertTrue(dao.getEnabledRules(OWNER_UID).isEmpty())
    }

    // ── round-trip field values ───────────────────────────────────────────────

    @Test
    fun upsert_roundTripsAllFields() = runTest {
        dao.upsert(
            rule(
                name = "Medical trips",
                keyword = "clinic",
                category = "MEDICAL",
                confidence = 0.85f,
                priority = 30,
                isEnabled = false,
                createdAt = 9_999L,
            ),
        )

        val loaded = dao.observeAll(OWNER_UID).first().single()
        assertEquals("Medical trips", loaded.name)
        assertEquals("clinic", loaded.keyword)
        assertEquals("MEDICAL", loaded.category)
        assertEquals(0.85f, loaded.confidence)
        assertEquals(30, loaded.priority)
        assertEquals(false, loaded.isEnabled)
        assertEquals(9_999L, loaded.createdAt)
    }

    @Test
    fun observeAll_isScopedByOwnerUid() = runTest {
        dao.upsert(rule(name = "Owner 1", ownerUid = OWNER_UID))
        dao.upsert(rule(name = "Owner 2", ownerUid = OTHER_UID))

        val owned = dao.observeAll(OWNER_UID).first()
        val other = dao.observeAll(OTHER_UID).first()

        assertEquals(listOf("Owner 1"), owned.map { it.name })
        assertEquals(listOf("Owner 2"), other.map { it.name })
    }

    @Test
    fun deleteById_doesNotDeleteOtherOwnersMatchingId() = runTest {
        val ownerId = dao.upsert(rule(name = "Mine", ownerUid = OWNER_UID)).toInt()
        dao.upsert(rule(id = ownerId, name = "Theirs", ownerUid = OTHER_UID))

        dao.deleteById(OWNER_UID, ownerId)

        assertTrue(dao.observeAll(OWNER_UID).first().isEmpty())
        assertEquals(listOf("Theirs"), dao.observeAll(OTHER_UID).first().map { it.name })
    }
}
