package dev.maintracker.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
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

    data class Imported(val fileName: String, val mimeType: String, val byteSize: Long)

    /** Copies a picked/captured document into private storage (§3.6). Null if it can't be read. */
    fun importAttachment(source: Uri): Imported? {
        val tmp = File(attachmentsDir, "${UUID.randomUUID()}.part")
        val ok = runCatching {
            context.contentResolver.openInputStream(source)?.use { input -> tmp.outputStream().use { input.copyTo(it) } } != null
        }.getOrDefault(false)
        if (!ok || tmp.length() == 0L) { tmp.delete(); return null }
        // Providers don't always say (file:// URIs, some file managers): fall back to the
        // name's extension, then to the file's own magic bytes, so photos still get thumbnails.
        val mime = context.contentResolver.getType(source)?.takeUnless { it == GENERIC }
            ?: source.lastPathSegment?.substringAfterLast('.', "")?.lowercase()
                ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
            ?: sniff(tmp)
            ?: GENERIC
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        val dest = File(attachmentsDir, tmp.nameWithoutExtension + ".$ext")
        if (!tmp.renameTo(dest)) { tmp.delete(); return null }
        return Imported(dest.name, mime, dest.length())
    }

    private fun sniff(f: File): String? {
        val h = ByteArray(12)
        val n = f.inputStream().use { it.read(h) }
        fun at(i: Int, vararg b: Int) = n >= i + b.size && b.indices.all { h[i + it] == b[it].toByte() }
        return when {
            at(0, 0xFF, 0xD8, 0xFF) -> "image/jpeg"
            at(0, 0x89, 0x50, 0x4E, 0x47) -> "image/png"
            at(0, 0x25, 0x50, 0x44, 0x46) -> "application/pdf" // %PDF
            at(0, 0x52, 0x49, 0x46, 0x46) && at(8, 0x57, 0x45, 0x42, 0x50) -> "image/webp" // RIFF....WEBP
            at(4, 0x66, 0x74, 0x79, 0x70) && (at(8, 0x68, 0x65, 0x69, 0x63) || at(8, 0x6D, 0x69, 0x66, 0x31)) -> "image/heic"
            else -> null
        }
    }

    /** Writes bytes (from a backup) as a private attachment file. */
    fun writeAttachment(fileName: String, bytes: ByteArray) = File(attachmentsDir, fileName).writeBytes(bytes)

    fun attachmentFile(fileName: String): File = File(attachmentsDir, fileName)

    /** Path relative to filesDir, for [load]. */
    fun attachmentPath(fileName: String) = "attachments/$fileName"

    /** content:// URI another app can open (granted per intent). */
    fun shareUri(fileName: String): Uri = FileProvider.getUriForFile(context, authority, attachmentFile(fileName))

    /** A fresh cache file for the camera to write into; imported (copied) after capture. */
    fun newCameraUri(): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // previous captures were already copied in
        return FileProvider.getUriForFile(context, authority, File(dir, "${UUID.randomUUID()}.jpg"))
    }

    /** Bytes used by photos and attachments (Settings > Data). */
    fun usage(): Pair<Long, Long> = dirSize(photosDir) to dirSize(attachmentsDir)

    fun deleteAllFiles() {
        photosDir.deleteRecursively()
        attachmentsDir.deleteRecursively()
    }

    private fun dirSize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private companion object { const val GENERIC = "application/octet-stream" }

    private val authority get() = "${context.packageName}.files"

    /** Decodes downsampled to roughly [maxPx] on the long edge. Call off the main thread. */
    fun load(relativePath: String, maxPx: Int = 1280): Bitmap? {
        val file = File(context.filesDir, relativePath)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        // Camera JPEGs are usually stored sideways with an EXIF rotation tag; BitmapFactory ignores it.
        val degrees = runCatching {
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (degrees == 0f) return bmp
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
    }
}
