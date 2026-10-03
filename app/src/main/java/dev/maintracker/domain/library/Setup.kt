package dev.maintracker.domain.library

import dev.maintracker.data.model.MaintenanceTemplate
import dev.maintracker.data.model.QuestionType
import dev.maintracker.data.model.TemplateKind
import dev.maintracker.data.model.Vehicle
import dev.maintracker.data.model.VehicleType
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer

/** Answers as the UI holds them: questionId -> chosen optionIds. */
typealias SetupAnswers = Map<String, Set<String>>

/** assets/library/setup_questions_v1.json (IMPLEMENTATION.md §4.3). */
@Serializable
data class SetupFile(
    val schemaVersion: Int,
    val dataVersion: Int,
    val derivedTags: List<DerivedTag> = emptyList(),
    val makeHints: List<MakeHint> = emptyList(),
    val commonMakes: Map<VehicleType, List<String>> = emptyMap(),
    val questions: List<SetupQuestion>,
) {
    companion object {
        const val ASSET = "library/setup_questions_v1.json"

        fun decode(text: String): SetupFile = LibraryJson.decodeFromString(serializer(), text)
    }
}

@Serializable
data class SetupQuestion(
    val id: String,
    val vehicleTypes: List<VehicleType>,
    val phase: Int,
    val prompt: String,
    val help: String? = null,
    val type: QuestionType,
    val required: Boolean = false,
    val showIf: ShowIf? = null,
    val options: List<SetupOption>,
)

@Serializable
data class SetupOption(
    val id: String,
    val label: String,
    val tags: List<String> = emptyList(),
    val maybeTags: List<String> = emptyList(),
    val showIf: ShowIf? = null,
    /** Null = every type the question is asked for. */
    val vehicleTypes: List<VehicleType>? = null,
)

@Serializable
data class ShowIf(val questionId: String, val anyOptionOf: List<String>) {
    fun isMet(effective: SetupAnswers): Boolean = effective[questionId].orEmpty().any { it in anyOptionOf }
}

@Serializable
data class DerivedTag(val tag: String, val allOf: List<String>)

@Serializable
data class MakeHint(val vehicleType: VehicleType, val makePattern: String, val questionId: String, val optionId: String)

/** A question as shown right now: only its currently visible options. */
data class VisibleQuestion(val question: SetupQuestion, val options: List<SetupOption>)

data class SetupResult(
    val confirmed: Set<String>,
    val maybe: Set<String>,
    /** Answers that still count: hidden questions and options are dropped. */
    val effectiveAnswers: SetupAnswers,
)

/** The §4.3 algorithm, exactly; the golden fixtures pin it. Pure. */
object SetupEvaluator {

    fun visibleQuestions(file: SetupFile, type: VehicleType, answers: SetupAnswers): List<VisibleQuestion> =
        walk(file, type, answers).map { it.first }

    fun evaluate(file: SetupFile, type: VehicleType, answers: SetupAnswers): SetupResult {
        val confirmed = HashSet<String>()
        val maybe = HashSet<String>()
        val effective = LinkedHashMap<String, Set<String>>()
        for ((vq, chosen) in walk(file, type, answers)) {
            if (chosen.isNotEmpty()) {
                effective[vq.question.id] = chosen.mapTo(LinkedHashSet()) { it.id }
                chosen.forEach { confirmed += it.tags; maybe += it.maybeTags }
            } else if (vq.question.type == QuestionType.SINGLE) {
                // Unanswered SINGLE = "Not sure". An unanswered MULTI means "none of these".
                vq.options.forEach { maybe += it.tags; maybe += it.maybeTags }
            }
        }
        for (rule in file.derivedTags) {
            if (confirmed.containsAll(rule.allOf)) confirmed += rule.tag
            else if (rule.allOf.all { it in confirmed || it in maybe }) maybe += rule.tag
        }
        maybe -= confirmed
        return SetupResult(confirmed, maybe, effective)
    }

    /** Questions in file order with what was chosen; visibility depends only on earlier answers. */
    private fun walk(file: SetupFile, type: VehicleType, answers: SetupAnswers): List<Pair<VisibleQuestion, List<SetupOption>>> {
        val effective = HashMap<String, Set<String>>()
        val out = ArrayList<Pair<VisibleQuestion, List<SetupOption>>>()
        for (q in file.questions) {
            if (type !in q.vehicleTypes || q.phase > 1) continue
            if (q.showIf != null && !q.showIf.isMet(effective)) continue
            val opts = q.options.filter { o ->
                (o.vehicleTypes == null || type in o.vehicleTypes) && (o.showIf == null || o.showIf.isMet(effective))
            }
            val picked = answers[q.id].orEmpty()
            val chosen = opts.filter { it.id in picked }
            if (chosen.isNotEmpty()) effective[q.id] = chosen.mapTo(HashSet()) { it.id }
            out += VisibleQuestion(q, opts) to chosen
        }
        return out
    }

