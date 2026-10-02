package dev.maintracker.ui.parts

import dev.maintracker.ui.common.LabeledFab
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import dev.maintracker.ui.common.app
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PartsState(val loaded: Boolean = false, val parts: List<Part> = emptyList(), val tasks: List<MaintenanceTask> = emptyList())

class PartsViewModel(repo: VehicleRepository, vehicleId: String) : ViewModel() {
    val state: StateFlow<PartsState> = combine(repo.observeParts(vehicleId), repo.observeTasks(vehicleId)) { p, t ->
        PartsState(true, p, t)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PartsState())
}

/** Part numbers, torque and fluid specs — the DIY-mechanic differentiator (Build Guide §3.5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartsScreen(vehicleId: String, onBack: () -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit) {
    val context = LocalContext.current
    val vm: PartsViewModel = viewModel(key = "parts-$vehicleId") { PartsViewModel(context.app().repository, vehicleId) }
    val s by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parts & specs") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            LabeledFab("Add part", Icons.Filled.Add, onClick = onAdd)
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (!s.loaded) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
                return@Box
            }
            if (s.parts.isEmpty()) {
                Text(
                    "No parts yet. Add the oil filter, plugs, belts or fluids you buy for this vehicle — " +
                        "part numbers, torque values and capacities, all in one place.",
                    Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Box
            }
            val titles = s.tasks.associate { it.id to it.title }
            val groups = s.parts.groupBy { it.taskId?.let(titles::get) }
                .toSortedMap(nullsFirst(String.CASE_INSENSITIVE_ORDER))
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                groups.forEach { (taskTitle, list) ->
                    item(key = "h-$taskTitle") {
                        Text(
                            taskTitle ?: "General",
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(list, key = { it.id }) { p ->
                        PartRow(p, Modifier.clickable { onEdit(p.id) }.padding(horizontal = 16.dp))
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PartRow(p: Part, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp)) {
        Text(
            listOfNotNull(p.name, p.quantity?.let { "× $it" }).joinToString(" "),
            style = MaterialTheme.typography.titleSmall,
        )
        val number = listOfNotNull(p.manufacturer, p.partNumber).joinToString(" ")
        if (number.isNotEmpty()) Text(number, style = MaterialTheme.typography.bodyMedium)
        val specs = listOfNotNull(p.torqueSpec?.let { "Torque $it" }, p.fluidSpec).joinToString(" · ")
        if (specs.isNotEmpty()) {
            Text(specs, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
