package com.example.mileagetracker.domain.usecase

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.preferences.UserPreferencesDataSource
import com.example.mileagetracker.data.preferences.UserPreferencesRepository
import com.example.mileagetracker.domain.model.CanadianUnits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ApplyCanadianDefaultsUseCaseTest {

    private lateinit var dataSource: UserPreferencesDataSource
    private lateinit var repository: UserPreferencesRepository
    private lateinit var useCase: ApplyCanadianDefaultsUseCase
    private lateinit var testFile: File
    private lateinit var testScope: CoroutineScope

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        testFile = File(context.cacheDir, "canadian_defaults_${System.nanoTime()}.preferences_pb")
        testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile },
        )

        dataSource = UserPreferencesDataSource(dataStore)
        repository = UserPreferencesRepository(dataSource)
        useCase = ApplyCanadianDefaultsUseCase(repository)
    }

    @After
    fun tearDown() {
        testScope.cancel()
        testFile.delete()
    }

    @Test
    fun invoke_marksDefaultsApplied_andPersistsCanadianValues() = runTest {
        useCase()

        assertEquals(CanadianUnits.DISTANCE_UNIT, repository.distanceUnit.first())
        assertEquals(CanadianUnits.CURRENCY_CODE, repository.currencyCode.first())
        assertTrue(dataSource.canadianDefaultsApplied.first())
    }

    @Test
    fun invoke_isIdempotent_andDoesNotOverwriteUserChanges_afterFlagSet() = runTest {
        useCase()
        repository.setDistanceUnit("mi")
        repository.setCurrencyCode("USD")

        useCase()

        assertEquals("mi", repository.distanceUnit.first())
        assertEquals("USD", repository.currencyCode.first())
        assertTrue(dataSource.canadianDefaultsApplied.first())
    }
}
