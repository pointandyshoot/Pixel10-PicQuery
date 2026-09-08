package au.pointandyshoot.picquery

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class IndexedPhoto(
    val uri: String, val fingerprint: String, val name: String, val taken: Long,
    val latitude: Double?, val longitude: Double?, val ocr: String,
    val vector: FloatArray?, val model: String, val ocrDone: Boolean, val gpsDone: Boolean
)
data class SearchHit(val uri: String, val name: String, val score: Double, val matchedText: Boolean)

class PhotoIndex(context: Context) : SQLiteOpenHelper(context, "photo-index.db", null, 1) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE photos (
            uri TEXT PRIMARY KEY, fingerprint TEXT NOT NULL, name TEXT NOT NULL, taken INTEGER NOT NULL,
            latitude REAL, longitude REAL, ocr TEXT NOT NULL, vector BLOB, model TEXT NOT NULL,
            ocr_done INTEGER NOT NULL, gps_done INTEGER NOT NULL)""")
        db.execSQL("CREATE INDEX photos_date ON photos(taken)")
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = error("Unsupported index version; clear app data to rebuild.")
    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM photos", null).use { it.moveToFirst(); it.getInt(0) }
    fun embeddingCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM photos WHERE vector IS NOT NULL", null).use { it.moveToFirst(); it.getInt(0) }
    fun get(uri: String): IndexedPhoto? = readableDatabase.query("photos", null, "uri=?", arrayOf(uri), null, null, null).use {
        if (it.moveToFirst()) row(it) else null
    }
    private fun row(c: android.database.Cursor): IndexedPhoto {
        fun s(key: String) = c.getString(c.getColumnIndexOrThrow(key))
        fun d(key: String): Double? = c.getColumnIndexOrThrow(key).let { if (c.isNull(it)) null else c.getDouble(it) }
        val vi = c.getColumnIndexOrThrow("vector")
        val vector = if (c.isNull(vi)) null else {
            val bytes = c.getBlob(vi)
            if (bytes.size != 512 * 4) null else FloatArray(512).also {
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it)
            }
        }
        return IndexedPhoto(s("uri"), s("fingerprint"), s("name"), c.getLong(c.getColumnIndexOrThrow("taken")),
            d("latitude"), d("longitude"), s("ocr"), vector, s("model"),
            c.getInt(c.getColumnIndexOrThrow("ocr_done")) == 1, c.getInt(c.getColumnIndexOrThrow("gps_done")) == 1)
    }
    fun upsert(photo: IndexedPhoto) {
        val v = ContentValues().apply {
            put("uri", photo.uri); put("fingerprint", photo.fingerprint); put("name", photo.name); put("taken", photo.taken)
            put("latitude", photo.latitude); put("longitude", photo.longitude); put("ocr", photo.ocr.take(32000)); put("model", photo.model)
            put("ocr_done", if (photo.ocrDone) 1 else 0); put("gps_done", if (photo.gpsDone) 1 else 0)
            if (photo.vector == null) putNull("vector") else {
                require(photo.vector.size == 512 && photo.vector.all { it.isFinite() })
                val buffer = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN)
                photo.vector.forEach(buffer::putFloat); put("vector", buffer.array())
            }
        }
        writableDatabase.insertWithOnConflict("photos", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }
    // Only call after a complete successful MediaStore snapshot. Never prune from an interrupted query.
    fun prune(visible: Set<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery("SELECT uri FROM photos", null).use { c ->
                while (c.moveToNext()) if (c.getString(0) !in visible) db.delete("photos", "uri=?", arrayOf(c.getString(0)))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun clear() { writableDatabase.delete("photos", null, null); writableDatabase.execSQL("VACUUM") }
    fun clearGps() { writableDatabase.execSQL("UPDATE photos SET latitude=NULL, longitude=NULL, gps_done=0") }
    fun clearOcr() { writableDatabase.execSQL("UPDATE photos SET ocr='', ocr_done=0") }

    fun search(query: SearchQuery, vector: FloatArray?, model: String, limit: Int = 100, visible: Set<String>? = null): List<SearchHit> {
        require(limit in 1..1000)
        val clauses = mutableListOf<String>(); val args = mutableListOf<String>()
        query.after?.let { clauses += "taken>=?"; args += it.toString() }
        query.before?.let { clauses += "taken<?"; args += it.toString() }
        val queue = java.util.PriorityQueue<SearchHit>(compareBy { it.score })
        readableDatabase.query("photos", null, clauses.joinToString(" AND ").ifBlank { null }, args.toTypedArray(), null, null, "taken DESC").use { c ->
            while (c.moveToNext()) {
                val p = row(c)
                if (visible != null && p.uri !in visible) continue
                if (query.near != null && (p.latitude == null || p.longitude == null ||
                    distanceKm(query.near.latitude, query.near.longitude, p.latitude, p.longitude) > query.near.radiusKm)) continue
                val exact = query.ocr?.let { p.ocr.contains(it, ignoreCase = true) } ?: false
                if (query.ocr != null && !exact) continue
                val words = query.visual.lowercase().split(Regex("\\W+")).filter { it.length > 2 }
                val matches = if (words.isEmpty()) 0.0 else words.count { p.ocr.contains(it, true) }.toDouble() / words.size
                val visualScore = if (vector != null && p.vector != null && p.model == model) cosine(vector, p.vector) else 0.0
                if (vector != null && (p.vector == null || p.model != model) && matches == 0.0 && !exact) continue
                if (query.visual.isNotBlank() && vector == null && matches == 0.0) continue
                val score = if (query.visual.isBlank()) 1.0 else (visualScore + 0.18 * matches).coerceAtMost(1.0)
                if (queue.size >= limit && score <= queue.peek()!!.score) continue
                queue.add(SearchHit(p.uri, p.name, score, exact || matches > 0))
                if (queue.size > limit) queue.poll()
            }
        }
        return queue.toList().sortedByDescending { it.score }
    }
}
