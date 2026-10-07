package com.example.mileagetracker.di

import com.example.mileagetracker.data.repository.FirebaseSyncRepository
import com.example.mileagetracker.domain.repository.SyncRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    @Singleton
    abstract fun bindSyncRepository(repository: FirebaseSyncRepository): SyncRepository
}
