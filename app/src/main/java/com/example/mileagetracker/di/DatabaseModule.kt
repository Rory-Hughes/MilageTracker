package com.example.mileagetracker.di

import android.content.Context
import androidx.room.Room
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.dao.AccountDataDao
import com.example.mileagetracker.data.local.dao.ActiveTrackingDao
import com.example.mileagetracker.data.local.dao.ApprovalRequestDao
import com.example.mileagetracker.data.local.dao.BufferedTripDao
import com.example.mileagetracker.data.local.dao.CraRateDao
import com.example.mileagetracker.data.local.dao.DriveDao
import com.example.mileagetracker.data.local.dao.OdometerCaptureDao
import com.example.mileagetracker.data.local.dao.PeriodLockDao
import com.example.mileagetracker.data.local.dao.PolicyDao
import com.example.mileagetracker.data.local.dao.ReportHistoryDao
import com.example.mileagetracker.data.local.dao.RoutePointDao
import com.example.mileagetracker.data.local.dao.SyncDao
import com.example.mileagetracker.data.local.dao.TripAttachmentDao
import com.example.mileagetracker.data.local.dao.TripAuditDao
import com.example.mileagetracker.data.local.dao.UserClassificationRuleDao
import com.example.mileagetracker.data.local.dao.UserDao
import com.example.mileagetracker.data.local.dao.VehicleDao
import com.example.mileagetracker.data.local.dao.VehicleExpenseDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): MileageDatabase = Room.databaseBuilder(
        context.applicationContext,
        MileageDatabase::class.java,
        MileageDatabase.DB_NAME,
    )
        .addMigrations(*MileageDatabase.ALL_MIGRATIONS)
        .addCallback(MileageDatabase.SEEDING_CALLBACK)
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
object LegacyDaoModule {
    @Provides @Singleton
    fun provideDriveDao(db: MileageDatabase): DriveDao = db.driveDao()

    @Provides @Singleton
    fun provideUserDao(db: MileageDatabase): UserDao = db.userDao()

    @Provides @Singleton
    fun providePolicyDao(db: MileageDatabase): PolicyDao = db.policyDao()
}

@Module
@InstallIn(SingletonComponent::class)
object TripDaoModule {
    @Provides @Singleton
    fun provideTripAuditDao(db: MileageDatabase): TripAuditDao = db.tripAuditDao()

    @Provides @Singleton
    fun provideTripAttachmentDao(db: MileageDatabase): TripAttachmentDao =
        db.tripAttachmentDao()

    @Provides @Singleton
    fun provideRoutePointDao(db: MileageDatabase): RoutePointDao = db.routePointDao()

    @Provides @Singleton
    fun provideBufferedTripDao(db: MileageDatabase): BufferedTripDao = db.bufferedTripDao()
}

@Module
@InstallIn(SingletonComponent::class)
object VehicleDaoModule {
    @Provides @Singleton
    fun provideVehicleDao(db: MileageDatabase): VehicleDao = db.vehicleDao()

    @Provides @Singleton
    fun provideVehicleExpenseDao(db: MileageDatabase): VehicleExpenseDao =
        db.vehicleExpenseDao()

    @Provides @Singleton
    fun provideOdometerCaptureDao(db: MileageDatabase): OdometerCaptureDao =
        db.odometerCaptureDao()
}

@Module
@InstallIn(SingletonComponent::class)
object ReferenceDaoModule {
    @Provides @Singleton
    fun provideCraRateDao(db: MileageDatabase): CraRateDao = db.craRateDao()

    @Provides @Singleton
    fun provideUserClassificationRuleDao(db: MileageDatabase): UserClassificationRuleDao =
        db.userClassificationRuleDao()

    @Provides @Singleton
    fun provideReportHistoryDao(db: MileageDatabase): ReportHistoryDao =
        db.reportHistoryDao()
}

@Module
@InstallIn(SingletonComponent::class)
object TeamDataDaoModule {
    @Provides @Singleton
    fun provideApprovalRequestDao(db: MileageDatabase): ApprovalRequestDao =
        db.approvalRequestDao()

    @Provides @Singleton
    fun providePeriodLockDao(db: MileageDatabase): PeriodLockDao = db.periodLockDao()
}

@Module
@InstallIn(SingletonComponent::class)
object SyncDataDaoModule {
    @Provides @Singleton
    fun provideActiveTrackingDao(db: MileageDatabase): ActiveTrackingDao =
        db.activeTrackingDao()

    @Provides @Singleton
    fun provideSyncDao(db: MileageDatabase): SyncDao = db.syncDao()

    @Provides @Singleton
    fun provideAccountDataDao(db: MileageDatabase): AccountDataDao = db.accountDataDao()
}
