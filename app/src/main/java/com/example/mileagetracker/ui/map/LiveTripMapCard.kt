package com.example.mileagetracker.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

/**
 * A card-wrapped Google Map that tracks the user's live position during an
 * active trip recording session.
 *
 * ## Behaviour
 * - When [currentLocation] is null the card shows an "Acquiring GPS…" message.
 * - When [currentLocation] is non-null the map animates its camera to follow
 *   the latest fix at zoom level 15, and places a marker at that position.
 * - Scroll/tilt gestures are disabled so the user can still interact with the
 *   controls above and below the map without accidentally panning it.
 *
 * ## Integration
 * Drop this composable inside [ManualTripControlScreen] and pass it the
 * `liveLocation` state collected from [ManualTripControlViewModel].
 *
 * @param currentLocation Latest GPS fix, or null while waiting for the first fix.
 * @param modifier         Standard Compose modifier chain.
 */
@Composable
fun LiveTripMapCard(
    currentLocation: LatLng?,
    modifier: Modifier = Modifier,
) {
    val cameraPositionState = rememberLiveTripCameraPositionState(currentLocation)
    FollowLiveTripCamera(
        currentLocation = currentLocation,
        cameraPositionState = cameraPositionState,
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .testTag("LiveTripMapCard"),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        AnimatedVisibility(
            visible = currentLocation == null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            LiveTripLoadingState()
        }

        AnimatedVisibility(
            visible = currentLocation != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            LiveTripMapContent(
                currentLocation = currentLocation,
                cameraPositionState = cameraPositionState,
            )
        }
    }
}

@Composable
private fun rememberLiveTripCameraPositionState(
    currentLocation: LatLng?,
): CameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(
        currentLocation ?: LatLng(46.0878, -64.7782),
        15f,
    )
}

@Composable
private fun FollowLiveTripCamera(
    currentLocation: LatLng?,
    cameraPositionState: CameraPositionState,
) {
    LaunchedEffect(currentLocation) {
        currentLocation?.let {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(it, 15f),
                durationMs = 800,
            )
        }
    }
}

@Composable
private fun LiveTripLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Acquiring GPS\u2026",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LiveTripMapContent(
    currentLocation: LatLng?,
    cameraPositionState: CameraPositionState,
) {
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            scrollGesturesEnabled = false,
            tiltGesturesEnabled = false,
            rotationGesturesEnabled = false,
        ),
        properties = MapProperties(
            isMyLocationEnabled = true,
        ),
    ) {
        currentLocation?.let { loc ->
            Marker(
                state = rememberUpdatedMarkerState(position = loc),
                title = "You are here",
            )
        }
    }
}
