package com.example.mileagetracker.di

import com.example.mileagetracker.data.places.GooglePlacesService
import com.example.mileagetracker.data.repository.AttachmentRepositoryImpl
import com.example.mileagetracker.data.repository.AuditRepositoryImpl
import com.example.mileagetracker.data.repository.DriveRepositoryImpl
import com.example.mileagetracker.data.repository.EvidenceSyncStore
import com.example.mileagetracker.data.repository.FirebaseEvidenceSyncStore
import com.example.mileagetracker.data.repository.FirebaseTripEndpointAddressRepository
import com.example.mileagetracker.data.repository.GoogleDirectionsRepository
import com.example.mileagetracker.data.repository.MapsCoTripEndpointAddressRepository
import com.example.mileagetracker.data.repository.OdometerRepositoryImpl
import com.example.mileagetracker.data.repository.RateRepositoryImpl
import com.example.mileagetracker.data.repository.ReportHistoryRepositoryImpl
import com.example.mileagetracker.data.repository.SettingsRepositoryImpl
import com.example.mileagetracker.data.repository.TrackingRepositoryImpl
import com.example.mileagetracker.data.repository.UserClassificationRuleRepositoryImpl
import com.example.mileagetracker.data.repository.VehicleExpenseRepositoryImpl
import com.example.mileagetracker.data.repository.VehicleRepositoryImpl
import com.example.mileagetracker.domain.places.PlacesService
import com.example.mileagetracker.domain.repository.AttachmentRepository
import com.example.mileagetracker.domain.repository.AuditRepository
import com.example.mileagetracker.domain.repository.DirectionsRepository
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.OdometerRepository
import com.example.mileagetracker.domain.repository.RateRepository
import com.example.mileagetracker.domain.repository.ReportHistoryRepository
import com.example.mileagetracker.domain.repository.SettingsRepository
import com.example.mileagetracker.domain.repository.TrackingRepository
import com.example.mileagetracker.domain.repository.VehicleDefaultSettingsRepository
import com.example.mileagetracker.domain.repository.TripEndpointAddressRepository
import com.example.mileagetracker.domain.repository.UserClassificationRuleRepository
import com.example.mileagetracker.domain.repository.VehicleExpenseRepository
import com.example.mileagetracker.domain.repository.VehicleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindDriveRepository(impl: DriveRepositoryImpl): DriveRepository

    @Binds @Singleton
    abstract fun bindVehicleRepository(impl: VehicleRepositoryImpl): VehicleRepository

    @Binds @Singleton
    abstract fun bindAuditRepository(impl: AuditRepositoryImpl): AuditRepository

    @Binds @Singleton
    abstract fun bindRateRepository(impl: RateRepositoryImpl): RateRepository

    @Binds @Singleton
    abstract fun bindTrackingRepository(impl: TrackingRepositoryImpl): TrackingRepository

    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds @Singleton
    abstract fun bindVehicleDefaultSettingsRepository(
        impl: SettingsRepositoryImpl,
    ): VehicleDefaultSettingsRepository
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EvidenceRepositoryModule {
    @Binds @Singleton
    abstract fun bindEvidenceSyncStore(impl: FirebaseEvidenceSyncStore): EvidenceSyncStore

    /**
     * Reverse-geocoding is served by [MapsCoTripEndpointAddressRepository]
     * (geocode.maps.co) — direct client call, no Firebase Function.
     *
     * The original [FirebaseTripEndpointAddressRepository] is left in the
     * codebase for now in case we want to switch back; it can be removed
     * once we've confirmed the new path is stable in production.
     */
    @Binds @Singleton
    abstract fun bindTripEndpointAddressRepository(
        impl: MapsCoTripEndpointAddressRepository,
    ): TripEndpointAddressRepository

    @Binds @Singleton
    abstract fun bindAttachmentRepository(impl: AttachmentRepositoryImpl): AttachmentRepository
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VehicleRepositoryModule {
    @Binds @Singleton
    abstract fun bindOdometerRepository(impl: OdometerRepositoryImpl): OdometerRepository

    @Binds @Singleton
    abstract fun bindVehicleExpenseRepository(
        impl: VehicleExpenseRepositoryImpl,
    ): VehicleExpenseRepository
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReportingRepositoryModule {
    @Binds @Singleton
    abstract fun bindUserClassificationRuleRepository(
        impl: UserClassificationRuleRepositoryImpl,
    ): UserClassificationRuleRepository

    @Binds @Singleton
    abstract fun bindReportHistoryRepository(
        impl: ReportHistoryRepositoryImpl,
    ): ReportHistoryRepository
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MapsRepositoryModule {
    @Binds @Singleton
    abstract fun bindDirectionsRepository(impl: GoogleDirectionsRepository): DirectionsRepository

    @Binds @Singleton
    abstract fun bindPlacesService(impl: GooglePlacesService): PlacesService
}
