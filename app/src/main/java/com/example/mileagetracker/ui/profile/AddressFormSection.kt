package com.example.mileagetracker.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mileagetracker.domain.model.CanadianAddress
import com.example.mileagetracker.ui.common.PostalCodeTextField
import java.util.Locale

/**
 * A Compose section for entering a Canadian mailing address.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressFormSection(
    address: CanadianAddress,
    onAddressChange: (CanadianAddress) -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.ENGLISH,
) {
    var provinceExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        AddressLineField(
            value = address.street,
            label = localizedLabel(locale, english = "Street Address", french = "Adresse"),
            onValueChange = { onAddressChange(address.copy(street = it)) },
        )
        SectionSpacer()
        AddressLineField(
            value = address.city,
            label = localizedLabel(locale, english = "City", french = "Ville"),
            onValueChange = { onAddressChange(address.copy(city = it)) },
        )
        SectionSpacer()
        ProvinceDropdown(
            address = address,
            locale = locale,
            expanded = provinceExpanded,
            onExpandedChange = { provinceExpanded = it },
            onAddressChange = onAddressChange,
        )
        SectionSpacer()
        PostalField(
            address = address,
            locale = locale,
            onAddressChange = onAddressChange,
        )
        SectionSpacer()
        CountryField(locale = locale)
    }
}

@Composable
private fun AddressLineField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProvinceDropdown(
    address: CanadianAddress,
    locale: Locale,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddressChange: (CanadianAddress) -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { onExpandedChange(!expanded) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = address.provinceName(locale),
            onValueChange = {},
            readOnly = true,
            label = {
                Text(
                    localizedLabel(
                        locale,
                        english = "Province / Territory",
                        french = "Province / Territoire",
                    )
                )
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            CanadianAddress.PROVINCE_CODES.forEach { code ->
                DropdownMenuItem(
                    text = { Text(provinceLabel(code = code, locale = locale)) },
                    onClick = {
                        onAddressChange(address.copy(provinceAbbreviation = code))
                        onExpandedChange(false)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun PostalField(
    address: CanadianAddress,
    locale: Locale,
    onAddressChange: (CanadianAddress) -> Unit,
) {
    PostalCodeTextField(
        value = address.postalCode,
        onValueChange = { onAddressChange(address.copy(postalCode = it)) },
        label = localizedLabel(locale, english = "Postal Code", french = "Code postal"),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CountryField(locale: Locale) {
    OutlinedTextField(
        value = "Canada",
        onValueChange = {},
        readOnly = true,
        label = { Text(localizedLabel(locale, english = "Country", french = "Pays")) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SectionSpacer() {
    Spacer(Modifier.height(8.dp))
}

private fun localizedLabel(
    locale: Locale,
    english: String,
    french: String,
): String = if (locale.language == "fr") french else english

private fun provinceLabel(
    code: String,
    locale: Locale,
): String {
    val displayName = if (locale.language == "fr") {
        CanadianAddress.PROVINCE_NAMES_FR[code] ?: code
    } else {
        CanadianAddress.PROVINCE_NAMES_EN[code] ?: code
    }
    return "$code - $displayName"
}
