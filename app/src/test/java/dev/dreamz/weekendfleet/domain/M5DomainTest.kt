package dev.dreamz.weekendfleet.domain

import dev.dreamz.weekendfleet.data.model.Area
import dev.dreamz.weekendfleet.data.model.Attachment
import dev.dreamz.weekendfleet.data.model.AttachmentKind
import dev.dreamz.weekendfleet.data.model.MaintenanceTask
import dev.dreamz.weekendfleet.data.model.MeterUnit
import dev.dreamz.weekendfleet.data.model.Part
import dev.dreamz.weekendfleet.data.model.ServiceRecord
import dev.dreamz.weekendfleet.data.model.TaskStatus
import dev.dreamz.weekendfleet.data.model.Vehicle
import dev.dreamz.weekendfleet.data.model.VehicleType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class M5DomainTest {
    private val zone = ZoneOffset.UTC
    private fun at(d: String) = LocalDate.parse(d).atStartOfDay(zone).toInstant().toEpochMilli()
    private val now = at("2026-10-01")

    private val v = Vehicle(id = "v1", name = "Big Blue", vehicleType = VehicleType.MOTORHOME, currentMiles = 61230.0,
        vinOrHin = "1ABC8421", photoUri = "photos/p.jpg", createdAt = 1, updatedAt = 2)
    private val t = MaintenanceTask(id = "t1", vehicleId = "v1", area = Area.ENGINE, title = "Oil change", timeIntervalDays = 365,
        meterInterval = 5000, meterUnit = MeterUnit.MILES, baselineDate = at("2026-03-14"), baselineMeter = 41220.0,
        lastRecordId = "r2", status = TaskStatus.OK, leadDays = 14, createdAt = 1, updatedAt = 2)

    private fun rec(id: String, date: String, cost: Double?, area: Area = Area.ENGINE, currency: String = "USD", taskId: String? = null, title: String = "Work") =
        ServiceRecord(id = id, vehicleId = "v1", taskId = taskId, area = area, title = title, date = at(date), cost = cost,
            currency = currency, createdAt = at(date))

    @After fun reset() { Display.prefs = DisplayPrefs() }

    // ---- Costs ----

    @Test fun `totals by month year and all time, uncosted counted not summed`() {
        val rs = listOf(
            rec("a", "2026-10-01", 100.0), rec("b", "2026-02-01", 50.0), rec("c", "2025-06-01", 25.0), rec("d", "2026-10-01", null),
        )
        val s = Costs.summarize(rs, "USD", CostPeriod.THIS_YEAR, now, zone)
        assertEquals(100.0, s.thisMonth, 0.0)
        assertEquals(150.0, s.thisYear, 0.0)
        assertEquals(175.0, s.allTime, 0.0)
        assertEquals(150.0, s.periodTotal, 0.0)
        assertEquals(3, s.costedRecords)
        assertEquals(1, s.uncostedRecords)
    }

    @Test fun `areas sorted biggest first and other currencies kept apart`() {
        val rs = listOf(
            rec("a", "2026-05-01", 40.0, Area.BRAKES), rec("b", "2026-05-02", 90.0, Area.ROOF_SEALS),
            rec("c", "2026-05-03", 30.0, Area.BRAKES), rec("d", "2026-05-04", 500.0, Area.HULL, currency = "CAD"),
        )
        val s = Costs.summarize(rs, "USD", CostPeriod.ALL_TIME, now, zone)
        assertEquals(listOf(Area.ROOF_SEALS to 90.0, Area.BRAKES to 70.0), s.byArea.map { it.area to it.total })
        assertEquals(mapOf("CAD" to 500.0), s.otherCurrencies)
    }

    @Test fun `top items group by task, else by title`() {
        val rs = listOf(
            rec("a", "2026-01-01", 60.0, taskId = "t1", title = "Oil change"),
            rec("b", "2026-06-01", 70.0, taskId = "t1", title = "Oil + filter"),
            rec("c", "2026-03-01", 200.0, title = "Tow"), rec("d", "2026-04-01", 10.0, title = "tow "),
        )
        val top = Costs.summarize(rs, "USD", CostPeriod.ALL_TIME, now, zone).topItems
        assertEquals(listOf("tow" to 210.0, "Oil + filter" to 130.0), top.map { it.title to it.total })
        assertEquals(2, top[1].count)
    }

    @Test fun `this month period drives the breakdown`() {
        val rs = listOf(rec("a", "2026-10-01", 10.0, Area.BRAKES), rec("b", "2026-09-30", 99.0, Area.HULL))
        val s = Costs.summarize(rs, "USD", CostPeriod.THIS_MONTH, now, zone)
        assertEquals(10.0, s.periodTotal, 0.0)
        assertEquals(listOf(Area.BRAKES), s.byArea.map { it.area })
    }

    @Test fun `headline currency prefers the default when used`() {
        val rs = listOf(rec("a", "2026-01-01", 1.0, currency = "EUR"), rec("b", "2026-01-01", 1.0, currency = "EUR"), rec("c", "2026-01-01", 1.0))
        assertEquals("USD", Costs.headlineCurrency(rs, "USD"))
        assertEquals("EUR", Costs.headlineCurrency(rs, "GBP"))
    }

    // ---- Backup ----

    private fun backup() = Backup(
        exportedAt = "2026-10-01T15:00:00Z", vehicles = listOf(v), tasks = listOf(t),
        records = listOf(rec("r2", "2026-03-14", 89.5, taskId = "t1").copy(miles = 41220.0, vendor = "Self", notes = "line1\nline2")),
        parts = listOf(Part(id = "p1", vehicleId = "v1", taskId = "t1", name = "Filter", torqueSpec = "18 ft-lb", createdAt = 3)),
        attachments = listOf(Attachment(id = "a1", vehicleId = "v1", serviceRecordId = "r2", kind = AttachmentKind.RECEIPT,
            fileName = "x.jpg", mimeType = "image/jpeg", byteSize = 10, createdAt = 4)),
    )

    @Test fun `backup round-trips every field`() {
        val b = backup()
        val text = BackupCodec.encode(b)
        assertTrue(text.contains("\"serviceRecords\""))
        assertTrue(text.contains("\"schemaVersion\": 1"))
        assertEquals(b, BackupCodec.decode(text))
    }

    @Test fun `unknown fields are ignored, missing optional fields defaulted`() {
        val text = BackupCodec.encode(backup()).replaceFirst("{", "{\n  \"futureField\": 7,")
        assertEquals(backup(), BackupCodec.decode(text))
    }

    private fun rejects(text: String, contains: String) {
        try { BackupCodec.decode(text); fail("accepted") } catch (e: BackupException) {
            assertTrue(e.message, e.message!!.contains(contains))
        }
    }

    @Test fun `rejects junk, other apps, newer schemas, broken references and unsafe names`() {
        rejects("hello", "isn't a Maintenance Tracker backup")
        rejects(BackupCodec.encode(backup().copy(app = "other")), "different app")
        rejects(BackupCodec.encode(backup().copy(schemaVersion = 2)), "newer version")
        rejects(BackupCodec.encode(backup().copy(tasks = emptyList())), "incomplete")
        rejects(BackupCodec.encode(backup().copy(vehicles = listOf(v, v))), "duplicate")
        rejects(BackupCodec.encode(backup().copy(attachments = backup().attachments.map { it.copy(fileName = "../../db") })), "file names")
        rejects(BackupCodec.encode(backup().copy(vehicles = listOf(v.copy(photoUri = "/data/x")))), "file names")
    }

    // ---- CSV ----

    @Test fun `csv quotes, escapes formulas, and uses ISO dates and plain numbers`() {
        val b = backup()
        val csv = Csv.records(b.records + rec("r3", "2026-04-01", null, title = "=HYPERLINK(\"x\")"), b.vehicles, b.tasks, zone)
        val lines = csv.removePrefix("﻿").split("\r\n")
        assertEquals("date,vehicle,task,title,area,odometer_mi,engine_hours,generator_hours,cost,currency,vendor,parts,description,notes", lines[0])
        // Records split on \r\n only, so the quoted multi-line note stays inside its row.
        assertEquals("2026-03-14,Big Blue,Oil change,Work,Engine,41220,,,89.50,USD,Self,,,\"line1\nline2\"", lines[1])
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"x\"\")\""))
    }

    @Test fun `csv escape rules`() {
        assertEquals("plain", Csv.escape("plain"))
        assertEquals("\"a,b\"", Csv.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", Csv.escape("say \"hi\""))
        assertEquals("'+1", Csv.escape("+1"))
    }

    // ---- Units & formats ----

    @Test fun `km display converts miles both ways and leaves hours alone`() {
        Display.prefs = DisplayPrefs(distance = DistanceUnit.KM)
        assertEquals(16.09344, Display.toDisplay(10.0, MeterUnit.MILES), 1e-9)
        assertEquals(10.0, Display.fromDisplay(16.09344, MeterUnit.MILES), 1e-9)
        assertEquals(812.0, Display.toDisplay(812.0, MeterUnit.GENERATOR_HOURS), 0.0)
        assertEquals("km", Display.short(MeterUnit.MILES))
        assertEquals("Kilometres", MeterUnit.MILES.label)
        assertTrue(Format.meter(5000.0, MeterUnit.MILES).endsWith("km"))
        assertTrue(Format.interval(null, 5000, MeterUnit.MILES), Format.interval(null, 5000, MeterUnit.MILES).contains("8,047 km"))
    }

    @Test fun `date styles`() {
        val d = at("2026-03-14")
        Display.prefs = DisplayPrefs(dateStyle = DateStyle.ISO); assertEquals("2026-03-14", Format.date(d, zone))
        Display.prefs = DisplayPrefs(dateStyle = DateStyle.DMY); assertEquals("14/03/2026", Format.date(d, zone))
        Display.prefs = DisplayPrefs(dateStyle = DateStyle.MDY); assertEquals("03/14/2026", Format.date(d, zone))
    }

    @Test fun `currency setting overrides locale`() {
        Display.prefs = DisplayPrefs(currency = "CAD")
        assertEquals("CAD", Format.defaultCurrency())
    }
}
