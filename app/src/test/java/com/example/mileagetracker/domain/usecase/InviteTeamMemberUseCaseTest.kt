package com.example.mileagetracker.domain.usecase

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.example.mileagetracker.domain.model.TeamRole
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class InviteTeamMemberUseCaseTest {

    private lateinit var context: Context
    private lateinit var auth: FirebaseAuth
    private lateinit var useCase: InviteTeamMemberUseCase

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        auth = mockk()
        useCase = InviteTeamMemberUseCase(context, auth)
        mockkStatic(ActionCodeSettings::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(ActionCodeSettings::class)
    }

    @Test
    fun `returns pending team member and calls firebase`() = runTest {
        val user = mockk<FirebaseUser>()
        every { auth.currentUser } returns user
        every { user.uid } returns "admin-1"
        
        val mockBuilder = mockk<ActionCodeSettings.Builder>(relaxed = true)
        every { ActionCodeSettings.newBuilder() } returns mockBuilder
        every { mockBuilder.setUrl(any()) } returns mockBuilder
        every { mockBuilder.build() } returns mockk()
        
        every { auth.sendSignInLinkToEmail(any(), any()) } returns mockk<Task<Void>>()

        val result = useCase("test@example.com", TeamRole.DRIVER)

        assertEquals("test@example.com", result.email)
        assertFalse(result.isActive)
        verify { auth.sendSignInLinkToEmail("test@example.com", any()) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws on invalid email`() = runTest {
        useCase("invalid-email", TeamRole.DRIVER)
    }

    @Test(expected = IllegalStateException::class)
    fun `throws if no admin signed in`() = runTest {
        every { auth.currentUser } returns null
        useCase("test@example.com", TeamRole.DRIVER)
    }
}
