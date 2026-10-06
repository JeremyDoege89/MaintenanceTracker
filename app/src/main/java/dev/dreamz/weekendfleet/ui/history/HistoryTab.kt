package dev.dreamz.weekendfleet.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dreamz.weekendfleet.data.VehicleRepository
import dev.dreamz.weekendfleet.data.model.Area
import dev.dreamz.weekendfleet.data.model.MaintenanceTask
import dev.dreamz.weekendfleet.data.model.MeterUnit
import dev.dreamz.weekendfleet.data.model.ServiceRecord
import dev.dreamz.weekendfleet.domain.Format
import dev.dreamz.weekendfleet.ui.common.app
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HistoryFilter(val query: String = "", val area: Area? = null, val taskId: String? = null) {
    val active: Boolean get() = query.isNotBlank() || area != null || taskId != null
}

class HistoryViewModel(repo: VehicleRepository, vehicleId: String) : ViewModel() {
    val filter = MutableStateFlow(HistoryFilter())

    /** Null until first load. Query is debounced so typing doesn't hammer the DB. */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val records: StateFlow<List<ServiceRecord>?> = combine(
        filter.map { it.query }.debounce(200),
        filter.map { it.area to it.taskId },
    ) { q, (a, t) -> HistoryFilter(q, a, t) }
        .flatMapLatest { f -> repo.observeHistory(vehicleId, f.area, f.taskId, f.query) }
        .map<List<ServiceRecord>, List<ServiceRecord>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Vehicle detail → History: reverse-chronological, filter by area/task, search (Build Guide §5.4). */
@Composable
fun HistoryTab(vehicleId: String, tasks: List<MaintenanceTask>, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    val vm: HistoryViewModel = viewModel(key = "history-$vehicleId") { HistoryViewModel(context.app().repository, vehicleId) }
    val f by vm.filter.collectAsStateWithLifecycle()
    val list by vm.records.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = f.query,
            onValueChange = { q -> vm.filter.value = f.copy(query = q) },
            placeholder = { Text("Search title, vendor, parts, notes") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (f.query.isNotEmpty()) {
                    IconButton(onClick = { vm.filter.value = f.copy(query = "") }) { Icon(Icons.Filled.Clear, "Clear search") }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ChipMenu(
                    label = f.area?.label ?: "All areas",
                    selected = f.area != null,
                    options = listOf<Area?>(null) + Area.entries,
                    optionLabel = { it?.label ?: "All areas" },
                ) { vm.filter.value = f.copy(area = it) }
            }
            item {
                val taskTitle = tasks.firstOrNull { it.id == f.taskId }?.title
                ChipMenu(
                    label = taskTitle ?: "All tasks",
                    selected = f.taskId != null,
                    options = listOf<MaintenanceTask?>(null) + tasks.sortedBy { it.title },
                    optionLabel = { it?.title ?: "All tasks" },
                ) { vm.filter.value = f.copy(taskId = it?.id) }
            }
        }
        val l = list
        when {
            l == null -> Unit
            l.isEmpty() -> Text(
                if (f.active) "No records match." else "No service logged yet. Use Log service to add the first record.",
                Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(l, key = { it.id }) { r ->
                    RecordRow(r, onClick = { onOpenRecord(r.id) }, modifier = Modifier.padding(horizontal = 16.dp))
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun <T> ChipMenu(
    label: String,
    selected: Boolean,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { open = true },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(optionLabel(o)) }, onClick = { onSelect(o); open = false }) }
        }
    }
}

/** One history line: date, title, area, readings, cost, vendor. */
@Composable
fun RecordRow(r: ServiceRecord, onClick: () -> Unit, modifier: Modifier = Modifier, showArea: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).then(modifier).heightIn(min = 56.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(
                Format.date(r.date),
                r.area.label.takeIf { showArea },
                meterSummary(r),
                r.vendor,
            ).joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        r.cost?.let { Text(Format.money(it, r.currency), style = MaterialTheme.typography.bodyLarge) }
    }
}

fun meterSummary(r: ServiceRecord): String? = listOfNotNull(
    r.miles?.let { Format.meter(it, MeterUnit.MILES) },
    r.engineHours?.let { Format.meter(it, MeterUnit.ENGINE_HOURS) },
    r.generatorHours?.let { Format.meter(it, MeterUnit.GENERATOR_HOURS) },
).joinToString(", ").ifEmpty { null }
