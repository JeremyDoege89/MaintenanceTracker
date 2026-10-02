package dev.maintracker.ui.vehicle

import dev.maintracker.ui.common.toMeterInput
import dev.maintracker.ui.common.toStoredMeter

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.maintracker.data.PhotoStore
import dev.maintracker.data.VehicleRepository
import dev.maintracker.data.model.MaintenanceTemplate
import dev.maintracker.data.model.MeterUnit
import dev.maintracker.data.model.Vehicle
import dev.maintracker.data.model.VehicleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Year

enum class EditStep { TYPE, DETAILS, SCHEDULE }

data class VehicleForm(
    val name: String = "",
    val year: String = "",
    val make: String = "",
    val model: String = "",
    val vinOrHin: String = "",
    val purchaseDate: Long? = null,
    val purchaseMeter: String = "",
    val miles: String = "",
    val engineHours: String = "",
    val generatorHours: String = "",
    val notes: String = "",
    val photoPath: String? = null,
)

data class EditState(
    val isNew: Boolean,
    val loading: Boolean = false,
    val step: EditStep = EditStep.TYPE,
    val type: VehicleType? = null,
    val form: VehicleForm = VehicleForm(),
    val errors: Map<String, String> = emptyMap(),
    val templates: List<MaintenanceTemplate> = emptyList(),
    val selected: Set<String> = emptySet(),
    val saving: Boolean = false,
    /** Set once saved; the screen navigates on it. */
    val doneVehicleId: String? = null,
)

class VehicleEditViewModel(
    private val repo: VehicleRepository,
    private val photos: PhotoStore,
    private val vehicleId: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(EditState(isNew = vehicleId == null, loading = vehicleId != null))
    val state: StateFlow<EditState> = _state.asStateFlow()

    private var original: Vehicle? = null
    /** Photos imported this session; any not kept are deleted on exit. */
    private val importedPhotos = mutableSetOf<String>()

    init {
        if (vehicleId != null) viewModelScope.launch {
            val v = repo.getVehicle(vehicleId) ?: return@launch
            original = v
            _state.update {
                it.copy(loading = false, step = EditStep.DETAILS, type = v.vehicleType, form = v.toForm())
            }
        }
    }

    fun pickType(type: VehicleType) = _state.update { it.copy(type = type, step = EditStep.DETAILS) }

    fun back(): Boolean {
        val s = _state.value
        return when {
            s.step == EditStep.SCHEDULE -> { _state.update { it.copy(step = EditStep.DETAILS) }; true }
            s.step == EditStep.DETAILS && s.isNew -> { _state.update { it.copy(step = EditStep.TYPE) }; true }
            else -> false
        }
    }

    fun edit(transform: VehicleForm.() -> VehicleForm) =
        _state.update { it.copy(form = it.form.transform(), errors = emptyMap()) }

    fun setPhoto(uri: Uri) = viewModelScope.launch {
        val path = withContext(Dispatchers.IO) { photos.importPhoto(uri) } ?: return@launch
        importedPhotos += path
        edit { copy(photoPath = path) }
    }

    fun removePhoto() = edit { copy(photoPath = null) }

    /** Details step "Next"/"Save". New vehicles go on to the schedule summary. */
    fun submitDetails() {
        val s = _state.value
        val type = s.type ?: return
        val errors = validate(s.form, type)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        if (s.isNew) {
            viewModelScope.launch {
                val templates = repo.templatesFor(type)
                _state.update {
                    it.copy(step = EditStep.SCHEDULE, templates = templates, selected = templates.map { t -> t.id }.toSet())
                }
            }
        } else {
            save()
        }
    }

    fun toggleTemplate(id: String) = _state.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    fun setAllTemplates(on: Boolean) = _state.update {
        it.copy(selected = if (on) it.templates.map { t -> t.id }.toSet() else emptySet())
    }

    fun save() {
        val s = _state.value
        val type = s.type ?: return
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val f = s.form
            val draft = (original ?: Vehicle(name = "", vehicleType = type, createdAt = 0, updatedAt = 0)).copy(
                name = f.name.trim(),
                year = f.year.trim().toIntOrNull(),
                make = f.make.trim().ifEmpty { null },
                model = f.model.trim().ifEmpty { null },
                vinOrHin = f.vinOrHin.trim().uppercase().ifEmpty { null },
                purchaseDate = f.purchaseDate,
                purchaseMeter = f.purchaseMeter.toStoredMeter(type.primaryMeter),
                currentMiles = f.miles.toStoredMeter(MeterUnit.MILES),
                currentEngineHours = f.engineHours.toMeter(),
                currentGeneratorHours = f.generatorHours.toMeter(),
                notes = f.notes.trim().ifEmpty { null },
                photoUri = f.photoPath,
            )
            val id = if (original == null) {
                repo.createVehicle(draft, s.selected).id
            } else {
                repo.updateVehicle(draft)
                draft.id
            }
            importedPhotos -= f.photoPath.orEmpty()
            _state.update { it.copy(saving = false, doneVehicleId = id) }
        }
    }

    override fun onCleared() {
        // Discard photos picked but never saved.
        importedPhotos.forEach(photos::delete)
    }

    companion object {
        fun validate(f: VehicleForm, type: VehicleType): Map<String, String> = buildMap {
            if (f.name.isBlank()) put("name", "Give it a name, like 'Big Blue'")
            if (f.year.isNotBlank()) {
                val y = f.year.trim().toIntOrNull()
                if (y == null || y < 1900 || y > Year.now().value + 2) put("year", "Enter a 4-digit year")
            }
            val meters = buildList {
                add("purchaseMeter" to f.purchaseMeter)
                if (MeterUnit.MILES in type.meters) add("miles" to f.miles)
                if (MeterUnit.ENGINE_HOURS in type.meters) add("engineHours" to f.engineHours)
                if (MeterUnit.GENERATOR_HOURS in type.meters) add("generatorHours" to f.generatorHours)
            }
            for ((key, text) in meters) {
                if (text.isNotBlank() && (text.toMeter() == null || text.toMeter()!! < 0)) put(key, "Numbers only")
            }
        }

        private fun String.toMeter(): Double? = trim().replace(",", "").takeIf { it.isNotEmpty() }?.toDoubleOrNull()

        private fun Double?.text(): String = this?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: ""

        private fun Vehicle.toForm() = VehicleForm(
            name = name,
            year = year?.toString() ?: "",
            make = make ?: "",
            model = model ?: "",
            vinOrHin = vinOrHin ?: "",
            purchaseDate = purchaseDate,
            purchaseMeter = purchaseMeter.toMeterInput(vehicleType.primaryMeter),
            miles = currentMiles.toMeterInput(MeterUnit.MILES),
            engineHours = currentEngineHours.text(),
            generatorHours = currentGeneratorHours.text(),
            notes = notes ?: "",
            photoPath = photoUri,
        )
    }
}
