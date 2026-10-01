package dev.maintracker

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.maintracker.data.PhotoStore
import dev.maintracker.data.SettingsStore
import dev.maintracker.data.VehicleRepository
import dev.maintracker.data.db.AppDatabase
import dev.maintracker.reminders.AndroidAlarmScheduler
import dev.maintracker.reminders.AndroidNotifier
import dev.maintracker.reminders.DailyCheckWorker
import dev.maintracker.reminders.ReminderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MaintTrackerApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { AppDatabase.build(this) }
    val photos by lazy { PhotoStore(this) }
    val settings by lazy { SettingsStore(this) }
    val alarms by lazy { AndroidAlarmScheduler(this) }
    val notifier by lazy { AndroidNotifier(this) }
    val reminders by lazy {
        ReminderEngine(database, prefs = { settings.current().reminders }, alarms = alarms, notifier = notifier)
    }
    val repository by lazy {
        VehicleRepository(
            database, photos,
            leadCap = { settings.current().dueSoonDays },
            onChanged = { vehicleId -> reminders.sync(vehicleId) },
        )
    }

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DailyCheckWorker.NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyCheckWorker>(1, TimeUnit.DAYS).build(),
        )
        appScope.launch {
            repository.ensureSeedTemplates()
            repository.recomputeAll()
        }
    }
}
