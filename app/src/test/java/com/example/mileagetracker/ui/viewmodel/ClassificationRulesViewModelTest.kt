package com.example.mileagetracker.ui.viewmodel

import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.repository.SettingsRepository
import com.example.mileagetracker.domain.repository.UserClassificationRuleRepository
import com.example.mileagetracker.domain.usecase.classification.AutoClassifyPendingTripsUseCase
import com.example.mileagetracker.domain.usecase.classification.DeleteUserRuleUseCase
import com.example.mileagetracker.domain.usecase.classification.SaveUserRuleUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClassificationRulesViewModelTest {

    private lateinit var viewModel: ClassificationRulesViewModel
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var autoClassifyPendingTripsUseCase: AutoClassifyPendingTripsUseCase
    private lateinit var userRuleRepository: UserClassificationRuleRepository
    private lateinit var saveUserRuleUseCase: SaveUserRuleUseCase
    private lateinit var deleteUserRuleUseCase: DeleteUserRuleUseCase
    private lateinit var currentUserProvider: CurrentUserProvider
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = mockk(relaxed = true)
        autoClassifyPendingTripsUseCase = mockk()
        userRuleRepository = mockk()
        saveUserRuleUseCase = mockk()
        deleteUserRuleUseCase = mockk()
        currentUserProvider = mockk()

        every { currentUserProvider.requireUid() } returns "u1"
        every { settingsRepository.getDisabledRules() } returns flowOf(emptySet())
        every { userRuleRepository.observeAll() } returns flowOf(emptyList())

        viewModel = ClassificationRulesViewModel(
            settingsRepository,
            autoClassifyPendingTripsUseCase,
            userRuleRepository,
            saveUserRuleUseCase,
            deleteUserRuleUseCase,
            currentUserProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects built-in rules`() = runTest {
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.builtInRules.isNotEmpty())
        assertTrue(state.builtInRules.any { it.name == "MorningCommute" })
        collector.cancel()
    }

    @Test
    fun `runBatchClassification updates progress and final result`() = runTest {
        val result = AutoClassifyPendingTripsUseCase.BatchResult(
            total = 10, classified = 5, flaggedReview = 2, noRuleMatched = 3, skipped = 0, errors = 0
        )
        coEvery { autoClassifyPendingTripsUseCase(any(), any()) } answers {
            val progress = secondArg<(Int, Int) -> Unit>()
            progress(5, 10)
            progress(10, 10)
            result
        }

        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.runBatchClassification()
        advanceUntilIdle()

        val summary = viewModel.uiState.value.lastBatchResult.orEmpty()
        assertTrue(summary.contains("5 classified"))
        assertTrue(summary.contains("2 flagged"))
        assertTrue(summary.contains("3 unmatched"))
        collector.cancel()
    }

    @Test
    fun `deleteUserRule calls use case`() = runTest {
        coEvery { deleteUserRuleUseCase(5) } returns Unit

        viewModel.deleteUserRule(5)
        advanceUntilIdle()

        coVerify { deleteUserRuleUseCase(5) }
    }
}
