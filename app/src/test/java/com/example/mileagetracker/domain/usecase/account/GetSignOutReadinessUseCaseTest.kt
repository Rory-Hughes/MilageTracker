package com.example.mileagetracker.domain.usecase.account

import com.example.mileagetracker.data.local.ActiveTripStore
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.CloudUploadConsent
import com.example.mileagetracker.domain.model.LocalDataInventory
import com.example.mileagetracker.domain.repository.SettingsRepository
import com.example.mileagetracker.domain.repository.SyncRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetSignOutReadinessUseCaseTest {
    private lateinit var currentUserProvider: CurrentUserProvider
    private lateinit var activeTripStore: ActiveTripStore
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var syncRepository: SyncRepository
    private lateinit var getLocalDataInventoryUseCase: GetLocalDataInventoryUseCase
    private lateinit var useCase: GetSignOutReadinessUseCase

    @Before
    fun setUp() {
        currentUserProvider = mockk()
        activeTripStore = mockk()
        settingsRepository = mockk()
        syncRepository = mockk()
        getLocalDataInventoryUseCase = mockk()

        every { currentUserProvider.requireUid() } returns "u1"
        useCase = GetSignOutReadinessUseCase(
            currentUserProvider = currentUserProvider,
            activeTripStore = activeTripStore,
            settingsRepository = settingsRepository,
            syncRepository = syncRepository,
            getLocalDataInventoryUseCase = getLocalDataInventoryUseCase,
        )
    }

    @Test
    fun `blocks sign-out when backup is disabled and local data exists`() = runTest {
        every { settingsRepository.getCloudUploadConsentVersion() } returns flowOf(0)
        coEvery { activeTripStore.hasActiveTrip() } returns false
        coEvery { syncRepository.pendingCount() } returns 0
        coEvery { syncRepository.unresolvedConflicts() } returns emptyList()
        coEvery { getLocalDataInventoryUseCase("u1") } returns LocalDataInventory(drives = 3)

        val readiness = useCase()

        assertFalse(readiness.canAttemptSignOut)
        assertTrue(readiness.failureMessage?.contains("cloud backup", ignoreCase = true) == true)
    }

    @Test
    fun `allows sign-out attempt when backup is enabled but pending sync remains`() = runTest {
        every {
            settingsRepository.getCloudUploadConsentVersion()
        } returns flowOf(CloudUploadConsent.CURRENT_VERSION)
        coEvery { activeTripStore.hasActiveTrip() } returns false
        coEvery { syncRepository.pendingCount() } returns 4
        coEvery { syncRepository.unresolvedConflicts() } returns emptyList()
        coEvery { getLocalDataInventoryUseCase("u1") } returns LocalDataInventory(drives = 7)

        val readiness = useCase()

        assertTrue(readiness.canAttemptSignOut)
        assertFalse(readiness.canFinishImmediately)
        assertTrue(readiness.requiresSyncBeforeSignOut)
        assertEquals(
            "Some mileage records are still waiting to upload. Retry sign-out when you are online.",
            readiness.failureMessage,
        )
    }

    @Test
    fun `blocks sign-out when conflicts exist`() = runTest {
        every {
            settingsRepository.getCloudUploadConsentVersion()
        } returns flowOf(CloudUploadConsent.CURRENT_VERSION)
        coEvery { activeTripStore.hasActiveTrip() } returns false
        coEvery { syncRepository.pendingCount() } returns 0
        coEvery { syncRepository.unresolvedConflicts() } returns listOf(mockk())
        coEvery { getLocalDataInventoryUseCase("u1") } returns LocalDataInventory(drives = 1)

        val readiness = useCase()

        assertFalse(readiness.canAttemptSignOut)
        assertEquals("Resolve sync conflicts before signing out.", readiness.failureMessage)
    }
}
