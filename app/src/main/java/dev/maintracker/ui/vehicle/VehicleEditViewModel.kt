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
import dev.maintracker.data.model.QuestionType
import dev.maintracker.domain.library.LibraryMatcher
import dev.maintracker.data.vin.VinDecoder
import dev.maintracker.domain.VinFill
import dev.maintracker.domain.VinValidator
import dev.maintracker.domain.library.MakeHints
import dev.maintracker.domain.library.requiresMake
import dev.maintracker.domain.library.SetupAnswers
import dev.maintracker.domain.library.SetupEvaluator
import dev.maintracker.domain.library.SetupFile
import dev.maintracker.domain.library.SetupResult
import dev.maintracker.domain.library.isOfferable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Year

enum class EditStep { TYPE, DETAILS, SETUP, SCHEDULE }

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
    /** Recommended + Might apply, as offered on the Schedule step. */
    val templates: List<MaintenanceTemplate> = emptyList(),
    val selected: Set<String> = emptySet(),
    val saving: Boolean = false,
    /** Setup questions; loaded on first entry to the Setup step. */
    val setup: SetupFile? = null,
    val answers: SetupAnswers = emptyMap(),
    /** Questions whose answer came from a make hint and not a tap; they follow the make if it changes. */
    val hintedAnswers: Set<String> = emptySet(),
    /** Hinted questions the user asked to see again this visit to Setup; the rest stay folded into a one-line note. */
    val revealedHints: Set<String> = emptySet(),
    /** Last evaluated answers; null until Setup's Next (or for an edit that never opened Setup). */
    val setupResult: SetupResult? = null,
    val recommendedIds: Set<String> = emptySet(),
    val mightApplyIds: Set<String> = emptySet(),
    /** EXPIRY template id -> made / installed / renewed date (local-zone millis). */
    val expiryDates: Map<String, Long> = emptyMap(),
    val disclaimer: String = "",
    /** Set once saved; the screen navigates on it. */
    val doneVehicleId: String? = null,
    val vin: VinUi = VinUi(),
)

/** The VIN field's Decode button. Survives rotation with the ViewModel. */
data class VinUi(
    val decoding: Boolean = false,
    /** Inline under the field: bad format, offline, timeout, no decode. */
    val error: String? = null,
    /** Inline under the field; the decode still runs. */
    val warning: String? = null,
    /** Motorhomes: the decoded chassis, shown read-only (the VIN isn't the coach's). */
    val chassis: String? = null,
    /** One snackbar, consumed by the screen. */
    val message: String? = null,
)

