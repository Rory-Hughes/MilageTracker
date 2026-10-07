package com.example.mileagetracker.domain.model

import java.util.Locale

/**
 * Supported app language choices for per-app localization.
 */
enum class AppLanguage(
    val tag: String,
    val displayNameEn: String,
    val displayNameFr: String,
) {
    SYSTEM_DEFAULT(
        tag = "",
        displayNameEn = "System Default",
        displayNameFr = "Langue du système",
    ),
    ENGLISH(
        tag = "en",
        displayNameEn = "English",
        displayNameFr = "Anglais",
    ),
    FRENCH(
        tag = "fr-CA",
        displayNameEn = "French (Canada)",
        displayNameFr = "Français (Canada)",
    );

    fun toLocale(): Locale? = when (this) {
        SYSTEM_DEFAULT -> null
        ENGLISH -> Locale.forLanguageTag("en")
        FRENCH -> Locale.forLanguageTag("fr-CA")
    }

    fun displayName(currentLocale: Locale = Locale.getDefault()): String {
        return if (currentLocale.language.equals("fr", ignoreCase = true)) {
            displayNameFr
        } else {
            displayNameEn
        }
    }

    companion object {
        fun fromTag(tag: String?): AppLanguage = when (tag?.trim().orEmpty()) {
            "en" -> ENGLISH
            "fr-CA", "fr_CA", "fr" -> FRENCH
            else -> SYSTEM_DEFAULT
        }
    }
}
