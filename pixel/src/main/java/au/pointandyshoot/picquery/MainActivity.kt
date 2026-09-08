package au.pointandyshoot.picquery

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as PicQueryApp).graph
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF216355), secondary = Color(0xFF516451),
                background = Color(0xFFF6F8F1), surface = Color(0xFFF6F8F1))) {
                Surface(Modifier.fillMaxSize()) { PhotoSearch(graph) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoSearch(g: AppGraph) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var settings by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf("Describe a photo, or search text on a sign.") }
    var selected by remember { mutableStateOf<SearchHit?>(null) }
    var access by remember { mutableStateOf(photoAccess(context)) }
    val preferences by g.preferences.collectAsState()
    val progress by g.progress.collectAsState()
    val count by g.count.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = photoAccess(context)
        if (hasPhotos(context)) {
            IndexScheduler.configure(context, g.preferences.value.automatic)
            IndexScheduler.enqueue(context, false)
            message = "Photo access updated. Automatic indexing waits until charging."
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                access = photoAccess(context)
                hits = emptyList(); selected = null
                if (!hasPhotos(context)) { searchJob?.cancel(); message = "Grant photo access to search." }
                if (!hasGps(context) && g.preferences.value.gps) g.update(g.preferences.value.copy(gps = false))
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun search(uri: Uri? = null) {
        keyboard?.hide()
        searchJob?.cancel()
        searchJob = scope.launch {
            searching = true; hits = emptyList()
            try {
                check(hasPhotos(context)) { "Grant photo access first." }
                val results = withContext(Dispatchers.IO) {
                    run {
                        val visible = PhotoLibrary(context).snapshot().mapTo(HashSet()) { it.uri.toString() }
                        if (uri != null) {
                            check(g.engine.hasModels) { "This build needs visual model assets; see Settings." }
                            val bitmap = PhotoLibrary(context).decode(uri, 512)
                            try { g.index.search(SearchQuery("", null, null, null, null), g.engine.image(bitmap), g.engine.modelId, visible = visible) }
                            finally { bitmap.recycle() }
                        } else {
                            require(query.isNotBlank()) { "Enter a description or filter." }
                            val parsed = SearchQuery.parse(query, g.places.value)
                            val vector = if (parsed.visual.isNotBlank() && g.engine.hasModels) g.engine.text(parsed.visual) else null
                            g.index.search(parsed, vector, g.engine.modelId, visible = visible)
                        }
                    }
                }
                hits = results
                message = if (results.isEmpty()) "No matches. Check your filters and whether indexing has finished."
                    else "${results.size} closest matches · similarity is not a probability"
            } catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { message = e.message ?: "Check your search filters." }
            catch (e: IllegalStateException) { message = e.message ?: "Check Settings for model status." }
            catch (e: Exception) { message = "Search could not finish (${e.javaClass.simpleName}). Check Settings." }
            finally { searching = false }
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) search(uri) }
    Scaffold(topBar = {
        TopAppBar(title = { Column { Text(if (settings) "Settings" else "Pixel Photo Search");
            Text("Private, on your phone", style = MaterialTheme.typography.labelMedium) } },
            actions = { TextButton(onClick = { searchJob?.cancel(); hits = emptyList(); selected = null; settings = !settings }) { Text(if (settings) "Back" else "Settings") } })
    }) { padding ->
        if (settings) SettingsPage(g, Modifier.padding(padding)) else Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            if (!hasPhotos(context)) {
                Card(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Find the photo you remember", style = MaterialTheme.typography.headlineSmall)
                        Text("Photos, visual fingerprints and recognised text stay on this phone. Choose all photos or just a selection. The app has no internet permission.")
                        Button(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) }) { Text("Choose photo access") }
                    }
                }
            }
            Text("$count indexed · $access", style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) }) { Text("Manage photo access") }
            OutlinedTextField(value = query, onValueChange = { query = it.take(4096) }, modifier = Modifier.fillMaxWidth(),
                label = { Text("What are you looking for?") }, placeholder = { Text("camper beside a river") },
                maxLines = 3, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { search() }, enabled = !searching && hasPhotos(context)) { Text("Search") }
                OutlinedButton(onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !searching && hasPhotos(context)) { Text("Similar photo") }
            }
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
            if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (hits.isEmpty() && !searching) {
                Text("Try: sunset yesterday\ntext:\"Bramwell\"\nwaterfall after:2026-08-01\ncamper near Broome (save Broome in Settings)",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
            }
            Text(progress.message, style = MaterialTheme.typography.bodySmall)
            if (progress.busy) {
                if (progress.total > 0) LinearProgressIndicator(progress = { progress.completed.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { IndexScheduler.enqueue(context, true); g.status(IndexProgress("Indexing queued…")) },
                    enabled = hasPhotos(context) && !progress.busy) { Text("Index now") }
                if (progress.busy) TextButton(onClick = {
                    g.update(preferences.copy(automatic = false)); IndexScheduler.cancel(context)
                }) { Text("Stop indexing") }
            }
            LazyVerticalGrid(columns = GridCells.Adaptive(110.dp), modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(hits, key = { it.uri }) { hit ->
                    Column(Modifier.clickable { selected = hit }) {
                        PhotoThumbnail(hit.uri, Modifier.fillMaxWidth().aspectRatio(1f))
                        if (hit.matchedText) Text("Text match", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    selected?.let { photo ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(photo.name, maxLines = 2) },
            text = { PhotoThumbnail(photo.uri, Modifier.fillMaxWidth().height(360.dp), 1200, ContentScale.Fit) },
            confirmButton = { TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(photo.uri), "image/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
            }) { Text("Open original") } }, dismissButton = { TextButton(onClick = { selected = null }) { Text("Close") } })
    }
}

@Composable
private fun PhotoThumbnail(uri: String, modifier: Modifier, maxSide: Int = 320, scale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { runCatching { PhotoLibrary(context).decode(Uri.parse(uri), maxSide) }.getOrNull() }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), "Photo", Modifier.fillMaxSize(), contentScale = scale) }
            ?: Text("Photo unavailable", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SettingsPage(g: AppGraph, modifier: Modifier) {
    val context = LocalContext.current
    val preferences by g.preferences.collectAsState()
    val runtime by g.runtime.collectAsState()
    val places by g.places.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }; var lat by remember { mutableStateOf("") }; var lon by remember { mutableStateOf("") }
    var placeError by remember { mutableStateOf("") }
    val gpsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        g.update(g.preferences.value.copy(gps = granted))
    }
    LazyColumn(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Inference") }
        item {
            Text("${Build.MODEL} · ${Build.SOC_MODEL} · Android ${Build.VERSION.RELEASE}")
            Text("LiteRT 2.2.0 · ${if (g.engine.hasModels) "MobileCLIP2-S0" else "Visual models missing"}", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Text("Image indexing preference", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Backend.entries.forEach { backend ->
                    FilterChip(selected = preferences.backend == backend, onClick = { g.update(preferences.copy(backend = backend)) },
                        label = { Text(if (backend == Backend.AUTO) "Auto / TPU first" else backend.name) })
                }
            }
            Text("Used for the next image. Auto tries TPU when a compatible compiled model and runtime are bundled, then GPU, then CPU.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Last image: ${runtime.image}", fontWeight = FontWeight.SemiBold)
                Text("Text search: ${runtime.text}")
                Text("Last image inference: ${runtime.lastImageMs} ms")
                Text(runtime.detail, style = MaterialTheme.typography.bodySmall)
                Text("OCR: bundled ML Kit recogniser. Its internal accelerator is not exposed by ML Kit.", style = MaterialTheme.typography.bodySmall)
            } }
        }
        item { SectionTitle("Indexing") }
        item { Toggle("Automatic indexing", "While charging, with sufficient battery and storage. New/changed photos only; pauses when warm or in Battery Saver.", preferences.automatic) { g.update(preferences.copy(automatic = it)) } }
        item { Toggle("Read text in photos", "Offline OCR for signs, menus and screenshots. Turning off removes stored OCR text.", preferences.ocr) { g.update(preferences.copy(ocr = it)) } }
        item { Toggle("Use photo locations", "Reads GPS already saved in photos; does not track your phone. Turning off removes stored coordinates.", preferences.gps) {
            if (it && !hasGps(context)) gpsPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
            else g.update(preferences.copy(gps = it))
        } }
        item { Text("Index now also runs while unplugged. Android may delay background work. Photos without GPS cannot match location filters.", style = MaterialTheme.typography.bodySmall) }
        item { SectionTitle("Saved places") }
        item { Text("Add a place to use “near Broome” (within 50 km). Coordinates are stored locally; no online geocoder is used.") }
        items(places, key = { it.name }) { place -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${place.name}\n${place.latitude}, ${place.longitude}", Modifier.weight(1f))
            TextButton(onClick = { g.removePlace(place) }) { Text("Remove") }
        } }
        item {
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("Place name") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(lat, { lat = it.take(24) }, label = { Text("Latitude") }, modifier = Modifier.weight(1f))
                OutlinedTextField(lon, { lon = it.take(24) }, label = { Text("Longitude") }, modifier = Modifier.weight(1f))
            }
            TextButton(onClick = {
                try { g.savePlace(Place(name, lat.toDouble(), lon.toDouble())); name = ""; lat = ""; lon = ""; placeError = "" }
                catch (_: Exception) { placeError = "Enter a name, latitude (−90 to 90) and longitude (−180 to 180)." }
            }) { Text("Save place") }
            if (placeError.isNotEmpty()) Text(placeError, color = MaterialTheme.colorScheme.error)
        }
        item { SectionTitle("Search filters") }
        item { Text("on:2026-09-07 · one day\nafter:2026-08-01 · inclusive\nbefore:2026-09-01 · exclusive\nyesterday / today · phone’s time zone\ntext:\"Bramwell\" · required OCR phrase\nnear:-17.96,122.24,50 · latitude, longitude, radius in km\n\nCombine filters with visual descriptions. Dates use photo capture time, or modified time when unavailable. Personal names are not face identities.") }
        item { SectionTitle("Privacy") }
        item { Text("No internet permission, analytics, advertising, query history or cloud backup. The index stays in Android’s private app storage. Your original photos are never modified. Only “Open original” passes a photo to an app you choose.") }
        item { OutlinedButton(onClick = { confirmClear = true }) { Text("Clear photo index") } }
        item { Text("Pixel10-PicQuery 0.1.0 · Based on greyovo/PicQuery (MIT) and Apple MobileCLIP2 (research licence). Source and build instructions: github.com/pointandyshoot/Pixel10-PicQuery", style = MaterialTheme.typography.bodySmall) }
        item { Spacer(Modifier.height(20.dp)) }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear photo index?") },
        text = { Text("Removes embeddings, recognised text and indexed GPS. Stops automatic indexing. Original photos and saved place names remain.") },
        confirmButton = { TextButton(onClick = { g.clear(); confirmClear = false }) { Text("Clear index") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}

@Composable
private fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
@Composable
private fun Toggle(title: String, description: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(description, style = MaterialTheme.typography.bodySmall) }
        Switch(checked = value, onCheckedChange = onChange)
    }
}
