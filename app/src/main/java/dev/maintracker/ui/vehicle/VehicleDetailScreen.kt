package dev.maintracker.ui.vehicle

import androidx.compose.material3.SecondaryTabRow
import dev.maintracker.ui.costs.CostsTab
import dev.maintracker.ui.documents.DocumentsTab

import dev.maintracker.domain.label

import dev.maintracker.ui.common.toNumberOrNull
import dev.maintracker.ui.common.toMeterInput
import dev.maintracker.ui.common.toStoredMeter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.maintracker.data.model.MaintenanceTask
import dev.maintracker.data.model.MeterUnit
import dev.maintracker.data.model.TaskStatus
import dev.maintracker.data.model.Vehicle
import dev.maintracker.domain.DueText
import dev.maintracker.domain.Format
import dev.maintracker.ui.common.LocalAppActions
import dev.maintracker.ui.common.ServiceSheetRequest
import dev.maintracker.ui.history.HistoryTab
import dev.maintracker.ui.history.RecordRow
import dev.maintracker.ui.theme.LocalStatusColors
import dev.maintracker.ui.common.InfoCard
import dev.maintracker.ui.common.KeyValue
import dev.maintracker.ui.common.StatusPill
import dev.maintracker.ui.common.VehicleImage
import dev.maintracker.ui.common.app
import dev.maintracker.ui.common.label
import kotlinx.coroutines.launch
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

