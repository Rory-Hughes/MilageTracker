package com.example.mileagetracker.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CurrentUserProviderTest {

    @Test
    fun `requireUid returns uid when signed in user exists`() {
        val provider = object : CurrentUserProvider {
            override fun currentUser(): CurrentUser? = CurrentUser(
                uid = "uid-123",
                email = "test@example.com",
                displayName = "Test User",
                createdAtMillis = 1_000L,
            )
        }

        val uid = provider.requireUid()

        assertEquals("uid-123", uid)
    }

    @Test
    fun `requireUid throws SignedInUserRequiredException when no user is signed in`() {
        val provider = object : CurrentUserProvider {
            override fun currentUser(): CurrentUser? = null
        }

        val exception = assertThrows(SignedInUserRequiredException::class.java) {
            provider.requireUid()
        }

        assertEquals(
            "A signed-in user is required for this operation.",
            exception.message,
        )
    }
}
