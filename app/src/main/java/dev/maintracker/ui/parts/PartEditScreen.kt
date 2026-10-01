package dev.maintracker.ui.parts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.maintracker.data.VehicleRepository
import dev.maintracker.data.model.MaintenanceTask
import dev.maintracker.data.model.Part
import dev.maintracker.ui.common.Dropdown
import dev.maintracker.ui.common.Field
import dev.maintracker.ui.common.PrimaryButton
import dev.maintracker.ui.common.app
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PartEditState(
    val loading: Boolean = true,
    val existing: Part? = null,
    val tasks: List<MaintenanceTask> = emptyList(),
    val part: Part,
    val nameError: String? = null,
    val done: Boolean = false,
)

class PartEditViewModel(
    private val repo: VehicleRepository,
    vehicleId: String,
    partId: String?,
    taskId: String?,
) : ViewModel() {
    private val _state = MutableStateFlow(
        PartEditState(part = Part(vehicleId = vehicleId, taskId = taskId, name = "", createdAt = System.currentTimeMillis())),
    )
    val state: StateFlow<PartEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = partId?.let { repo.getPart(it) }
            val tasks = repo.tasksFor(vehicleId).sortedBy { it.title }
            _state.update { it.copy(loading = false, existing = existing, tasks = tasks, part = existing ?: it.part) }
        }
    }

    fun edit(transform: Part.() -> Part) = _state.update { it.copy(part = it.part.transform(), nameError = null) }

    fun save() {
        val p = _state.value.part
        if (p.name.isBlank()) { _state.update { it.copy(nameError = "Name the part") }; return }
        fun String?.clean() = this?.trim()?.ifEmpty { null }
        viewModelScope.launch {
            repo.savePart(
                p.copy(
                    name = p.name.trim(), partNumber = p.partNumber.clean(), manufacturer = p.manufacturer.clean(),
                    torqueSpec = p.torqueSpec.clean(), fluidSpec = p.fluidSpec.clean(), quantity = p.quantity.clean(),
                    notes = p.notes.clean(),
                ),
            )
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() = viewModelScope.launch {
        repo.deletePart(_state.value.part.id)
        _state.update { it.copy(done = true) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartEditScreen(vehicleId: String, partId: String?, taskId: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val vm: PartEditViewModel = viewModel(key = "part-$vehicleId-$partId-$taskId") {
        PartEditViewModel(context.app().repository, vehicleId, partId, taskId)
    }
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(s.done) { if (s.done) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (partId == null) "Add part" else "Edit part") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (s.existing != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete part") }
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (s.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
                return@Box
            }
            val p = s.part
            val words = KeyboardCapitalization.Words
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Field("Part *", p.name, s.nameError, { vm.edit { copy(name = it) } }, caps = KeyboardCapitalization.Sentences,
                    placeholder = "Engine oil filter")
                Field("Manufacturer", p.manufacturer.orEmpty(), null, { vm.edit { copy(manufacturer = it) } }, caps = words,
                    placeholder = "Wix")
                Field("Part number", p.partNumber.orEmpty(), null, { vm.edit { copy(partNumber = it) } },
                    caps = KeyboardCapitalization.Characters, placeholder = "51515")
                Field("Torque spec", p.torqueSpec.orEmpty(), null, { vm.edit { copy(torqueSpec = it) } },
                    placeholder = "18 ft-lb")
                Field("Fluid / capacity", p.fluidSpec.orEmpty(), null, { vm.edit { copy(fluidSpec = it) } },
                    placeholder = "Rotella T6 5W-40, 3.6 qt")
                Field("Quantity", p.quantity.orEmpty(), null, { vm.edit { copy(quantity = it) } }, placeholder = "2")
                Dropdown(
                    "Used for task", listOf<MaintenanceTask?>(null) + s.tasks, s.tasks.firstOrNull { it.id == p.taskId },
                    optionLabel = { it?.title ?: "None — general part" },
                    onSelect = { t -> vm.edit { copy(taskId = t?.id) } },
                )
                Field("Notes", p.notes.orEmpty(), null, { vm.edit { copy(notes = it) } },
                    caps = KeyboardCapitalization.Sentences, singleLine = false, supporting = "Cross-references, source links")
                PrimaryButton("Save", vm::save, Modifier.fillMaxWidth().navigationBarsPadding())
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${s.part.name}?") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}
