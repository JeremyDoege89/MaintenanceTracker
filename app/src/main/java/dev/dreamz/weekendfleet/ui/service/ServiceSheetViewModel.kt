package dev.dreamz.weekendfleet.ui.service

import dev.dreamz.weekendfleet.data.model.VendorType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dreamz.weekendfleet.data.ServiceEntry
import dev.dreamz.weekendfleet.data.UndoSnapshot
import dev.dreamz.weekendfleet.data.VehicleRepository
import dev.dreamz.weekendfleet.data.model.Area
import dev.dreamz.weekendfleet.data.model.MaintenanceTask
import dev.dreamz.weekendfleet.data.model.MeterUnit
import dev.dreamz.weekendfleet.data.model.ServiceRecord
import dev.dreamz.weekendfleet.data.model.TaskStatus
import dev.dreamz.weekendfleet.data.model.Vehicle
import dev.dreamz.weekendfleet.domain.DueCalculator
import dev.dreamz.weekendfleet.domain.Format
import dev.dreamz.weekendfleet.domain.MeterReadings
import dev.dreamz.weekendfleet.ui.common.ServiceSheetRequest
import dev.dreamz.weekendfleet.ui.common.toInputText
import dev.dreamz.weekendfleet.ui.common.toMeterInput
import dev.dreamz.weekendfleet.ui.common.toStoredMeter
import dev.dreamz.weekendfleet.ui.common.toNumberOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class SheetForm(
    val title: String = "",
    val area: Area = Area.OTHER,
    val description: String = "",
    /** Local-zone millis; "today" is stored as now so same-day ordering is kept. */
    val date: Long = System.currentTimeMillis(),
    val miles: String = "",
    val engineHours: String = "",
    val generatorHours: String = "",
    val cost: String = "",
    val currency: String = Format.defaultCurrency(),
    /** The name for Shop / Dealer / Mobile; unused for Self. */
    val vendor: String = "",
    val vendorType: VendorType? = null,
    val partsText: String = "",
    val notes: String = "",
) {
    fun meterText(u: MeterUnit) = when (u) {
        MeterUnit.MILES -> miles
        MeterUnit.ENGINE_HOURS -> engineHours
        MeterUnit.GENERATOR_HOURS -> generatorHours
    }

    fun withMeter(u: MeterUnit, v: String) = when (u) {
        MeterUnit.MILES -> copy(miles = v)
        MeterUnit.ENGINE_HOURS -> copy(engineHours = v)
        MeterUnit.GENERATOR_HOURS -> copy(generatorHours = v)
    }

    /** What's stored: Self is recorded as the word "Self" so history and CSV read naturally. */
    fun doneBy(): Pair<String?, VendorType?> = when (vendorType) {
        null -> null to null
        VendorType.SELF -> "Self" to VendorType.SELF
        else -> vendor.trim().ifEmpty { null } to vendorType
    }

    fun readings() = MeterReadings(miles.toStoredMeter(MeterUnit.MILES), engineHours.toNumberOrNull(), generatorHours.toNumberOrNull())
}

data class SheetState(
    val loading: Boolean = true,
    val metersOnly: Boolean = false,
    val vehicles: List<Vehicle> = emptyList(),
    val vehicle: Vehicle? = null,
    val tasks: List<MaintenanceTask> = emptyList(),
    val task: MaintenanceTask? = null,
    val editing: ServiceRecord? = null,
    val form: SheetForm = SheetForm(),
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val done: Done? = null,
) {
    data class Done(val message: String, val undo: UndoSnapshot?)
}

