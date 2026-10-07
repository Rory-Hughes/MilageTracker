package com.example.mileagetracker.domain.auth

data class CurrentUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val createdAtMillis: Long?,
)
