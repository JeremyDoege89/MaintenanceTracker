package dev.maintracker.ui.task

import dev.maintracker.domain.label

import dev.maintracker.domain.Display
import dev.maintracker.ui.common.toMeterInput
import dev.maintracker.ui.common.toStoredMeter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.maintracker.data.VehicleRepository
import dev.maintracker.data.model.Area
import dev.maintracker.data.model.MaintenanceTask
import dev.maintracker.data.model.MeterUnit
import dev.maintracker.data.model.TaskStatus
import dev.maintracker.data.model.Vehicle
import dev.maintracker.domain.IntervalUnit
import dev.maintracker.ui.common.DateField
import dev.maintracker.ui.common.Dropdown
import dev.maintracker.ui.common.Field
import dev.maintracker.ui.common.MeterField
import dev.maintracker.ui.common.PrimaryButton
import dev.maintracker.ui.common.SectionLabel
import dev.maintracker.ui.common.app
import dev.maintracker.ui.common.toInputText
import dev.maintracker.ui.common.toNumberOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class TaskForm(
    val area: Area = Area.OTHER,
    val areaDetail: String = "",
    val title: String = "",
    val description: String = "",
    val every: String = "",
    val everyUnit: IntervalUnit = IntervalUnit.MONTHS,
    val meterEvery: String = "",
    val meterUnit: MeterUnit? = null,
    val lastDone: Long = System.currentTimeMillis(),
    val lastMeter: String = "",
    val notes: String = "",
    val notify: Boolean = true,
)

data class TaskEditState(
    val loading: Boolean = true,
    val vehicle: Vehicle? = null,
    val existing: MaintenanceTask? = null,
    val form: TaskForm = TaskForm(),
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val done: Boolean = false,
)

class TaskEditViewModel(
    private val repo: VehicleRepository,
    private val vehicleId: String,
    private val taskId: String?,
) : ViewModel() {
    private val _state = MutableStateFlow(TaskEditState())
    val state: StateFlow<TaskEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val v = repo.getVehicle(vehicleId)
            val t = taskId?.let { repo.getTask(it) }
            val form = t?.toForm() ?: TaskForm(
                meterUnit = v?.vehicleType?.primaryMeter,
                lastMeter = v?.vehicleType?.primaryMeter?.let(v::meter).toInputText(),
            )
            _state.update { it.copy(loading = false, vehicle = v, existing = t, form = form) }
        }
    }

    fun edit(transform: TaskForm.() -> TaskForm) = _state.update { it.copy(form = it.form.transform(), errors = emptyMap()) }

    fun setMeterUnit(u: MeterUnit) = edit {
        val v = _state.value.vehicle
        copy(meterUnit = u, lastMeter = if (_state.value.existing == null) v?.meter(u).toInputText() else lastMeter)
    }

    fun setLastDone(day: LocalDate) = edit { copy(lastDone = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val errors = validate(s.form)
        if (errors.isNotEmpty()) { _state.update { it.copy(errors = errors) }; return }
        _state.update { it.copy(saving = true) }
        val f = s.form
        val n = f.every.trim().toIntOrNull()
        // Unchanged interval keeps its stored days (library 5 years = 1826, which toDays would make 1825).
        val days = s.existing?.timeIntervalDays?.takeIf { IntervalUnit.split(it) == (n to f.everyUnit) }
            ?: n?.let(f.everyUnit::toDays)
        val unit = f.meterUnit ?: s.vehicle?.vehicleType?.primaryMeter ?: MeterUnit.MILES
        val meter = f.meterEvery.toStoredMeter(unit)?.let { Math.round(it).toInt() }
        viewModelScope.launch {
            val base = s.existing ?: MaintenanceTask(
                vehicleId = vehicleId, area = f.area, title = "", baselineDate = f.lastDone,
                baselineMeter = f.lastMeter.toStoredMeter(unit), status = TaskStatus.OK, leadDays = 0,
                sortOrder = 1_000, createdAt = 0, updatedAt = 0,
            )
            repo.saveTask(
                base.copy(
                    area = f.area,
                    areaDetail = f.areaDetail.trim().takeIf { f.area == Area.OTHER && it.isNotEmpty() },
                    title = f.title.trim(),
                    description = f.description.trim().ifEmpty { null },
                    timeIntervalDays = days,
                    meterInterval = meter,
                    meterUnit = (f.meterUnit ?: s.vehicle?.vehicleType?.primaryMeter).takeIf { meter != null },
                    baselineDate = f.lastDone,
                    baselineMeter = f.lastMeter.toStoredMeter(unit),
                    notes = f.notes.trim().ifEmpty { null },
                    notifyEnabled = f.notify,
                ),
            )
            _state.update { it.copy(saving = false, done = true) }
        }
    }

    private fun validate(f: TaskForm): Map<String, String> = buildMap {
        if (f.title.isBlank()) put("title", "Name the task")
        if (f.every.isNotBlank() && (f.every.trim().toIntOrNull() ?: 0) <= 0) put("every", "Whole number above 0")
        if (f.meterEvery.isNotBlank() && (f.meterEvery.toNumberOrNull() ?: 0.0) < 1.0) put("meterEvery", "Number above 0")
        if (f.lastMeter.isNotBlank() && f.lastMeter.toNumberOrNull() == null) put("lastMeter", "Numbers only")
    }

    private fun MaintenanceTask.toForm(): TaskForm {
        val (n, unit) = timeIntervalDays?.let(IntervalUnit::split) ?: (null to IntervalUnit.MONTHS)
        return TaskForm(
            area = area, areaDetail = areaDetail.orEmpty(), title = title, description = description.orEmpty(),
            every = n?.toString() ?: "", everyUnit = unit,
            meterEvery = meterUnit?.let { u -> meterInterval?.let { Math.round(Display.toDisplay(it.toDouble(), u)).toString() } } ?: "",
            meterUnit = meterUnit,
            lastDone = baselineDate, lastMeter = meterUnit?.let { baselineMeter.toMeterInput(it) } ?: baselineMeter.toInputText(), notes = notes.orEmpty(),
            notify = notifyEnabled,
        )
    }
}