class ServiceSheetViewModel(
    private val repo: VehicleRepository,
    private val request: ServiceSheetRequest,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

    private val _state = MutableStateFlow(SheetState(metersOnly = request.metersOnly))
    val state: StateFlow<SheetState> = _state.asStateFlow()

    val vendors: StateFlow<List<String>> = repo.observeRecentVendors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val editing = request.recordId?.let { repo.getRecord(it) }
            val all = repo.observeSummaries(includeArchived = false).first().map { it.vehicle }
            val vehicleId = editing?.vehicleId ?: request.vehicleId ?: all.singleOrNull()?.id
            _state.update { it.copy(vehicles = all, editing = editing) }
            vehicleId?.let { selectVehicleNow(it) }
            if (editing != null) {
                _state.update { s ->
                    s.copy(
                        task = s.tasks.firstOrNull { it.id == editing.taskId },
                        form = editing.toForm(),
                    )
                }
            } else {
                request.taskId?.let { id -> _state.value.tasks.firstOrNull { it.id == id }?.let(::selectTask) }
            }
            _state.update { it.copy(loading = false) }
        }
    }

    fun selectVehicle(id: String) = viewModelScope.launch { selectVehicleNow(id) }

    private suspend fun selectVehicleNow(id: String) {
        val v = repo.getVehicle(id) ?: return
        val tasks = repo.tasksFor(id).filter { it.isActive }.sortedWith(compareBy({ rank(it.status) }, { it.title }))
        _state.update {
            // New entries start from the readings on the vehicle's profile; the user overtypes if they differ.
            val form = if (it.editing != null) it.form else it.form.copy(
                miles = v.currentMiles.toMeterInput(MeterUnit.MILES),
                engineHours = v.currentEngineHours.toInputText(),
                generatorHours = v.currentGeneratorHours.toInputText(),
            )
            it.copy(vehicle = v, tasks = tasks, task = null, form = form, errors = emptyMap())
        }
    }

    /** Overdue first, then due soon — the tasks most likely being completed. */
    private fun rank(status: TaskStatus) = when (status) {
        TaskStatus.OVERDUE -> 0
        TaskStatus.DUE_SOON -> 1
        TaskStatus.OK -> 2
        TaskStatus.MANUAL -> 3
    }

    fun selectTask(t: MaintenanceTask?) = _state.update { s ->
        val f = if (t != null) s.form.copy(title = t.title, area = t.area) else s.form.copy(title = "", area = Area.OTHER)
        s.copy(task = t, form = f, errors = emptyMap())
    }

    fun setMetersOnly(on: Boolean) = _state.update { it.copy(metersOnly = on, errors = emptyMap()) }

    fun edit(transform: SheetForm.() -> SheetForm) =
        _state.update { it.copy(form = it.form.transform(), errors = emptyMap()) }

    /** Picked calendar day: today keeps the current time, other days are stored at local midnight. */
    fun setDate(day: LocalDate) {
        val z = zone()
        val ms = if (day == LocalDate.now(z)) System.currentTimeMillis() else day.atStartOfDay(z).toInstant().toEpochMilli()
        edit { copy(date = ms) }
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val v = s.vehicle
        val errors = validate(s)
        if (errors.isNotEmpty() || v == null) {
            _state.update { it.copy(errors = errors + (if (v == null) mapOf("vehicle" to "Pick a vehicle") else emptyMap())) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val f = s.form
            val done = when {
                s.metersOnly -> SheetState.Done("Readings updated", repo.logMeters(v.id, f.readings()))
                s.editing != null -> {
                    repo.updateRecord(
                        s.editing.copy(
                            area = f.area, title = f.title.trim(), description = f.description.clean(),
                            date = f.date, miles = f.readings().miles, engineHours = f.readings().engineHours,
                            generatorHours = f.readings().generatorHours, cost = f.cost.toNumberOrNull(),
                            currency = f.currency, vendor = f.doneBy().first, vendorType = f.doneBy().second,
                            partsText = f.partsText.clean(), notes = f.notes.clean(),
                        ),
                    )
                    SheetState.Done("Record updated", null)
                }
                else -> {
                    val undo = repo.logService(
                        ServiceEntry(
                            vehicleId = v.id, taskId = s.task?.id, area = f.area, title = f.title,
                            description = f.description.clean(), date = f.date, readings = f.readings(),
                            cost = f.cost.toNumberOrNull(), currency = f.currency, vendor = f.doneBy().first,
                            partsText = f.partsText.clean(), notes = f.notes.clean(), vendorType = f.doneBy().second,
                        ),
                    )
                    SheetState.Done("Logged: ${f.title.trim()}", undo)
                }
            }
            _state.update { it.copy(saving = false, done = done) }
        }
    }

    private fun validate(s: SheetState): Map<String, String> = buildMap {
        val f = s.form
        val v = s.vehicle
        val shown = v?.vehicleType?.meters.orEmpty()
        for (u in shown) {
            val text = f.meterText(u)
            val n = text.toNumberOrNull()
            if (text.isNotBlank() && (n == null || n < 0)) put(u.name, "Numbers only")
        }
        if (s.metersOnly) {
            val entered = shown.filter { f.meterText(it).toNumberOrNull() != null }
            if (entered.isEmpty()) put("meters", "Enter at least one reading")
            // A meters-only log can't lower a reading; corrections go through Edit vehicle.
            for (u in entered) {
                val cur = v?.meter(u)
                if (cur != null && f.meterText(u).toNumberOrNull()!! < cur) {
                    put(u.name, "Below current ${Format.meter(cur, u)} — readings only go up")
                }
            }
            return@buildMap
        }
        if (f.title.isBlank()) put("title", "What was done?")
        if (f.cost.isNotBlank() && (f.cost.toNumberOrNull() == null || f.cost.toNumberOrNull()!! < 0)) put("cost", "Numbers only")
        val today = LocalDate.now(zone())
        if (DueCalculator.localDate(f.date, zone()).isAfter(today)) put("date", "Can't be in the future")
    }

    private fun String.clean() = trim().ifEmpty { null }

    private fun ServiceRecord.toForm() = SheetForm(
        title = title, area = area, description = description.orEmpty(), date = date,
        miles = miles.toMeterInput(MeterUnit.MILES), engineHours = engineHours.toInputText(), generatorHours = generatorHours.toInputText(),
        cost = cost.toInputText(), currency = currency,
        // v1 records have no type: "Self" maps to Self, any other name to Shop.
        vendorType = vendorType ?: when {
            vendor.isNullOrBlank() -> null
            vendor.equals("self", ignoreCase = true) -> VendorType.SELF
            else -> VendorType.SHOP
        },
        vendor = vendor.takeUnless { it.equals("self", ignoreCase = true) }.orEmpty(), partsText = partsText.orEmpty(),
        notes = notes.orEmpty(),
    )
}
