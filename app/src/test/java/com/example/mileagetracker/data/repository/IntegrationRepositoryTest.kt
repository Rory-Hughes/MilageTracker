package com.example.mileagetracker.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.example.mileagetracker.domain.model.IntegrationConfig
import com.example.mileagetracker.domain.model.IntegrationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IntegrationRepositoryTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var repository: IntegrationRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = context.getSharedPreferences(
            "integration-repo-test-${System.nanoTime()}",
            Context.MODE_PRIVATE,
        )
        repository = IntegrationRepository(prefs)
    }

    @Test
    fun `getConfig returns null when provider is not persisted`() {
        val result = repository.getConfig(IntegrationProvider.QUICKBOOKS)

        assertNull(result)
    }

    @Test
    fun `saveConfig persists config under provider key`() {
        val config = config(provider = IntegrationProvider.QUICKBOOKS)

        repository.saveConfig(config)

        val raw = prefs.getString(IntegrationProvider.QUICKBOOKS.name, null)
        assertNotNull(raw)
        assertEquals(config, repository.getConfig(IntegrationProvider.QUICKBOOKS))
    }

    @Test
    fun `getConfig returns null for malformed json`() {
        prefs.edit().putString(IntegrationProvider.XERO.name, "{not-json").commit()

        val result = repository.getConfig(IntegrationProvider.XERO)

        assertNull(result)
    }

    @Test
    fun `revokeConfig removes saved entry`() {
        repository.saveConfig(config(provider = IntegrationProvider.XERO))

        repository.revokeConfig(IntegrationProvider.XERO)

        assertNull(repository.getConfig(IntegrationProvider.XERO))
    }

    @Test
    fun `getAllConfigs returns one config per stored provider`() {
        val qbo = config(provider = IntegrationProvider.QUICKBOOKS)
        val xero = config(provider = IntegrationProvider.XERO)
        repository.saveConfig(qbo)
        repository.saveConfig(xero)

        val result = repository.getAllConfigs()

        assertEquals(2, result.size)
        assertEquals(
            setOf(IntegrationProvider.QUICKBOOKS, IntegrationProvider.XERO),
            result.map { it.provider }.toSet()
        )
    }

    @Test
    fun `getAllConfigs ignores malformed provider payloads`() {
        repository.saveConfig(config(provider = IntegrationProvider.QUICKBOOKS))
        prefs.edit().putString(IntegrationProvider.XERO.name, "bad").commit()

        val result = repository.getAllConfigs()

        assertEquals(1, result.size)
        assertEquals(IntegrationProvider.QUICKBOOKS, result.single().provider)
    }

    private fun config(provider: IntegrationProvider) = IntegrationConfig(
        provider = provider,
        accessToken = "access-${provider.name}",
        refreshToken = "refresh-${provider.name}",
        connectedAt = 1_700_000_000_000L,
        lastSyncAt = 1_700_000_123_000L,
        realmId = "realm",
        tenantId = "tenant",
    )
}