class VehicleEditViewModel(
    private val repo: VehicleRepository,
    private val photos: PhotoStore,
    private val vehicleId: String?,
    private val vinDecoder: VinDecoder,
) : ViewModel() {

    private val _state = MutableStateFlow(EditState(isNew = vehicleId == null, loading = vehicleId != null))
    val state: StateFlow<EditState> = _state.asStateFlow()

    private var original: Vehicle? = null
    /** Photos imported this session; any not kept are deleted on exit. */
    private val importedPhotos = mutableSetOf<String>()

    init {
        // Setup questions also feed the make suggestions on Details.
        viewModelScope.launch {
            val file = repo.setupQuestions()
            _state.update { it.copy(setup = it.setup ?: file) }
        }
        if (vehicleId != null) viewModelScope.launch {
            val v = repo.getVehicle(vehicleId) ?: return@launch
            original = v
            _state.update {
                it.copy(
                    loading = false, step = EditStep.DETAILS, type = v.vehicleType, form = v.toForm(),
                    answers = SetupEvaluator.decodeAnswers(v.setupAnswers),
                )
            }
        }
    }

    fun pickType(type: VehicleType) = _state.update { it.copy(type = type, step = EditStep.DETAILS) }

    fun back(): Boolean {
        val s = _state.value
        return when {
            s.step == EditStep.SCHEDULE -> { _state.update { it.copy(step = EditStep.SETUP) }; true }
            s.step == EditStep.SETUP -> { _state.update { it.copy(step = EditStep.DETAILS) }; true }
            s.step == EditStep.DETAILS && s.isNew -> { _state.update { it.copy(step = EditStep.TYPE) }; true }
            else -> false
        }
    }

    fun edit(transform: VehicleForm.() -> VehicleForm) = _state.update {
        val form = it.form.transform()
        // A different VIN invalidates what was said about the old one.
        val vin = if (form.vinOrHin == it.form.vinOrHin) it.vin else it.vin.copy(error = null, warning = null, chassis = null)
        it.copy(form = form, errors = emptyMap(), vin = vin)
    }

    /**
     * Decode button: validate, then cache / NHTSA vPIC, then fill blank fields only. Taps while a
     * decode is running are ignored. Saving never depends on any of this.
     */
    fun decodeVin() {
        val s = _state.value
        val type = s.type ?: return
        if (s.vin.decoding || !type.decodesVin) return
        val checked = VinValidator.validate(s.form.vinOrHin)
        if (checked is VinValidator.Result.BadFormat) {
            _state.update { it.copy(vin = VinUi(error = checked.reason)) }
            return
        }
        val warning = if (checked is VinValidator.Result.BadCheckDigit) "VIN looks mistyped — double-check it" else null
        _state.update { it.copy(vin = VinUi(decoding = true, warning = warning)) }
        val typedYear = s.form.year.trim().takeIf { it.length == 4 }?.toIntOrNull()
        viewModelScope.launch {
            val outcome = vinDecoder.decode(checked.vin, typedYear)
            _state.update { cur -> cur.copy(vin = cur.vin.copy(decoding = false)).applyVin(type, outcome) }
        }
    }

    fun vinMessageShown() = _state.update { it.copy(vin = it.vin.copy(message = null)) }

    private fun EditState.applyVin(type: VehicleType, outcome: VinDecoder.Outcome): EditState = when (outcome) {
        is VinDecoder.Outcome.Failed -> copy(vin = vin.copy(error = outcome.failure.message))
        is VinDecoder.Outcome.Decoded -> {
            val d = outcome.decode
            val fill = VinFill.fill(
                type, form.year, form.make, form.model, d.make, d.model, d.modelYear,
                knownMakes = setup?.commonMakes?.get(type).orEmpty(),
            )
            val message = when {
                fill.chassis != null -> "Chassis decoded: ${fill.chassis}"
                fill.filled.isNotEmpty() -> "Filled: " + fill.filled.joinToString(" · ")
                else -> "Nothing to fill — your entries were kept."
            }
            copy(
                form = form.copy(
                    year = fill.year?.toString() ?: form.year,
                    make = fill.make ?: form.make,
                    model = fill.model ?: form.model,
                ),
                errors = errors - setOfNotNull("year".takeIf { fill.year != null }, "make".takeIf { fill.make != null }),
                vin = vin.copy(chassis = fill.chassis, message = message),
            )
        }
    }

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
        if (s.isNew) openSetup() else save()
    }

    /** Details -> Setup. Make hints pre-fill questions that are unanswered or were only hinted. */
    fun openSetup() {
        val s = _state.value
        val type = s.type ?: return
        if (!s.isNew && validate(s.form, type).isNotEmpty()) return submitDetails()
        viewModelScope.launch {
            val file = s.setup ?: repo.setupQuestions()
            _state.update {
                // A saved vehicle's answers that match its make count as hinted, so they fold here too.
                val wasHinted = it.hintedAnswers + MakeHints.agreeing(file, type, it.form.make, it.answers)
                val (answers, hinted) = MakeHints.refresh(file, type, it.form.make, it.answers, wasHinted)
                it.copy(
                    setup = file, step = EditStep.SETUP, answers = answers, hintedAnswers = hinted, revealedHints = emptySet(),
                )
            }
        }
    }

    /** SINGLE: pick [optionId]. MULTI: toggle it. */
    fun answer(questionId: String, optionId: String) = _state.update { s ->
        val q = s.setup?.questions?.firstOrNull { it.id == questionId } ?: return@update s
        val current = s.answers[questionId].orEmpty()
        val next = when (q.type) {
            QuestionType.SINGLE -> setOf(optionId)
            QuestionType.MULTI -> if (optionId in current) current - optionId else current + optionId
        }
        s.copy(answers = s.answers + (questionId to next), hintedAnswers = s.hintedAnswers - questionId)
    }

    /** "Change" on a question the make answered: show it in full. */
    fun revealHint(questionId: String) = _state.update { it.copy(revealedHints = it.revealedHints + questionId) }

    /** Setup -> Schedule: evaluate, match, keep what can be offered (IMPLEMENTATION.md §5 step 5). */
    fun submitSetup() {
        val s = _state.value
        val type = s.type ?: return
        val file = s.setup ?: return
        if (SetupEvaluator.missingRequired(file, type, s.answers).isNotEmpty()) return
        viewModelScope.launch {
            val result = SetupEvaluator.evaluate(file, type, s.answers)
            val match = LibraryMatcher.match(repo.templatesFor(type), type, result)
            val vehicle = draft(s, type)
            // Re-running Setup on a saved vehicle only offers what it doesn't already have.
            val have = original?.let { v -> repo.tasksFor(v.id).mapNotNullTo(HashSet()) { it.templateId } }.orEmpty()
            val offer = { list: List<MaintenanceTemplate> -> list.filter { it.isOfferable(vehicle) && it.id !in have } }
            val recommended = offer(match.recommended)
            val might = offer(match.mightApply)
            val disclaimer = s.disclaimer.ifEmpty { repo.libraryDisclaimer() }
            _state.update {
                it.copy(
                    step = EditStep.SCHEDULE,
                    setupResult = result,
                    templates = recommended + might,
                    recommendedIds = recommended.mapTo(HashSet()) { t -> t.id },
                    mightApplyIds = might.mapTo(HashSet()) { t -> t.id },
                    selected = recommended.mapTo(HashSet()) { t -> t.id },
                    expiryDates = it.expiryDates.filterKeys { id -> recommended.any { t -> t.id == id } || might.any { t -> t.id == id } },
                    disclaimer = disclaimer,
                )
            }
        }
    }

    fun toggleTemplate(id: String) = _state.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    /** "All / None" covers the Recommended section only; Might apply stays opt-in per row. */
    fun setAllTemplates(on: Boolean) = _state.update {
        it.copy(selected = if (on) it.selected + it.recommendedIds else it.selected - it.recommendedIds)
    }

    fun setExpiryDate(templateId: String, date: Long?) = _state.update {
        it.copy(expiryDates = if (date == null) it.expiryDates - templateId else it.expiryDates + (templateId to date))
    }

    fun save() {
        val s = _state.value
        val type = s.type ?: return
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val f = s.form
            val draft = draft(s, type)
            // Dates only matter for EXPIRY rows the user kept.
            val expiry = s.expiryDates.filterKeys { it in s.selected }
            val id = if (original == null) {
                repo.createVehicle(draft, s.selected, expiry).id
            } else {
                repo.updateVehicle(draft, if (s.setupResult != null) s.selected else emptySet(), expiry)
                draft.id
            }
            importedPhotos -= f.photoPath.orEmpty()
            _state.update { it.copy(saving = false, doneVehicleId = id) }
        }
    }

    /** The vehicle as the form describes it; Setup's tags/answers once Setup has been evaluated. */
    private fun draft(s: EditState, type: VehicleType): Vehicle {
        val f = s.form
        val base = original ?: Vehicle(name = "", vehicleType = type, createdAt = 0, updatedAt = 0)
        return base.copy(
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
        ).let { v ->
            val r = s.setupResult ?: return@let v
            v.copy(
                configTags = r.confirmed.sorted().joinToString(";"),
                setupAnswers = SetupEvaluator.encodeAnswers(r.effectiveAnswers),
            )
        }
    }

    override fun onCleared() {
        // Discard photos picked but never saved.
        importedPhotos.forEach(photos::delete)
    }

    companion object {
        fun validate(f: VehicleForm, type: VehicleType): Map<String, String> = buildMap {
            if (f.name.isBlank()) put("name", "Give it a name, like 'Big Blue'")
            if (type.requiresMake && f.make.isBlank()) put("make", "Enter the make, like Honda or Harley-Davidson")
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

/** Boats and PWC carry a HIN, which NHTSA can't decode. */
val VehicleType.decodesVin: Boolean get() = idLabel == "VIN"

private val VinDecoder.Failure.message: String
    get() = when (this) {
        VinDecoder.Failure.OFFLINE -> "Connect to the internet to decode."
        VinDecoder.Failure.TIMEOUT -> "Decoder timed out — try again or enter details manually."
        VinDecoder.Failure.SERVER -> "NHTSA's decoder isn't responding — enter details manually."
        VinDecoder.Failure.EMPTY -> "Couldn't decode this VIN — enter details manually."
    }
