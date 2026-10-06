package dev.dreamz.weekendfleet.ui.common

import dev.dreamz.weekendfleet.data.model.MeterUnit
import dev.dreamz.weekendfleet.domain.Display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.dreamz.weekendfleet.domain.Format
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp))
}

@Composable
fun Field(
    label: String,
    value: String,
    error: String?,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String? = null,
    supporting: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    caps: KeyboardCapitalization = KeyboardCapitalization.None,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(capitalization = caps, keyboardType = keyboard, imeAction = ImeAction.Next),
        modifier = modifier,
    )
}

/** Large numeric field for gloved thumbs (Build Guide §8). */
@Composable
fun MeterField(
    label: String,
    value: String,
    error: String?,
    modifier: Modifier = Modifier.fillMaxWidth(),
    supporting: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' || it == ',' }) },
        label = { Text(label) },
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = modifier,
    )
}

/** "1,234.5" → 1234.5; blank → null. Callers validate non-null-but-unparseable separately. */
fun String.toNumberOrNull(): Double? = trim().replace(",", "").takeIf { it.isNotEmpty() }?.toDoubleOrNull()

fun Double?.toInputText(): String =
    this?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: ""

/** Stored reading → field text in the user's unit (km shown to 0.1). */
fun Double?.toMeterInput(u: MeterUnit): String = this?.let { v ->
    val d = Display.toDisplay(v, u)
    if (d == v) d else Math.round(d * 10) / 10.0
}.toInputText()

/** Field text in the user's unit → stored reading (miles for distance). */
fun String.toStoredMeter(u: MeterUnit): Double? = toNumberOrNull()?.let { Display.fromDisplay(it, u) }

/** Local calendar day <-> DatePicker's UTC-midnight millis. */
object PickerDates {
    fun toPicker(localMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(localMillis).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromPicker(utcMillis: Long): LocalDate =
        Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
}

/** Read-only date field with a picker. [value] is local-zone epoch millis; [onPick] gets the calendar day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: Long?,
    onPick: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    error: String? = null,
    clearable: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value?.let { Format.date(it) } ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        trailingIcon = {
            IconButton(onClick = { open = true }) { Icon(Icons.Filled.CalendarMonth, contentDescription = "Pick $label") }
        },
        modifier = modifier,
    )
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = value?.let { PickerDates.toPicker(it) })
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(PickerDates.fromPicker(it)) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = {
                if (clearable) {
                    TextButton(onClick = { onPick(null); open = false }) { Text("Clear") }
                } else {
                    TextButton(onClick = { open = false }) { Text("Cancel") }
                }
            },
        ) { DatePicker(state) }
    }
}

/** Single-choice dropdown field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Dropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(optionLabel(o)) }, onClick = { onSelect(o); expanded = false })
            }
        }
    }
}

@Composable
fun InfoCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(key, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
