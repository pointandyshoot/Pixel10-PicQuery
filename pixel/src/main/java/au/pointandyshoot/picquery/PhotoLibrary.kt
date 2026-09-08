package au.pointandyshoot.picquery

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.CancellationSignal
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class MediaPhoto(val uri: Uri, val name: String, val taken: Long, val fingerprint: String)

class PhotoLibrary(private val context: Context) {
    // Pixel 10 has no removable media. A concrete volume also avoids ID collisions.
    private val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    suspend fun snapshot(): List<MediaPhoto> {
        check(hasPhotos(context)) { "Photo access is required." }
        val cols = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE, MediaStore.Images.Media.GENERATION_MODIFIED)
        val version = MediaStore.getVersion(context, MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val photos = ArrayList<MediaPhoto>()
        val cursor = context.contentResolver.query(collection, cols,
            "${MediaStore.Images.Media.IS_PENDING}=0 AND ${MediaStore.Images.Media.IS_TRASHED}=0", null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media._ID} DESC")
            ?: error("Photo library is temporarily unavailable.")
        cursor.use { c ->
            while (c.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val uri = ContentUris.withAppendedId(collection, c.getLong(0))
                val modified = c.getLong(3)
                photos += MediaPhoto(uri, c.getString(1) ?: "Photo", c.getLong(2).takeIf { it > 0 } ?: modified * 1000,
                    "$version:${c.getLong(5)}:$modified:${c.getLong(4)}")
            }
        }
        return photos
    }
    fun decode(uri: Uri, maxSide: Int): Bitmap {
        require(uri.scheme == "content") { "Only local content URIs are supported." }
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            val scale = minOf(1.0, maxSide.toDouble() / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
        }
    }
    fun coordinates(uri: Uri): DoubleArray? {
        if (!hasGps(context)) return null
        return context.contentResolver.openInputStream(MediaStore.setRequireOriginal(uri))?.use { input ->
            ExifInterface(input).latLong?.takeIf { it[0].isFinite() && it[1].isFinite() && it[0] in -90.0..90.0 && it[1] in -180.0..180.0 }
        }
    }
}
