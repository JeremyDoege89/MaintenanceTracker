package dev.dreamz.weekendfleet.domain

import dev.dreamz.weekendfleet.data.model.MaintenanceTask
import dev.dreamz.weekendfleet.data.model.ServiceRecord
import dev.dreamz.weekendfleet.data.model.Vehicle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Spreadsheet-friendly summaries (Build Guide §9: "CSV (records + tasks, summary view)").
 * Fixed formats so a column never changes meaning: ISO dates, plain numbers, distances in miles
 * (stated in the header), money with its currency in its own column. Lossy by design — the
 * JSON backup is the full-fidelity export.
 */
object Csv {
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    fun records(records: List<ServiceRecord>, vehicles: List<Vehicle>, tasks: List<MaintenanceTask>, zone: ZoneId = ZoneId.systemDefault()): String {
        val vName = vehicles.associate { it.id to it.name }
        val tName = tasks.associate { it.id to it.title }
        val header = listOf(
            "date", "vehicle", "task", "title", "area", "odometer_mi", "engine_hours", "generator_hours",
            "cost", "currency", "vendor", "parts", "description", "notes",
        )
        val rows = records.sortedWith(compareBy({ vName[it.vehicleId] }, { it.date })).map { r ->
            listOf(
                date(r.date, zone), vName[r.vehicleId], r.taskId?.let(tName::get), r.title, r.area.label,
                num(r.miles), num(r.engineHours), num(r.generatorHours), r.cost?.let { "%.2f".format(java.util.Locale.ROOT, it) },
                r.currency, r.vendor, r.partsText, r.description, r.notes,
            )
        }
        return table(header, rows)
    }

    fun tasks(tasks: List<MaintenanceTask>, vehicles: List<Vehicle>, zone: ZoneId = ZoneId.systemDefault()): String {
        val vName = vehicles.associate { it.id to it.name }
        val header = listOf(
            "vehicle", "task", "area", "status", "active", "every_days", "every_meter", "meter_unit",
            "last_done", "last_meter", "due_date", "due_meter", "times_completed", "notes",
        )
        val rows = tasks.sortedWith(compareBy({ vName[it.vehicleId] }, { it.sortOrder }, { it.title })).map { t ->
            listOf(
                vName[t.vehicleId], t.title, t.areaDetail ?: t.area.label, t.status.name, t.isActive.toString(),
                t.timeIntervalDays?.toString(), t.meterInterval?.toString(), t.meterUnit?.name,
                date(t.baselineDate, zone), num(t.baselineMeter), t.dueDate?.let { date(it, zone) }, num(t.dueMeter),
                t.completedCount.toString(), t.notes,
            )
        }
        return table(header, rows)
    }

    private fun date(millis: Long, zone: ZoneId) = iso.format(Instant.ofEpochMilli(millis).atZone(zone))

    private fun num(v: Double?): String? = v?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }

    private fun table(header: List<String>, rows: List<List<String?>>): String = buildString {
        append('﻿') // BOM: Excel otherwise reads UTF-8 as ANSI and mangles "°" or "€"
        (listOf(header) + rows).forEach { row -> append(row.joinToString(",") { escape(it.orEmpty()) }).append("\r\n") }
    }

    /** RFC 4180 quoting, plus a leading quote on formula-like cells so a spreadsheet won't execute them. */
    fun escape(cell: String): String {
        val safe = if (cell.isNotEmpty() && cell[0] in "=+-@\t\r") "'$cell" else cell
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
