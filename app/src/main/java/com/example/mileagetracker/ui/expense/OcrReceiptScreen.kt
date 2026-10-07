package com.example.mileagetracker.ui.expense

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.mileagetracker.domain.model.OcrExtractionResult
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.theme.Stroke
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrReceiptScreen(
    onBack: () -> Unit,
    onConfirm: (amountRaw: String, vendor: String, dateMillis: Long?, gstHstRaw: String) -> Unit,
    viewModel: OcrReceiptViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) viewModel.onImageCaptured()
    }

    OcrReceiptScaffold(
        state = state,
        onBack = onBack,
        onOpenCamera = {
            val uri = viewModel.prepareImageUri()
            cameraLauncher.launch(uri)
        },
        onAmountChanged = viewModel::onAmountChanged,
        onVendorChanged = viewModel::onVendorChanged,
        onGstHstChanged = viewModel::onGstHstChanged,
        onConfirm = onConfirm,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OcrReceiptScaffold(
    state: OcrReceiptUiState,
    onBack: () -> Unit,
    onOpenCamera: () -> Unit,
    onAmountChanged: (String) -> Unit,
    onVendorChanged: (String) -> Unit,
    onGstHstChanged: (String) -> Unit,
    onConfirm: (amountRaw: String, vendor: String, dateMillis: Long?, gstHstRaw: String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Receipt") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        OcrReceiptContent(
            state = state,
            innerPadding = innerPadding,
            onOpenCamera = onOpenCamera,
            onAmountChanged = onAmountChanged,
            onVendorChanged = onVendorChanged,
            onGstHstChanged = onGstHstChanged,
            onConfirm = onConfirm,
        )
    }
}

@Composable
private fun OcrReceiptContent(
    state: OcrReceiptUiState,
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    onOpenCamera: () -> Unit,
    onAmountChanged: (String) -> Unit,
    onVendorChanged: (String) -> Unit,
    onGstHstChanged: (String) -> Unit,
    onConfirm: (amountRaw: String, vendor: String, dateMillis: Long?, gstHstRaw: String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = Spacing.lg)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.xs))
        ReceiptThumbnail(state = state)
        CameraButton(
            hasImage = state.imageUri != null,
            onOpenCamera = onOpenCamera,
        )
        ConfidenceBanner(result = state.result)
        OcrEditableFields(
            state = state,
            onAmountChanged = onAmountChanged,
            onVendorChanged = onVendorChanged,
            onGstHstChanged = onGstHstChanged,
        )
        OcrErrorMessage(errorMessage = state.errorMessage)
        ConfirmReceiptButton(
            state = state,
            onConfirm = onConfirm,
        )
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun ReceiptThumbnail(state: OcrReceiptUiState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(12.dp))
            .border(
                Stroke.hairline,
                MaterialTheme.colorScheme.outline,
                RoundedCornerShape(12.dp),
            )
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (state.imageUri != null) {
            AsyncImage(
                model = state.imageUri,
                contentDescription = "Receipt thumbnail",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ReceiptPlaceholder()
        }

        if (state.isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@Composable
private fun ReceiptPlaceholder() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "No receipt scanned yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CameraButton(
    hasImage: Boolean,
    onOpenCamera: () -> Unit,
) {
    OutlinedButton(
        onClick = onOpenCamera,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.CameraAlt, contentDescription = null)
        Text(if (hasImage) "  Re-scan" else "  Open Camera")
    }
}

@Composable
private fun ConfidenceBanner(result: OcrExtractionResult?) {
    if (result == null) {
        return
    }

    val pct = (result.confidence * 100).roundToInt()
    val bannerColor = when {
        pct >= 75 -> MaterialTheme.colorScheme.primaryContainer
        pct >= 40 -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val bannerText = when {
        pct >= 75 -> "High confidence ($pct %)"
        pct >= 40 -> "Partial confidence ($pct %) - please review"
        else -> "Low confidence ($pct %) - manual entry recommended"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bannerColor)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Text(bannerText, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun OcrEditableFields(
    state: OcrReceiptUiState,
    onAmountChanged: (String) -> Unit,
    onVendorChanged: (String) -> Unit,
    onGstHstChanged: (String) -> Unit,
) {
    OcrField(
        label = "Amount (CAD)",
        value = state.amountRaw,
        onValueChange = onAmountChanged,
        isDetected = state.result?.detectedAmount != null,
        keyboardType = KeyboardType.Decimal,
    )
    OcrField(
        label = "Vendor",
        value = state.vendorRaw,
        onValueChange = onVendorChanged,
        isDetected = state.result?.detectedVendor != null,
    )
    OcrField(
        label = "Date (displayed as ms)",
        value = state.dateDisplay,
        onValueChange = {},
        isDetected = state.result?.detectedDate != null,
        readOnly = true,
    )
    OcrField(
        label = "GST/HST Amount",
        value = state.gstHstRaw,
        onValueChange = onGstHstChanged,
        isDetected = state.result?.detectedGstHst != null,
        keyboardType = KeyboardType.Decimal,
    )
}

@Composable
private fun OcrErrorMessage(errorMessage: String?) {
    if (errorMessage == null) {
        return
    }
    Text(
        text = errorMessage,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ConfirmReceiptButton(
    state: OcrReceiptUiState,
    onConfirm: (amountRaw: String, vendor: String, dateMillis: Long?, gstHstRaw: String) -> Unit,
) {
    Button(
        onClick = {
            onConfirm(
                state.amountRaw,
                state.vendorRaw,
                state.result?.detectedDate,
                state.gstHstRaw,
            )
        },
        enabled = state.imageUri != null && !state.isProcessing,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Confirm & Fill Form")
    }
}

@Composable
private fun OcrField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isDetected: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            readOnly = readOnly,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth(),
        ) {
            AssistChip(
                onClick = {},
                label = { Text(if (isDetected) "Auto-detected" else "Manual entry") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isDetected) {
                        Color(0xFF1B5E20).copy(alpha = 0.12f)
                    } else {
                        Color.Transparent
                    },
                    labelColor = if (isDetected) {
                        Color(0xFF1B5E20)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
            )
        }
    }
}
