package com.example.mileagetracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mileagetracker.data.local.converter.RoomTypeConverters
import com.example.mileagetracker.data.local.dao.AccountDataDao
import com.example.mileagetracker.data.local.dao.ActiveTrackingDao
import com.example.mileagetracker.data.local.dao.ApprovalRequestDao
import com.example.mileagetracker.data.local.dao.BufferedTripDao
import com.example.mileagetracker.data.local.dao.CraRateDao
import com.example.mileagetracker.data.local.dao.DriveDao
import com.example.mileagetracker.data.local.dao.OdometerCaptureDao
import com.example.mileagetracker.data.local.dao.PeriodLockDao
import com.example.mileagetracker.data.local.dao.PolicyDao
import com.example.mileagetracker.data.local.dao.PolicyTemplateDao
import com.example.mileagetracker.data.local.dao.ReportHistoryDao
import com.example.mileagetracker.data.local.dao.RoutePointDao
import com.example.mileagetracker.data.local.dao.SyncDao
import com.example.mileagetracker.data.local.dao.TripAttachmentDao
import com.example.mileagetracker.data.local.dao.TripAuditDao
import com.example.mileagetracker.data.local.dao.UserClassificationRuleDao
import com.example.mileagetracker.data.local.dao.UserDao
import com.example.mileagetracker.data.local.dao.VehicleDao
import com.example.mileagetracker.data.local.dao.VehicleExpenseDao
import com.example.mileagetracker.data.local.entity.ActiveRoutePointEntity
import com.example.mileagetracker.data.local.entity.ActiveTrackingSessionEntity
import com.example.mileagetracker.data.local.entity.ApprovalRequestEntity
import com.example.mileagetracker.data.local.entity.BufferedTripEntity
import com.example.mileagetracker.data.local.entity.CraRateEntity
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.OdometerCaptureEntity
import com.example.mileagetracker.data.local.entity.PeriodLockEntity
import com.example.mileagetracker.data.local.entity.PolicyEntity
import com.example.mileagetracker.data.local.entity.PolicyTemplateEntity
import com.example.mileagetracker.data.local.entity.ReportHistoryEntity
import com.example.mileagetracker.data.local.entity.RoutePointEntity
import com.example.mileagetracker.data.local.entity.SyncConflictEntity
import com.example.mileagetracker.data.local.entity.SyncMetadataEntity
import com.example.mileagetracker.data.local.entity.SyncOutboxEntity
import com.example.mileagetracker.data.local.entity.TripAttachmentEntity
import com.example.mileagetracker.data.local.entity.TripAuditEntity
import com.example.mileagetracker.data.local.entity.UserClassificationRuleEntity
import com.example.mileagetracker.data.local.entity.UserEntity
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.local.entity.VehicleExpenseEntity
import com.example.mileagetracker.data.local.migration.Migration10To11
import com.example.mileagetracker.data.local.migration.Migration11To12
import com.example.mileagetracker.data.local.migration.Migration12To13
import com.example.mileagetracker.data.local.migration.Migration13To14
import com.example.mileagetracker.data.local.migration.Migration14To15
import com.example.mileagetracker.data.local.migration.Migration15To16
import com.example.mileagetracker.data.local.migration.Migration1To2
import com.example.mileagetracker.data.local.migration.Migration2To3
import com.example.mileagetracker.data.local.migration.Migration3To4
import com.example.mileagetracker.data.local.migration.Migration4To5
import com.example.mileagetracker.data.local.migration.Migration5To6
import com.example.mileagetracker.data.local.migration.Migration6To7
import com.example.mileagetracker.data.local.migration.Migration7To8
import com.example.mileagetracker.data.local.migration.Migration8To9
import com.example.mileagetracker.data.local.migration.Migration9To10

