package com.example.mileagetracker.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.dao.PolicyTemplateDao
import com.example.mileagetracker.data.repository.PolicyRepository
import com.example.mileagetracker.domain.usecase.InviteTeamMemberUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Singleton-scoped team bindings: database-derived dependencies and FirebaseAuth.
 */
@Module
@InstallIn(SingletonComponent::class)
object TeamSingletonModule {

    @Provides
    @Singleton
    fun providePolicyTemplateDao(db: MileageDatabase): PolicyTemplateDao =
        db.policyTemplateDao()

    @Provides
    @Singleton
    fun providePolicyRepository(dao: PolicyTemplateDao): PolicyRepository =
        PolicyRepository(dao)

    /**
     * Provides the Firebase Auth singleton.
     * FirebaseAuth.getInstance() is itself a singleton under the hood;
     * wrapping it here makes it injectable anywhere in the Hilt graph.
     */
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore =
        FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder(firestoreSettings)
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions =
        FirebaseFunctions.getInstance("northamerica-northeast1")

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()
}

/**
 * ViewModelScoped team use-case bindings.
 *
 * [InviteTeamMemberUseCase] requires an explicit @Provides because it takes
 * @ApplicationContext — Hilt only auto-injects @ApplicationContext in classes
 * that are themselves @AndroidEntryPoint or directly installed in a component;
 * use cases need the explicit wiring.
 */
@Module
@InstallIn(ViewModelComponent::class)
object TeamViewModelModule {

    @Provides
    @ViewModelScoped
    fun provideInviteTeamMemberUseCase(
        @ApplicationContext context: Context,
        auth: FirebaseAuth,
    ): InviteTeamMemberUseCase = InviteTeamMemberUseCase(context, auth)
}
