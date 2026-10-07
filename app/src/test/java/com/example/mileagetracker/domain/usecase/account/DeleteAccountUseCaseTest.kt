package com.example.mileagetracker.domain.usecase.account

import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.android.gms.tasks.Task
import com.google.firebase.functions.HttpsCallableResult
import com.example.mileagetracker.data.local.ActiveTripStore
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.repository.SyncRepository
import com.example.mileagetracker.service.AutomaticTrackingController
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.lang.IllegalStateException

class DeleteAccountUseCaseTest {

    private lateinit var auth: FirebaseAuth
    private lateinit var functions: FirebaseFunctions
    private lateinit var activeTripStore: ActiveTripStore
    private lateinit var syncRepository: SyncRepository
    private lateinit var currentUserProvider: CurrentUserProvider
    private lateinit var automaticTrackingController: AutomaticTrackingController
    private lateinit var useCase: DeleteAccountUseCase

    @Before
    fun setUp() {
        auth = mockk(relaxed = true)
        functions = mockk()
        activeTripStore = mockk()
        syncRepository = mockk(relaxed = true)
        currentUserProvider = mockk()
        automaticTrackingController = mockk(relaxed = true)
        useCase = DeleteAccountUseCase(
            auth,
            functions,
            activeTripStore,
            syncRepository,
            currentUserProvider,
            automaticTrackingController,
        )
        
        mockkStatic(EmailAuthProvider::class)
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    @After
    fun tearDown() {
        unmockkStatic(EmailAuthProvider::class)
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    @Test
    fun `executes successfully when all conditions met`() = runTest {
        val user = mockk<FirebaseUser>()
        val credential = mockk<AuthCredential>()
        val reauthTask = mockk<Task<Void>>()
        val callable = mockk<HttpsCallableReference>()
        val callTask = mockk<Task<HttpsCallableResult>>()
        val callResult = mockk<HttpsCallableResult>()

        coEvery { activeTripStore.hasActiveTrip() } returns false
        every { auth.currentUser } returns user
        every { user.email } returns "test@example.com"
        every { currentUserProvider.requireUid() } returns "u1"
        
        every { EmailAuthProvider.getCredential("test@example.com", "password") } returns credential
        every { user.reauthenticate(credential) } returns reauthTask
        coEvery { reauthTask.await() } returns mockk()

        every { functions.getHttpsCallable("deleteAccountData") } returns callable
        every { callable.call() } returns callTask
        coEvery { callTask.await() } returns callResult

        useCase("password")

        coVerify { syncRepository.clearLocalCache("u1") }
        verify { automaticTrackingController.setEnabled(false) }
        verify { auth.signOut() }
    }

    @Test
    fun `throws if active trip exists`() = runTest {
        coEvery { activeTripStore.hasActiveTrip() } returns true
        try {
            useCase("password")
            fail("Expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun `throws if password blank`() = runTest {
        try {
            useCase(" ")
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `throws if no user signed in`() = runTest {
        coEvery { activeTripStore.hasActiveTrip() } returns false
        every { auth.currentUser } returns null
        
        try {
            useCase("password")
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("No signed-in account.", e.message)
        }
    }

    @Test
    fun `throws if user has no email`() = runTest {
        val user = mockk<FirebaseUser>()
        coEvery { activeTripStore.hasActiveTrip() } returns false
        every { auth.currentUser } returns user
        every { user.email } returns null

        try {
            useCase("password")
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("This account does not have an email address.", e.message)
        }
    }
}