/**
 * Single Room database instance for MileageTracker.
 *
 * ## Version history
 * | Version | Task        | Changes                                                        |
 * |---------|-------------|----------------------------------------------------------------|
 * | 1       | —           | Initial schema: `drives`, `policies`, `users`                  |
 * | 2       | P1-06–P1-10 | Expand `drives`; add `vehicles`, `trip_audit`,                 |
 * |         |             | `cra_rates`, `trip_attachments`                                |
 * | 3       | Task 2.1    | Add `route_points`; add `meta_*` columns to `drives`           |
 * | 4       | Task 2.2    | Add `buffered_trips`                                           |
 * | 5       | —           | Version bump only; no DDL changes (see Migration4To5)          |
 * | 6       | Task 4.5    | Add `odometer_captures` with FK to `vehicles`                  |
 * | 7       | Task 4.3b   | Add `user_classification_rules` for user-defined rules         |
 * | 8       | Task 5.3    | Add `report_history` for CRA logbook export metadata           |
 * | 9       | Task 8.1    | Add `vehicle_expenses` for full vehicle expense tracking       |
 * | 10      | Task 8.3    | Add `approval_requests` + `period_locks`                       |
 * | 11      | Task 8.4    | Add `policy_templates` for reimbursement policy management     |
 * | 12      | Beta        | Correct the 2026 CRA provincial allowance seed                 |
 * | 13      | Beta        | Add durable active-trip sessions and checkpointed route points |
 * | 14      | Beta        | Add sync UUID metadata, outbox, and visible conflict records    |
 * | 15      | Beta        | Scope user classification rules by owner UID                    |
 * | 16      | CRA         | Add tax year and boundary type to odometer captures             |
 *
 */
@Database(
    entities = [
        DriveEntity::class,
        PolicyEntity::class,
        UserEntity::class,
        VehicleEntity::class,
        TripAuditEntity::class,
        CraRateEntity::class,
        TripAttachmentEntity::class,
        RoutePointEntity::class,
        BufferedTripEntity::class,
        OdometerCaptureEntity::class,
        UserClassificationRuleEntity::class,
        ReportHistoryEntity::class,
        VehicleExpenseEntity::class,
        ApprovalRequestEntity::class,
        PeriodLockEntity::class,
        PolicyTemplateEntity::class,
        ActiveTrackingSessionEntity::class,
        ActiveRoutePointEntity::class,
        SyncOutboxEntity::class,
        SyncMetadataEntity::class,
        SyncConflictEntity::class,
    ],
    version = 16,
    exportSchema = true,
)
@TypeConverters(RoomTypeConverters::class)
abstract class MileageDatabase : RoomDatabase() {

    abstract fun driveDao(): DriveDao
    abstract fun policyDao(): PolicyDao
    abstract fun userDao(): UserDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun tripAuditDao(): TripAuditDao
    abstract fun craRateDao(): CraRateDao
    abstract fun tripAttachmentDao(): TripAttachmentDao
    abstract fun routePointDao(): RoutePointDao
    abstract fun bufferedTripDao(): BufferedTripDao
    abstract fun odometerCaptureDao(): OdometerCaptureDao
    abstract fun userClassificationRuleDao(): UserClassificationRuleDao

    // ── Task 5.3 ──────────────────────────────────────────────────────────────────
    abstract fun reportHistoryDao(): ReportHistoryDao

    // ── Task 8.1 ──────────────────────────────────────────────────────────────────
    abstract fun vehicleExpenseDao(): VehicleExpenseDao

    // ── Task 8.3 ──────────────────────────────────────────────────────────────────
    abstract fun approvalRequestDao(): ApprovalRequestDao
    abstract fun periodLockDao(): PeriodLockDao

    // ── Task 8.4 ──────────────────────────────────────────────────────────────────
    abstract fun policyTemplateDao(): PolicyTemplateDao
    abstract fun activeTrackingDao(): ActiveTrackingDao
    abstract fun syncDao(): SyncDao
    abstract fun accountDataDao(): AccountDataDao

    companion object {
        internal const val DEFAULT_POLICY_BASE_RATE = 0.73f
        internal const val DEFAULT_POLICY_SECONDARY_RATE = 0.67f
        internal const val DB_NAME = "mileage_database"

        internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            Migration1To2,
            Migration2To3,
            Migration3To4,
            Migration4To5,
            Migration5To6,
            Migration6To7,
            Migration7To8,
            Migration8To9,
            Migration9To10,
            Migration10To11,
            Migration11To12,
            Migration12To13,
            Migration13To14,
            Migration14To15,
            Migration15To16,
        )

        internal val SEEDING_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                seedDefaults(db)
            }
        }

        private fun seedDefaults(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                INSERT INTO policies (policyName, policyType, baseRate, secondaryRate, thresholdKm)
                VALUES ('Official CRA Rates (2026)', 'CRA_Standard', $DEFAULT_POLICY_BASE_RATE, $DEFAULT_POLICY_SECONDARY_RATE, 5000.0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO users (name, email, activePolicyId, totalYtdDistance)
                VALUES ('Freelancer', '', (SELECT id FROM policies ORDER BY id DESC LIMIT 1), 0.0)
                """.trimIndent()
            )
        }
    }
}
