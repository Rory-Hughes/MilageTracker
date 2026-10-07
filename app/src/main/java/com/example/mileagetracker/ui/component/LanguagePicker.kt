package com.example.mileagetracker.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.mileagetracker.R
import com.example.mileagetracker.domain.model.AppLanguage
import com.example.mileagetracker.ui.theme.Spacing

@Composable
fun LanguagePicker(
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        AppLanguage.entries.forEach { language ->
            FilterChip(
                selected = selectedLanguage == language,
                onClick = { onLanguageSelected(language) },
                label = {
                    Text(
                        text = when (language) {
                            AppLanguage.SYSTEM_DEFAULT -> stringResource(R.string.language_system_default)
                            AppLanguage.ENGLISH -> stringResource(R.string.language_english)
                            AppLanguage.FRENCH -> stringResource(R.string.language_french)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                modifier = Modifier.padding(end = Spacing.xs),
            )
        }
    }
}
