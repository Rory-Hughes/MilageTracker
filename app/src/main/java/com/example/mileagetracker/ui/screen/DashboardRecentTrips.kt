package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.mileagetracker.R
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.util.DateFormats

@Composable
internal fun RecentTripsSection(
    recentTrips: List<DriveEntity>,
    onTripTapped: (Int) -> Unit = {},
) {
    Text(
        text = stringResource(R.string.dashboard_recent_trips),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = Spacing.sm),
    )

    if (recentTrips.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.dashboard_no_trips),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.lg),
            )
        }
        return
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            recentTrips.forEachIndexed { index, drive ->
                RecentTripRow(
                    drive = drive,
                    onTap = { onTripTapped(drive.id) },
                )
                if (index < recentTrips.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.lg))
                }
            }
        }
    }
}

@Composable
private fun RecentTripRow(
    drive: DriveEntity,
    onTap: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${drive.startLocation} → ${drive.endLocation}",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${"%,.1f".format(drive.distanceKm)} km",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = DateFormats.uiMonthDayTime(drive.startedAt, locale),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = drive.tripCategory.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = tripCategoryColor(drive.tripCategory),
            )
        }
    }
}

@Composable
private fun tripCategoryColor(category: TripCategory): Color = when (category) {
    TripCategory.BUSINESS -> MaterialTheme.colorScheme.primary
    TripCategory.MEDICAL,
    TripCategory.CHARITY -> MaterialTheme.colorScheme.tertiary
    TripCategory.PERSONAL,
    TripCategory.COMMUTE -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.error
}