private val Tabs = listOf("Overview", "Schedule", "History", "Documents", "Costs")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailScreen(
    vehicleId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenTask: (String) -> Unit,
    onAddTask: () -> Unit,
    onEditTask: (String) -> Unit,
    onOpenRecord: (String) -> Unit,
    onOpenParts: () -> Unit,
) {
    val actions = LocalAppActions.current
    val context = LocalContext.current
    val vm: VehicleDetailViewModel = viewModel(key = "detail-$vehicleId") {
        VehicleDetailViewModel(context.app().repository, vehicleId)
    }
    val s by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editMeters by remember { mutableStateOf(false) }

    val v = s.vehicle
    Scaffold(
        floatingActionButton = {
            // Logging is one tap from every tab that shows work (Build Guide §5.5).
            if (v != null && !v.archived && tab <= 2) {
                ExtendedFloatingActionButton(
                    onClick = { actions.openServiceSheet(ServiceSheetRequest(vehicleId = v.id)) },
                    icon = { Icon(Icons.Filled.Build, contentDescription = null) },
                    text = { Text("Log service") },
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(v?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (v != null) {
                        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit vehicle") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (v.archived) "Unarchive" else "Archive (sold / gone)") },
                                    onClick = { menu = false; vm.setArchived(!v.archived) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete…") },
                                    onClick = { menu = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!s.loaded) {
                Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
                return@Column
            }
            if (v == null) {
                // Deleted underneath us.
                Text("This vehicle no longer exists.", Modifier.padding(24.dp))
                return@Column
            }
            PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                Tabs.forEachIndexed { i, name ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(name) },
                        modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            when (tab) {
                0 -> Overview(v, s, onUpdateMeters = { editMeters = true }, onOpenParts = onOpenParts,
                    onOpenRecord = onOpenRecord)
                1 -> ScheduleList(
                    v, s.tasks, onOpenTask = onOpenTask, onAddTask = onAddTask, onEditTask = onEditTask,
                    onComplete = { t -> actions.openServiceSheet(ServiceSheetRequest(vehicleId = v.id, taskId = t.id)) },
                )
                2 -> HistoryTab(v.id, s.tasks, onOpenRecord)
                3 -> DocumentsTab(v.id)
                else -> CostsTab(v.id)
            }
        }
    }

    if (confirmDelete && v != null) {
        DeleteVehicleDialog(v, s.tasks.size, onConfirm = { confirmDelete = false; vm.delete(onBack) },
            onDismiss = { confirmDelete = false })
    }
    if (editMeters && v != null) {
        MetersDialog(v, onDismiss = { editMeters = false }) { mi, eh, gh ->
            vm.updateMeters(mi, eh, gh)
            editMeters = false
        }
    }
}

@Composable
fun DeleteVehicleDialog(v: Vehicle, taskCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${v.name}?") },
        text = {
            Text(
                "This permanently removes ${v.name}" +
                    (if (taskCount > 0) ", its $taskCount scheduled tasks" else "") +
                    ", service history, parts and attachments. It can't be undone.\n\n" +
                    "Sold it? Archive instead to keep the history.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep") } },
    )
}

@Composable
private fun Overview(
    v: Vehicle,
    s: DetailState,
    onUpdateMeters: () -> Unit,
    onOpenParts: () -> Unit,
    onOpenRecord: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (v.photoUri != null) {
            VehicleImage(v.photoUri, v.vehicleType, size = 360.dp, maxPx = 1280,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        }
        if (v.archived) {
            Text("Archived — reminders are off for this vehicle.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        InfoCard("Vehicle") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (v.photoUri == null) {
                    VehicleImage(null, v.vehicleType, 56.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Column {
                    Text(listOfNotNull(v.year?.toString(), v.make, v.model).joinToString(" ").ifEmpty { "—" },
                        style = MaterialTheme.typography.titleMedium)
                    Text(v.vehicleType.label, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Format.maskId(v.vinOrHin)?.let { KeyValue(v.vehicleType.idLabel, it) }
        }

        InfoCard("Readings") {
            val shown = v.vehicleType.meters
            shown.forEach { unit ->
                KeyValue(unit.label, v.meter(unit)?.let { Format.meter(it, unit) } ?: "Not entered")
            }
            staleMeterWarning(v)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = LocalStatus.dueSoonText())
            }
            FilledTonalButton(onClick = onUpdateMeters, modifier = Modifier.heightIn(min = 52.dp)) {
                Icon(Icons.Filled.Speed, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Update readings")
            }
        }

        InfoCard("Open tasks") {
            val active = s.tasks.filter { it.isActive }
            if (active.isEmpty()) {
                Text("No scheduled tasks.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (st in listOf(TaskStatus.OVERDUE, TaskStatus.DUE_SOON, TaskStatus.OK, TaskStatus.MANUAL)) {
                        val n = active.count { it.status == st }
                        if (n > 0) StatusPill(st, count = n)
                    }
                }
            }
        }

        InfoCard("Recent activity") {
            if (s.recent.isEmpty()) {
                Text("No service logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                s.recent.forEach { r -> RecordRow(r, onClick = { onOpenRecord(r.id) }) }
            }
        }

        InfoCard("Parts & specs") {
            Text(
                if (s.partCount == 0) "Part numbers, torque values and fluid specs for this vehicle."
                else "${s.partCount} parts on file",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onOpenParts, modifier = Modifier.heightIn(min = 52.dp)) {
                Text(if (s.partCount == 0) "Add parts" else "Open parts list")
            }
        }

        if (v.purchaseDate != null || v.purchaseMeter != null) {
            InfoCard("Purchase") {
                v.purchaseDate?.let { KeyValue("Date", Format.date(it, ZoneOffset.UTC)) }
                v.purchaseMeter?.let { KeyValue("Reading", Format.meter(it, v.vehicleType.primaryMeter)) }
            }
        }
        v.notes?.let { InfoCard("Notes") { Text(it) } }
    }
}

private object LocalStatus {
    @Composable
    fun dueSoonText() = dev.maintracker.ui.theme.LocalStatusColors.current.dueSoon
}

private const val STALE_DAYS = 45L

/** Build Guide §6.5: never estimate silently — say when readings are old. */
private fun staleMeterWarning(v: Vehicle): String? {
    val at = v.metersUpdatedAt ?: return null
    val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - at)
    if (days < STALE_DAYS) return null
    val what = if (v.vehicleType.primaryMeter == MeterUnit.MILES) "Odometer reading" else "Hour meter reading"
    return "$what is $days days old — update it to keep reminders accurate."
}

/** Grouped by status, then by area (Build Guide §5.4). Swipe right = complete, left = edit. */
@Composable
private fun ScheduleList(
    v: Vehicle,
    tasks: List<MaintenanceTask>,
    onOpenTask: (String) -> Unit,
    onAddTask: () -> Unit,
    onEditTask: (String) -> Unit,
    onComplete: (MaintenanceTask) -> Unit,
) {
    val all = listOf(
        "Overdue" to tasks.filter { it.isActive && it.status == TaskStatus.OVERDUE },
        "Due soon" to tasks.filter { it.isActive && it.status == TaskStatus.DUE_SOON },
        "Upcoming" to tasks.filter { it.isActive && it.status == TaskStatus.OK },
        "Checklist" to tasks.filter { it.isActive && it.status == TaskStatus.MANUAL },
        "Disabled" to tasks.filter { !it.isActive },
    ).filter { it.second.isNotEmpty() }
    val due = all.filter { it.first == "Overdue" || it.first == "Due soon" }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val groups = if (showAll) all else due

    Column {
        SecondaryTabRow(selectedTabIndex = if (showAll) 1 else 0) {
            Tab(selected = !showAll, onClick = { showAll = false }, modifier = Modifier.heightIn(min = 48.dp),
                text = { Text("Due soon (${due.sumOf { it.second.size }})") })
            Tab(selected = showAll, onClick = { showAll = true }, modifier = Modifier.heightIn(min = 48.dp),
                text = { Text("All tasks (${tasks.size})") })
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            item(key = "add") {
                OutlinedButton(
                    onClick = onAddTask,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add custom task")
                }
            }
            if (groups.isEmpty()) {
                item {
                    if (showAll || tasks.isEmpty()) {
                        Text("No scheduled tasks yet.", Modifier.padding(horizontal = 16.dp))
                    } else {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Text("Nothing overdue or due soon.", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { showAll = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("See all ${tasks.size} tasks")
                            }
                        }
                    }
                }
            }
            groups.forEach { (title, list) ->
                item(key = "h-$title") {
                    Text("$title (${list.size})", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp))
                }
                list.groupBy { it.area }.toSortedMap().forEach { (area, inArea) ->
                    item(key = "a-$title-$area") {
                        Text(area.label.uppercase(), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp))
                    }
                    items(inArea, key = { it.id }) { t ->
                        SwipeTaskRow(
                            v, t,
                            onOpen = { onOpenTask(t.id) },
                            onComplete = { onComplete(t) },
                            onEdit = { onEditTask(t.id) },
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeTaskRow(v: Vehicle, t: MaintenanceTask, onOpen: () -> Unit, onComplete: () -> Unit, onEdit: () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = t.isActive && !v.archived,
        onDismiss = { value ->
            // Swipes trigger an action, never a removal: snap the row back.
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onComplete()
                SwipeToDismissBoxValue.EndToStart -> onEdit()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            scope.launch { state.reset() }
        },
        backgroundContent = {
            val (text, icon, align, color) = when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> SwipeBg("Complete", Icons.Filled.Check, Alignment.CenterStart,
                    LocalStatusColors.current.ok)
                SwipeToDismissBoxValue.EndToStart -> SwipeBg("Edit", Icons.Filled.Edit, Alignment.CenterEnd,
                    MaterialTheme.colorScheme.secondary)
                SwipeToDismissBoxValue.Settled -> SwipeBg("", null, Alignment.Center, Color.Transparent)
            }
            Box(Modifier.fillMaxSize().background(color).padding(horizontal = 24.dp), contentAlignment = align) {
                if (icon != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
    ) {
        TaskRow(
            t,
            Modifier
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onOpen)
                .semantics {
                    // Swipes are invisible to TalkBack; expose them as actions.
                    customActions = listOfNotNull(
                        CustomAccessibilityAction("Mark complete") { onComplete(); true }.takeIf { t.isActive },
                        CustomAccessibilityAction("Edit task") { onEdit(); true },
                    )
                },
        )
    }
}

private data class SwipeBg(val text: String, val icon: ImageVector?, val align: Alignment, val color: Color)

@Composable
private fun TaskRow(t: MaintenanceTask, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.title, style = MaterialTheme.typography.titleSmall)
            Text(Format.interval(t.timeIntervalDays, t.meterInterval, t.meterUnit),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DueText.dueLine(t)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        Spacer(Modifier.width(8.dp))
        if (t.isActive) StatusPill(t.status)
    }
}

@Composable
private fun MetersDialog(v: Vehicle, onDismiss: () -> Unit, onSave: (Double?, Double?, Double?) -> Unit) {
    fun Double?.text() = this?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: ""
    var miles by remember { mutableStateOf(v.currentMiles.toMeterInput(MeterUnit.MILES)) }
    var eng by remember { mutableStateOf(v.currentEngineHours.text()) }
    var gen by remember { mutableStateOf(v.currentGeneratorHours.text()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update readings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (unit in v.vehicleType.meters) {
                    val (value, set) = when (unit) {
                        MeterUnit.MILES -> miles to { x: String -> miles = x }
                        MeterUnit.ENGINE_HOURS -> eng to { x: String -> eng = x }
                        MeterUnit.GENERATOR_HOURS -> gen to { x: String -> gen = x }
                    }
                    OutlinedTextField(
                        value = value,
                        onValueChange = { x -> set(x.filter { it.isDigit() || it == '.' || it == ',' }) },
                        label = { Text(unit.label) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val types = v.vehicleType.meters
                onSave(
                    if (MeterUnit.MILES in types) miles.toStoredMeter(MeterUnit.MILES) else v.currentMiles,
                    if (MeterUnit.ENGINE_HOURS in types) eng.toNumberOrNull() else v.currentEngineHours,
                    if (MeterUnit.GENERATOR_HOURS in types) gen.toNumberOrNull() else v.currentGeneratorHours,
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
