package com.example.mileagetracker.domain.usecase.drive

import java.util.Calendar

internal fun twoTierReimbursement(
    businessKm: Float,
    ytdBefore: Float,
    limitKm: Int,
    firstTierCents: Int,
    additionalCents: Int,
): Float {
    if (businessKm == 0f) return 0f
    val threshold = limitKm.toFloat()
    val firstTierRemaining = (threshold - ytdBefore).coerceAtLeast(0f)
    val firstKm = businessKm.coerceAtMost(firstTierRemaining)
    val extraKm = (businessKm - firstKm).coerceAtLeast(0f)
    return (firstKm * firstTierCents + extraKm * additionalCents) / 100f
}

internal fun yearBounds(year: Int): Pair<Long, Long> {
    val start = Calendar.getInstance().apply {
        set(year, Calendar.JANUARY, 1, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val end = Calendar.getInstance().apply {
        set(year, Calendar.DECEMBER, 31, 23, 59, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
    return start to end
}

internal fun monthLabel(month: Int, year: Int): String {
    val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    return "${months[month]} $year"
}
