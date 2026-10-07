package com.example.mileagetracker.ui.attachment

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.mileagetracker.ui.theme.Spacing
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File

/**
 * Modal bottom sheet that lets the user choose between taking a new photo
 * or picking an existing image from the gallery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerSheet(
    onDismiss: () -> Unit,
    onAttachmentPicked: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val authority = "${context.packageName}.provider"
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val hideSheet = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            cameraUri?.let { uri ->
                onAttachmentPicked(uri)
                hideSheet()
            }
        } else {
            hideSheet()
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            onAttachmentPicked(uri)
        }
        hideSheet()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) galleryLauncher.launch("image/*")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        AttachmentPickerSheetContent(
            onLaunchCamera = {
                cameraUri = createTemporaryCameraUri(
                    context = context,
                    authority = authority,
                )
                cameraUri?.let(cameraLauncher::launch)
            },
            onLaunchGallery = {
                openGallery(
                    context = context,
                    launchGallery = { galleryLauncher.launch("image/*") },
                    requestPermission = permissionLauncher::launch,
                )
            },
        )
    }
}

@Composable
private fun AttachmentPickerSheetContent(
    onLaunchCamera: () -> Unit,
    onLaunchGallery: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.xxl),
    ) {
        Text(
            text = "Add Attachment",
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        )
        HorizontalDivider()
        AttachmentPickerOption(
            title = "Take Photo",
            icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
            onClick = onLaunchCamera,
        )
        Spacer(Modifier.height(Spacing.xs))
        AttachmentPickerOption(
            title = "Choose from Gallery",
            icon = { Icon(Icons.Default.Photo, contentDescription = null) },
            onClick = onLaunchGallery,
        )
        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
private fun AttachmentPickerOption(
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = icon,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

private fun createTemporaryCameraUri(
    context: Context,
    authority: String,
): Uri {
    val captureDir = File(context.cacheDir, "camera_captures").also { it.mkdirs() }
    val tempFile = File(captureDir, "photo_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, authority, tempFile)
}

private fun openGallery(
    context: Context,
    launchGallery: () -> Unit,
    requestPermission: (String) -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        launchGallery()
        return
    }

    val permission = Manifest.permission.READ_EXTERNAL_STORAGE
    if (ContextCompat.checkSelfPermission(context, permission) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        launchGallery()
    } else {
        requestPermission(permission)
    }
}
