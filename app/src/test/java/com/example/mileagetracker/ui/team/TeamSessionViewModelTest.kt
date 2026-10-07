package com.example.mileagetracker.ui.team

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.example.mileagetracker.domain.auth.CurrentUser
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.TeamRole
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TeamSessionViewModelTest {

    private lateinit var viewModel: TeamSessionViewModel
    private lateinit var currentUserProvider: CurrentUserProvider
    private lateinit var firestore: FirebaseFirestore
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        currentUserProvider = mockk()
        firestore = mockk()

        // Default: user not signed in
        every { currentUserProvider.currentUser() } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadSession synthesises DRIVER for solo user`() = runTest {
        val user = CurrentUser("u1", "u1@e.com", "User 1", 1000L)
        every { currentUserProvider.currentUser() } returns user

        val collection = mockk<CollectionReference>()
        val document = mockk<DocumentReference>()
        every { firestore.collection("team") } returns collection
        every { collection.document("u1") } returns document
        every { document.get() } throws RuntimeException("No team document")

        viewModel = TeamSessionViewModel(currentUserProvider, firestore)
        advanceUntilIdle()

        val member = viewModel.currentMember.value
        assertNotNull(member)
        assertEquals("u1", member?.userId)
        assertEquals(TeamRole.DRIVER, member?.role)
        assertEquals("User 1", member?.displayName)
    }
}
