package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mileagetracker.R
import com.example.mileagetracker.ui.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun OnboardingScreenContent(
    pages: List<OnboardingPage>,
    pagerState: PagerState,
    coroutineScope: CoroutineScope,
    permissionController: OnboardingPermissionController,
    onFinish: () -> Unit,
) {
    PermissionStepDialog(permissionController)
    Scaffold(
        bottomBar = {
            OnboardingBottomBar(
                pages = pages,
                pagerState = pagerState,
                permissionController = permissionController,
                coroutineScope = coroutineScope,
                onFinish = onFinish,
            )
        },
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) { position ->
            OnboardingPageContent(page = pages[position])
        }
    }
}

@Composable
private fun PermissionStepDialog(
    permissionController: OnboardingPermissionController,
) {
    val step = permissionController.dialogStep ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text(step.title) },
        text = { Text(step.body) },
        confirmButton = {
            TextButton(onClick = permissionController.confirmDialog) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = permissionController.dismissDialog) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun OnboardingBottomBar(
    pages: List<OnboardingPage>,
    pagerState: PagerState,
    permissionController: OnboardingPermissionController,
    coroutineScope: CoroutineScope,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.xl)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (pagerState.currentPage == pages.lastIndex) {
            PermissionPreviewCard()
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OnboardingPageIndicators(
                pageCount = pages.size,
                currentPage = pagerState.currentPage,
            )
            OnboardingActionButtons(
                isLastPage = pagerState.currentPage == pages.lastIndex,
                onSkip = onFinish,
                onEnableTracking = permissionController.startAutomaticTrackingFlow,
                onNext = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
            )
        }

        permissionController.statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OnboardingPageIndicators(
    pageCount: Int,
    currentPage: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        repeat(pageCount) { index ->
            val isSelected = currentPage == index
            Box(
                modifier = Modifier
                    .size(if (isSelected) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun OnboardingActionButtons(
    isLastPage: Boolean,
    onSkip: () -> Unit,
    onEnableTracking: () -> Unit,
    onNext: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        TextButton(onClick = onSkip) {
            Text(stringResource(R.string.onboarding_skip))
        }
        Button(onClick = if (isLastPage) onEnableTracking else onNext) {
            Text(
                if (isLastPage) {
                    "Enable Tracking"
                } else {
                    stringResource(R.string.action_next)
                },
            )
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = page.icon,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionPreviewCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = "Automatic trip detection uses these permissions:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            PermissionPreviewLine(
                "Location while using the app - maps trip start and end points.",
            )
            PermissionPreviewLine(
                "Background location - detects trips while the app is closed.",
            )
            PermissionPreviewLine(
                "Physical activity - detects when driving starts and stops.",
            )
            PermissionPreviewLine(
                "You can skip this and use manual trip entry, then enable " +
                    "automatic detection later in Settings.",
            )
        }
    }
}

@Composable
private fun PermissionPreviewLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
