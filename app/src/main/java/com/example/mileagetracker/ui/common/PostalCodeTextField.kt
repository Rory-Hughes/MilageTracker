package com.example.mileagetracker.ui.common

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.mileagetracker.data.validation.CanadianAddressValidator

/**
 * An [OutlinedTextField] for entering a Canadian postal code.
 *
 * - Auto upper-cases all alphabetic input.
 * - Inserts a space after the third character via [VisualTransformation].
 * - Limits input to 6 alphanumeric characters (displayed as 7 with the space).
 * - Shows an inline error when the value does not pass [CanadianAddressValidator.validatePostalCode].
 */
@Composable
fun PostalCodeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Postal Code",
    enabled: Boolean = true
) {
    var showError by remember { mutableStateOf(false) }

    val errorMessage: String? = if (showError) {
        val result = CanadianAddressValidator.validatePostalCode(value)
        if (result is CanadianAddressValidator.ValidationResult.Invalid) result.reason else null
    } else {
        null
    }

    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            // Strip non-alphanumeric, upper-case, cap at 6 chars
            val cleaned = raw.replace(Regex("[^A-Za-z0-9]"), "").uppercase().take(6)
            onValueChange(cleaned)
            if (cleaned.length == 6) showError = true
        },
        modifier = modifier,
        label = { Text(label) },
        placeholder = { Text("A1A 1A1") },
        isError = errorMessage != null,
        supportingText = if (errorMessage != null) {
            { Text(errorMessage) }
        } else {
            null
        },
        visualTransformation = PostalCodeVisualTransformation,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Characters
        ),
        singleLine = true,
        enabled = enabled
    )
}

/**
 * [VisualTransformation] that inserts a space after the third character of a 6-char postal code,
 * producing the `A1A 1A1` display format while the underlying state stores `A1A1A1`.
 */
private object PostalCodeVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val out = if (raw.length > 3) "${raw.substring(0, 3)} ${raw.substring(3)}" else raw

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                if (offset <= 3) offset else offset + 1

            override fun transformedToOriginal(offset: Int): Int =
                when {
                    offset <= 3 -> offset
                    offset == 4 -> 3 // the inserted space maps back to position 3
                    else -> offset - 1
                }
        }
        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}
