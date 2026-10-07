package com.example.mileagetracker.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.FirebaseUserMetadata
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FirebaseCurrentUserProviderTest {

    private lateinit var auth: FirebaseAuth
    private lateinit var provider: FirebaseCurrentUserProvider

    @Before
    fun setUp() {
        auth = mockk()
        provider = FirebaseCurrentUserProvider(auth)
    }

    @Test
    fun `currentUser returns null when no firebase user`() {
        every { auth.currentUser } returns null
        assertNull(provider.currentUser())
    }

    @Test
    fun `currentUser maps firebase user to domain model`() {
        val mockUser = mockk<FirebaseUser>()
        val mockMetadata = mockk<FirebaseUserMetadata>()
        
        every { auth.currentUser } returns mockUser
        every { mockUser.uid } returns "uid-123"
        every { mockUser.email } returns "test@example.com"
        every { mockUser.displayName } returns "John Doe"
        every { mockUser.metadata } returns mockMetadata
        every { mockMetadata.creationTimestamp } returns 1000L

        val user = provider.currentUser()

        assertEquals("uid-123", user?.uid)
        assertEquals("test@example.com", user?.email)
        assertEquals("John Doe", user?.displayName)
        assertEquals(1000L, user?.createdAtMillis)
    }
}
