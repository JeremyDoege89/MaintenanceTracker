package dev.maintracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import dev.maintracker.data.ServiceEntry
import dev.maintracker.data.model.Area
import dev.maintracker.data.model.AttachmentKind
import dev.maintracker.data.model.Part
import dev.maintracker.data.model.Vehicle
import dev.maintracker.data.model.VehicleType
import dev.maintracker.domain.MeterReadings
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Debug-only: drive the M5 export/import round trip on the real app database, and load a demo
 * garage for UI checks and Play screenshots (Build Guide §11 "screenshots from real build").
 *
 *   adb shell am broadcast -n dev.maintracker/.DebugDataReceiver -a export_to --es name user.zip
 *   adb shell am broadcast -n dev.maintracker/.DebugDataReceiver -a import_from --es name user.zip
 *   adb shell am broadcast -n dev.maintracker/.DebugDataReceiver -a seed_demo
 *
 * Files live in the app's cache dir (cache/debug/<name>). Logs under tag DebugData.
 */
class DebugDataReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MaintTrackerApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                val dir = File(app.cacheDir, "debug").apply { mkdirs() }
                val file = File(dir, intent.getStringExtra("name") ?: "backup.zip")
                when (intent.action) {
                    "export_to" -> {
                        app.backups.exportBackup(Uri.fromFile(file), withFiles = file.name.endsWith(".zip"))
                        Log.i(TAG, "exported ${file.length()} bytes to $file")
                    }
                    "import_from" -> Log.i(TAG, "imported: ${app.backups.import(Uri.fromFile(file))}")
                    "seed_demo" -> { seedDemo(app, dir); Log.i(TAG, "demo garage loaded") }
                }
                Log.i(TAG, "vehicles now: ${app.database.vehicles().all().map { it.name }}")
            } catch (e: Exception) {
                Log.e(TAG, "failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun seedDemo(app: MaintTrackerApp, dir: File) {
        app.backups.deleteAll()
        val repo = app.repository
        val zone = ZoneId.systemDefault()
        fun day(s: String) = LocalDate.parse(s).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        fun ago(months: Long, days: Long = 0) = LocalDate.now(zone).minusMonths(months).minusDays(days).atTime(10, 0)
            .atZone(zone).toInstant().toEpochMilli()

        suspend fun vehicle(v: Vehicle, n: Int): Vehicle {
            val ids = repo.templatesFor(v.vehicleType).take(n).map { it.id }.toSet()
            return repo.createVehicle(v, ids)
        }
        suspend fun log(v: Vehicle, title: String, area: Area, at: Long, miles: Double? = null, eng: Double? = null,
                        gen: Double? = null, cost: Double? = null, vendor: String? = null, taskTitle: String? = null) {
            val task = taskTitle?.let { t -> repo.tasksFor(v.id).firstOrNull { it.title.contains(t, ignoreCase = true) } }
            repo.logService(ServiceEntry(v.id, task?.id, task?.area ?: area, title, null, at, MeterReadings(miles, eng, gen),
                cost, "USD", vendor, null, null))
        }

        val rv = vehicle(Vehicle(name = "Big Blue", vehicleType = VehicleType.MOTORHOME, year = 2018, make = "Winnebago",
            model = "Vista 31BE", vinOrHin = "1F66F5DY8J0A08421", purchaseDate = day("2022-04-10"), purchaseMeter = 38_400.0,
            currentMiles = 61_230.0, currentGeneratorHours = 812.0, createdAt = 0, updatedAt = 0), 14)
        log(rv, "Roof sealant inspection & touch-up", Area.ROOF_SEALS, ago(13), miles = 52_100.0, cost = 64.0, vendor = "Self", taskTitle = "roof")
        log(rv, "Chassis oil & filter", Area.ENGINE, ago(10), miles = 54_900.0, cost = 89.5, vendor = "Self", taskTitle = "oil")
        log(rv, "Generator service", Area.ELECTRICAL, ago(7), miles = 57_300.0, gen = 760.0, cost = 142.0, vendor = "Camping World", taskTitle = "generator")
        log(rv, "Wheel bearing repack", Area.TIRES_WHEELS, ago(5), miles = 58_800.0, cost = 310.0, vendor = "Lazydays Service", taskTitle = "bearing")
        log(rv, "Brake fluid flush", Area.BRAKES, ago(3), miles = 60_100.0, cost = 120.0, vendor = "Lazydays Service", taskTitle = "brake")
        log(rv, "Water heater anode", Area.PLUMBING_WATER, ago(1, 12), miles = 61_000.0, cost = 38.0, vendor = "Self", taskTitle = "anode")
        repo.savePart(Part(vehicleId = rv.id, name = "Engine oil filter", partNumber = "Motorcraft FL-820S", manufacturer = "Motorcraft",
            fluidSpec = "Motorcraft 5W-20, 7 qt", createdAt = System.currentTimeMillis()))
        repo.savePart(Part(vehicleId = rv.id, name = "Lug nuts", torqueSpec = "140 ft-lb", quantity = "8 per wheel",
            createdAt = System.currentTimeMillis()))

        val boat = vehicle(Vehicle(name = "Reel Therapy", vehicleType = VehicleType.BOAT_POWER, year = 2015, make = "Sea Ray",
            model = "SPX 210", vinOrHin = "SERV4521K415", currentEngineHours = 418.0, createdAt = 0, updatedAt = 0), 10)
        log(boat, "Impeller replacement", Area.ENGINE, ago(14), eng = 330.0, cost = 185.0, vendor = "Marina Mechanics", taskTitle = "impeller")
        log(boat, "Lower unit oil", Area.DRIVETRAIN, ago(9), eng = 371.0, cost = 45.0, vendor = "Self", taskTitle = "gear")
        log(boat, "Hull zincs", Area.HULL, ago(4), eng = 402.0, cost = 62.0, vendor = "Self", taskTitle = "zinc")

        val bike = vehicle(Vehicle(name = "Wing", vehicleType = VehicleType.MOTORCYCLE, year = 2019, make = "Honda",
            model = "Gold Wing Tour", vinOrHin = "JH2SC7900KK100733", currentMiles = 18_950.0, createdAt = 0, updatedAt = 0), 9)
        log(bike, "Oil & filter", Area.ENGINE, ago(8), miles = 14_200.0, cost = 74.0, vendor = "Self", taskTitle = "oil")
        log(bike, "Rear tire", Area.TIRES_WHEELS, ago(2), miles = 17_600.0, cost = 289.0, vendor = "Cycle Gear", taskTitle = "tire")
        repo.updateMeters(bike.id, 19_480.0, null, null)

        // Two fake documents so the Documents tab and record detail have something to show.
        val receipt = demoImage(File(dir, "receipt.jpg"), "RECEIPT", listOf("Lazydays Service", "Wheel bearing repack", "Total  $310.00"))
        val card = demoImage(File(dir, "insurance.jpg"), "INSURANCE", listOf("Policy RV-2026-0412", "Big Blue · 2018 Vista", "Valid to 2027-04-10"))
        val bearingRecord = app.database.records().all().first { it.title.startsWith("Wheel bearing") }
        repo.addAttachment(rv.id, Uri.fromFile(receipt), AttachmentKind.RECEIPT, "Bearing repack invoice", bearingRecord.id)
        repo.addAttachment(rv.id, Uri.fromFile(card), AttachmentKind.INSURANCE, "Insurance card")
        repo.recomputeAll()
    }

    private fun demoImage(f: File, heading: String, lines: List<String>): File {
        val bmp = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(250, 248, 240))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 40, 40); textSize = 64f; isFakeBoldText = true }
        c.drawText(heading, 60f, 140f, p)
        p.textSize = 44f; p.isFakeBoldText = false
        lines.forEachIndexed { i, s -> c.drawText(s, 60f, 260f + i * 80f, p) }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return f
    }

    private companion object {
        const val TAG = "DebugData"
    }
}
