package dev.maintracker.domain

import dev.maintracker.data.model.MeterUnit

enum class DistanceUnit(val short: String, val long: String) {
    MILES("mi", "Miles"),
    KM("km", "Kilometres"),
}

enum class DateStyle(val label: String) {
    SYSTEM("Phone default"),
    ISO("2026-03-14"),
    DMY("14/03/2026"),
    MDY("03/14/2026"),
}

/** Build Guide §5.7 "Units". Display-only: what's stored never changes when these do. */
data class DisplayPrefs(
    val distance: DistanceUnit = DistanceUnit.MILES,
    val dateStyle: DateStyle = DateStyle.SYSTEM,
    /** ISO 4217 code for new records; null = the phone's locale currency. */
    val currency: String? = null,
)

/**
 * Distances are always stored in miles (the unit the schedules are written in), so switching
 * mi/km is instant and lossless: [toDisplay] on the way out, [fromDisplay] on the way in.
 * Hours are hours either way.
 */
object Display {
    const val KM_PER_MILE = 1.609344

    /** Set from settings at startup and on every change (MaintTrackerApp). */
    @Volatile var prefs = DisplayPrefs()

    private fun km(u: MeterUnit) = u == MeterUnit.MILES && prefs.distance == DistanceUnit.KM

    fun toDisplay(stored: Double, u: MeterUnit): Double = if (km(u)) stored * KM_PER_MILE else stored
    fun fromDisplay(entered: Double, u: MeterUnit): Double = if (km(u)) entered / KM_PER_MILE else entered

    fun short(u: MeterUnit): String = if (km(u)) DistanceUnit.KM.short else u.short
    fun long(u: MeterUnit): String = if (km(u)) "Kilometres" else u.long
}

/** The label to show for a meter, honouring the mi/km setting. */
val MeterUnit.label: String get() = Display.long(this)