/** Add or edit a task (Build Guide M2: custom tasks, interval editing; no intervals = checklist). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(vehicleId: String, taskId: String?, onDone: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val vm: TaskEditViewModel = viewModel(key = "task-edit-$vehicleId-$taskId") {
        TaskEditViewModel(context.app().repository, vehicleId, taskId)
    }
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.done) { if (s.done) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (taskId == null) "Add custom task" else "Edit task") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val v = s.vehicle
            if (s.loading || v == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
                return@Box
            }
            val f = s.form
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Field("Task *", f.title, s.errors["title"], { vm.edit { copy(title = it) } },
                    caps = KeyboardCapitalization.Sentences, placeholder = "Grease kingpins")
                Dropdown("Area", Area.entries, f.area, { it.label }, { a -> vm.edit { copy(area = a) } })
                if (f.area == Area.OTHER) {
                    Field("Area detail", f.areaDetail, null, { vm.edit { copy(areaDetail = it) } },
                        caps = KeyboardCapitalization.Sentences, placeholder = "Leveling jacks")
                }
                Field("How / what", f.description, null, { vm.edit { copy(description = it) } },
                    caps = KeyboardCapitalization.Sentences)

                SectionLabel("Repeat — whichever comes first")
                Text("Leave both blank for a checklist item that never comes due on its own.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Every", f.every, s.errors["every"], { vm.edit { copy(every = it.filter(Char::isDigit).take(4)) } },
                        keyboard = KeyboardType.Number, modifier = Modifier.width(120.dp))
                    Dropdown("Unit", IntervalUnit.entries, f.everyUnit, { it.label }, { u -> vm.edit { copy(everyUnit = u) } },
                        modifier = Modifier.weight(1f))
                }
                val units = v.vehicleType.meters
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MeterField("Or every", f.meterEvery, s.errors["meterEvery"], modifier = Modifier.width(150.dp)) {
                        vm.edit { copy(meterEvery = it) }
                    }
                    Dropdown("Meter", units, f.meterUnit ?: units.first(), { it.label }, vm::setMeterUnit,
                        modifier = Modifier.weight(1f))
                }

                SectionLabel(if (s.existing == null) "Last done" else "Counting from")
                DateField("Date", f.lastDone, { day -> day?.let(vm::setLastDone) })
                if (f.meterEvery.isNotBlank()) {
                    MeterField("${(f.meterUnit ?: units.first()).label} when last done", f.lastMeter, s.errors["lastMeter"],
                        supporting = "Leave blank to start from the next reading you enter") { vm.edit { copy(lastMeter = it) } }
                }

                Field("Notes", f.notes, null, { vm.edit { copy(notes = it) } },
                    caps = KeyboardCapitalization.Sentences, singleLine = false)

                ListItem(
                    headlineContent = { Text("Remind me") },
                    supportingContent = { Text("Notify when this comes due or goes overdue") },
                    trailingContent = { Switch(checked = f.notify, onCheckedChange = null) },
                    modifier = Modifier.clickable { vm.edit { copy(notify = !notify) } },
                )

                PrimaryButton("Save", vm::save, Modifier.fillMaxWidth().navigationBarsPadding(), enabled = !s.saving)
            }
        }
    }
}
