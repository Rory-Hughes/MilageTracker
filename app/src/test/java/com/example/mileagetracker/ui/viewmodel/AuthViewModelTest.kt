package com.example.mileagetracker.ui.viewmodel

import android.Manifest
import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.example.mileagetracker.data.local.ActiveTripStore
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.LocalDataInventory
import com.example.mileagetracker.domain.model.SignOutReadiness
import com.example.mileagetracker.domain.repository.SyncPushResult
import com.example.mileagetracker.domain.repository.SyncRepository
import com.example.mileagetracker.domain.repository.SyncRestoreResult
import com.example.mileagetracker.domain.usecase.account.DeleteAccountUseCase
import com.example.mileagetracker.domain.usecase.account.GetSignOutReadinessUseCase
import com.example.mileagetracker.domain.usecase.account.LegacyClaimUseCase
import com.example.mileagetracker.service.AutomaticTrackingController
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private lateinit var viewModel: AuthViewModel
    private lateinit var auth: FirebaseAuth
    private lateinit var currentUserProvider: CurrentUserProvider
    private lateinit var activeTripStore: ActiveTripStore
    private lateinit var syncRepository: SyncRepository
    private lateinit var deleteAccountUseCase: DeleteAccountUseCase
    private lateinit var legacyClaimUseCase: LegacyClaimUseCase
    private lateinit var getSignOutReadinessUseCase: GetSignOutReadinessUseCase
    private lateinit var automaticTrackingController: AutomaticTrackingController
    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        auth = mockk()
        currentUserProvider = mockk()
        activeTripStore = mockk()
        syncRepository = mockk()
        deleteAccountUseCase = mockk()
        legacyClaimUseCase = mockk()
        getSignOutReadinessUseCase = mockk()
        automaticTrackingController = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { auth.currentUser } returns null
        coEvery { getSignOutReadinessUseCase() } returns readyToSignOut()

        viewModel = AuthViewModel(
            auth,
            currentUserProvider,
            syncRepository,
            deleteAccountUseCase,
            legacyClaimUseCase,
            getSignOutReadinessUseCase,
            automaticTrackingController,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
        Dispatchers.resetMain()
    }

    @Test
    fun `isLoggedIn reflects firebase current user`() {
        assertFalse(viewModel.isLoggedIn.value)
        
        val user = mockk<FirebaseUser>()
        every { auth.currentUser } returns user
        
        // Re-init to pick up logged in state
        val vm = AuthViewModel(
            auth,
            currentUserProvider,
            syncRepository,
            deleteAccountUseCase,
            legacyClaimUseCase,
            getSignOutReadinessUseCase,
            automaticTrackingController,
        )
        assertTrue(vm.isLoggedIn.value)
    }

    @Test
    fun `login restores remote data before success callback for verified user`() {
        val signInTask = mockk<Task<AuthResult>>()
        val user = mockk<FirebaseUser>()
        var successCalled = false
        var errorCalled = false

        every { auth.signInWithEmailAndPassword("a@b.com", "secret") } returns signInTask
        coEvery { signInTask.await() } returns mockk()
        every { auth.currentUser } returns user
        every { user.isEmailVerified } returns true
        coEvery { syncRepository.restoreRemoteData() } returns SyncRestoreResult(1, 0, 0)

        viewModel.login(
            email = "a@b.com",
            pass = "secret",
            onSuccess = { successCalled = true },
            onError = { errorCalled = true },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(successCalled)
        assertFalse(errorCalled)
        assertTrue(viewModel.isLoggedIn.value)
        coVerify(exactly = 1) { syncRepository.restoreRemoteData() }
    }

    @Test
    fun `login ignores restore failure when credentials are valid and verified`() {
        val signInTask = mockk<Task<AuthResult>>()
        val user = mockk<FirebaseUser>()
        var successCalled = false

        every { auth.signInWithEmailAndPassword("a@b.com", "secret") } returns signInTask
        coEvery { signInTask.await() } returns mockk()
        every { auth.currentUser } returns user
        every { user.isEmailVerified } returns true
        coEvery { syncRepository.restoreRemoteData() } throws IllegalStateException("network")

        viewModel.login(
            email = "a@b.com",
            pass = "secret",
            onSuccess = { successCalled = true },
            onError = { throw AssertionError("onError should not be called") },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(successCalled)
        assertTrue(viewModel.isLoggedIn.value)
    }

    @Test
    fun `signOut syncs and clears cache when backup is enabled and status becomes clean`() {
        var successCalled = false
        var errorMessage: String? = null
        every { auth.currentUser } returns mockk()
        every { currentUserProvider.requireUid() } returns "u1"
        every { auth.signOut() } returns Unit
        coEvery { getSignOutReadinessUseCase() } returnsMany listOf(
            readyToSignOut(pendingSyncCount = 2),
            readyToSignOut(),
        )
        coEvery { syncRepository.push() } returns SyncPushResult(2, 0, 0)
        coEvery { syncRepository.clearLocalCache("u1") } returns Unit

        viewModel.signOut(
            onSuccess = { successCalled = true },
            onError = { errorMessage = it },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("errorMessage=$errorMessage", successCalled)
        assertFalse(viewModel.isLoggedIn.value)
        assertFalse(errorMessage != null)
        coVerify { syncRepository.push() }
        coVerify { syncRepository.clearLocalCache("u1") }
        verify { automaticTrackingController.setEnabled(false) }
        verify { auth.signOut() }
    }

    @Test
    fun `signOut blocks when cloud backup is off and local data exists`() {
        var successCalled = false
        var errorMessage: String? = null
        every { auth.currentUser } returns mockk()
        every { currentUserProvider.requireUid() } returns "u1"
        coEvery {
            getSignOutReadinessUseCase()
        } returns readyToSignOut(
            cloudBackupEnabled = false,
            localDataInventory = LocalDataInventory(drives = 4),
        )

        viewModel.signOut(
            onSuccess = { successCalled = true },
            onError = { errorMessage = it },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(successCalled)
        assertTrue(errorMessage?.contains("cloud backup", ignoreCase = true) == true)
        coVerify(exactly = 0) { syncRepository.push() }
        verify(exactly = 0) { auth.signOut() }
    }

    private fun readyToSignOut(
        cloudBackupEnabled: Boolean = true,
        pendingSyncCount: Int = 0,
        unresolvedConflictCount: Int = 0,
        localDataInventory: LocalDataInventory = LocalDataInventory(drives = 1),
    ): SignOutReadiness = SignOutReadiness(
        cloudBackupEnabled = cloudBackupEnabled,
        hasActiveTrip = false,
        pendingSyncCount = pendingSyncCount,
        unresolvedConflictCount = unresolvedConflictCount,
        localDataInventory = localDataInventory,
    )
}
