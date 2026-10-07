package com.example.mileagetracker.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.mileagetracker.domain.model.CanadianUnits
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UserPreferencesDataSourceTest {

    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        dataStore = mockk()
    }

    @Test
    fun `distanceUnit returns default km when missing`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[any<Preferences.Key<String>>()] } returns null
        every { dataStore.data } returns flowOf(prefs)
        val dataSource = UserPreferencesDataSource(dataStore)

        assertEquals(CanadianUnits.DISTANCE_UNIT, dataSource.distanceUnit.first())
    }

    @Test
    fun `currencyCode returns stored value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[any<Preferences.Key<String>>()] } returns "USD"
        every { dataStore.data } returns flowOf(prefs)
        val dataSource = UserPreferencesDataSource(dataStore)

        assertEquals("USD", dataSource.currencyCode.first())
    }

    @Test
    fun `canadianDefaultsApplied returns false by default`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[any<Preferences.Key<Boolean>>()] } returns null
        every { dataStore.data } returns flowOf(prefs)
        val dataSource = UserPreferencesDataSource(dataStore)

        assertEquals(false, dataSource.canadianDefaultsApplied.first())
    }
}
