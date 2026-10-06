package dev.dreamz.weekendfleet.data

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import dev.dreamz.weekendfleet.data.db.AppDatabase

/** A [LibrarySync] over the shipped assets with its applied version kept in memory. */
fun testLibrarySync(
    db: AppDatabase,
    ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext,
): LibrarySync {
    var version = 0
    return LibrarySync(db, LibraryAssets(ctx.assets::open), storedVersion = { version }, setStoredVersion = { version = it })
}
