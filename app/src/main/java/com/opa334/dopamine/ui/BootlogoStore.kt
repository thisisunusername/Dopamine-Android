package com.opa334.dopamine.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.opa334.dopamine.core.JailbreakState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copies the picked boot logo into the app's private storage.
 *
 * The picker hands back a `content://` URI whose read grant belongs to this process. That is enough
 * to decode the picture once, but it is not a file the app may assume it can re-open later: the
 * grant is tied to the process, and after a restart the URI can be unreadable. Since the boot
 * sequence has to work at launch, the bytes are copied in at pick time and the copy is what is
 * kept.
 *
 * The picture is also normalised: decoded first, then written out as PNG. A file picked from a
 * camera roll can be HEIC or a huge JPEG, and `BitmapFactory.decodeFile` on the boot path would
 * either fail or allocate tens of megabytes. Storing a decoded-and-re-encoded PNG means the boot
 * sequence reads one format it always understands.
 */
object BootlogoStore {

    private const val DIR = "bootlogo"
    private const val FILE = "custom_bootlogo.png"

    /**
     * Longest edge kept, in pixels.
     *
     * The boot image is drawn at about 160 dp, so 1024 px covers a 3x screen with room to spare
     * while keeping the file small enough to decode on the launch path without a visible pause.
     */
    private const val MAX_EDGE = 1024

    fun targetFile(context: Context): File =
        File(File(context.filesDir, DIR).apply { mkdirs() }, FILE)

    /**
     * Copies [uri] in and records its path. Returns the saved path, or null if it could not be read.
     *
     * Runs on Dispatchers.IO: decoding and writing a picture on the main thread is exactly the kind
     * of work that made the app feel frozen.
     */
    suspend fun copyInto(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val folder = File(context.filesDir, DIR).apply { mkdirs() }
        val out = File(folder, FILE)

        val decoded = runCatching {
            // First pass: bounds only, so a 8000x8000 photo cannot be decoded at full size.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val sample = sampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }.getOrNull() ?: return@withContext null

        val written = runCatching {
            out.outputStream().use { stream ->
                decoded.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }.getOrDefault(false)

        decoded.recycle()
        if (!written) return@withContext null

        val path = out.absolutePath
        JailbreakState.setCustomBootlogoPath(context, path)
        path
    }

    /** Removes the stored picture, if any. */
    fun clear(context: Context) {
        targetFile(context).delete()
        JailbreakState.clearCustomBootlogoImage(context)
    }

    /** The largest power-of-two skip that still leaves both edges at or above [maxEdge]. */
    private fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= maxEdge || height / (sample * 2) >= maxEdge) {
            sample *= 2
        }
        return sample
    }
}