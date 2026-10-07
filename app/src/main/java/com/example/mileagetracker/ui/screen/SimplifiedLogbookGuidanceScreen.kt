package com.example.mileagetracker.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mileagetracker.R
import com.example.mileagetracker.domain.model.SimplifiedLogbookStatus
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.theme.Stroke
import com.example.mileagetracker.ui.viewmodel.SimplifiedLogbookUiState
import com.example.mileagetracker.ui.viewmodel.SimplifiedLogbookViewModel
import kotlin.math.roundToInt

/**
 * Step-by-step wizard that explains the CRA simplified logbook method and
 * shows the user's current eligibility and sample-period progress.
 *
 * Step structure:
 * - Step 1 - Eligibility check
 * - Step 2 - Sample period
 * - Step 3 - Applying the ratio
 *
 * @param onNavigateBack Lambda called when the back arrow is tapped.
 * @param viewModel [SimplifiedLogbookViewModel] injected by Hilt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimplifiedLogbookGuidanceScreen(
    onNavigateBack: () -> Unit,
    viewModel: SimplifiedLogbookViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.simplified_logbook_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = uiState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "simplified_logbook_state",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) { state ->
            when (state) {
                SimplifiedLogbookUiState.Loading -> LoadingState()
                is SimplifiedLogbookUiState.Error -> ErrorState(state.message)
                is SimplifiedLogbookUiState.Success -> {
                    WizardContent(
                        status = state.status,
                        onSelectBaseYear = viewModel::selectBaseYear,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xxl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun WizardContent(
    status: SimplifiedLogbookStatus,
    onSelectBaseYear: (Int) -> Unit,
) {
    var currentStep by remember(status.isEligible) { mutableIntStateOf(1) }
    val totalSteps = if (status.isEligible) 3 else 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        StepIndicator(totalSteps = totalSteps, currentStep = currentStep)
        HorizontalDivider()

        when (currentStep) {
            1 -> Step1Eligibility(status = status, onSelectBaseYear = onSelectBaseYear)
            2 -> Step2SamplePeriod(status = status)
            3 -> Step3ApplyingRatio(status = status)
        }

        Spacer(Modifier.height(Spacing.sm))

        if (status.isEligible) {
            NavigationButtons(
                currentStep = currentStep,
                totalSteps = totalSteps,
                onPrevious = { currentStep-- },
                onNext = { currentStep++ },
            )
        }

        CraReferenceNote()
    }
}

@Composable
private fun NavigationButtons(
    currentStep: Int,
    totalSteps: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (currentStep > 1) {
            Button(onClick = onPrevious) { Text("< Previous") }
        } else {
            Spacer(Modifier.width(1.dp))
        }
        if (currentStep < totalSteps) {
            Button(onClick = onNext) { Text("Next >") }
        }
    }
}

@Composable
private fun Step3ApplyingRatio(status: SimplifiedLogbookStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StepHeading(step = 3, title = "Applying the Ratio")

        Text(
            text = "Once your sample logbook is complete, apply your base-year " +
                "business-use percentage to your total motor vehicle expenses for " +
                "the year to calculate your deductible amount.",
            style = MaterialTheme.typography.bodyMedium,
        )

        RatioExampleCard(status)

        Text(
            text = "If your sample year's business-use % differs by more than " +
                "${SimplifiedLogbookStatus.MAX_RATIO_DEVIATION_PERCENT.roundToInt()} " +
                "percentage points from your base year, you must revert to a full " +
                "logbook for that year and establish a new base year.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RatioExampleCard(status: SimplifiedLogbookStatus) {
    InfoCard {
        Text(
            text = "Example",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(Spacing.xs))
        val pct = status.baseYearBusinessUsePercent?.roundToInt() ?: 72
        Text(
            text = "Total vehicle expenses: $8,000\n" +
                "Base-year business use: $pct%\n" +
                "Deductible amount: $${8000 * pct / 100}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StepIndicator(totalSteps: Int, currentStep: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalSteps) { index ->
            val stepNum = index + 1
            val isActive = stepNum == currentStep
            val isDone = stepNum < currentStep
            val icon = when {
                isDone -> Icons.Default.CheckCircle
                isActive -> Icons.Default.AutoStories
                else -> Icons.Default.RadioButtonUnchecked
            }
            val tint = when {
                isDone || isActive -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Icon(
                imageVector = icon,
                contentDescription = "Step $stepNum",
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            if (index < totalSteps - 1) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = Stroke.hairline,
                    color = if (isDone) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                )
            }
        }
    }
}
