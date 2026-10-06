package dev.dreamz.weekendfleet.domain

import dev.dreamz.weekendfleet.data.model.Area
import dev.dreamz.weekendfleet.data.model.ServiceRecord
import java.time.LocalDate
import java.time.ZoneId

enum class CostPeriod(val label: String) {
    THIS_MONTH("This month"),
    THIS_YEAR("This year"),
    ALL_TIME("All time"),
}

data class AreaCost(val area: Area, val total: Double)

/** Spending grouped by task when linked, else by record title (ad-hoc work). */
data class ItemCost(val title: String, val total: Double, val count: Int)

data class CostSummary(
    val currency: String,
    val thisMonth: Double,
    val thisYear: Double,
    val allTime: Double,
    /** Within the chosen period, biggest first. */
    val byArea: List<AreaCost>,
    val topItems: List<ItemCost>,
    val periodTotal: Double,
    /** Records costed in other currencies: summed per currency, never converted. */
    val otherCurrencies: Map<String, Double>,
    val costedRecords: Int,
    /** Records with no cost entered — reported, never guessed (Build Guide §5.4 "damage-safe"). */
    val uncostedRecords: Int,
)

object Costs {
    /**
     * [currency]: the one to total in. The caller picks the user's default, or the currency
     * most of this vehicle's records use, so a trip abroad doesn't hijack the headline.
     */
    fun summarize(
        records: List<ServiceRecord>,
        currency: String,
        period: CostPeriod,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        topN: Int = 5,
    ): CostSummary {
        val today = DueCalculator.localDate(now, zone)
        fun day(r: ServiceRecord): LocalDate = DueCalculator.localDate(r.date, zone)
        val costed = records.filter { it.cost != null }
        val mine = costed.filter { it.currency == currency }
        val inPeriod = mine.filter {
            when (period) {
                CostPeriod.THIS_MONTH -> day(it).year == today.year && day(it).month == today.month
                CostPeriod.THIS_YEAR -> day(it).year == today.year
                CostPeriod.ALL_TIME -> true
            }
        }
        return CostSummary(
            currency = currency,
            thisMonth = mine.filter { day(it).year == today.year && day(it).month == today.month }.sumOf { it.cost!! },
            thisYear = mine.filter { day(it).year == today.year }.sumOf { it.cost!! },
            allTime = mine.sumOf { it.cost!! },
            byArea = inPeriod.groupBy { it.area }
                .map { (area, rs) -> AreaCost(area, rs.sumOf { it.cost!! }) }
                .filter { it.total > 0 }
                .sortedByDescending { it.total },
            topItems = inPeriod.groupBy { it.taskId ?: "title:${it.title.trim().lowercase()}" }
                .map { (_, rs) -> ItemCost(rs.maxBy { it.date }.title.trim(), rs.sumOf { it.cost!! }, rs.size) }
                .filter { it.total > 0 }
                .sortedByDescending { it.total }
                .take(topN),
            periodTotal = inPeriod.sumOf { it.cost!! },
            otherCurrencies = costed.filter { it.currency != currency }
                .groupBy { it.currency }
                .mapValues { (_, rs) -> rs.sumOf { it.cost!! } },
            costedRecords = costed.size,
            uncostedRecords = records.size - costed.size,
        )
    }

    /** The default currency if any record uses it, otherwise the most common one. */
    fun headlineCurrency(records: List<ServiceRecord>, preferred: String): String {
        val used = records.filter { it.cost != null }.groupingBy { it.currency }.eachCount()
        return if (used.isEmpty() || preferred in used) preferred else used.maxBy { it.value }.key
    }
}
