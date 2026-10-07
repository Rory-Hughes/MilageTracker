package com.example.mileagetracker.di

import android.content.Context
import com.example.mileagetracker.data.local.TripBufferStore
import com.example.mileagetracker.data.local.dao.BufferedTripDao
import com.example.mileagetracker.domain.usecase.drive.PersistTrackedDriveUseCase
import com.example.mileagetracker.domain.usecase.tracking.FlushTripBufferUseCase
import com.example.mileagetracker.domain.usecase.tracking.ValidateTrackingPermissionsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for trip-buffer, flush, and tracking-diagnostics infrastructure.
 *
 * ## Provider inventory
 * | Method                                    | Type                                  | Task  |
 * |-------------------------------------------|---------------------------------------|-------|
 * | [provideTripBufferStore]                  | [TripBufferStore]                     | 2.2   |
 * | [provideFlushTripBufferUseCase]            | [FlushTripBufferUseCase]              | 2.2   |
 * | [provideValidateTrackingPermissionsUseCase]| [ValidateTrackingPermissionsUseCase]  | 2.5   |
 *
 * Note: [BufferedTripDao] is provided by [DatabaseModule]; it is NOT
 * re-declared here to avoid a Dagger/DuplicateBindings error (Task 2.3 fix).
 *
 * Note: [ValidateTrackingPermissionsUseCase] has `@Inject constructor` so
 * Hilt can construct it automatically when scoped to the ViewModel.  The
 * explicit [provideValidateTrackingPermissionsUseCase] provider is included
 * here for documentation clarity and to ensure the [ApplicationContext]
 * qualifier is applied consistently at the module level.
 */
@Module
@InstallIn(SingletonComponent::class)
object TrackingModule {

    @Provides
    @Singleton
    fun provideTripBufferStore(
        dao: BufferedTripDao,
    ): TripBufferStore = TripBufferStore(dao)

    @Provides
    @Singleton
    fun provideFlushTripBufferUseCase(
        tripBufferStore: TripBufferStore,
        persistTrackedDriveUseCase: PersistTrackedDriveUseCase,
    ): FlushTripBufferUseCase = FlushTripBufferUseCase(
        tripBufferStore = tripBufferStore,
        persistTrackedDriveUseCase = persistTrackedDriveUseCase,
    )

    @Provides
    @Singleton
    fun provideValidateTrackingPermissionsUseCase(
        @ApplicationContext context: Context,
    ): ValidateTrackingPermissionsUseCase = ValidateTrackingPermissionsUseCase(context)
}
