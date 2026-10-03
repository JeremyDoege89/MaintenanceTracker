package dev.maintracker.domain.library

import dev.maintracker.data.model.TemplateKind
import dev.maintracker.data.model.Vehicle
import dev.maintracker.data.model.VehicleType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** IMPLEMENTATION.md §6 test 2: the golden scenarios in matching_fixtures_v1.json, exactly. */
class MatchingFixturesTest {
    @Serializable
    data class Fixtures(val scenarios: List<Scenario>)

    @Serializable
    data class Scenario(
        val name: String,
        val vehicleType: VehicleType,
        val answers: Map<String, List<String>>,
        val expectedConfirmedTags: List<String>,
        val expectedMaybeTags: List<String>,
        val expectedRecommendedIds: List<String>,
        val expectedMightApplyIds: List<String>,
    )

    private val fixtures: Fixtures = Json { ignoreUnknownKeys = true }.decodeFromString(
        Fixtures.serializer(),
        javaClass.getResource("/library/matching_fixtures_v1.json")!!.readText(),
    )
    private val setup = TestLibrary.setup
    private val templates = TestLibrary.templates

    @Test fun nineScenarios() = assertEquals(9, fixtures.scenarios.size)

    @Test fun evaluateAndMatchAgreeWithFixtures() {
        for (s in fixtures.scenarios) {
            val answers = s.answers.mapValues { it.value.toSet() }
            val result = SetupEvaluator.evaluate(setup, s.vehicleType, answers)
            assertEquals("${s.name}: confirmed", s.expectedConfirmedTags.toSet(), result.confirmed)
            assertEquals("${s.name}: maybe", s.expectedMaybeTags.toSet(), result.maybe)

            val m = LibraryMatcher.match(templates, s.vehicleType, result)
            assertEquals("${s.name}: recommended", s.expectedRecommendedIds.toSet(), m.recommended.map { it.id }.toSet())
            assertEquals("${s.name}: might apply", s.expectedMightApplyIds.toSet(), m.mightApply.map { it.id }.toSet())
        }
    }

    @Test fun hiddenFollowUpsAreIgnored() {
        // mh_allison only shows for a diesel pusher; an answer left over from before is dropped.
        val answers = mapOf("mh_chassis" to setOf("ford_f53"), "mh_allison" to setOf("tes295"))
        val r = SetupEvaluator.evaluate(setup, VehicleType.MOTORHOME, answers)
        assertFalse("mh_allison" in r.effectiveAnswers)
        assertTrue(SetupEvaluator.visibleQuestions(setup, VehicleType.MOTORHOME, answers).none { it.question.id == "mh_allison" })
        val shown = SetupEvaluator.visibleQuestions(setup, VehicleType.MOTORHOME, mapOf("mh_chassis" to setOf("diesel_pusher")))
        assertTrue(shown.any { it.question.id == "mh_allison" })
    }

    @Test fun phase2QuestionsNeverShow() {
        for (type in VehicleType.entries) {
            val all = setup.questions.flatMap { q -> q.options.map { q.id to it.id } }
                .groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
            assertTrue(SetupEvaluator.visibleQuestions(setup, type, all).all { it.question.phase == 1 })
        }
    }

    @Test fun requiredMissingUntilAnswered() {
        val type = VehicleType.MOTORCYCLE
        assertEquals(setOf("mc_brand", "mc_drive"), SetupEvaluator.missingRequired(setup, type, emptyMap()).map { it.id }.toSet())
        val done = mapOf("mc_brand" to setOf("other"), "mc_drive" to setOf("chain"))
        assertTrue(SetupEvaluator.missingRequired(setup, type, done).isEmpty())
    }

    @Test fun answersRoundTripThroughJson() {
        val a = mapOf("rv_equipment" to setOf("awning", "leveling"), "mh_chassis" to setOf("ford_f53"), "empty" to emptySet())
        val back = SetupEvaluator.decodeAnswers(SetupEvaluator.encodeAnswers(a))
        assertEquals(a - "empty", back)
        assertEquals(emptyMap<String, Set<String>>(), SetupEvaluator.decodeAnswers("not json"))
        assertEquals(emptyMap<String, Set<String>>(), SetupEvaluator.decodeAnswers(null))
    }

    @Test fun makeHintsOnlyFillUnansweredQuestions() {
        val type = VehicleType.MOTORCYCLE
        assertEquals(setOf("harley"), MakeHints.prefill(setup, type, "Harley-Davidson", emptyMap())["mc_brand"])
        val answered = mapOf("mc_brand" to setOf("other"))
        assertEquals(answered, MakeHints.prefill(setup, type, "harley", answered))
        assertEquals(emptyMap<String, Set<String>>(), MakeHints.prefill(setup, type, "Honda", emptyMap()))
        // Other types' hints never leak across.
        assertEquals(emptyMap<String, Set<String>>(), MakeHints.prefill(setup, VehicleType.MOTORHOME, "Harley", emptyMap()))
    }

    @Test fun wouldApplyIfNamesTheOption() {
        val answers = mapOf("mh_chassis" to setOf("not_sure"))
        val r = SetupEvaluator.evaluate(setup, VehicleType.MOTORHOME, answers)
        val m = LibraryMatcher.match(templates, VehicleType.MOTORHOME, r)
        val ford = m.mightApply.first { it.appliesToTags.toSet() == setOf("ford_f53", "ford_e_series") }
        val labels = SetupEvaluator.wouldApplyIf(setup, VehicleType.MOTORHOME, answers, ford)
        assertTrue(labels.any { it.startsWith("Ford F-53") })
        assertTrue(labels.any { it.startsWith("Ford E-Series") })
        assertFalse("maybe-only option is not a reason", "Not sure" in labels)
    }

    @Test fun oneTimeOfferedOnlyBelowItsInterval() {
        val t = templates.first { it.kind == TemplateKind.ONE_TIME && it.vehicleType == VehicleType.MOTORCYCLE }
        fun v(miles: Double?) = Vehicle(name = "b", vehicleType = VehicleType.MOTORCYCLE, currentMiles = miles, createdAt = 0, updatedAt = 0)
        assertTrue(t.isOfferable(v(12.0)))
        assertFalse(t.isOfferable(v(t.meterInterval!!.toDouble())))
        assertFalse("unknown meter", t.isOfferable(v(null)))
        assertTrue(templates.first { it.kind == TemplateKind.RECURRING }.isOfferable(v(null)))
    }
}
