package com.example.mileagetracker.domain.auth

interface CurrentUserProvider {
    fun currentUser(): CurrentUser?

    fun requireUid(): String =
        currentUser()?.uid ?: throw SignedInUserRequiredException()
}

class SignedInUserRequiredException : IllegalStateException(
    "A signed-in user is required for this operation."
)
