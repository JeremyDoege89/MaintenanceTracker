package dev.maintracker

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.maintracker.billing.BillingGateway
import dev.maintracker.billing.DataStoreEntitlementCache
import dev.maintracker.billing.FakeBillingGateway
import dev.maintracker.billing.PlayBillingGateway
import dev.maintracker.billing.ProManager
import dev.maintracker.data.BackupManager
import dev.maintracker.data.LibraryAssets
import dev.maintracker.data.LibrarySync
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import dev.maintracker.data.vin.HttpVpicRemote
import dev.maintracker.data.vin.VinDecoder
import dev.maintracker.domain.Display
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
    val billing: BillingGateway by lazy {
        if (BuildConfig.DEBUG && FakeBillingGateway.enabled(this)) FakeBillingGateway(this) else PlayBillingGateway(this)
    }
    val pro by lazy { ProManager(billing, DataStoreEntitlementCache(this), appScope) }
    val library by lazy {
        LibrarySync(
            database, LibraryAssets(assets::open),
            storedVersion = settings::libraryDataVersion, setStoredVersion = { settings.setLibraryDataVersion(it) },
        )
    }
    val vinDecoder by lazy { VinDecoder(database.vinDecodes(), HttpVpicRemote()) }
    val repository by lazy {
        VehicleRepository(
            database, photos, library,
            leadCap = { settings.current().dueSoonDays },
            onChanged = { vehicleId -> reminders.sync(vehicleId) },
        )
    }

    val backups by lazy {
        BackupManager(this, database, photos, repository, clearReminders = {
            reminders.sync(null) // rows cascaded away; this cancels the alarm
            androidx.core.app.NotificationManagerCompat.from(this).cancelAll()
        })
    }

    override fun onCreate() {
        super.onCreate()
        // Units/date/currency must be right on the very first frame; it's one tiny prefs read.
        Display.prefs = runBlocking { settings.current().display }
        appScope.launch { settings.flow.map { it.display }.distinctUntilChanged().collect { Display.prefs = it } }
        notifier.createChannels()
        pro.start() // cached entitlement first, then queryPurchases on every cold start (Build Guide §10)
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DailyCheckWorker.NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyCheckWorker>(1, TimeUnit.DAYS).build(),
        )
        appScope.launch {
            repository.syncLibrary()
            repository.recomputeAll()
        }
    }
}
