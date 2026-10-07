package com.example.mileagetracker.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.example.mileagetracker.domain.model.CanadianUnits
import java.text.NumberFormat

/**
 * Displays a kilometre value formatted for the Canadian locale.
 *
 * Example output: `1,234.5 km`
 *
 * @param km       Distance in kilometres.
 * @param modifier Optional [Modifier].
 * @param style    Text style; defaults to [MaterialTheme.typography.bodyMedium].
 * @param color    Text colour; defaults to [Color.Unspecified] (inherits from theme).
 */
@Composable
fun DistanceText(
    km: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified
) {
    val formatted = remember(km) {
        val nf = NumberFormat.getInstance(CanadianUnits.CURRENCY_LOCALE).apply {
            maximumFractionDigits = 1
            minimumFractionDigits = 1
        }
        "${nf.format(km)} ${CanadianUnits.DISTANCE_UNIT}"
    }
    Text(
        text = formatted,
        modifier = modifier,
        style = style,
        color = color
    )
}
