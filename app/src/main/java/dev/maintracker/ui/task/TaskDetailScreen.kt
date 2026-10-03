package dev.maintracker.ui.task

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.maintracker.data.VehicleRepository
import dev.maintracker.data.model.MaintenanceTask
import dev.maintracker.data.model.Part
import dev.maintracker.data.model.ServiceRecord
import dev.maintracker.data.model.TaskStatus
import dev.maintracker.data.model.Vehicle
import dev.maintracker.domain.DueText
import dev.maintracker.domain.Format
import dev.maintracker.domain.isFinishedOneTime
import dev.maintracker.ui.common.InfoCard
import dev.maintracker.data.model.TemplateKind
import androidx.compose.material.icons.filled.Shield
import dev.maintracker.ui.common.LocalAppActions
import dev.maintracker.ui.common.PrimaryButton
import dev.maintracker.ui.common.ServiceSheetRequest
import dev.maintracker.ui.common.StatusPill
import dev.maintracker.ui.common.app
import dev.maintracker.ui.parts.PartRow
import dev.maintracker.ui.history.RecordRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

data class TaskDetailState(
    val loaded: Boolean = false,
    val task: MaintenanceTask? = null,
    val vehicle: Vehicle? = null,
    val records: List<ServiceRecord> = emptyList(),
    val parts: List<Part> = emptyList(),
    /** Library template this task came from, for "Why this interval?". */
    val template: dev.maintracker.data.model.MaintenanceTemplate? = null,
)

class TaskDetailViewModel(private val repo: VehicleRepository, private val taskId: String) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TaskDetailState> = repo.observeTask(taskId).flatMapLatest { t ->
        if (t == null) flowOf(TaskDetailState(loaded = true))
        else combine(
            repo.observeVehicle(t.vehicleId),
            repo.observeRecordsForTask(taskId),
            repo.observePartsForTask(taskId),
            kotlinx.coroutines.flow.flow { emit(t.templateId?.let { repo.getTemplate(it) }) },
        ) { v, r, p, tpl -> TaskDetailState(true, t, v, r, p, tpl) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskDetailState())

    fun setActive(active: Boolean) = viewModelScope.launch { repo.setTaskActive(taskId, active) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { repo.deleteTask(taskId); onDone() }
}

/** Build Guide §5.6: the "Mark complete" loop that keeps the engine honest. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: String,
    onBack: () -> Unit,
    onEdit: (vehicleId: String) -> Unit,
    onOpenRecord: (String) -> Unit,
    onAddPart: (vehicleId: String) -> Unit,
    onEditPart: (vehicleId: String, partId: String) -> Unit,
) {
    val context = LocalContext.current
    val actions = LocalAppActions.current
    val vm: TaskDetailViewModel = viewModel(key = "task-$taskId") { TaskDetailViewModel(context.app().repository, taskId) }
    val s by vm.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val t = s.task

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (t != null) {
                        IconButton(onClick = { onEdit(t.vehicleId) }) { Icon(Icons.Filled.Edit, "Edit task") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (t.isActive) "Disable (keep, stop tracking)" else "Enable") },
                                    onClick = { menu = false; vm.setActive(!t.isActive) },
                                )
                                DropdownMenuItem(text = { Text("Delete…") }, onClick = { menu = false; confirmDelete = true })
                            }
                        }
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val v = s.vehicle
            when {
                !s.loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                t == null || v == null -> Text("This task no longer exists.", Modifier.padding(24.dp))
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(v.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(t.title, style = MaterialTheme.typography.headlineSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(onClick = {}, label = { Text(listOfNotNull(t.area.label, t.areaDetail).joinToString(" · ")) })
                        when {
                            t.isActive -> StatusPill(t.status)
                            t.isFinishedOneTime ->
                                Text("Done", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else -> Text("Disabled", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (t.safetyCritical) {
                            AssistChip(onClick = {}, label = { Text("Safety") },
                                leadingIcon = { Icon(Icons.Filled.Shield, contentDescription = null) })
                        }
                    }
                    t.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }

                    InfoCard("Schedule") {
                        Text(Format.interval(t.timeIntervalDays, t.meterInterval, t.meterUnit, t.kind == TemplateKind.ONE_TIME), style = MaterialTheme.typography.bodyLarge)
                        Text(baselineLine(t), style = MaterialTheme.typography.bodyMedium)
                        DueText.dueLine(t)?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                        if (t.isActive && t.status != TaskStatus.MANUAL) {
                            val reason = DueText.reason(t, t.meterUnit?.let(v::meter), System.currentTimeMillis())
                            if (reason.isNotEmpty()) Text(reason, style = MaterialTheme.typography.bodyMedium)
                        }
                        val nextReminder by produceState<Long?>(null, t) { value = context.app().reminders.nextFireAt(t.id) }
                        Text(
                            when {
                                t.isFinishedOneTime -> "One-time task — done, nothing more is due"
                                !t.notifyEnabled -> "Reminders off for this task"
                                nextReminder != null -> "Next reminder " +
                                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(nextReminder!!))
                                t.meterInterval != null -> "Reminds when a reading reaches the due point"
                                else -> "No reminder pending"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (!t.isFinishedOneTime) {
                        PrimaryButton(
                            "Mark complete",
                            onClick = { actions.openServiceSheet(ServiceSheetRequest(vehicleId = v.id, taskId = t.id)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    val tpl = s.template
                    val why = listOfNotNull(
                        tpl?.typicalRange?.let { "Typical range" to it },
                        tpl?.severeService?.let { "Severe service" to it },
                        tpl?.source?.let { "Source" to it },
                    )
                    if (why.isNotEmpty()) {
                        InfoCard("Why this interval?") {
                            why.forEach { (k, v) ->
                                Column {
                                    Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(v, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    // Library notes repeat the "Why" card; show notes only once the owner has changed them.
                    t.notes?.takeIf { why.isEmpty() || it != tpl?.notes }?.let { InfoCard("Notes") { Text(it) } }

                    InfoCard("History (${s.records.size})") {
                        if (s.records.isEmpty()) {
                            Text("Not logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        s.records.forEachIndexed { i, r ->
                            if (i > 0) HorizontalDivider()
                            RecordRow(r, onClick = { onOpenRecord(r.id) }, showArea = false)
                        }
                    }

                    InfoCard("Parts & specs") {
                        if (s.parts.isEmpty()) {
                            Text("Track the filter, plug or fluid this task uses — part numbers, torque, capacity.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        s.parts.forEachIndexed { i, p ->
                            if (i > 0) HorizontalDivider()
                            PartRow(p, Modifier.clickable { onEditPart(v.id, p.id) })
                        }
                        OutlinedButton(onClick = { onAddPart(v.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add part") }
                    }
                }
            }
        }
    }

    if (confirmDelete && t != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${t.title}”?") },
            text = {
                Text(
                    "The task stops being tracked. Its ${s.records.size} service records and linked parts are kept " +
                        "(unlinked). To pause it instead, use Disable.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

/** "Last done 2026-03-14 at 41,220 mi" — or, before any completion, where the clock started. */
private fun baselineLine(t: MaintenanceTask): String {
    val meter = if (t.baselineMeter != null && t.meterUnit != null) " at ${Format.meter(t.baselineMeter, t.meterUnit)}" else ""
    return if (t.lastRecordId != null || t.completedCount > 0) {
        "Last done ${Format.date(t.baselineDate)}$meter"
    } else {
        "Counting from ${Format.date(t.baselineDate)}$meter. Log when you last did it for accurate due dates."
    }
}
