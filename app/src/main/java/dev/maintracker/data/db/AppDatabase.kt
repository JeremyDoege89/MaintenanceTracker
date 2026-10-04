package dev.maintracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.maintracker.data.model.Attachment
import dev.maintracker.data.model.MaintenanceTask
import dev.maintracker.data.model.MaintenanceTemplate
import dev.maintracker.data.model.Part
import dev.maintracker.data.model.Reminder
import dev.maintracker.data.model.ServiceRecord
import dev.maintracker.data.model.Vehicle
import dev.maintracker.data.model.VinDecodeCache

@Database(
    entities = [
        Vehicle::class,
        MaintenanceTemplate::class,
        MaintenanceTask::class,
        ServiceRecord::class,
        Part::class,
        Attachment::class,
        Reminder::class,
        VinDecodeCache::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicles(): VehicleDao
    abstract fun templates(): TemplateDao
    abstract fun tasks(): TaskDao
    abstract fun records(): ServiceRecordDao
    abstract fun parts(): PartDao
    abstract fun attachments(): AttachmentDao
    abstract fun reminders(): ReminderDao
    abstract fun vinDecodes(): VinDecodeCacheDao

    companion object {
        const val VERSION = 4
        const val NAME = "maintracker.db"

        /** Every schema bump adds its Migration here; never fallbackToDestructiveMigration. */
        val MIGRATIONS = arrayOf<androidx.room.migration.Migration>(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}

/** v2: who did the work (Self / Shop / Dealer / Mobile). Nullable: older records simply have none. */
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE service_record ADD COLUMN vendorType TEXT")
    }
}

/**
 * v3: maintenance library (docs/maintenance-library/IMPLEMENTATION.md). Template metadata, the
 * task's kind + safety flag, and the vehicle's Setup tags/answers. Defaults mirror the entities'
 * @ColumnInfo(defaultValue) so Room's schema validation passes.
 */
val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        listOf(
            "ALTER TABLE maintenance_template ADD COLUMN appliesTo TEXT NOT NULL DEFAULT 'all'",
            "ALTER TABLE maintenance_template ADD COLUMN kind TEXT NOT NULL DEFAULT 'RECURRING'",
            "ALTER TABLE maintenance_template ADD COLUMN phase INTEGER NOT NULL DEFAULT 1",
            "ALTER TABLE maintenance_template ADD COLUMN safetyCritical INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE maintenance_template ADD COLUMN dateBasis TEXT",
            "ALTER TABLE maintenance_template ADD COLUMN season TEXT",
            "ALTER TABLE maintenance_template ADD COLUMN eventTrigger TEXT",
            "ALTER TABLE maintenance_template ADD COLUMN altMeterInterval INTEGER",
            "ALTER TABLE maintenance_template ADD COLUMN altMeterUnit TEXT",
            "ALTER TABLE maintenance_template ADD COLUMN typicalRange TEXT",
            "ALTER TABLE maintenance_template ADD COLUMN severeService TEXT",
            "ALTER TABLE maintenance_template ADD COLUMN diyPro TEXT",
            "ALTER TABLE maintenance_template ADD COLUMN source TEXT",
            "ALTER TABLE maintenance_template ADD COLUMN sourceTier TEXT",
            "ALTER TABLE maintenance_template ADD COLUMN libraryGroup TEXT",
            "ALTER TABLE maintenance_template ADD COLUMN retired INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE maintenance_task ADD COLUMN kind TEXT NOT NULL DEFAULT 'RECURRING'",
            "ALTER TABLE maintenance_task ADD COLUMN safetyCritical INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE vehicle ADD COLUMN configTags TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE vehicle ADD COLUMN setupAnswers TEXT",
        ).forEach(db::execSQL)
    }
}

/** v4: cache of NHTSA VIN decodes. A new table only; no existing rows change. */
val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `vin_decode_cache` (`vin` TEXT NOT NULL, `make` TEXT NOT NULL, " +
                "`model` TEXT, `modelYear` INTEGER, `vehicleType` TEXT, `errorCode` TEXT, `rawJson` TEXT NOT NULL, " +
                "`fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`vin`))",
        )
    }
}