    /** Required questions that are visible but have no visible option chosen. */
    fun missingRequired(file: SetupFile, type: VehicleType, answers: SetupAnswers): List<SetupQuestion> =
        walk(file, type, answers).filter { (vq, chosen) -> vq.question.required && chosen.isEmpty() }.map { it.first.question }

    /**
     * Labels of the visible options that would make a "Might apply" template apply, e.g.
     * "Diesel pusher (Freightliner, Spartan, etc.)". Options that only carry it as a maybe tag
     * ("Not sure") are skipped.
     */
    fun wouldApplyIf(file: SetupFile, type: VehicleType, answers: SetupAnswers, template: MaintenanceTemplate): List<String> {
        // A derived tag (saltwater_launch) applies via the options that feed it.
        val tags = template.appliesToTags + file.derivedTags.filter { it.tag in template.appliesToTags }.flatMap { it.allOf }
        return visibleQuestions(file, type, answers)
            .flatMap { vq -> vq.options.filter { o -> o.tags.any { it in tags } }.map { it.label } }
            .distinct()
    }

    // ---- Persisted form (Vehicle.setupAnswers) ----

    private val answersSerializer = MapSerializer(String.serializer(), SetSerializer(String.serializer()))

    fun encodeAnswers(answers: SetupAnswers): String =
        LibraryJson.encodeToString(answersSerializer, answers.filterValues { it.isNotEmpty() })

    fun decodeAnswers(json: String?): SetupAnswers =
        json?.let { runCatching { LibraryJson.decodeFromString(answersSerializer, it) }.getOrNull() } ?: emptyMap()
}

data class Match(val recommended: List<MaintenanceTemplate>, val mightApply: List<MaintenanceTemplate>)

object LibraryMatcher {
    /** Recommended = "all" or a confirmed tag; Might apply = otherwise a maybe tag. Contradicted = dropped. */
    fun match(templates: List<MaintenanceTemplate>, type: VehicleType, result: SetupResult): Match {
        val pool = templates.filter { it.vehicleType == type && it.phase == 1 && !it.retired }
        val recommended = pool.filter { t ->
            val tags = t.appliesToTags
            "all" in tags || tags.any { it in result.confirmed }
        }
        val recIds = recommended.mapTo(HashSet()) { it.id }
        val might = pool.filter { t -> t.id !in recIds && t.appliesToTags.any { it in result.maybe } }
        return Match(recommended, might)
    }
}

/** ONE_TIME (break-in / first service) only while the vehicle's meter is known and below the interval. */
fun MaintenanceTemplate.isOfferable(vehicle: Vehicle): Boolean {
    if (kind != TemplateKind.ONE_TIME) return true
    val unit = meterUnit ?: return false
    val interval = meterInterval ?: return false
    val reading = vehicle.meter(unit) ?: return false
    return reading < interval
}

object MakeHints {
    /** Pre-selects a hinted option only where its question has no answer yet. The make is a hint, never a filter. */
    fun prefill(file: SetupFile, type: VehicleType, make: String?, answers: SetupAnswers): SetupAnswers {
        val m = make?.trim().orEmpty()
        if (m.isEmpty()) return answers
        val out = answers.toMutableMap()
        for (h in file.makeHints) {
            if (h.vehicleType != type || out[h.questionId].orEmpty().isNotEmpty()) continue
            if (Regex(h.makePattern).containsMatchIn(m)) out[h.questionId] = setOf(h.optionId)
        }
        return out
    }

    /**
     * [prefill] again after the make may have changed: answers in [hinted] (set by an earlier hint,
     * never tapped) are re-derived, so Honda -> Harley flips "No" back. Returns the answers and the
     * question ids that now hold a hinted answer.
     */
    fun refresh(
        file: SetupFile, type: VehicleType, make: String?, answers: SetupAnswers, hinted: Set<String>,
    ): Pair<SetupAnswers, Set<String>> {
        val base = answers - hinted
        val out = prefill(file, type, make, base)
        return out to out.keys.filter { base[it].orEmpty().isEmpty() && out[it].orEmpty().isNotEmpty() }.toSet()
    }
}
