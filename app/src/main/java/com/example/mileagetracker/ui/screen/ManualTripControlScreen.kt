package com.example.mileagetracker.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mileagetracker.R
import com.example.mileagetracker.domain.model.TrackingState
import com.example.mileagetracker.ui.map.LiveTripMapCard
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.viewmodel.ManualTripControlViewModel
import kotlinx.coroutines.delay

/**
 * Full-screen Compose UI for manually controlling trip recording.
 *
 * ## Deep-link entry
 * This screen is navigated to via [Screen.ManualTrip.deepLink]
 * (`mileagetracker://manual_trip`), which the persistent foreground
 * notification fires when the user taps its body.  Register the deep-link
 * scheme in `AndroidManifest.xml`:
 * ```xml
 * <intent-filter android:autoVerify="true">
 *     <action android:name="android.intent.action.VIEW"/>
 *     <category android:name="android.intent.category.DEFAULT"/>
 *     <category android:name="android.intent.category.BROWSABLE"/>
 *     <data android:scheme="mileagetracker" android:host="manual_trip"/>
 * </intent-filter>
 * ```
 *
 * ## Layout
 * ```
 * ┌────────────────────────────────┐
 * │   ● RECORDING  (pulsing dot)   │  ← only when recording
 * │                                │
 * │    HH:MM:SS  elapsed time      │
 * │    X.XX km   live odometer     │
 * │                                │
 * │  ┌──────────────────────────┐  │
 * │  │  Google Map (live GPS)   │  │  ← only when recording
 * │  └──────────────────────────┘  │
 * │                                │
 * │  ┌───────────────────────────┐ │
 * │  │  START / STOP (button)    │ │
 * │  └───────────────────────────┘ │
 * └────────────────────────────────┘
 * ```
 */
@Composable
fun ManualTripControlScreen(
    viewModel: ManualTripControlViewModel = hiltViewModel(),
) {
    val state by viewModel.trackingState.collectAsStateWithLifecycle()
    val liveLocation by viewModel.liveLocation.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "TrackingStateContent",
            ) { currentState ->
                when (currentState) {
                    is TrackingState.Idle -> {
                        IdleContent(onStart = viewModel::startManualTrip)
                    }
                    is TrackingState.ManualRecording -> {
                        RecordingContent(
                            state = currentState,
                            onStop = viewModel::stopManualTrip,
                            liveLocation = liveLocation,
                        )
                    }
                    is TrackingState.AutoRecording -> {
                        AutoRecordingContent()
                    }
                    is TrackingState.Paused -> {
                        // Reserved for Task 2.5 (pause/resume).
                        RecordingContent(
                            state = TrackingState.ManualRecording(
                                startTimeMs = currentState.startTimeMs,
                                currentKm = currentState.kmAtPause,
                            ),
                            onStop = viewModel::stopManualTrip,
                            isPaused = true,
                            liveLocation = liveLocation,
                        )
                    }
                }
            }
        }
    }
}

// ── Idle state ───────────────────────────────────────────────────────────────────────

@Composable
private fun IdleContent(onStart: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Icon(
            imageVector = Icons.Default.Place,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.tracking_control_idle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StartStopButton(isRecording = false, onClick = onStart)
    }
}

// ── Manual recording state ────────────────────────────────────────────────────────

@Composable
private fun RecordingContent(
    state: TrackingState.ManualRecording,
    onStop: () -> Unit,
    liveLocation: com.google.android.gms.maps.model.LatLng?,
    isPaused: Boolean = false,
) {
    val elapsedMs = rememberElapsedMs(
        startTimeMs = state.startTimeMs,
        isPaused = isPaused,
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        if (!isPaused) RecordingIndicator()

        RecordingStatusCard(
            elapsedMs = elapsedMs,
            currentKm = state.currentKm,
            isPaused = isPaused,
        )

        // Live map — shown as soon as recording starts; fades in on first GPS fix.
        LiveTripMapCard(
            currentLocation = liveLocation,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(Spacing.xs))

        StartStopButton(isRecording = true, onClick = onStop)
    }
}

@Composable
private fun rememberElapsedMs(startTimeMs: Long, isPaused: Boolean): Long {
    var elapsedMs by remember(startTimeMs) {
        mutableLongStateOf(System.currentTimeMillis() - startTimeMs)
    }
    if (!isPaused) {
        LaunchedEffect(startTimeMs) {
            while (true) {
                delay(1_000L)
                elapsedMs = System.currentTimeMillis() - startTimeMs
            }
        }
    }
    return elapsedMs
}

@Composable
private fun RecordingStatusCard(
    elapsedMs: Long,
    currentKm: Float,
    isPaused: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            RecordingStatusLabel(isPaused = isPaused)
            Text(
                text = stringResource(R.string.tracking_control_elapsed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = elapsedMs.toElapsedString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            DistanceSoFar(currentKm = currentKm)
        }
    }
}

@Composable
private fun RecordingStatusLabel(isPaused: Boolean) {
    Text(
        text = if (isPaused) {
            stringResource(R.string.tracking_control_paused)
        } else {
            stringResource(R.string.tracking_control_in_progress)
        },
        style = MaterialTheme.typography.labelMedium,
        color = if (isPaused) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        },
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun DistanceSoFar(currentKm: Float) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.tracking_control_distance_so_far),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${"%,.2f".format(currentKm)} km",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

// ── Auto-recording info banner ─────────────────────────────────────────────────────

@Composable
private fun AutoRecordingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.tracking_control_auto_in_progress),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.tracking_control_auto_locked),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Shared composables ──────────────────────────────────────────────────────────────

/**
 * Pulsing red dot shown while recording is active.
 */
@Composable
private fun RecordingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recordingDotAlpha",
    )
    Box(modifier = Modifier.padding(bottom = Spacing.sm)) {
        Surface(
            modifier = Modifier
                .size(14.dp)
                .alpha(alpha),
            shape = CircleShape,
            color = Color.Red,
        ) {}
    }
}

/**
 * Single large circular Start/Stop button.
 */
@Composable
private fun StartStopButton(isRecording: Boolean, onClick: () -> Unit) {
    val containerColor = if (isRecording) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        modifier = Modifier.size(120.dp),
    ) {
        Text(
            text = if (isRecording) {
                stringResource(R.string.tracking_control_stop)
            } else {
                stringResource(R.string.tracking_control_start)
            },
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────────────

/** Formats epoch millis duration as `HH:MM:SS`. */
private fun Long.toElapsedString(): String {
    val totalSeconds = this / 1_000L
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
