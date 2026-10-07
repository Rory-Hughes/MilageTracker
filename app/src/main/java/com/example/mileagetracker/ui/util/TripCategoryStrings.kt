package com.example.mileagetracker.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.mileagetracker.R
import com.example.mileagetracker.data.local.entity.TripCategory

@Composable
fun TripCategory.localizedDisplayName(): String = when (this) {
    TripCategory.BUSINESS -> stringResource(R.string.trip_category_business)
    TripCategory.MEDICAL -> stringResource(R.string.trip_category_medical)
    TripCategory.CHARITY -> stringResource(R.string.trip_category_charity)
    TripCategory.PERSONAL -> stringResource(R.string.trip_category_personal)
    TripCategory.UNCLASSIFIED -> stringResource(R.string.trip_category_unclassified)
    TripCategory.COMMUTE -> stringResource(R.string.trip_category_commute)
    TripCategory.CUSTOM -> stringResource(R.string.trip_category_custom)
}
