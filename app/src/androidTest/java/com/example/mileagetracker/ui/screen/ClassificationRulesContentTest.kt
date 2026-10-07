package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.UserRule
import com.example.mileagetracker.ui.viewmodel.ClassificationRulesUiState
import com.example.mileagetracker.ui.viewmodel.RuleUiModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClassificationRulesContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: ClassificationRulesUiState = ClassificationRulesUiState(),
        onEditRule: (UserRule) -> Unit = {},
        onToggleBuiltInRule: (String, Boolean) -> Unit = { _, _ -> },
        onDeleteRule: (Int) -> Unit = {},
        onRunBatch: () -> Unit = {},
    ) {
        composeRule.setContent {
            ClassificationRulesContent(
                state = state,
                padding = PaddingValues(0.dp),
                onEditRule = onEditRule,
                onToggleBuiltInRule = onToggleBuiltInRule,
                onDeleteRule = onDeleteRule,
                onRunBatch = onRunBatch,
            )
        }
    }

    @Test
    fun allSections_areDisplayed() {
        setContent()

        composeRule.onNodeWithText("Built-in Rules", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Your Rules", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Batch Classification", substring = true).assertIsDisplayed()
    }

    @Test
    fun builtInRules_displayedWithNames() {
        setContent(
            state = ClassificationRulesUiState(
                builtInRules = listOf(
                    RuleUiModel(
                        name = "weekend_personal",
                        title = "Weekend Personal",
                        description = "Classify weekend trips as personal",
                        category = TripCategory.PERSONAL,
                        confidence = 0.8f,
                        enabled = true,
                    ),
                    RuleUiModel(
                        name = "business_hours",
                        title = "Business Hours",
                        description = "Trips during 9-5 are business",
                        category = TripCategory.BUSINESS,
                        confidence = 0.7f,
                        enabled = false,
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText("Weekend Personal").assertIsDisplayed()
        composeRule.onNodeWithText("Business Hours").assertIsDisplayed()
    }

    @Test
    fun emptyUserRules_showsEmptyHint() {
        setContent(
            state = ClassificationRulesUiState(userRules = emptyList()),
        )

        composeRule.onNodeWithText("No rules set up yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun userRules_displayedWithKeywords() {
        setContent(
            state = ClassificationRulesUiState(
                userRules = listOf(
                    UserRule(
                        id = 1,
                        name = "Hospital trips",
                        keyword = "hospital",
                        category = TripCategory.MEDICAL,
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText("hospital", substring = true).assertIsDisplayed()
    }

    @Test
    fun batchSection_showsRunButton() {
        setContent()

        composeRule.onNodeWithText("Run", substring = true).assertIsDisplayed()
    }

    @Test
    fun batchSection_runButtonInvokesCallback() {
        var batchRan = false
        setContent(onRunBatch = { batchRan = true })

        composeRule.onNodeWithText("Run", substring = true).performClick()
        assertTrue(batchRan)
    }

    @Test
    fun batchRunning_showsProgress() {
        setContent(
            state = ClassificationRulesUiState(
                isBatchRunning = true,
                batchProgress = 3,
                batchTotal = 10,
            ),
        )

        composeRule.onNodeWithText("3", substring = true).assertIsDisplayed()
    }
}
