package com.example.mileagetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mileagetracker.data.local.entity.TripAuditEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for [TripAuditEntity].
 *
 * ## Immutability contract
 * This DAO exposes **insert and read operations only**.  No `@Update` or
 * `@Delete` methods are provided; audit rows must never be mutated after
 * insert.  Hard deletion is only permitted as part of a GDPR data-erasure
 * flow, handled at the database level via a CASCADE delete from the parent
 * `drives` row (added in Task 6.1).
 */
@Dao
interface TripAuditDao {

    // ───────────────────────────────────────────────────────────────────────────
    // Write
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Append a new audit row.  Returns the new row’s `rowId` for confirmation
     * (rarely needed by callers but useful in tests).
     *
     * [OnConflictStrategy.ABORT] is used so that a duplicate PK surfaces as
     * an exception rather than silently overwriting an immutable audit record.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAuditRow(auditRow: TripAuditEntity): Long

    @Query("DELETE FROM trip_audit WHERE trip_id = :tripId")
    suspend fun deleteAuditRowsForTrip(tripId: Int)

    // ───────────────────────────────────────────────────────────────────────────
    // Reads – per trip
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Full audit trail for a single trip, ordered oldest-first so the
     * timeline reads chronologically in the trip-detail screen.
     *
     * Returns a [Flow] so the audit timeline updates reactively if a new
     * event is appended while the screen is open (e.g. an auto-classification
     * fires in the background immediately after the user opens the detail).
     *
     * @param tripId the [TripAuditEntity.tripId] to fetch rows for.
     */
    @Query(
        """
        SELECT * FROM trip_audit
        WHERE  trip_id = :tripId
        ORDER BY changed_at ASC
    """
    )
    fun getAuditRowsForTrip(tripId: Int): Flow<List<TripAuditEntity>>

    /**
     * One-shot fetch of the full audit trail for a trip.  Use this in
     * use-cases or export flows where a [Flow] collector is not appropriate.
     *
     * @param tripId the trip to fetch rows for.
     */
    @Query(
        """
        SELECT * FROM trip_audit
        WHERE  trip_id = :tripId
        ORDER BY changed_at ASC
    """
    )
    suspend fun getAuditRowsForTripOnce(tripId: Int): List<TripAuditEntity>

    // ───────────────────────────────────────────────────────────────────────────
    // Reads – date range
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * All audit rows within a timestamp window, ordered oldest-first.
     * Used by admin/debug screens and future activity-feed features to
     * show a chronological list of all changes across all trips in a period.
     *
     * @param fromMillis start of range, inclusive (epoch millis).
     * @param toMillis   end of range, inclusive (epoch millis).
     */
    @Query(
        """
        SELECT * FROM trip_audit
        WHERE  changed_at >= :fromMillis
          AND  changed_at <= :toMillis
        ORDER BY changed_at ASC
    """
    )
    suspend fun getAuditRowsByDateRange(
        fromMillis: Long,
        toMillis: Long,
    ): List<TripAuditEntity>

    /**
     * All audit rows attributable to a specific actor within a timestamp
     * window, ordered oldest-first.  Useful for per-user activity summaries
     * and distinguishing human edits from system (`"SYSTEM"`) events.
     *
     * @param actorUid   the [TripAuditEntity.actorUid] to filter by.
     * @param fromMillis start of range, inclusive (epoch millis).
     * @param toMillis   end of range, inclusive (epoch millis).
     */
    @Query(
        """
        SELECT * FROM trip_audit
        WHERE  actor_uid  = :actorUid
          AND  changed_at >= :fromMillis
          AND  changed_at <= :toMillis
        ORDER BY changed_at ASC
    """
    )
    suspend fun getAuditRowsByActor(
        actorUid: String,
        fromMillis: Long,
        toMillis: Long,
    ): List<TripAuditEntity>
}
