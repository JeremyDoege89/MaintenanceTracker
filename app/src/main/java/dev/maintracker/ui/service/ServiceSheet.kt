package dev.maintracker.ui.service

import dev.maintracker.data.model.VendorType
import dev.maintracker.domain.label

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.maintracker.data.model.Area
import dev.maintracker.data.model.MaintenanceTask
import dev.maintracker.domain.Format
import dev.maintracker.ui.common.DateField
import dev.maintracker.ui.common.Dropdown
import dev.maintracker.ui.common.Field
import dev.maintracker.ui.common.LocalAppActions
import dev.maintracker.ui.common.MeterField
import dev.maintracker.ui.common.PrimaryButton
import dev.maintracker.ui.common.SectionLabel
import dev.maintracker.ui.common.ServiceSheetRequest
import dev.maintracker.ui.common.app
import dev.maintracker.ui.common.label

/**
 * Quick-add bottom sheet (Build Guide §5.5): log service, complete a task, or just update
 * readings. Date is the only required input for a record besides what was done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceSheet(request: ServiceSheetRequest, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val actions = LocalAppActions.current
    val vm: ServiceSheetViewModel = viewModel(key = "sheet-$request") {
        ServiceSheetViewModel(context.app().repository, request)
    }
    val s by vm.state.collectAsStateWithLifecycle()
    val vendors by vm.vendors.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(s.done) {
        val d = s.done ?: return@LaunchedEffect
        sheet.hide()
        onDismiss()
        actions.showMessage(d.message, d.undo)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        if (s.loading) {
            Box(Modifier.fillMaxWidth().padding(48.dp)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
            return@ModalBottomSheet
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                when {
                    s.editing != null -> "Edit record"
                    s.metersOnly -> "Update readings"
                    s.task != null && request.taskId != null -> "Mark complete"
                    else -> "Log service"
                },
                style = MaterialTheme.typography.titleLarge,
            )

            if (s.editing == null && request.taskId == null) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !s.metersOnly, onClick = { vm.setMetersOnly(false) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2), modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Service") }
                    SegmentedButton(
                        selected = s.metersOnly, onClick = { vm.setMetersOnly(true) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2), modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Readings only") }
                }
            }

            if (s.vehicles.size > 1 && s.editing == null && request.vehicleId == null) {
                Dropdown(
                    "Vehicle", listOf(null) + s.vehicles, s.vehicle,
                    optionLabel = { it?.name ?: "Choose…" },
                    onSelect = { v -> v?.let { vm.selectVehicle(it.id) } },
                )
                s.errors["vehicle"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } else if (s.vehicle != null && request.vehicleId == null) {
                Text(s.vehicle!!.name, style = MaterialTheme.typography.titleMedium)
            }

            val v = s.vehicle
            if (v == null) {
                if (s.vehicles.isEmpty()) Text("Add a vehicle first — then log its service here.")
                return@Column
            }
            val f = s.form

            if (!s.metersOnly) {
                if (s.editing == null) {
                    Dropdown(
                        "Scheduled task", listOf<MaintenanceTask?>(null) + s.tasks, s.task,
                        optionLabel = { t -> t?.let { "${it.title} · ${it.status.label}" } ?: "None — unscheduled work" },
                        onSelect = vm::selectTask,
                        enabled = request.taskId == null,
                    )
                } else if (s.task != null) {
                    Text("Completes: ${s.task!!.title}", style = MaterialTheme.typography.bodyMedium)
                }
                Field("What was done *", f.title, s.errors["title"], { vm.edit { copy(title = it) } },
                    caps = KeyboardCapitalization.Sentences, placeholder = "Replaced water pump")
                if (s.task == null) {
                    Dropdown("Area", Area.entries, f.area, { it.label }, { a -> vm.edit { copy(area = a) } })
                }
                DateField("Date *", f.date, { day -> day?.let(vm::setDate) }, error = s.errors["date"])
            }

            SectionLabel(if (s.metersOnly) "Current readings" else "Readings at time of service")
            s.errors["meters"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            for (u in v.vehicleType.meters) {
                MeterField(
                    u.label, f.meterText(u), s.errors[u.name],
                    supporting = v.meter(u)?.let { "From the vehicle profile (${Format.meter(it, u)}). Change it if different." },
                ) { vm.edit { withMeter(u, it) } }
            }

            if (!s.metersOnly) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MeterField("Cost", f.cost, s.errors["cost"], modifier = Modifier.weight(1f)) { vm.edit { copy(cost = it) } }
                    Field("Currency", f.currency, null,
                        { c -> vm.edit { copy(currency = c.uppercase().filter(Char::isLetter).take(3)) } },
                        caps = KeyboardCapitalization.Characters, modifier = Modifier.width(110.dp))
                }
                DoneBy(f.vendorType, f.vendor, vendors.filterNot { it.equals("self", true) },
                    onType = { t -> vm.edit { copy(vendorType = if (vendorType == t) null else t) } },
                    onName = { n -> vm.edit { copy(vendor = n) } })
                Field("Parts used", f.partsText, null, { vm.edit { copy(partsText = it) } },
                    caps = KeyboardCapitalization.Sentences, placeholder = "Wix 51515 filter, 5 qt 15W-40")
                Field("Details", f.description, null, { vm.edit { copy(description = it) } },
                    caps = KeyboardCapitalization.Sentences, singleLine = false)
                Field("Notes", f.notes, null, { vm.edit { copy(notes = it) } },
                    caps = KeyboardCapitalization.Sentences, singleLine = false, keyboard = KeyboardType.Text)
            }

            PrimaryButton(
                if (s.metersOnly) "Save readings" else "Save",
                onClick = vm::save,
                enabled = !s.saving,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).navigationBarsPadding(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
/** "Done by": four tabs; Shop / Dealer / Mobile open a name box with recent names one tap away. */
@Composable
private fun DoneBy(type: VendorType?, name: String, recent: List<String>, onType: (VendorType) -> Unit, onName: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Done by", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            VendorType.entries.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = type == t, onClick = { onType(t) },
                    shape = SegmentedButtonDefaults.itemShape(i, VendorType.entries.size),
                    modifier = Modifier.heightIn(min = 48.dp),
                    label = { Text(t.label, maxLines = 1) },
                )
            }
        }
        type?.nameLabel?.let { label ->
            Field(label, name, null, onName, caps = KeyboardCapitalization.Words)
            if (recent.isNotEmpty()) VendorChips(recent, onName)
        }
    }
}

@Composable
private fun VendorChips(vendors: List<String>, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        vendors.forEach { name ->
            SuggestionChip(onClick = { onPick(name) }, label = { Text(name) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}
