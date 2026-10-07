package com.example.mileagetracker.di

import com.example.mileagetracker.data.seeder.CraRateSeeder
import com.example.mileagetracker.domain.repository.RateRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides seeder dependencies.
 *
 * [CraRateSeeder] is provided as a [Singleton] so that
 * [MileageApplication] and any other injection site share a single
 * instance.  The seeder itself is stateless (all state lives in the
 * database via [RateRepository]), so the singleton scope has no
 * practical effect beyond avoiding unnecessary re-construction.
 */
@Module
@InstallIn(SingletonComponent::class)
object SeedingModule {

    @Provides
    @Singleton
    fun provideCraRateSeeder(
        rateRepository: RateRepository,
    ): CraRateSeeder = CraRateSeeder(rateRepository)
}
