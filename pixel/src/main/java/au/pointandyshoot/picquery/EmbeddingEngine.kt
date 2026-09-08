package au.pointandyshoot.picquery

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** All creation, invocation and disposal stays on the same thread (GPU affinity). */
class EmbeddingEngine(
    private val context: Context,
    private val preferences: MutableStateFlow<Preferences>,
    private val status: MutableStateFlow<RuntimeStatus>
) {
    private val dispatcher = Executors.newSingleThreadExecutor { task -> Thread(task, "photo-inference") }.asCoroutineDispatcher()
    private val assets by lazy { context.assets.list("")!!.toSet() }
    val hasModels get() = "image_model.tflite" in assets && "text_model.tflite" in assets
    val modelId: String by lazy {
        runCatching { context.assets.open("model-id.txt").bufferedReader().use { it.readText().trim() } }
            .getOrDefault("MobileCLIP2-S0-unversioned")
    }
    private val tokenizer by lazy { BPETokenizer(context) }
    private var imageSession: Session? = null
    private var textSession: Session? = null
    private var chosen: Backend? = null
    private val failed = mutableSetOf<Accelerator>()
    private var reason = ""
    private var tpuReason = ""

    private class Session(val model: CompiledModel, val accelerator: Accelerator, val environment: Environment? = null) : AutoCloseable {
        val inputs: List<TensorBuffer> = model.createInputBuffers()
        val outputs: List<TensorBuffer> = try { model.createOutputBuffers() } catch (e: Exception) { inputs.forEach { it.close() }; throw e }
        init {
            if (inputs.size != 1 || outputs.size != 1) {
                inputs.forEach { it.close() }; outputs.forEach { it.close() }
                error("Expected a single-input embedding model.")
            }
        }
        fun run(data: FloatArray): FloatArray { inputs[0].writeFloat(data); model.run(inputs, outputs); return normalise(outputs[0].readFloat()) }
        fun run(data: IntArray): FloatArray { inputs[0].writeInt(data); model.run(inputs, outputs); return normalise(outputs[0].readFloat()) }
        override fun close() { inputs.forEach { it.close() }; outputs.forEach { it.close() }; model.close(); environment?.close() }
    }
    private fun open(asset: String, accelerator: Accelerator): Session {
        val options = CompiledModel.Options(accelerator).apply { cpuOptions = CompiledModel.CpuOptions(numThreads = 4) }
        var env: Environment? = null
        var model: CompiledModel? = null
        try {
            if (accelerator == Accelerator.NPU) {
                env = Environment.create(mapOf(Environment.Option.DispatchLibraryDir to context.applicationInfo.nativeLibraryDir))
            }
            model = if (env == null) CompiledModel.create(context.assets, asset, options)
                else CompiledModel.create(context.assets, asset, options, env)
            return Session(model, accelerator, env)
        } catch (e: Exception) { model?.close(); env?.close(); throw e }
        catch (e: LinkageError) { model?.close(); env?.close(); throw e }
    }
    private fun configure() {
        val wanted = preferences.value.backend
        if (chosen != wanted) {
            imageSession?.close(); imageSession = null
            failed.clear(); reason = ""; tpuReason = ""; chosen = wanted
        }
        check(hasModels) { "Visual model assets are not bundled. See the installation guide." }
    }
    private fun candidateList(): List<Accelerator> {
        if (chosen == Backend.CPU) return listOf(Accelerator.CPU)
        if (chosen == Backend.GPU) return listOf(Accelerator.GPU, Accelerator.CPU)
        val pixelG5 = Build.MANUFACTURER.equals("Google", true) && Build.SOC_MODEL.contains("G5", true)
        val tpuPack = "image_model_tensor_g5.tflite" in assets &&
            File(context.applicationInfo.nativeLibraryDir, "libLiteRtDispatch.so").isFile
        if (!pixelG5 || !tpuPack) {
            tpuReason = if (!pixelG5) "Tensor G5 was not detected. " else "Tensor G5 AOT model/runtime not bundled. "
            return listOf(Accelerator.GPU, Accelerator.CPU)
        }
        return listOf(Accelerator.NPU, Accelerator.GPU, Accelerator.CPU)
    }
    suspend fun image(bitmap: Bitmap): FloatArray = withContext(dispatcher) {
        configure()
        val input = pixels(bitmap)
        for (accelerator in candidateList().filterNot { it in failed }) {
            try {
                if (imageSession?.accelerator != accelerator) {
                    imageSession?.close(); imageSession = null
                    imageSession = open(if (accelerator == Accelerator.NPU) "image_model_tensor_g5.tflite" else "image_model.tflite", accelerator)
                }
                val start = System.nanoTime()
                val output = imageSession!!.run(input)
                val label = when (accelerator) {
                    Accelerator.NPU -> "Tensor G5 AOT / NPU invocation succeeded"
                    Accelerator.GPU -> "GPU invocation succeeded"
                    else -> "CPU · 4 threads"
                }
                status.value = status.value.copy(image = label, lastImageMs = (System.nanoTime() - start) / 1_000_000,
                    detail = tpuReason + reason + if (accelerator == Accelerator.CPU) "LiteRT CPU execution."
                    else "LiteRT does not report per-operation hardware here; CPU partitions may still be used.")
                return@withContext output
            } catch (e: Exception) {
                imageSession?.close(); imageSession = null
                failed += accelerator; reason += "$accelerator failed (${e.javaClass.simpleName}); "
            } catch (e: LinkageError) {
                imageSession?.close(); imageSession = null
                failed += accelerator; reason += "$accelerator runtime unavailable; "
            }
        }
        status.value = status.value.copy(image = "Unavailable", detail = tpuReason + reason)
        error("All image backends failed. See Settings → Inference.")
    }
    suspend fun text(query: String): FloatArray = withContext(dispatcher) {
        configure()
        // The integer-token text tower is small enough for infrequent CPU queries; no NNAPI.
        try {
            if (textSession == null) textSession = open("text_model.tflite", Accelerator.CPU)
            val result = textSession!!.run(tokenizer.tokenize(query).first)
            status.value = status.value.copy(text = "CPU · 4 threads")
            result
        } catch (e: Exception) {
            textSession?.close(); textSession = null
            status.value = status.value.copy(text = "Unavailable (${e.javaClass.simpleName})")
            throw e
        }
    }
    suspend fun close() = withContext(dispatcher) {
        imageSession?.close(); textSession?.close(); imageSession = null; textSession = null
        failed.clear(); chosen = null; status.value = RuntimeStatus()
    }
    private fun pixels(bitmap: Bitmap): FloatArray {
        // MobileCLIP2-S0: resize shortest edge to 256, centre crop, RGB NCHW / 255.
        val side = minOf(bitmap.width, bitmap.height)
        val left = (bitmap.width - side) / 2; val top = (bitmap.height - side) / 2
        val resized = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(resized)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(bitmap, android.graphics.Rect(left, top, left + side, top + side),
            android.graphics.Rect(0, 0, 256, 256), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        val pixels = IntArray(256 * 256)
        resized.getPixels(pixels, 0, 256, 0, 0, 256, 256)
        resized.recycle()
        val data = FloatArray(3 * pixels.size)
        for (i in pixels.indices) {
            data[i] = ((pixels[i] shr 16) and 255) / 255f
            data[i + pixels.size] = ((pixels[i] shr 8) and 255) / 255f
            data[i + pixels.size * 2] = (pixels[i] and 255) / 255f
        }
        return data
    }
}
