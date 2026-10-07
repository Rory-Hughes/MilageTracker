package com.example.mileagetracker.di

import com.example.mileagetracker.data.remote.ocr.MlKitOcrService
import com.example.mileagetracker.domain.service.OcrService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the expense feature.
 *
 * Binds [OcrService] (domain layer) to [MlKitOcrService] (data layer).
 * To swap in a cloud OCR provider, replace the binding here without
 * touching any use-case or UI code.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExpenseModule {

    @Binds
    @Singleton
    abstract fun bindOcrService(impl: MlKitOcrService): OcrService
}
