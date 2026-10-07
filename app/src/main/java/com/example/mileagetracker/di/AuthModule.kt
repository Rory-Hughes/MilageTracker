package com.example.mileagetracker.di

import com.example.mileagetracker.data.auth.FirebaseCurrentUserProvider
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindCurrentUserProvider(
        provider: FirebaseCurrentUserProvider,
    ): CurrentUserProvider
}
