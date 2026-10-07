package com.example.mileagetracker.domain.model

import com.example.mileagetracker.data.local.entity.TripCategory

/**
 * A classification suggestion derived by matching a candidate trip against
 * historical trips with similar origin/destination and time-of-day profile.
 *
 * ## Fields
 * - [suggestedCategory]  – the [TripCategory] most frequently applied to
 *   matched historical trips.
 * - [suggestedPurpose]   – the most common [DriveEntity.purposeText] value
 *   from matched trips, or null if none was recorded.
 * - [suggestedVehicleId] – the vehicle most frequently used on matched trips,
 *   or null if no vehicle was assigned.
 * - [matchConfidence]    – 0.0–1.0 score indicating how strongly the
 *   candidate trip resembles the matched pattern.  Computed as:
 *   `matchingTrips.size / candidateWindowSize`, capped at 1.0.
 * - [basedOnTripIds]     – IDs of the historical [DriveEntity] rows that
 *   contributed to this suggestion, for audit and explainability.
 */
data class TripSuggestion(
    val suggestedCategory: TripCategory,
    val suggestedPurpose: String?,
    val suggestedVehicleId: Int?,
    val matchConfidence: Float,
    val basedOnTripIds: List<Int>,
)
