package com.example.mileagetracker.ui.attachment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.mileagetracker.domain.model.TripAttachment
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.theme.Stroke

/**
 * Horizontally scrolling strip of attachment thumbnails shown in trip details.
 */
@Composable
fun AttachmentThumbnailRow(
    attachments: List<TripAttachment>,
    onAddClicked: () -> Unit,
    onDeleteAttachment: (TripAttachment) -> Unit,
    onViewAttachment: (TripAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AttachmentRowHeader(count = attachments.size)
        Spacer(Modifier.height(Spacing.sm))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = PaddingValues(end = Spacing.sm),
        ) {
            items(attachments, key = { it.id }) { attachment ->
                AttachmentThumbnailCard(
                    attachment = attachment,
                    onView = { onViewAttachment(attachment) },
                    onDelete = { onDeleteAttachment(attachment) },
                )
            }
            item {
                AddAttachmentCard(onClick = onAddClicked)
            }
        }
    }
}

@Composable
private fun AttachmentRowHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Attachments", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddAttachmentCard(onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .size(width = 90.dp, height = 110.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
    ) {
        Box(
            modifier = Modifier.size(90.dp, 110.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add attachment",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = "Add",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun AttachmentThumbnailCard(
    attachment: TripAttachment,
    onView: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .size(width = 90.dp, height = 110.dp)
            .clickable(onClick = onView),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = Stroke.thin),
    ) {
        Box {
            AttachmentThumbnailContent(attachment = attachment)
            AttachmentDeleteButton(onDelete = onDelete)
        }
    }
}

@Composable
private fun AttachmentThumbnailContent(attachment: TripAttachment) {
    if (attachment.isImage) {
        AttachmentImageThumbnail(attachment = attachment)
    } else {
        AttachmentFileThumbnail(attachment = attachment)
    }
}

@Composable
private fun AttachmentImageThumbnail(attachment: TripAttachment) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(attachment.fileUri.toUri())
            .crossfade(true)
            .build(),
        contentDescription = attachment.fileName,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(90.dp, 110.dp)
            .clip(RoundedCornerShape(8.dp)),
    )
}

@Composable
private fun AttachmentFileThumbnail(attachment: TripAttachment) {
    Box(
        modifier = Modifier
            .size(90.dp, 110.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (attachment.isPdf) {
                    Icons.Default.PictureAsPdf
                } else {
                    Icons.Default.AttachFile
                },
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = attachment.fileName,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .width(80.dp)
                    .padding(horizontal = Spacing.xs),
            )
        }
    }
}

@Composable
private fun BoxScope.AttachmentDeleteButton(onDelete: () -> Unit) {
    IconButton(
        onClick = onDelete,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .size(28.dp)
            .padding(2.dp),
    ) {
        Icon(
            Icons.Default.Close,
            contentDescription = "Remove attachment",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
    }
}
