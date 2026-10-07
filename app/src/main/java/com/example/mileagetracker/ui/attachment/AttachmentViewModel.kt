package com.example.mileagetracker.ui.attachment

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.domain.model.TripAttachment
import com.example.mileagetracker.domain.usecase.attachment.AddAttachmentUseCase
import com.example.mileagetracker.domain.usecase.attachment.DeleteAttachmentUseCase
import com.example.mileagetracker.domain.usecase.attachment.GetAttachmentsForTripUseCase
import com.example.mileagetracker.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the attachment strip and attachment viewer.
 *
 * Reads [Screen.ARG_DRIVE_ID] from [SavedStateHandle] to scope all
 * operations to the correct trip.  The [attachments] StateFlow is
 * collected by [AttachmentThumbnailRow] and reacts to adds/deletes
 * in real time.
 *
 * ## Events
 * One-shot events are emitted on [events] for toasts / navigation:
 * - [AttachmentEvent.AddSuccess] — file was stored and row inserted.
 * - [AttachmentEvent.DeleteSuccess] — row and file removed.
 * - [AttachmentEvent.Error] — something went wrong; show snackbar.
 *
 * @param savedStateHandle              Hilt-injected handle carrying [Screen.ARG_DRIVE_ID].
 * @param getAttachmentsForTripUseCase  Flow-based read use-case.
 * @param addAttachmentUseCase          Copies file + inserts row.
 * @param deleteAttachmentUseCase       Removes row + file.
 */
@HiltViewModel
class AttachmentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAttachmentsForTripUseCase: GetAttachmentsForTripUseCase,
    private val addAttachmentUseCase: AddAttachmentUseCase,
    private val deleteAttachmentUseCase: DeleteAttachmentUseCase,
) : ViewModel() {

    private val tripId: Int? = savedStateHandle[Screen.ARG_DRIVE_ID]

    /**
     * Live list of attachments for the current trip.
     * Starts as an empty list while the trip ID resolves or if none is present.
     */
    val attachments: StateFlow<List<TripAttachment>> =
        if (tripId != null) {
            getAttachmentsForTripUseCase(tripId)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = emptyList(),
                )
        } else {
            kotlinx.coroutines.flow.MutableStateFlow(emptyList())
        }

    private val _events = Channel<AttachmentEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Copies the file at [sourceUri] into internal storage and inserts a
     * [TripAttachment] row.
     *
     * @param sourceUri The content URI from the camera or gallery picker.
     * @param mimeType  Optional MIME type; resolved from content resolver if blank.
     * @param fileName  Optional display name; resolved from content resolver if blank.
     */
    fun addAttachment(sourceUri: Uri, mimeType: String = "", fileName: String = "") {
        val id = tripId ?: return
        viewModelScope.launch {
            runCatching {
                val added = addAttachmentUseCase(
                    tripId = id,
                    sourceUri = sourceUri,
                    mimeType = mimeType,
                    fileName = fileName,
                )
                _events.send(AttachmentEvent.AddSuccess(added))
            }.onFailure { error ->
                _events.send(AttachmentEvent.Error("Failed to add attachment: ${error.message}"))
            }
        }
    }

    /**
     * Deletes [attachment] from the database and internal storage.
     */
    fun deleteAttachment(attachment: TripAttachment) {
        viewModelScope.launch {
            runCatching {
                deleteAttachmentUseCase(attachment)
                _events.send(AttachmentEvent.DeleteSuccess)
            }.onFailure { error ->
                _events.send(AttachmentEvent.Error("Failed to delete attachment: ${error.message}"))
            }
        }
    }
}

// ── One-shot events ────────────────────────────────────────────────────────────────

sealed interface AttachmentEvent {
    data class AddSuccess(val attachment: TripAttachment) : AttachmentEvent
    data object DeleteSuccess : AttachmentEvent
    data class Error(val message: String) : AttachmentEvent
}
