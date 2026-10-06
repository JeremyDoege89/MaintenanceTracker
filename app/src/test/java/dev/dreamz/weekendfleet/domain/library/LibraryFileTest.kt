package dev.dreamz.weekendfleet.domain.library

import dev.dreamz.weekendfleet.data.model.MeterUnit
import dev.dreamz.weekendfleet.data.model.TemplateKind
import dev.dreamz.weekendfleet.data.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** IMPLEMENTATION.md §6 test 1 (library parse) and test 3 (tag coverage). */
class LibraryFileTest {
    private val lib = TestLibrary.library
    private val phase1 = lib.templates.filter { it.phase == 1 }

    @Test fun decodes529TemplatesWithUniqueIds() {
        assertEquals(529, lib.templates.size)
        assertEquals(529, lib.templates.map { it.id }.toSet().size)
        lib.templates.forEach { assertTrue(it.id, it.id.startsWith("std:${it.vehicleType.name}:")) }
        assertTrue(lib.disclaimer.isNotBlank())
    }

    /** Enums are typed in the DTO, so decoding at all proves every enum name is valid. */
    @Test fun everyMeterUnitIsOneTheTypeLogs() {
        lib.templates.forEach { t ->
            t.meterUnit?.let { assertTrue("${t.id} uses $it", it in t.vehicleType.meters) }
            assertEquals(t.id, t.meterInterval != null, t.meterUnit != null)
            assertTrue(t.id, t.description.isNotBlank())
        }
    }

    @Test fun phase1CountsMatchTheBrief() {
        assertEquals(417, phase1.size)
        val byType = phase1.groupingBy { it.vehicleType }.eachCount()
        assertEquals(
            mapOf(
                VehicleType.MOTORHOME to 94, VehicleType.TRAVEL_TRAILER to 67, VehicleType.BOAT_POWER to 71,
                VehicleType.BOAT_SAIL to 90, VehicleType.MOTORCYCLE to 30, VehicleType.ATV_UTV to 26, VehicleType.PWC to 39,
            ),
            byType,
        )
        val kinds = setOf(TemplateKind.PRE_USE, TemplateKind.POST_USE, TemplateKind.EVENT)
        assertTrue(phase1.none { it.kind in kinds })
    }

    @Test fun schedulingKindsHaveWhatTheyNeed() {
        phase1.forEach { t ->
            when (t.kind) {
                TemplateKind.RECURRING, TemplateKind.EXPIRY, TemplateKind.SEASONAL ->
                    assertTrue("${t.id} has no interval", t.timeIntervalDays != null || t.meterInterval != null)
                TemplateKind.ONE_TIME -> assertNotNull("${t.id} meterInterval", t.meterInterval)
                TemplateKind.PER_MANUAL ->
                    assertTrue(t.id, t.timeIntervalDays == null && t.meterInterval == null)
                else -> Unit
            }
            if (t.kind == TemplateKind.SEASONAL) {
                assertNotNull(t.id, t.season)
                assertNotNull(t.id, t.timeIntervalDays)
            }
            if (t.kind == TemplateKind.EXPIRY) assertNotNull(t.id, t.dateBasis)
        }
    }

    @Test fun generatorTemplatesUseGeneratorHours() {
        val gen = phase1.filter { it.title.startsWith("Generator", ignoreCase = true) && it.meterUnit != null }
        assertTrue(gen.isNotEmpty())
        gen.forEach { assertEquals(it.id, MeterUnit.GENERATOR_HOURS, it.meterUnit) }
    }

    @Test fun atvOilIsEngineHoursAt50() {
        val oil = phase1.filter { it.vehicleType == VehicleType.ATV_UTV && it.title.contains("oil", ignoreCase = true) &&
            it.title.contains("engine", ignoreCase = true) }
        assertTrue("no ATV/UTV engine-oil template", oil.isNotEmpty())
        oil.forEach {
            assertEquals(it.id, MeterUnit.ENGINE_HOURS, it.meterUnit)
            assertEquals(it.id, 50, it.meterInterval)
        }
    }

    /** §6 test 3: every tag a phase-1 template uses can come from Setup. */
    @Test fun everyTemplateTagIsProducible() {
        val setup = TestLibrary.setup
        for (type in VehicleType.entries) {
            val producible = HashSet<String>().apply {
                add("all")
                setup.derivedTags.forEach { add(it.tag) }
                setup.questions.filter { type in it.vehicleTypes && it.phase == 1 }.forEach { q ->
                    q.options.filter { it.vehicleTypes == null || type in it.vehicleTypes }
                        .forEach { addAll(it.tags); addAll(it.maybeTags) }
                }
            }
            val used = phase1.filter { it.vehicleType == type }.flatMap { it.appliesTo }.toSet()
            assertEquals("$type tags nothing in Setup produces", emptySet<String>(), used - producible)
        }
    }

    @Test fun seasonAnchorsParse() {
        val a = lib.defaults.anchors
        assertEquals(java.time.MonthDay.of(10, 1), a[dev.dreamz.weekendfleet.data.model.Season.FALL])
        assertEquals(java.time.MonthDay.of(4, 1), a[dev.dreamz.weekendfleet.data.model.Season.SPRING])
    }
}
