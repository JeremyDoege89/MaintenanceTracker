package dev.dreamz.weekendfleet.domain

import dev.dreamz.weekendfleet.data.model.MeterUnit
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

object Format {
    private val number: NumberFormat get() = NumberFormat.getIntegerInstance()
    private val dateFormats = mapOf(
        DateStyle.SYSTEM to DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM),
        DateStyle.ISO to DateTimeFormatter.ISO_LOCAL_DATE,
        DateStyle.DMY to DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateStyle.MDY to DateTimeFormatter.ofPattern("MM/dd/yyyy"),
    )

    /** [value] is in storage units (miles for distance); shown in the user's chosen unit. */
    fun meter(value: Double, unit: MeterUnit): String =
        "${number.format(Display.toDisplay(value, unit))} ${Display.short(unit)}"

    fun date(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateFormats.getValue(Display.prefs.dateStyle).format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    fun days(days: Int): String {
        val (n, unit) = IntervalUnit.split(days)
        return if (n == 1) unit.label.dropLast(1) else "$n ${unit.label}"
    }

    /** "$1,234.50" in the record's own currency. */
    fun money(amount: Double, currencyCode: String): String =
        runCatching {
            NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(currencyCode) }.format(amount)
        }.getOrElse { "%.2f %s".format(amount, currencyCode) }

    /** Settings currency, else the locale's (ISO 4217), falling back to USD. */
    fun defaultCurrency(): String = Display.prefs.currency ?: localeCurrency()

    fun localeCurrency(): String =
        runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull() ?: "USD"

    /** "Every 6 months or 5,000 mi — whichever first"; a [oneTime] task reads "Once at 1,000 mi". */
    fun interval(days: Int?, meter: Int?, unit: MeterUnit?, oneTime: Boolean = false): String {
        val lead = if (oneTime) "Once at" else "Every"
        val time = days?.let { "$lead ${days(it)}" }
        val m = if (meter != null && unit != null) meter(meter.toDouble(), unit) else null
        return when {
            time != null && m != null -> "$time or $m — whichever first"
            time != null -> time
            m != null -> "$lead $m"
            else -> "Checklist item — no set interval"
        }
    }

    /** Masked for display: "···8421". */
    fun maskId(id: String?): String? = id?.trim()?.takeIf { it.isNotEmpty() }?.let { "···" + it.takeLast(4) }
}
