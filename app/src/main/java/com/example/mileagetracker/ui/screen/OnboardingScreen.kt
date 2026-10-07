package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Swipe

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = remember { onboardingPages() }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    val permissionController = rememberOnboardingPermissionController(onFinish = onFinish)

    OnboardingScreenContent(
        pages = pages,
        pagerState = pagerState,
        coroutineScope = coroutineScope,
        permissionController = permissionController,
        onFinish = onFinish,
    )
}

private fun onboardingPages(): List<OnboardingPage> = listOf(
    OnboardingPage(
        title = "Automate Your CRA Logs",
        description =
        "Never miss a deduction. MileageTracker records your trips and keeps " +
            "them ready for CRA reporting.",
        icon = Icons.Filled.DirectionsCar,
    ),
    OnboardingPage(
        title = "Background Detection Is Optional",
        description =
        "Turn on automatic detection if you want trips captured while the app " +
            "is closed. Manual trip entry remains available either way.",
        icon = Icons.Filled.LocationOn,
    ),
    OnboardingPage(
        title = "Review Trips Quickly",
        description =
        "Swipe or edit trips to confirm the business purpose, vehicle, and " +
            "category before exporting your records.",
        icon = Icons.Filled.Swipe,
    ),
)
