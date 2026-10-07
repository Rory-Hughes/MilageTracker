package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val samplePages = listOf(
        OnboardingPage(
            title = "Welcome to MileageTracker",
            description = "Automate your CRA-ready mileage log.",
            icon = Icons.Filled.DirectionsCar,
        ),
        OnboardingPage(
            title = "Detect Trips Automatically",
            description = "Background detection captures trips for you.",
            icon = Icons.Filled.LocationOn,
        ),
    )

    private fun setContent(
        pages: List<OnboardingPage> = samplePages,
        initialPage: Int = 0,
        controller: OnboardingPermissionController = noOpController(),
        onFinish: () -> Unit = {},
    ) {
        composeRule.setContent {
            val pagerState = rememberPagerState(
                initialPage = initialPage,
                pageCount = { pages.size },
            )
            val scope = rememberCoroutineScope()
            OnboardingScreenContent(
                pages = pages,
                pagerState = pagerState,
                coroutineScope = scope,
                permissionController = controller,
                onFinish = onFinish,
            )
        }
    }

    @Test
    fun firstPage_rendersTitleAndDescription() {
        setContent()

        composeRule.onNodeWithText("Welcome to MileageTracker").assertIsDisplayed()
        composeRule.onNodeWithText("Automate your CRA-ready mileage log.").assertIsDisplayed()
    }

    @Test
    fun firstPage_showsNextButton_notEnableTracking() {
        setContent()

        composeRule.onNodeWithText("Next").assertIsDisplayed()
        composeRule.onNodeWithText("Enable Tracking").assertDoesNotExist()
    }

    @Test
    fun lastPage_showsEnableTrackingButton_andPreviewCard() {
        setContent(initialPage = samplePages.lastIndex)

        composeRule.onNodeWithText("Enable Tracking").assertIsDisplayed()
        composeRule.onNodeWithText("Next").assertDoesNotExist()
        composeRule.onNodeWithText("Automatic trip detection uses these permissions:")
            .assertIsDisplayed()
    }

    @Test
    fun previewCard_hiddenOnNonLastPages() {
        setContent(initialPage = 0)

        composeRule.onNodeWithText("Automatic trip detection uses these permissions:")
            .assertDoesNotExist()
    }

    @Test
    fun skipButton_invokesOnFinish() {
        var finished = false
        setContent(onFinish = { finished = true })

        composeRule.onNodeWithText("Skip").performClick()

        assertTrue(finished)
    }

    @Test
    fun enableTrackingButton_invokesStartAutomaticTrackingFlow() {
        var started = false
        val controller = noOpController(
            startAutomaticTrackingFlow = { started = true },
        )
        setContent(initialPage = samplePages.lastIndex, controller = controller)

        composeRule.onNodeWithText("Enable Tracking").performClick()

        assertTrue(started)
    }

    @Test
    fun nextButton_advancesPagerToNextPage() {
        setContent(initialPage = 0)

        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Detect Trips Automatically").assertIsDisplayed()
    }

    @Test
    fun permissionDialog_shownWhenStepPresent() {
        val controller = noOpController(
            dialogStep = OnboardingPermissionStep.FOREGROUND_LOCATION,
        )
        setContent(controller = controller)

        composeRule.onNodeWithText(
            OnboardingPermissionStep.FOREGROUND_LOCATION.title,
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun permissionDialog_hiddenWhenStepNull() {
        setContent(controller = noOpController(dialogStep = null))

        composeRule.onNodeWithText("Continue").assertDoesNotExist()
    }

    @Test
    fun permissionDialog_confirmButton_invokesConfirmCallback() {
        var confirmed = false
        val controller = noOpController(
            dialogStep = OnboardingPermissionStep.BACKGROUND_LOCATION,
            confirmDialog = { confirmed = true },
        )
        setContent(controller = controller)

        composeRule.onNodeWithText("Continue").performClick()

        assertTrue(confirmed)
    }

    @Test
    fun permissionDialog_dismissButton_invokesDismissCallback() {
        var dismissed = false
        val controller = noOpController(
            dialogStep = OnboardingPermissionStep.BACKGROUND_LOCATION,
            dismissDialog = { dismissed = true },
        )
        setContent(controller = controller)

        composeRule.onNodeWithText("Cancel").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun statusMessage_rendersWhenPresent() {
        val message = "Background location was not granted."
        setContent(controller = noOpController(statusMessage = message))

        composeRule.onNodeWithText(message).assertIsDisplayed()
    }

    @Test
    fun statusMessage_hiddenWhenNull() {
        setContent(controller = noOpController(statusMessage = null))

        composeRule.onAllNodesWithText("was not granted", substring = true)
            .assertCountEquals(0)
    }

    private fun noOpController(
        dialogStep: OnboardingPermissionStep? = null,
        statusMessage: String? = null,
        startAutomaticTrackingFlow: () -> Unit = {},
        dismissDialog: () -> Unit = {},
        confirmDialog: () -> Unit = {},
    ) = OnboardingPermissionController(
        dialogStep = dialogStep,
        statusMessage = statusMessage,
        startAutomaticTrackingFlow = startAutomaticTrackingFlow,
        dismissDialog = dismissDialog,
        confirmDialog = confirmDialog,
    )
}
