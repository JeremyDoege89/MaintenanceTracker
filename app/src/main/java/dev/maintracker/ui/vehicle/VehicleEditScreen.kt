package dev.maintracker.ui.vehicle

import dev.maintracker.domain.label

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.maintracker.data.model.MaintenanceTemplate
import dev.maintracker.data.model.MeterUnit
import dev.maintracker.data.model.VehicleType
import dev.maintracker.data.seed.SeedTemplates
import dev.maintracker.domain.Format
import dev.maintracker.ui.common.Field
import dev.maintracker.ui.common.MeterField
import dev.maintracker.ui.common.PrimaryButton
import dev.maintracker.ui.common.SectionLabel
import dev.maintracker.ui.common.VehicleImage
import dev.maintracker.ui.common.app
import dev.maintracker.ui.common.icon
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleEditScreen(vehicleId: String?, onDone: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val vm: VehicleEditViewModel = viewModel(key = "edit-$vehicleId") {
        VehicleEditViewModel(context.app().repository, context.app().photos, vehicleId)
    }
    val s by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(s.doneVehicleId) { s.doneVehicleId?.let(onDone) }
    BackHandler { if (!vm.back()) onCancel() }

    val title = when {
        !s.isNew -> "Edit vehicle"
        s.step == EditStep.TYPE -> "What are you tracking?"
        s.step == EditStep.DETAILS -> "Add ${s.type?.label?.lowercase() ?: "vehicle"}"
        else -> "Starting schedule"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { if (!vm.back()) onCancel() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                s.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                s.step == EditStep.TYPE -> TypePicker(vm::pickType)
                s.step == EditStep.DETAILS -> DetailsForm(s, vm)
                else -> ScheduleSummary(s, vm)
            }
        }
    }
}

@Composable
private fun TypePicker(onPick: (VehicleType) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(VehicleType.entries) { type ->
            Card(
                onClick = { onPick(type) },
                modifier = Modifier.heightIn(min = 120.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(type.icon, contentDescription = null, modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Text(type.label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsForm(s: EditState, vm: VehicleEditViewModel) {
    val type = s.type ?: return
    val f = s.form
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::setPhoto)
    }
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            VehicleImage(f.photoPath, type, 88.dp)
            Column {
                TextButton(onClick = {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (f.photoPath == null) "Add photo" else "Change photo")
                }
                if (f.photoPath != null) {
                    TextButton(onClick = vm::removePhoto, modifier = Modifier.heightIn(min = 48.dp)) { Text("Remove photo") }
                }
            }
        }

        Field("Name *", f.name, s.errors["name"], { vm.edit { copy(name = it) } },
            placeholder = "Big Blue", caps = KeyboardCapitalization.Words)

        SectionLabel("Year / make / model")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Year", f.year, s.errors["year"], { vm.edit { copy(year = it.filter(Char::isDigit).take(4)) } },
                keyboard = KeyboardType.Number, modifier = Modifier.width(110.dp))
            Field("Make", f.make, null, { vm.edit { copy(make = it) } },
                caps = KeyboardCapitalization.Words, modifier = Modifier.weight(1f))
        }
        Field("Model", f.model, null, { vm.edit { copy(model = it) } }, caps = KeyboardCapitalization.Words)
        Field(type.idLabel, f.vinOrHin, null, { vm.edit { copy(vinOrHin = it) } },
            caps = KeyboardCapitalization.Characters,
            supporting = "Optional. Stays on this phone; shown masked.")

        SectionLabel("Current readings")
        Text(
            "Readings drive mileage- and hour-based reminders. Leave blank if you don't track one.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (unit in type.meters) {
            val (value, key) = when (unit) {
                MeterUnit.MILES -> f.miles to "miles"
                MeterUnit.ENGINE_HOURS -> f.engineHours to "engineHours"
                MeterUnit.GENERATOR_HOURS -> f.generatorHours to "generatorHours"
            }
            MeterField(unit.label, value, s.errors[key]) { v ->
                vm.edit {
                    when (unit) {
                        MeterUnit.MILES -> copy(miles = v)
                        MeterUnit.ENGINE_HOURS -> copy(engineHours = v)
                        MeterUnit.GENERATOR_HOURS -> copy(generatorHours = v)
                    }
                }
            }
        }

        SectionLabel("Purchase")
        OutlinedTextField(
            value = f.purchaseDate?.let { Format.date(it, ZoneOffset.UTC) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Purchase date") },
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = "Pick purchase date")
                }
            },
            modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
        )
        MeterField("${type.primaryMeter.label} at purchase", f.purchaseMeter, s.errors["purchaseMeter"]) {
            vm.edit { copy(purchaseMeter = it) }
        }

        Field("Notes", f.notes, null, { vm.edit { copy(notes = it) } }, singleLine = false,
            caps = KeyboardCapitalization.Sentences,
            supporting = "Anything else — tow vehicle, storage spot, quirks.")

        PrimaryButton(
            if (s.isNew) "Next: review schedule" else "Save",
            onClick = vm::submitDetails,
            enabled = !s.saving,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).navigationBarsPadding(),
        )
    }

    if (showDatePicker) {
        // DatePicker works in UTC midnight millis; we store that value as-is for a date-only field.
        val state = rememberDatePickerState(initialSelectedDateMillis = f.purchaseDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.edit { copy(purchaseDate = state.selectedDateMillis) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.edit { copy(purchaseDate = null) }
                    showDatePicker = false
                }) { Text("Clear") }
            },
        ) { DatePicker(state) }
    }
}

@Composable
private fun ScheduleSummary(s: EditState, vm: VehicleEditViewModel) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Info, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(SeedTemplates.DISCLAIMER, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${s.selected.size} of ${s.templates.size} selected — untick anything that doesn't apply.",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    )
                    val allOn = s.selected.size == s.templates.size
                    TextButton(onClick = { vm.setAllTemplates(!allOn) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (allOn) "None" else "All")
                    }
                }
            }
            items(s.templates, key = { it.id }) { t ->
                TemplateRow(t, t.id in s.selected) { vm.toggleTemplate(t.id) }
                HorizontalDivider(Modifier.padding(start = 72.dp))
            }
        }
        Surface(tonalElevation = 3.dp) {
            PrimaryButton(
                if (s.selected.isEmpty()) "Save without a schedule" else "Save with ${s.selected.size} tasks",
                onClick = vm::save,
                enabled = !s.saving,
                modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun TemplateRow(t: MaintenanceTemplate, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .heightIn(min = 64.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, modifier = Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, style = MaterialTheme.typography.titleSmall)
            Text(
                Format.interval(t.timeIntervalDays, t.meterInterval, t.meterUnit),
                style = MaterialTheme.typography.bodyMedium,
            )
            t.notes?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AssistChip(onClick = onToggle, label = { Text(t.area.label, fontSize = 12.sp) })
    }
}
