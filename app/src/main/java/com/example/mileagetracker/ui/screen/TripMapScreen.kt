package com.example.mileagetracker.ui.screen

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
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
import com.example.mileagetracker.ui.viewmodel.TripDetailUiState
import com.example.mileagetracker.ui.viewmodel.TripDetailViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun isMapsAvailable(context: Context): Boolean =
    GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripMapScreen(
    onNavigateBack: () -> Unit,
    viewModel: TripDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val routeMapState by viewModel.routeMapState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(resolveTripMapTitle(uiState)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        TripMapContent(
            routeMapState = routeMapState,
            context = context,
            innerPadding = innerPadding,
        )
    }
}

@Composable
private fun resolveTripMapTitle(uiState: TripDetailUiState): String = when (val state = uiState) {
    is TripDetailUiState.Success -> {
        val formatter = DateTimeFormatter
            .ofPattern("MMM d, yyyy")
            .withZone(ZoneId.systemDefault())
        formatter.format(Instant.ofEpochMilli(state.drive.startedAt))
    }

    else -> stringResource(R.string.map_screen_title_fallback)
}

@Composable
private fun TripMapContent(
    routeMapState: RouteMapUiState,
    context: Context,
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    when (val state = routeMapState) {
        RouteMapUiState.Loading -> LoadingMapState(innerPadding)
        RouteMapUiState.NoRoute -> NoRouteMapState(innerPadding)
        is RouteMapUiState.Ready -> ReadyTripMapState(
            state = state,
            context = context,
            innerPadding = innerPadding,
        )
    }
}

@Composable
private fun LoadingMapState(
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NoRouteMapState(
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard {
            Column(
                modifier = Modifier.padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.map_no_gps_data),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.map_no_gps_data_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun ReadyTripMapState(
    state: RouteMapUiState.Ready,
    context: Context,
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    val mapsAvailable = remember { isMapsAvailable(context) }
    if (mapsAvailable) {
        TripRouteMap(
            state = state,
            innerPadding = innerPadding,
        )
    } else {
        UnavailableMapState(innerPadding)
    }
}

@Composable
private fun TripRouteMap(
    state: RouteMapUiState.Ready,
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val cameraPositionState = rememberCameraPositionState()
    val boundsUpdate = remember(state.bounds) {
        CameraUpdateFactory.newLatLngBounds(state.bounds, 80)
    }

    GoogleMap(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            scrollGesturesEnabled = true,
            zoomGesturesEnabled = true,
            zoomControlsEnabled = true,
            rotationGesturesEnabled = true,
            tiltGesturesEnabled = true,
            compassEnabled = true,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = true,
        ),
        properties = MapProperties(),
        onMapLoaded = {
            cameraPositionState.move(boundsUpdate)
        },
    ) {
        Polyline(
            points = state.points,
            color = primaryColor,
            width = 10f,
        )
        Marker(
            state = rememberUpdatedMarkerState(position = state.startMarker),
            title = stringResource(R.string.map_marker_start),
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
        )
        Marker(
            state = rememberUpdatedMarkerState(position = state.endMarker),
            title = stringResource(R.string.map_marker_end),
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
        )
    }
}

@Composable
private fun UnavailableMapState(
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard {
            Text(
                text = stringResource(R.string.map_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.xl),
            )
        }
    }
}

