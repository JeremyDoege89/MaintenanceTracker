package dev.maintracker.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * App-private image files. Everything is copied in from the picker; we never keep external
 * URIs, which break on reinstall or when the source is deleted (Build Guide §3.6).
 */
class PhotoStore(private val context: Context) {
    private val photosDir get() = File(context.filesDir, "photos").apply { mkdirs() }
    private val attachmentsDir get() = File(context.filesDir, "attachments").apply { mkdirs() }

    /** Returns a path relative to filesDir, or null if the source can't be read. */
    fun importPhoto(source: Uri): String? {
        val name = "photos/${UUID.randomUUID()}.jpg"
        val dest = File(context.filesDir, name)
        photosDir
        val ok = context.contentResolver.openInputStream(source)?.use { input ->
            dest.outputStream().use { input.copyTo(it) }
            true
        } ?: false
        return if (ok) name else null
    }

    fun delete(relativePath: String) {
        File(context.filesDir, relativePath).takeIf { it.canonicalPath.startsWith(context.filesDir.canonicalPath) }
            ?.delete()
    }

    fun deleteAttachment(fileName: String) {
        File(attachmentsDir, fileName).delete()
    }

    /** Decodes downsampled to roughly [maxPx] on the long edge. Call off the main thread. */
    fun load(relativePath: String, maxPx: Int = 1280): Bitmap? {
        val file = File(context.filesDir, relativePath)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
