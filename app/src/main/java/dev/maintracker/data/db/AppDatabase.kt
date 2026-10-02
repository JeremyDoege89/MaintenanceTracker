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

@Database(
    entities = [
        Vehicle::class,
        MaintenanceTemplate::class,
        MaintenanceTask::class,
        ServiceRecord::class,
        Part::class,
        Attachment::class,
        Reminder::class,
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

    companion object {
        const val VERSION = 2
        const val NAME = "maintracker.db"

        /** Every schema bump adds its Migration here; never fallbackToDestructiveMigration. */
        val MIGRATIONS = arrayOf<androidx.room.migration.Migration>(MIGRATION_1_2)

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
