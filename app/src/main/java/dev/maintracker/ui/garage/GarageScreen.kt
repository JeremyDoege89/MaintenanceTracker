package dev.maintracker.ui.garage

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.maintracker.data.VehicleRepository
import dev.maintracker.data.db.VehicleSummary
import dev.maintracker.data.model.TaskStatus
import dev.maintracker.ui.common.LocalAppActions
import dev.maintracker.ui.common.PrimaryButton
import dev.maintracker.ui.common.StatusPill
import dev.maintracker.ui.common.VehicleImage
import dev.maintracker.ui.common.app
import dev.maintracker.ui.vehicle.DeleteVehicleDialog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GarageViewModel(private val repo: VehicleRepository) : ViewModel() {
    val showArchived = MutableStateFlow(false)

    /** Null until the first emission, so the empty state doesn't flash on open. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val vehicles: StateFlow<List<VehicleSummary>?> = showArchived
        .flatMapLatest { repo.observeSummaries(it) }
        .map<List<VehicleSummary>, List<VehicleSummary>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setArchived(id: String, archived: Boolean) = viewModelScope.launch { repo.setArchived(id, archived) }
    fun delete(id: String) = viewModelScope.launch { repo.deleteVehicle(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageScreen(onAddVehicle: () -> Unit, onOpenVehicle: (String) -> Unit, onEditVehicle: (String) -> Unit) {
    val context = LocalContext.current
    val vm: GarageViewModel = viewModel { GarageViewModel(context.app().repository) }
    val list by vm.vehicles.collectAsStateWithLifecycle()
    val showArchived by vm.showArchived.collectAsStateWithLifecycle()
    var topMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<VehicleSummary?>(null) }
    val actions = LocalAppActions.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Garage") },
                actions = {
                    Box {
                        IconButton(onClick = { topMenu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = topMenu, onDismissRequest = { topMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Show archived") },
                                leadingIcon = { Checkbox(checked = showArchived, onCheckedChange = null) },
                                onClick = { topMenu = false; vm.showArchived.value = !showArchived },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            // Free-tier gate for a 2nd vehicle lives in onAddVehicle (AppActions.requireVehicleSlot).
            if (!list.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAddVehicle,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add vehicle") },
                )
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val l = list
            when {
                l == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                l.isEmpty() -> EmptyGarage(showArchived, onAddVehicle)
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(l, key = { it.vehicle.id }) { s ->
                        VehicleCard(
                            s,
                            onOpen = { onOpenVehicle(s.vehicle.id) },
                            onEdit = { onEditVehicle(s.vehicle.id) },
                            onArchive = {
                                // Bringing one back counts as adding an active vehicle.
                                if (s.vehicle.archived) actions.requireVehicleSlot { vm.setArchived(s.vehicle.id, false) }
                                else vm.setArchived(s.vehicle.id, true)
                            },
                            onDelete = { confirmDelete = s },
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { s ->
        DeleteVehicleDialog(
            s.vehicle, s.taskCount,
            onConfirm = { confirmDelete = null; vm.delete(s.vehicle.id) },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun EmptyGarage(showArchived: Boolean, onAddVehicle: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (showArchived) "No vehicles yet." else "Your garage is empty.",
            style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.heightIn(min = 24.dp))
        PrimaryButton("Add your first vehicle", onAddVehicle, Modifier.fillMaxWidth())
    }
}

/** Tap opens; long-press (or the ⋮ button, for discoverability) opens edit / archive / delete. */
@Composable
private fun VehicleCard(
    s: VehicleSummary,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val v = s.vehicle
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .combinedClickable(onClick = onOpen, onLongClick = { menu = true }, onLongClickLabel = "Vehicle actions"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            VehicleImage(v.photoUri, v.vehicleType, 64.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(v.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val ymm = listOfNotNull(v.year?.toString(), v.make, v.model).joinToString(" ")
                Text(
                    ymm.ifEmpty { v.vehicleType.label } + if (v.archived) " · Archived" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                // Worst open status wins.
                when {
                    v.archived -> Unit
                    s.overdueCount > 0 -> StatusPill(TaskStatus.OVERDUE, count = s.overdueCount)
                    s.dueSoonCount > 0 -> StatusPill(TaskStatus.DUE_SOON, count = s.dueSoonCount)
                    s.taskCount > 0 -> StatusPill(TaskStatus.OK)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Actions for ${v.name}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(
                        text = { Text(if (v.archived) "Unarchive" else "Archive (sold / gone)") },
                        onClick = { menu = false; onArchive() },
                    )
                    DropdownMenuItem(text = { Text("Delete…") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
