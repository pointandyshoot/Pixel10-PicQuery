package au.pointandyshoot.picquery

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import org.json.JSONArray
import org.json.JSONObject

enum class Backend { AUTO, GPU, CPU }
data class Preferences(val backend: Backend = Backend.AUTO, val automatic: Boolean = true, val ocr: Boolean = true, val gps: Boolean = false)
data class RuntimeStatus(
    val image: String = "Not initialised", val text: String = "Not initialised",
    val detail: String = "Auto prefers Tensor G5 TPU, then GPU, then CPU.", val lastImageMs: Long = 0
)
data class IndexProgress(val message: String = "Ready to index", val completed: Int = 0, val total: Int = 0, val busy: Boolean = false)

class PicQueryApp : Application() {
    lateinit var graph: AppGraph
    private var observer: ContentObserver? = null
    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        val handler = Handler(Looper.getMainLooper())
        val refresh = Runnable { if (graph.preferences.value.automatic && hasPhotos(this)) IndexScheduler.enqueue(this, false) }
        observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                handler.removeCallbacks(refresh); handler.postDelayed(refresh, 5000)
            }
        }
        contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer!!)
        IndexScheduler.configure(this, graph.preferences.value.automatic)
    }
}

fun hasPhotos(context: Context): Boolean = context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
    context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
fun hasGps(context: Context): Boolean = context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
fun photoAccess(context: Context): String = when {
    context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED -> "All photos"
    hasPhotos(context) -> "Selected photos only"
    else -> "No photo access"
}

class AppGraph(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val operationLock = Mutex()
    private val saved = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val preferences = MutableStateFlow(Preferences(
        runCatching { Backend.valueOf(saved.getString("backend", "AUTO")!!) }.getOrDefault(Backend.AUTO),
        saved.getBoolean("automatic", true), saved.getBoolean("ocr", true), saved.getBoolean("gps", false)))
    val runtime = MutableStateFlow(RuntimeStatus())
    val progress = MutableStateFlow(IndexProgress(saved.getString("last_status", "Ready to index")!!))
    val places = MutableStateFlow(readPlaces())
    val index = PhotoIndex(context)
    val engine = EmbeddingEngine(context, preferences, runtime)
    val count = MutableStateFlow(0)
    init { scope.launch { refreshCount() } }
    fun refreshCount() { count.value = index.count() }
    fun update(p: Preferences) {
        val old = preferences.value
        preferences.value = p
        saved.edit().putString("backend", p.backend.name).putBoolean("automatic", p.automatic)
            .putBoolean("ocr", p.ocr).putBoolean("gps", p.gps).apply()
        IndexScheduler.configure(context, p.automatic)
        if ((old.ocr && !p.ocr) || (old.gps && !p.gps)) scope.launch {
            operationLock.lock()
            try {
                if (!preferences.value.ocr) index.clearOcr()
                if (!preferences.value.gps) index.clearGps()
            } finally { operationLock.unlock() }
        }
    }
    fun status(value: IndexProgress) {
        progress.value = value
        if (!value.busy) saved.edit().putString("last_status", value.message).apply()
    }
    fun savePlace(place: Place) {
        require(place.name.trim().isNotBlank() && place.name.length <= 60 && place.latitude.isFinite() && place.longitude.isFinite() &&
            place.latitude in -90.0..90.0 && place.longitude in -180.0..180.0) { "Enter a name and valid latitude/longitude." }
        val next = places.value.filterNot { it.name.equals(place.name, true) } + place.copy(name = place.name.trim())
        require(next.size <= 100) { "Up to 100 saved places are supported." }
        writePlaces(next)
    }
    fun removePlace(place: Place) = writePlaces(places.value - place)
    private fun writePlaces(next: List<Place>) {
        places.value = next
        val json = JSONArray()
        next.forEach { json.put(JSONObject().put("name", it.name).put("lat", it.latitude).put("lon", it.longitude)) }
        saved.edit().putString("places", json.toString()).apply()
    }
    private fun readPlaces(): List<Place> = runCatching {
        val a = JSONArray(saved.getString("places", "[]"))
        List(a.length()) { val p = a.getJSONObject(it); Place(p.getString("name"), p.getDouble("lat"), p.getDouble("lon")) }
    }.getOrDefault(emptyList())
    fun clear() {
        update(preferences.value.copy(automatic = false))
        IndexScheduler.cancel(context)
        scope.launch {
            operationLock.lock()
            try {
                index.clear(); engine.close(); refreshCount()
                status(IndexProgress("Index cleared. Automatic indexing is off."))
            } finally { operationLock.unlock() }
        }
    }
}
