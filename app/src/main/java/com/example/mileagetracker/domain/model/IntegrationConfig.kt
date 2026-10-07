package com.example.mileagetracker.domain.model

import kotlinx.serialization.Serializable

enum class IntegrationProvider {
    QUICKBOOKS,
    XERO,
    PAYROLL_CSV
}

/**
 * Persisted OAuth/connection config for a single integration provider.
 * Stored as JSON in EncryptedSharedPreferences via IntegrationRepository.
 *
 * [realmId]  — QBO company ID; returned as a query param on the OAuth redirect URI.
 * [tenantId] — Xero organisation ID; resolved from GET /connections after token exchange.
 */
@Serializable
data class IntegrationConfig(
    val provider: IntegrationProvider,
    val accessToken: String,
    val refreshToken: String? = null,
    val connectedAt: Long,
    val lastSyncAt: Long? = null,
    val realmId: String? = null,
    val tenantId: String? = null
)
