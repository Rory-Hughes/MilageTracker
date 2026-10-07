package com.example.mileagetracker.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.example.mileagetracker.R
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.viewmodel.RouteMapUiState

private fun isMapsAvailable(context: android.content.Context): Boolean =
    GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS &&
        runCatching {
            MapsInitializer.initialize(context)
        }.isSuccess

@Composable
fun TripRouteMapCard(
    state: RouteMapUiState,
    onExpandMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .height(220.dp)

    when (state) {
        RouteMapUiState.NoRoute -> Unit
        RouteMapUiState.Loading -> TripRouteLoadingCard(cardModifier = cardModifier)
        is RouteMapUiState.Ready -> TripRouteReadyCard(
            state = state,
            cardModifier = cardModifier,
            onExpandMap = onExpandMap,
        )
    }
}

@Composable
private fun TripRouteLoadingCard(cardModifier: Modifier) {
    ElevatedCard(modifier = cardModifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("map_progress_indicator"),
            )
        }
    }
}

@Composable
private fun TripRouteReadyCard(
    state: RouteMapUiState.Ready,
    cardModifier: Modifier,
    onExpandMap: () -> Unit,
) {
    val context = LocalContext.current
    val mapsAvailable = remember { isMapsAvailable(context) }

    Column(modifier = Modifier.fillMaxWidth()) {
        ElevatedCard(modifier = cardModifier) {
            if (mapsAvailable) {
                TripRouteGoogleMap(state = state)
            } else {
                TripRouteUnavailableState()
            }
        }

        TripRouteExpandButton(onExpandMap = onExpandMap)
        TripRouteAccuracyChip(hasLowAccuracy = state.hasLowAccuracy)
    }
}

@Composable
private fun TripRouteGoogleMap(state: RouteMapUiState.Ready) {
    val cameraPositionState = rememberCameraPositionState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val startMarkerIcon = remember {
        runCatching {
            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
        }.getOrNull()
    }
    val endMarkerIcon = remember {
        runCatching {
            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
        }.getOrNull()
    }

    GoogleMap(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .testTag("trip_route_map"),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            scrollGesturesEnabled = false,
            zoomGesturesEnabled = false,
            zoomControlsEnabled = false,
            rotationGesturesEnabled = false,
            tiltGesturesEnabled = false,
            compassEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
        ),
        properties = MapProperties(),
        onMapLoaded = {
            val boundsUpdate = runCatching {
                CameraUpdateFactory.newLatLngBounds(state.bounds, 64)
            }.getOrNull()
            if (boundsUpdate != null) {
                cameraPositionState.move(boundsUpdate)
            }
        },
    ) {
        Polyline(
            points = state.points,
            color = primaryColor,
            width = 8f,
        )
        Marker(
            state = rememberUpdatedMarkerState(position = state.startMarker),
            title = stringResource(R.string.map_marker_start),
            icon = startMarkerIcon,
        )
        Marker(
            state = rememberUpdatedMarkerState(position = state.endMarker),
            title = stringResource(R.string.map_marker_end),
            icon = endMarkerIcon,
        )
    }
}

@Composable
private fun TripRouteUnavailableState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.map_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("map_unavailable_text"),
        )
    }
}

@Composable
private fun TripRouteExpandButton(onExpandMap: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(
            onClick = onExpandMap,
            modifier = Modifier.padding(end = Spacing.xs),
        ) {
            Text(stringResource(R.string.map_view_full))
        }
    }
}

@Composable
private fun TripRouteAccuracyChip(hasLowAccuracy: Boolean) {
    if (!hasLowAccuracy) return

    AssistChip(
        onClick = {},
        label = { Text(stringResource(R.string.map_low_accuracy_warning)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
                tint = MaterialTheme.colorScheme.error,
            )
        },
        modifier = Modifier
            .padding(start = Spacing.xs, bottom = Spacing.xs)
            .testTag("map_low_accuracy_chip"),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            labelColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    )
}
