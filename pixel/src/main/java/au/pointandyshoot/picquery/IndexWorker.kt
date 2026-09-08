package au.pointandyshoot.picquery

import android.content.Context
import android.os.PowerManager
import android.provider.MediaStore
import androidx.work.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

object IndexScheduler {
    const val TAG = "photo-index"
    private fun constraints() = Constraints.Builder().setRequiresCharging(true)
        .setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build()
    fun configure(context: Context, enabled: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork("index-periodic"); work.cancelUniqueWork("index-changes"); work.cancelUniqueWork("index-auto")
            return
        }
        work.enqueueUniquePeriodicWork("index-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<IndexWorker>(6, TimeUnit.HOURS).setConstraints(constraints()).addTag(TAG).build())
        armChanges(context)
    }
    fun armChanges(context: Context, rearm: Boolean = false) {
        val c = Constraints.Builder().setRequiresCharging(true).setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true)
            .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
            .setTriggerContentUpdateDelay(30, TimeUnit.SECONDS).setTriggerContentMaxDelay(5, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniqueWork("index-changes", if (rearm) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<IndexWorker>().setConstraints(c).setInputData(workDataOf("trigger" to true)).addTag(TAG).build())
    }
    fun enqueue(context: Context, manual: Boolean) {
        val request = OneTimeWorkRequestBuilder<IndexWorker>().addTag(TAG)
            .setInputData(workDataOf("manual" to manual))
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
        if (!manual) request.setConstraints(constraints())
        else request.setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
        WorkManager.getInstance(context).enqueueUniqueWork(if (manual) "index-manual" else "index-auto", ExistingWorkPolicy.KEEP, request.build())
    }
    fun cancel(context: Context) { WorkManager.getInstance(context).cancelAllWorkByTag(TAG) }
}

class IndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val g = (applicationContext as PicQueryApp).graph
        val manual = inputData.getBoolean("manual", false)
        if (!manual && !g.preferences.value.automatic) return Result.success()
        if (!hasPhotos(applicationContext)) { g.status(IndexProgress("Grant photo access to index.")); return Result.success() }
        // Manual and automatic runs share a single writer, including clear-index operations.
        if (!g.operationLock.tryLock()) return Result.retry()
        var recognizer: com.google.mlkit.vision.text.TextRecognizer? = null
        var finished = false
        try {
            val startAccess = photoAccess(applicationContext)
            val library = PhotoLibrary(applicationContext)
            val settings = g.preferences.value
            g.status(IndexProgress("Checking for new or changed photos…", busy = true))
            val photos = library.snapshot()
            currentCoroutineContext().ensureActive()
            check(startAccess == photoAccess(applicationContext)) { "Photo access changed; please scan again." }
            g.index.prune(photos.mapTo(HashSet()) { it.uri.toString() })
            if (!settings.gps || !hasGps(applicationContext)) g.index.clearGps()
            if (!settings.ocr) g.index.clearOcr()
            val gpsWanted = settings.gps && hasGps(applicationContext)
            val visualWanted = g.engine.hasModels
            val pending = photos.filter { photo ->
                val old = g.index.get(photo.uri.toString())
                old == null || old.fingerprint != photo.fingerprint ||
                    (visualWanted && (old.vector == null || old.model != g.engine.modelId)) ||
                    (settings.ocr && !old.ocrDone) || (gpsWanted && !old.gpsDone)
            }
            val total = pending.size
            if (settings.ocr) recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            var processed = 0; var skipped = 0
            val start = System.nanoTime()
            val power = applicationContext.getSystemService(PowerManager::class.java)
            for (photo in pending) {
                currentCoroutineContext().ensureActive()
                if (power.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE || power.isPowerSaveMode) {
                    g.status(IndexProgress("Paused for temperature or Battery Saver; will retry.", processed, total))
                    return Result.retry()
                }
                // Short, checkpointed jobs avoid Android 16/17 long-running worker quotas.
                if (TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) >= 180) {
                    g.status(IndexProgress("Progress saved; next batch will resume shortly.", processed, total))
                    return Result.retry()
                }
                var bitmap: android.graphics.Bitmap? = null
                try {
                    val previous = g.index.get(photo.uri.toString())?.takeIf { it.fingerprint == photo.fingerprint }
                    val needVisual = visualWanted && (previous?.vector == null || previous.model != g.engine.modelId)
                    val needOcr = settings.ocr && previous?.ocrDone != true
                    if (needVisual || needOcr) bitmap = library.decode(photo.uri, if (needOcr) 1600 else 512)
                    val vector = if (needVisual) g.engine.image(bitmap!!) else previous?.vector
                    val ocr = if (needOcr) recognizer!!.process(InputImage.fromBitmap(bitmap!!, 0)).await().text.take(32000)
                        else previous?.ocr ?: ""
                    val gps = if (gpsWanted) library.coordinates(photo.uri) else null
                    currentCoroutineContext().ensureActive()
                    check(hasPhotos(applicationContext)) { "Photo access was removed." }
                    // Settings can change while inference is running. Never save disabled data.
                    val now = g.preferences.value
                    g.index.upsert(IndexedPhoto(photo.uri.toString(), photo.fingerprint, photo.name, photo.taken,
                        gps?.get(0).takeIf { now.gps && hasGps(applicationContext) },
                        gps?.get(1).takeIf { now.gps && hasGps(applicationContext) },
                        if (now.ocr) ocr else "", vector, if (needVisual) g.engine.modelId else previous?.model ?: "",
                        now.ocr && (needOcr || previous?.ocrDone == true), now.gps && gpsWanted))
                } catch (e: CancellationException) { throw e }
                catch (e: SecurityException) { skipped++ }
                catch (e: java.io.IOException) { skipped++ }
                finally { bitmap?.recycle() }
                processed++
                g.refreshCount()
                g.status(IndexProgress("Indexing on device · $skipped unreadable", processed, total, true))
                setProgress(workDataOf("completed" to processed, "total" to total))
            }
            g.refreshCount()
            g.status(IndexProgress(if (visualWanted) "Up to date · ${g.count.value} photos · $skipped unreadable"
                else "OCR/date index ready. Visual models are not bundled in this build.", total, total))
            finished = true
            return Result.success()
        } catch (e: CancellationException) {
            g.status(IndexProgress("Indexing stopped. Completed photos are saved.")); throw e
        } catch (e: Exception) {
            // Do not log exceptions that can contain photo URIs, OCR, queries or GPS.
            g.status(IndexProgress("Indexing could not finish (${e.javaClass.simpleName}). Check model status and photo access."))
            return if (runAttemptCount < 2) Result.retry() else Result.failure()
        } finally {
            recognizer?.close()
            g.operationLock.unlock()
            if (inputData.getBoolean("trigger", false) && finished && g.preferences.value.automatic) IndexScheduler.armChanges(applicationContext, true)
        }
    }
}
