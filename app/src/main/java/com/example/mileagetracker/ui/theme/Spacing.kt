package com.example.mileagetracker.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing scale for the MileageTracker design system.
 *
 * Derived from an empirical histogram of `.dp` literals in `ui/`. Use these
 * tokens instead of raw `.dp` for any **layout** spacing — padding, spacers,
 * `Arrangement.spacedBy`, icon-text gaps.
 *
 * Component dimensions (avatar size, fixed widths) and stroke widths
 * (borders, dividers) are NOT spacing — keep raw `.dp` for the former and
 * use [Stroke] for the latter.
 */
object Spacing {
    /** 4.dp — tightest gap between same-row siblings. */
    val xs = 4.dp

    /** 8.dp — default sub-component gap; most common token. */
    val sm = 8.dp

    /** 12.dp — inter-section nudge (header ↔ body within a card). */
    val md = 12.dp

    /** 16.dp — standard card / screen edge padding. */
    val lg = 16.dp

    /** 24.dp — gap between major card groups. */
    val xl = 24.dp

    /** 32.dp — screen-section gap; above/below empty/error states. */
    val xxl = 32.dp
}
