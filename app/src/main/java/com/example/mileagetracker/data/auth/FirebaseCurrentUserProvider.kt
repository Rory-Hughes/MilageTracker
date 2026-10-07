package com.example.mileagetracker.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.example.mileagetracker.domain.auth.CurrentUser
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseCurrentUserProvider @Inject constructor(
    private val auth: FirebaseAuth,
) : CurrentUserProvider {

    override fun currentUser(): CurrentUser? =
        auth.currentUser?.let { user ->
            CurrentUser(
                uid = user.uid,
                email = user.email,
                displayName = user.displayName,
                createdAtMillis = user.metadata?.creationTimestamp,
            )
        }
}
