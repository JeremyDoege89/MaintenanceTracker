package dev.maintracker.domain.library

import dev.maintracker.data.model.Area
import dev.maintracker.data.model.DateBasis
import dev.maintracker.data.model.MaintenanceTemplate
import dev.maintracker.data.model.MeterUnit
import dev.maintracker.data.model.Season
import dev.maintracker.data.model.SourceTier
import dev.maintracker.data.model.TemplateKind
import dev.maintracker.data.model.VehicleType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId

/**
 * assets/library/maintenance_library_v1.json (docs/maintenance-library/IMPLEMENTATION.md §4.1).
 * Enum-typed fields fail the decode on an unknown name, so a bad library never half-loads.
 */
@Serializable
data class LibraryFile(
    val schemaVersion: Int,
    val dataVersion: Int,
    val generatedOn: String? = null,
    val disclaimer: String,
    val defaults: LibraryDefaults = LibraryDefaults(),
    val templates: List<LibraryTemplate>,
) {
    companion object {
        const val ASSET = "library/maintenance_library_v1.json"

        fun decode(text: String): LibraryFile = LibraryJson.decodeFromString(serializer(), text)
    }
}

@Serializable
data class LibraryDefaults(
    val dueSoonPct: Double = 0.1,
    /** Season -> "MM-dd". */
    val seasonAnchors: Map<Season, String> = mapOf(Season.FALL to "10-01", Season.SPRING to "04-01"),
) {
    val anchors: Map<Season, MonthDay>
        get() = seasonAnchors.mapValues { (_, v) -> MonthDay.parse("--$v") }
}

@Serializable
data class LibraryTemplate(
    val id: String,
    val vehicleType: VehicleType,
    val area: Area,
    val title: String,
    val description: String,
    val kind: TemplateKind,
    val phase: Int,
    val timeIntervalDays: Int? = null,
    val meterInterval: Int? = null,
    val meterUnit: MeterUnit? = null,
    val altMeterInterval: Int? = null,
    val altMeterUnit: MeterUnit? = null,
    val defaultDueSoonPct: Double = 0.1,
    val appliesTo: List<String>,
    val safetyCritical: Boolean = false,
    val dateBasis: DateBasis? = null,
    val season: Season? = null,
    val eventTrigger: String? = null,
    val typicalRange: String? = null,
    val severeService: String? = null,
    val diyPro: String? = null,
    val source: String? = null,
    val sourceTier: SourceTier? = null,
    val libraryGroup: String? = null,
    val notes: String? = null,
    val sortOrder: Int,
    val isSeeded: Boolean = true,
) {
    fun toEntity() = MaintenanceTemplate(
        id = id, vehicleType = vehicleType, area = area, title = title, description = description,
        timeIntervalDays = timeIntervalDays, meterInterval = meterInterval, meterUnit = meterUnit,
        defaultDueSoonPct = defaultDueSoonPct, notes = notes, isSeeded = isSeeded, sortOrder = sortOrder,
        appliesTo = appliesTo.joinToString(";"), kind = kind, phase = phase, safetyCritical = safetyCritical,
        dateBasis = dateBasis, season = season, eventTrigger = eventTrigger,
        altMeterInterval = altMeterInterval, altMeterUnit = altMeterUnit,
        typicalRange = typicalRange, severeService = severeService, diyPro = diyPro,
        source = source, sourceTier = sourceTier?.name, libraryGroup = libraryGroup,
        retired = false,
    )
}

internal val LibraryJson = Json { ignoreUnknownKeys = true }

object Seasons {
    /** The next [season] anchor strictly after today's local date. */
    fun nextAnchor(season: Season, now: Long, zone: ZoneId, anchors: Map<Season, MonthDay>): LocalDate {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val md = anchors[season] ?: LibraryDefaults().anchors.getValue(season)
        val thisYear = md.atYear(today.year)
        return if (thisYear.isAfter(today)) thisYear else md.atYear(today.year + 1)
    }
}
