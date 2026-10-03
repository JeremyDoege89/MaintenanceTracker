package dev.maintracker.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.maintracker.data.db.AppDatabase
import dev.maintracker.data.model.Area
import dev.maintracker.data.model.MaintenanceTask
import dev.maintracker.data.model.MeterUnit
import dev.maintracker.data.model.ReminderKind
import dev.maintracker.data.model.TaskStatus
import dev.maintracker.data.model.Vehicle
import dev.maintracker.data.model.VehicleType
import dev.maintracker.domain.MeterReadings
import dev.maintracker.domain.ReminderPrefs
import dev.maintracker.reminders.AlarmScheduler
import dev.maintracker.reminders.Notifier
import dev.maintracker.reminders.ReminderEngine
import dev.maintracker.reminders.ReminderNotification
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** M3 acceptance (Build Guide §12/§13) against the real schema, with the OS alarm/notifications faked. */
@RunWith(AndroidJUnit4::class)
class ReminderEngineTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()
    private fun local(ms: Long?) = Instant.ofEpochMilli(ms!!).atZone(zone).toLocalDateTime()

    private class FakeAlarms : AlarmScheduler {
        var next: Long? = null
        override fun scheduleNext(at: Long?) { next = at }
    }

    private class FakeNotifier : Notifier {
        val posted = mutableListOf<ReminderNotification>()
        val cancelled = mutableListOf<String>()
        override fun post(n: ReminderNotification) { posted += n }
        override fun cancel(taskId: String) { cancelled += taskId }
    }

    private lateinit var db: AppDatabase
    private lateinit var repo: VehicleRepository
    private lateinit var engine: ReminderEngine
    private val alarms = FakeAlarms()
    private val notifier = FakeNotifier()
    private var prefs = ReminderPrefs()
    private var now = at(2026, 1, 10, 12)
    private lateinit var v: Vehicle

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        engine = ReminderEngine(db, { prefs }, alarms, notifier, clock = { now }, zone = { zone })
        repo = VehicleRepository(db, PhotoStore(InstrumentationRegistry.getInstrumentation().targetContext), testLibrarySync(db),
            clock = { now }, zone = { zone }, onChanged = { engine.sync(it) })
        v = repo.createVehicle(Vehicle(name = "Rig", vehicleType = VehicleType.MOTORHOME, currentMiles = 10_000.0,
            createdAt = 0, updatedAt = 0), emptySet())
    }

    @After fun tearDown() = db.close()

    private suspend fun task(days: Int?, meter: Int? = null): MaintenanceTask {
        val t = MaintenanceTask(vehicleId = v.id, area = Area.ENGINE, title = "Oil", timeIntervalDays = days,
            meterInterval = meter, meterUnit = meter?.let { MeterUnit.MILES }, baselineDate = now,
            baselineMeter = meter?.let { 10_000.0 }, status = TaskStatus.OK, leadDays = 0, createdAt = 0, updatedAt = 0)
        repo.saveTask(t)
        return db.tasks().get(t.id)!!
    }

    /** Simulates the alarm going off at whatever the engine armed. */
    private suspend fun fireAlarm() {
        now = alarms.next!!
        repo.recomputeAll()
        engine.deliverDue()
    }

    @Test fun timeReminder_fires_thenCompletionStopsFurtherAlarms() = runTest {
        val t = task(182) // due 2026-07-11, due-soon 06-27 09:00
        assertEquals(LocalDateTime.of(2026, 6, 27, 9, 0), local(alarms.next))

        fireAlarm()
        assertEquals("Due soon: Oil", notifier.posted.last().title)
        assertEquals(LocalDateTime.of(2026, 7, 11, 9, 0), local(alarms.next)) // re-armed for overdue

        fireAlarm()
        assertEquals("Overdue: Oil", notifier.posted.last().title)
        assertEquals(LocalDateTime.of(2026, 7, 18, 9, 0), local(alarms.next)) // day-7 escalation

        // Complete it: old cycle's rows go, the notification clears, next alarm is the NEW cycle.
        now = at(2026, 7, 12, 10)
        repo.logService(ServiceEntry(v.id, t.id, Area.ENGINE, "Oil", null, now, MeterReadings(), null, "USD", null, null, null))
        assertTrue(t.id in notifier.cancelled)
        assertEquals(LocalDateTime.of(2026, 12, 27, 9, 0), local(alarms.next)) // due 2027-01-10 minus 14 days
        assertTrue(db.reminders().forTask(t.id).all { it.deliveredAt == null })
    }

    @Test fun escalation_day7AndDay30_thenStops() = runTest {
        task(182)
        repeat(2) { fireAlarm() } // due soon, overdue
        fireAlarm()
        assertEquals("Still overdue: Oil", notifier.posted.last().title)
        fireAlarm()
        assertEquals(LocalDateTime.of(2026, 8, 10, 9, 0), local(now))
        assertEquals(4, notifier.posted.size)
        assertNull(alarms.next) // nothing left: escalation is finite
    }

    @Test fun snooze_refiresAfterThreeDays() = runTest {
        task(182)
        fireAlarm() // due soon
        val rowId = notifier.posted.last().reminderId
        engine.snooze(rowId, 3)
        // Snoozed until 06-30 09:00 — earlier than the overdue alarm, so that's what's armed.
        assertEquals(LocalDateTime.of(2026, 6, 30, 9, 0), local(alarms.next))
        fireAlarm()
        assertEquals(2, notifier.posted.size)
        assertEquals("Due soon: Oil", notifier.posted.last().title)
    }

    @Test fun meterDrive_notifiesDueSoonThenOverdue() = runTest {
        task(null, 5_000) // due-soon at 14,500, overdue at 15,000
        now = at(2026, 2, 1, 12)
        repo.logMeters(v.id, MeterReadings(miles = 14_600.0))
        assertEquals(ReminderKind.DUE_SOON_METER, notifier.posted.last().kind)
        repo.logMeters(v.id, MeterReadings(miles = 15_050.0))
        assertEquals(ReminderKind.OVERDUE_METER, notifier.posted.last().kind)
        assertEquals("Overdue: Oil", notifier.posted.last().title)
        repo.logMeters(v.id, MeterReadings(miles = 15_100.0))
        assertEquals(2, notifier.posted.size) // no repeats
    }

    @Test fun quietHours_holdMeterNotificationUntilMorning() = runTest {
        task(null, 5_000)
        now = at(2026, 2, 1, 22, 30)
        repo.logMeters(v.id, MeterReadings(miles = 15_050.0))
        assertTrue(notifier.posted.isEmpty())
        assertEquals(LocalDateTime.of(2026, 2, 2, 7, 0), local(alarms.next))
        fireAlarm()
        assertEquals(1, notifier.posted.size) // both crossed rows → one notification, the most severe
        assertEquals(ReminderKind.OVERDUE_METER, notifier.posted.single().kind)
    }

    @Test fun reboot_rearmsFromTable() = runTest {
        task(182)
        val armed = alarms.next
        val afterBoot = FakeAlarms()
        ReminderEngine(db, { prefs }, afterBoot, notifier, clock = { now }, zone = { zone }).rearm()
        assertEquals(armed, afterBoot.next)
    }

    @Test fun phoneOffForAMonth_postsOneCurrentNotification() = runTest {
        task(182)
        now = at(2026, 8, 1, 12) // missed due-soon, overdue and the day-7 nudge
        engine.deliverDue()
        assertEquals(1, notifier.posted.size)
        assertEquals("Still overdue: Oil", notifier.posted.single().title)
        assertEquals(LocalDateTime.of(2026, 8, 10, 9, 0), local(alarms.next))
    }

    @Test fun disablingTaskOrNotifyRemovesItsReminders() = runTest {
        val t = task(182)
        repo.saveTask(t.copy(notifyEnabled = false))
        assertTrue(db.reminders().forTask(t.id).isEmpty())
        assertNull(alarms.next)
        repo.saveTask(db.tasks().get(t.id)!!.copy(notifyEnabled = true))
        repo.setTaskActive(t.id, false)
        assertTrue(db.reminders().forTask(t.id).isEmpty())
    }

    @Test fun remindTimeChange_replansExistingRows() = runTest {
        task(182)
        prefs = prefs.copy(remindAtMinute = 18 * 60 + 30)
        repo.recomputeAll()
        assertEquals(LocalDateTime.of(2026, 6, 27, 18, 30), local(alarms.next))
    }
}
