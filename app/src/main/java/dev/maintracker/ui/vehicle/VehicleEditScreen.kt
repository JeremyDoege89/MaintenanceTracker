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
import dev.maintracker.data.model.Area
import dev.maintracker.data.model.DateBasis
import dev.maintracker.data.model.QuestionType
import dev.maintracker.data.model.TemplateKind
import dev.maintracker.domain.library.SetupEvaluator
import dev.maintracker.ui.common.DateField
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.semantics.Role
import java.time.ZoneId
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
        !s.isNew && s.step == EditStep.SETUP -> "How it's set up"
        !s.isNew && s.step == EditStep.SCHEDULE -> "Add tasks"
        !s.isNew -> "Edit vehicle"
        s.step == EditStep.TYPE -> "What are you tracking?"
        s.step == EditStep.DETAILS -> "Add ${s.type?.label?.lowercase() ?: "vehicle"}"
        s.step == EditStep.SETUP -> "How it's set up"
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
                s.step == EditStep.SETUP -> SetupStep(s, vm)
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
            MakeField(f.make, s.setup?.commonMakes?.get(type).orEmpty(), { vm.edit { copy(make = it) } },
                Modifier.weight(1f))
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
            // Trailers log generator hours only when they carry one.
            val label = if (unit == MeterUnit.GENERATOR_HOURS && type == VehicleType.TRAVEL_TRAILER) "${unit.label} (if equipped)" else unit.label
            MeterField(label, value, s.errors[key]) { v ->
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

        if (!s.isNew) {
            OutlinedButton(onClick = vm::openSetup, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (s.answers.isEmpty()) "Answer setup questions to add tasks" else "Review setup questions")
            }
        }
        PrimaryButton(
            if (s.isNew) "Next: how it's set up" else "Save",
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

/** Free-text make with suggestions from the library's common makes; anything typed is kept. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MakeField(value: String, makes: List<String>, onChange: (String) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val matches = makes.filter { it.contains(value.trim(), ignoreCase = true) && !it.equals(value.trim(), ignoreCase = true) }
    ExposedDropdownMenuBox(expanded = expanded && matches.isNotEmpty(), onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); expanded = true },
            label = { Text("Make") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = expanded && matches.isNotEmpty(), onDismissRequest = { expanded = false }) {
            matches.take(8).forEach { make ->
                DropdownMenuItem(text = { Text(make) }, onClick = { onChange(make); expanded = false })
            }
        }
    }
}

@Composable
private fun SetupStep(s: EditState, vm: VehicleEditViewModel) {
    val type = s.type ?: return
    val file = s.setup ?: return
    val questions = SetupEvaluator.visibleQuestions(file, type, s.answers)
    val missing = SetupEvaluator.missingRequired(file, type, s.answers).map { it.id }.toSet()
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Text(
                    "A few taps so the schedule only has what fits. The brand alone can't tell us this; " +
                        "\"Not sure\" is fine, and those tasks show up as \"Might apply\".",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(questions, key = { it.question.id }) { vq ->
                val q = vq.question
                val chosen = s.answers[q.id].orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(q.prompt + if (q.required) " *" else "", style = MaterialTheme.typography.titleMedium)
                    q.help?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (q.type == QuestionType.MULTI) {
                        Text("Pick all that apply", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    for (o in vq.options) {
                        val on = o.id in chosen
                        val rowMod = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        Row(
                            if (q.type == QuestionType.SINGLE) rowMod.selectable(on, role = Role.RadioButton) { vm.answer(q.id, o.id) }
                            else rowMod.toggleable(on, role = Role.Checkbox) { vm.answer(q.id, o.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (q.type == QuestionType.SINGLE) RadioButton(selected = on, onClick = null)
                            else Checkbox(checked = on, onCheckedChange = null)
                            Spacer(Modifier.width(12.dp))
                            Text(o.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
                if (missing.isNotEmpty()) {
                    Text("Answer the questions marked * to continue.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                }
                PrimaryButton("Next: review schedule", onClick = vm::submitSetup, enabled = missing.isEmpty(),
                    modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ScheduleSummary(s: EditState, vm: VehicleEditViewModel) {
    val type = s.type ?: return
    val recommended = s.templates.filter { it.id in s.recommendedIds }
    val might = s.templates.filter { it.id in s.mightApplyIds }
    val groups = recommended.groupBy { it.area }.toSortedMap(compareBy(Area::ordinal))
    val collapsed = remember { mutableStateMapOf<Area, Boolean>() }
    var confirmOff by remember { mutableStateOf<MaintenanceTemplate?>(null) }
    val toggle = { t: MaintenanceTemplate ->
        if (t.safetyCritical && t.id in s.selected) confirmOff = t else vm.toggleTemplate(t.id)
    }
    val row: @Composable (MaintenanceTemplate, List<String>) -> Unit = { t, hints ->
        TemplateRow(
            t, t.id in s.selected, hints, s.expiryDates[t.id],
            onToggle = { toggle(t) }, onDate = { vm.setExpiryDate(t.id, it) },
        )
        HorizontalDivider(Modifier.padding(start = 72.dp))
    }

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
                        Text(s.disclaimer, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            if (s.templates.isEmpty()) {
                item {
                    Text(
                        if (s.isNew) "Nothing in the library matches these answers. You can add custom tasks after saving."
                        else "No new tasks match these answers — everything that fits is already on this vehicle.",
                        style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp),
                    )
                }
            }
            if (recommended.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val on = recommended.count { it.id in s.selected }
                        Column(Modifier.weight(1f)) {
                            Text("Recommended", style = MaterialTheme.typography.titleMedium)
                            Text("$on of ${recommended.size} selected — untick anything that doesn't apply.",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        val allOn = on == recommended.size
                        TextButton(onClick = { vm.setAllTemplates(!allOn) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (allOn) "None" else "All")
                        }
                    }
                }
            }
            for ((area, list) in groups) {
                val closed = collapsed[area] == true
                item(key = "area-$area") {
                    Row(
                        Modifier.fillMaxWidth().clickable { collapsed[area] = !closed }.heightIn(min = 48.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(area.label, style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        Text("${list.count { it.id in s.selected }} of ${list.size}", style = MaterialTheme.typography.labelMedium)
                        Icon(if (closed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                            contentDescription = if (closed) "Expand ${area.label}" else "Collapse ${area.label}")
                    }
                }
                if (!closed) items(list, key = { it.id }) { t -> row(t, emptyList()) }
            }
            if (might.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)) {
                        Text("Might apply", style = MaterialTheme.typography.titleMedium)
                        Text("Check your manual. These depend on answers you weren't sure about.",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                items(might, key = { it.id }) { t ->
                    val file = s.setup
                    row(t, if (file == null) emptyList() else SetupEvaluator.wouldApplyIf(file, type, s.answers, t))
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            PrimaryButton(
                when {
                    !s.isNew && s.selected.isEmpty() -> "Save setup"
                    !s.isNew -> "Add ${s.selected.size} tasks"
                    s.selected.isEmpty() -> "Save without a schedule"
                    else -> "Save with ${s.selected.size} tasks"
                },
                onClick = vm::save,
                enabled = !s.saving,
                modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
            )
        }
    }

    confirmOff?.let { t ->
        AlertDialog(
            onDismissRequest = { confirmOff = null },
            icon = { Icon(Icons.Filled.Shield, contentDescription = null) },
            title = { Text(t.title) },
            text = { Text("This is a safety item. Turn it off anyway?") },
            confirmButton = { TextButton(onClick = { vm.toggleTemplate(t.id); confirmOff = null }) { Text("Turn off") } },
            dismissButton = { TextButton(onClick = { confirmOff = null }) { Text("Keep it") } },
        )
    }
}

@Composable
private fun TemplateRow(
    t: MaintenanceTemplate,
    checked: Boolean,
    appliesIf: List<String>,
    expiryDate: Long?,
    onToggle: () -> Unit,
    onDate: (Long?) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
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
                    if (t.kind == TemplateKind.PER_MANUAL) "Set the interval from your manual"
                    else Format.interval(t.timeIntervalDays, t.meterInterval, t.meterUnit, t.kind == TemplateKind.ONE_TIME),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (appliesIf.isNotEmpty()) {
                    Text("Applies if: " + appliesIf.joinToString(", "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (t.safetyCritical) {
                AssistChip(
                    onClick = onToggle,
                    label = { Text("Safety", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
        if (checked && t.kind == TemplateKind.EXPIRY) {
            val zone = ZoneId.systemDefault()
            DateField(
                label = if (t.dateBasis == DateBasis.REGISTRATION_DATE) "Last renewed (optional)" else "Made or installed (optional)",
                value = expiryDate,
                onPick = { d -> onDate(d?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()) },
                clearable = true,
                modifier = Modifier.fillMaxWidth().padding(start = 72.dp, end = 16.dp, bottom = 8.dp),
            )
        }
    }
}
