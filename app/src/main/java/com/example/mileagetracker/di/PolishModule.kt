package com.example.mileagetracker.di

import com.example.mileagetracker.data.repository.CalendarRepositoryImpl
import com.example.mileagetracker.domain.repository.CalendarRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing Phase 7 (Polish & Export) dependency bindings.
 *
 * ## Task 7.4 — Calendar-Assisted Purpose Hints
 * Binds [CalendarRepository] → [CalendarRepositoryImpl].
 *
 * Additional Task 7.x bindings (attachments, audit, report readiness) will
 * be added here as each task is completed to keep the Phase 7 deps isolated
 * from the earlier [RepositoryModule] and [DatabaseModule].
 *
 * | Interface            | Implementation             | Task  |
 * |----------------------|----------------------------|-------|
 * | [CalendarRepository] | [CalendarRepositoryImpl]   | 7.4   |
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PolishModule {

    // ── Task 7.4 ───────────────────────────────────────────────────────────

    @Binds @Singleton
    abstract fun bindCalendarRepository(
        impl: CalendarRepositoryImpl,
    ): CalendarRepository
}
