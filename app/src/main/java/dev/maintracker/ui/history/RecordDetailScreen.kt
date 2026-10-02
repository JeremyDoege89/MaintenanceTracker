package dev.maintracker.ui.history

import dev.maintracker.data.model.VendorType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import dev.maintracker.data.UndoSnapshot
import dev.maintracker.data.VehicleRepository
import dev.maintracker.data.model.MaintenanceTask
import dev.maintracker.data.model.ServiceRecord
import dev.maintracker.domain.Format
import dev.maintracker.ui.common.InfoCard
import dev.maintracker.ui.documents.RecordAttachments
import dev.maintracker.ui.common.KeyValue
import dev.maintracker.ui.common.LocalAppActions
import dev.maintracker.ui.common.ServiceSheetRequest
import dev.maintracker.ui.common.app
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecordState(val loaded: Boolean = false, val record: ServiceRecord? = null, val task: MaintenanceTask? = null)

class RecordDetailViewModel(private val repo: VehicleRepository, private val recordId: String) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<RecordState> = repo.observeRecord(recordId).flatMapLatest { r ->
        val taskId = r?.taskId
        if (taskId == null) flowOf(RecordState(true, r)) else repo.observeTask(taskId).map { RecordState(true, r, it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordState())

    fun delete(onDeleted: (UndoSnapshot?) -> Unit) = viewModelScope.launch { onDeleted(repo.deleteRecord(recordId)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(recordId: String, onBack: () -> Unit, onOpenTask: (String) -> Unit) {
    val context = LocalContext.current
    val actions = LocalAppActions.current
    val vm: RecordDetailViewModel = viewModel(key = "record-$recordId") { RecordDetailViewModel(context.app().repository, recordId) }
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val r = s.record

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Service record") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (r != null) {
                        IconButton(onClick = {
                            actions.openServiceSheet(ServiceSheetRequest(vehicleId = r.vehicleId, recordId = r.id))
                        }) { Icon(Icons.Filled.Edit, "Edit record") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete record") }
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                !s.loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                r == null -> Text("This record no longer exists.", Modifier.padding(24.dp))
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(r.title, style = MaterialTheme.typography.headlineSmall)
                    Text("${Format.date(r.date)} · ${r.area.label}", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    s.task?.let { t ->
                        Text(
                            "Completed task: ${t.title} ›",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onOpenTask(t.id) }.padding(vertical = 12.dp),
                        )
                    }
                    InfoCard("Details") {
                        meterSummary(r)?.let { KeyValue("Readings", it) }
                        r.cost?.let { KeyValue("Cost", Format.money(it, r.currency)) }
                        val doneBy = when {
                            r.vendorType == null || r.vendorType == VendorType.SELF -> r.vendor
                            r.vendor == null -> r.vendorType.label
                            else -> "${r.vendor} (${r.vendorType.label})"
                        }
                        doneBy?.let { KeyValue("Done by", it) }
                        if (meterSummary(r) == null && r.cost == null && doneBy == null) {
                            Text("No readings, cost or vendor entered.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    r.partsText?.let { InfoCard("Parts used") { Text(it) } }
                    r.description?.let { InfoCard("Work done") { Text(it) } }
                    r.notes?.let { InfoCard("Notes") { Text(it) } }
                    InfoCard("Photos & receipts") { RecordAttachments(r.vehicleId, r.id) }
                }
            }
        }
    }

    if (confirmDelete && r != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this record?") },
            text = {
                Text(
                    "“${r.title}” on ${Format.date(r.date)} is removed from history" +
                        (if (s.task != null) " and the task's last-done date falls back to the previous record" else "") +
                        ". Vehicle readings are not rolled back.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete { undo -> onBack(); actions.showMessage("Record deleted", undo) }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}
