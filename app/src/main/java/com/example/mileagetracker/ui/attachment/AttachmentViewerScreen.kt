package com.example.mileagetracker.ui.attachment

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.example.mileagetracker.ui.theme.Spacing
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.mileagetracker.domain.model.TripAttachment

/**
 * Full-screen viewer for a single [TripAttachment].
 *
 * ## Image attachments
 * Rendered via Coil's [AsyncImage] with pinch-to-zoom and pan support
 * implemented using [detectTransformGestures] and [Modifier.graphicsLayer].
 * Zoom is clamped between 1× and 5×.  Offset resets to zero when the
 * user zooms back to 1×.
 *
 * ## Non-image attachments (PDFs, etc.)
 * A placeholder icon and the file name are shown.
 *
 * @param attachment     The [TripAttachment] to display.
 * @param onNavigateBack Callback to pop the back stack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentViewerScreen(
    attachment: TripAttachment,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            AttachmentViewerTopBar(
                fileName = attachment.fileName,
                onNavigateBack = onNavigateBack,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (attachment.isImage) {
                ZoomableImage(
                    uri = attachment.fileUri.toUri(),
                    fileName = attachment.fileName,
                )
            } else {
                UnsupportedAttachmentContent(fileName = attachment.fileName)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentViewerTopBar(
    fileName: String,
    onNavigateBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = fileName,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Black,
        ),
    )
}

@Composable
private fun UnsupportedAttachmentContent(fileName: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.PictureAsPdf,
            contentDescription = null,
            modifier = Modifier.padding(bottom = Spacing.lg),
            tint = Color.White,
        )
        Text(
            text = fileName,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
        )
        Text(
            text = "Open in external app to view this file type.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.LightGray,
        )
    }
}

// ── Zoomable image ────────────────────────────────────────────────────────────

@Composable
private fun ZoomableImage(uri: Uri, fileName: String) {
    val context = LocalContext.current

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(uri)
            .crossfade(true)
            .build(),
        contentDescription = fileName,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                    offset = if (newScale == 1f) Offset.Zero else offset + pan
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
            ),
    )
}
