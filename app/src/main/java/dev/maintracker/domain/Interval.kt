package dev.maintracker.domain

/** Time intervals are stored as days; the editor shows them in the unit they were written in. */
enum class IntervalUnit(val label: String) {
    DAYS("days"), WEEKS("weeks"), MONTHS("months"), YEARS("years");

    fun toDays(n: Int): Int = when (this) {
        DAYS -> n
        WEEKS -> n * 7
        // Floor of n/12 years: 1 → 30, 3 → 91, 6 → 182, 12 → 365 — matching the seed constants.
        MONTHS -> n * 365 / 12
        YEARS -> n * 365
    }

    companion object {
        /** Largest unit that round-trips exactly. */
        fun split(days: Int): Pair<Int, IntervalUnit> {
            if (days > 0 && days % 365 == 0) return days / 365 to YEARS
            (1..240).firstOrNull { MONTHS.toDays(it) == days }?.let { return it to MONTHS }
            if (days > 0 && days % 7 == 0) return days / 7 to WEEKS
            return days to DAYS
        }
    }
}
