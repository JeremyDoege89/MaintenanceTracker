package dev.dreamz.weekendfleet

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.dreamz.weekendfleet.billing.BillingGateway
import dev.dreamz.weekendfleet.billing.DataStoreEntitlementCache
import dev.dreamz.weekendfleet.billing.FakeBillingGateway
import dev.dreamz.weekendfleet.billing.PlayBillingGateway
import dev.dreamz.weekendfleet.billing.ProManager
import dev.dreamz.weekendfleet.data.BackupManager
import dev.dreamz.weekendfleet.data.LibraryAssets
import dev.dreamz.weekendfleet.data.LibrarySync
import dev.dreamz.weekendfleet.data.PhotoStore
import dev.dreamz.weekendfleet.data.SettingsStore
import dev.dreamz.weekendfleet.data.VehicleRepository
import dev.dreamz.weekendfleet.data.db.AppDatabase
import dev.dreamz.weekendfleet.reminders.AndroidAlarmScheduler
import dev.dreamz.weekendfleet.reminders.AndroidNotifier
import dev.dreamz.weekendfleet.reminders.DailyCheckWorker
import dev.dreamz.weekendfleet.reminders.ReminderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import dev.dreamz.weekendfleet.data.vin.HttpVpicRemote
import dev.dreamz.weekendfleet.data.vin.VinDecoder
import dev.dreamz.weekendfleet.domain.Display
import dev.dreamz.weekendfleet.domain.MicTable
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
    /** USCG MIC list plus the hand-kept brand overrides (assets/hin/; see docs/maintenance-library/IMPLEMENTATION.md). Parsed on first use. */
    val micTable by lazy {
        fun read(path: String) = runCatching { assets.open(path).use { it.readBytes().decodeToString() } }.getOrNull()
        MicTable.load(read("hin/uscg_mic.csv").orEmpty(), read("hin/mic_brand_overrides.csv"))
    }
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
