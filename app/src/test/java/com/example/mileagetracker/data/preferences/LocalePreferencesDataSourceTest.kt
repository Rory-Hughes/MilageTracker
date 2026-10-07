package com.example.mileagetracker.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class LocalePreferencesDataSourceTest {

    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        dataStore = mockk()
    }

    @Test
    fun `languageTag returns null when missing`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[any<Preferences.Key<String>>()] } returns null
        every { dataStore.data } returns flowOf(prefs)
        val dataSource = LocalePreferencesDataSource(dataStore)

        assertNull(dataSource.languageTag.first())
    }

    @Test
    fun `languageTag returns stored value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[any<Preferences.Key<String>>()] } returns "fr-CA"
        every { dataStore.data } returns flowOf(prefs)
        val dataSource = LocalePreferencesDataSource(dataStore)

        assertEquals("fr-CA", dataSource.languageTag.first())
    }
}
