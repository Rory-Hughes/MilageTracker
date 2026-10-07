package com.example.mileagetracker.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.mileagetracker.domain.model.ReimbursementForecast

@Composable
internal fun DriverAnalyticsSummaryRow(forecast: ReimbursementForecast) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "${"%,.1f".format(forecast.actualBusinessKm)} / " +
                "${"%,.1f".format(forecast.projectedBusinessKm)} km",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "$${"%,.2f".format(forecast.actualReimbursement)} -> " +
                "$${"%,.2f".format(forecast.projectedReimbursement)}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}
