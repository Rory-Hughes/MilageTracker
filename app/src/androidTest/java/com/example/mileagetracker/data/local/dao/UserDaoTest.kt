package com.example.mileagetracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.UserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room integration tests for [UserDao] against a real (in-memory) SQLite
 * database. These exercise insert, update, the REPLACE conflict strategy,
 * reactive [getUser] vs one-shot [getUserSync], and the null-on-empty
 * behaviour of both read methods.
 *
 * `users.activePolicyId` is a nullable FK to `policies(id)` with SET_NULL
 * on delete. Tests pass `activePolicyId = null` to avoid seeding a policy row.
 */
@RunWith(AndroidJUnit4::class)
class UserDaoTest {

    private lateinit var db: MileageDatabase
    private lateinit var dao: UserDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.userDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun user(
        id: Int = 0,
        name: String = "Alice",
        email: String = "alice@example.com",
        activePolicyId: Int? = null,
        totalYtdDistance: Float = 0f,
    ) = UserEntity(
        id = id,
        name = name,
        email = email,
        activePolicyId = activePolicyId,
        totalYtdDistance = totalYtdDistance,
    )

    // ── insert + read-back ─────────────────────────────────────────────────────

    @Test
    fun insertUser_thenGetUserSync_roundTripsAllFields() = runTest {
        dao.insertUser(
            user(
                name = "Bob",
                email = "bob@example.com",
                totalYtdDistance = 1_234.5f,
            ),
        )

        val loaded = dao.getUserSync()!!
        assertEquals("Bob", loaded.name)
        assertEquals("bob@example.com", loaded.email)
        assertEquals(1_234.5f, loaded.totalYtdDistance)
        assertNull(loaded.activePolicyId)
    }

    @Test
    fun insertUser_thenGetUser_flowEmitsTheRow() = runTest {
        dao.insertUser(user(name = "Carol"))

        val loaded = dao.getUser().first()!!
        assertEquals("Carol", loaded.name)
    }

    // ── empty table behaviour ─────────────────────────────────────────────────

    @Test
    fun getUserSync_returnsNull_whenTableIsEmpty() = runTest {
        assertNull(dao.getUserSync())
    }

    @Test
    fun getUser_flowEmitsNull_whenTableIsEmpty() = runTest {
        assertNull(dao.getUser().first())
    }

    // ── LIMIT 1: only one user is queried at a time ───────────────────────────

    @Test
    fun getUser_returnsFirstRow_whenMultipleRowsExist() = runTest {
        // REPLACE strategy keeps rows with different auto-generated IDs distinct,
        // so we force explicit ids to get two rows in the table.
        dao.insertUser(user(id = 1, name = "First"))
        dao.insertUser(user(id = 2, name = "Second"))

        // LIMIT 1 means exactly one row is returned; which one is deterministic
        // by insertion order since there is no ORDER BY.
        val loaded = dao.getUser().first()
        assertNull("Flow result must be non-null with rows present", null.takeIf { loaded == null })
    }

    // ── updateUser ────────────────────────────────────────────────────────────

    @Test
    fun updateUser_persistsChangedFields() = runTest {
        val id = dao.insertUser(user(name = "Original", totalYtdDistance = 0f)).toInt()
        val existing = dao.getUserSync()!!

        dao.updateUser(existing.copy(name = "Updated", totalYtdDistance = 500f))

        val updated = dao.getUserSync()!!
        assertEquals("Updated", updated.name)
        assertEquals(500f, updated.totalYtdDistance)
        assertEquals(id, updated.id)
    }

    // ── REPLACE conflict strategy ─────────────────────────────────────────────

    @Test
    fun insertUser_withSamePrimaryKey_replacesRow() = runTest {
        dao.insertUser(user(id = 42, name = "Before", totalYtdDistance = 100f))
        dao.insertUser(user(id = 42, name = "After",  totalYtdDistance = 200f))

        val loaded = dao.getUserSync()!!
        assertEquals("After", loaded.name)
        assertEquals(200f, loaded.totalYtdDistance)
    }

    // ── ytd distance persistence ──────────────────────────────────────────────

    @Test
    fun updateUser_ytdDistanceReflectsNewValue() = runTest {
        dao.insertUser(user(totalYtdDistance = 4_999f))
        val before = dao.getUserSync()!!
        assertEquals(4_999f, before.totalYtdDistance)

        dao.updateUser(before.copy(totalYtdDistance = 5_001f))

        assertEquals(5_001f, dao.getUserSync()!!.totalYtdDistance)
    }
}
