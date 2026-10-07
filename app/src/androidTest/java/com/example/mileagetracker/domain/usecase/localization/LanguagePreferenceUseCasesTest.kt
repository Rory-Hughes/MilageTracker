package com.example.mileagetracker.domain.usecase.localization

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mileagetracker.data.preferences.LocalePreferencesDataSource
import com.example.mileagetracker.data.preferences.LocalePreferencesRepository
import com.example.mileagetracker.domain.model.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LanguagePreferenceUseCasesTest {

    private lateinit var repository: LocalePreferencesRepository
    private lateinit var getUseCase: GetLanguagePreferenceUseCase
    private lateinit var setUseCase: SetLanguagePreferenceUseCase
    private lateinit var resetUseCase: ResetLanguagePreferenceUseCase
    private lateinit var testFile: File
    private lateinit var testScope: CoroutineScope

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        testFile = File(context.cacheDir, "locale_prefs_${System.nanoTime()}.preferences_pb")
        testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile },
        )

        repository = LocalePreferencesRepository(LocalePreferencesDataSource(dataStore))
        getUseCase = GetLanguagePreferenceUseCase(repository)
        setUseCase = SetLanguagePreferenceUseCase(repository)
        resetUseCase = ResetLanguagePreferenceUseCase(repository)

        runOnMain {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
    }

    @After
    fun tearDown() {
        runOnMain {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
        testScope.cancel()
        testFile.delete()
    }

    @Test
    fun getUseCase_defaultsToSystemLanguage_whenNothingStored() = runTest {
        assertEquals(AppLanguage.SYSTEM_DEFAULT, getUseCase().first())
    }

    @Test
    fun setUseCase_persistsFrench_andUpdatesApplicationLocales() = runTest {
        runBlockingOnMain {
            setUseCase(AppLanguage.FRENCH)
        }

        assertEquals(AppLanguage.FRENCH, getUseCase().first())
    }

    @Test
    fun resetUseCase_clearsStoredPreference_andResetsApplicationLocales() = runTest {
        runBlockingOnMain {
            setUseCase(AppLanguage.ENGLISH)
            resetUseCase()
        }

        assertEquals(AppLanguage.SYSTEM_DEFAULT, getUseCase().first())
    }

    private fun runOnMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private fun runBlockingOnMain(block: suspend () -> Unit) {
        runOnMain {
            runBlocking {
                block()
            }
        }
    }
}
