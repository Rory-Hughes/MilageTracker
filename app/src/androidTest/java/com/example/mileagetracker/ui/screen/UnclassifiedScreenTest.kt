package com.example.mileagetracker.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.SettingsRepository
import com.example.mileagetracker.domain.usecase.drive.ClassifyTripUseCase
import com.example.mileagetracker.domain.usecase.drive.GetUnclassifiedDrivesUseCase
import com.example.mileagetracker.domain.usecase.drive.ResolveDriveLocationsUseCase
import com.example.mileagetracker.domain.usecase.drive.UpdateDriveUseCase
import com.example.mileagetracker.domain.usecase.rate.CalculateReimbursementUseCase
import com.example.mileagetracker.domain.usecase.rate.GetCraRateForYearUseCase
import com.example.mileagetracker.ui.viewmodel.DriveViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnclassifiedScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val currentUserProvider: CurrentUserProvider = mockk {
        every { requireUid() } returns "u1"
    }

    private fun createViewModel(
        drives: List<DriveEntity> = emptyList(),
    ): DriveViewModel {
        val getUnclassifiedDrivesUseCase: GetUnclassifiedDrivesUseCase = mockk {
            every { this@mockk.invoke(any()) } returns flowOf(drives)
        }
        val updateDriveUseCase: UpdateDriveUseCase = mockk(relaxed = true)
        val resolveDriveLocationsUseCase: ResolveDriveLocationsUseCase = mockk {
            every { requiresResolution(any()) } returns false
        }
        val classifyTripUseCase: ClassifyTripUseCase = mockk(relaxed = true)
        val calculateReimbursementUseCase: CalculateReimbursementUseCase = mockk(relaxed = true)
        val getCraRateForYearUseCase: GetCraRateForYearUseCase = mockk(relaxed = true)
        val driveRepository: DriveRepository = mockk {
            every { getAllDrives(any()) } returns flowOf(emptyList())
            every { getYearToDateBusinessDistance(any(), any(), any()) } returns flowOf(0f)
        }
        val settingsRepository: SettingsRepository = mockk {
            every { getCraRateRegion() } returns flowOf(null)
            every { getRateMode() } returns flowOf(com.example.mileagetracker.domain.model.RateMode.CRA_STANDARD)
        }

        return DriveViewModel(
            getUnclassifiedDrivesUseCase = getUnclassifiedDrivesUseCase,
            updateDriveUseCase = updateDriveUseCase,
            resolveDriveLocationsUseCase = resolveDriveLocationsUseCase,
            classifyTripUseCase = classifyTripUseCase,
            calculateReimbursementUseCase = calculateReimbursementUseCase,
            getCraRateForYearUseCase = getCraRateForYearUseCase,
            driveRepository = driveRepository,
            settingsRepository = settingsRepository,
            currentUserProvider = currentUserProvider,
        )
    }

    @Test
    fun emptyList_showsAllCaughtUpState() {
        val viewModel = createViewModel(drives = emptyList())
        composeRule.setContent {
            UnclassifiedScreen(viewModel = viewModel)
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("All caught up", substring = true).assertIsDisplayed()
    }

    @Test
    fun driveList_showsLocationAndDistance() {
        val drives = listOf(
            testDrive(id = 1, start = "Home", end = "Client HQ", distanceKm = 22.5f),
        )
        val viewModel = createViewModel(drives = drives)
        composeRule.setContent {
            UnclassifiedScreen(viewModel = viewModel)
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Home", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Client HQ", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("22.5", substring = true).assertIsDisplayed()
    }

    @Test
    fun tappingDrive_invokesCallback() {
        val drives = listOf(testDrive(id = 42, start = "A", end = "B"))
        val viewModel = createViewModel(drives = drives)
        var tappedId: Int? = null
        composeRule.setContent {
            UnclassifiedScreen(
                viewModel = viewModel,
                onTripTapped = { tappedId = it },
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("A", substring = true).performClick()

        assertEquals(42, tappedId)
    }

    @Test
    fun fab_showsAddTripText_andInvokesCallback() {
        val viewModel = createViewModel()
        var fabClicked = false
        composeRule.setContent {
            UnclassifiedScreen(
                viewModel = viewModel,
                onAddMissedTrip = { fabClicked = true },
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Add", substring = true, useUnmergedTree = true).performClick()
        assertTrue(fabClicked)
    }

    @Test
    fun multipleDrives_showsAllItems() {
        val drives = listOf(
            testDrive(id = 1, start = "Home", end = "Office"),
            testDrive(id = 2, start = "Office", end = "Restaurant"),
            testDrive(id = 3, start = "Restaurant", end = "Home"),
        )
        val viewModel = createViewModel(drives = drives)
        composeRule.setContent {
            UnclassifiedScreen(viewModel = viewModel)
        }

        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Home", substring = true).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("Office", substring = true).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("Restaurant", substring = true).onFirst().assertIsDisplayed()
    }

    private fun testDrive(
        id: Int = 1,
        start: String = "A",
        end: String = "B",
        distanceKm: Float = 10f,
    ) = DriveEntity(
        id = id,
        startedAt = System.currentTimeMillis() - 3_600_000,
        endedAt = System.currentTimeMillis(),
        startLocation = start,
        endLocation = end,
        distanceKm = distanceKm,
        tripCategory = TripCategory.UNCLASSIFIED,
        classificationStatus = ClassificationStatus.PENDING,
        driverUid = "u1",
    )
}
