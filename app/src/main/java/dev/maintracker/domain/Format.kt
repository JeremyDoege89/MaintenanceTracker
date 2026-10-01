package dev.maintracker.domain

import dev.maintracker.data.model.MeterUnit
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

object Format {
    private val number: NumberFormat get() = NumberFormat.getIntegerInstance()
    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE

    fun meter(value: Double, unit: MeterUnit): String = "${number.format(value)} ${unit.short}"

    fun date(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        isoDate.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    fun days(days: Int): String {
        val (n, unit) = IntervalUnit.split(days)
        return if (n == 1) unit.label.dropLast(1) else "$n ${unit.label}"
    }

    /** "$1,234.50" in the record's own currency. */
    fun money(amount: Double, currencyCode: String): String =
        runCatching {
            NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(currencyCode) }.format(amount)
        }.getOrElse { "%.2f %s".format(amount, currencyCode) }

    /** Locale default currency (ISO 4217), falling back to USD. */
    fun defaultCurrency(): String =
        runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull() ?: "USD"

    /** "Every 6 months or 5,000 mi — whichever first". */
    fun interval(days: Int?, meter: Int?, unit: MeterUnit?): String {
        val time = days?.let { "Every ${days(it)}" }
        val m = if (meter != null && unit != null) "${number.format(meter)} ${unit.short}" else null
        return when {
            time != null && m != null -> "$time or $m — whichever first"
            time != null -> time
            m != null -> "Every $m"
            else -> "Checklist item — no set interval"
        }
    }

    /** Masked for display: "···8421". */
    fun maskId(id: String?): String? = id?.trim()?.takeIf { it.isNotEmpty() }?.let { "···" + it.takeLast(4) }
}
