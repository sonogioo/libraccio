package it.libraccio.app.ui.add

import android.Manifest
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import it.libraccio.app.data.Book
import it.libraccio.app.data.Isbn
import it.libraccio.app.data.ReadingStatus
import it.libraccio.app.sound.Sfx
import it.libraccio.app.ui.LibraryViewModel
import it.libraccio.app.ui.components.BookCover
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private enum class ScanState { LOOKING, ADDED, DUPLICATE, NOT_FOUND }

private data class ScanItem(val isbn: String, val state: ScanState, val book: Book? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    vm: LibraryViewModel,
    onBack: () -> Unit,
    onManual: (String) -> Unit,
    onOpenBook: (String) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    val items = remember { mutableStateListOf<ScanItem>() }
    val seen = remember { mutableSetOf<String>() }
    var status by remember { mutableStateOf(ReadingStatus.TO_READ) }

    fun onCode(raw: String) {
        val isbn = Isbn.normalize(raw)
        if (!Isbn.isBookEan(isbn) || !seen.add(isbn)) return
        vm.play(Sfx.TOC)
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        items.add(0, ScanItem(isbn, ScanState.LOOKING))
        val chosenStatus = status
        scope.launch {
            val existing = vm.findByIsbn(isbn)
            val result = if (existing != null) {
                ScanItem(isbn, ScanState.DUPLICATE, existing)
            } else {
                val candidate = runCatching { vm.lookupIsbn(isbn) }.getOrNull()
                if (candidate == null) ScanItem(isbn, ScanState.NOT_FOUND)
                else ScanItem(isbn, ScanState.ADDED, vm.addCandidate(candidate, chosenStatus))
            }
            val idx = items.indexOfFirst { it.isbn == isbn }
            if (idx >= 0) items[idx] = result
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scansiona ISBN") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                if (hasPermission) {
                    BarcodeCamera(onCode = { onCode(it) }, modifier = Modifier.fillMaxSize())
                    Box(
                        Modifier
                            .size(width = 260.dp, height = 120.dp)
                            .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.85f)), RoundedCornerShape(12.dp)),
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
                    ) {
                        Text(
                            "Inquadra il codice a barre sul retro. Puoi scansionarne tanti di fila.",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text("Serve il permesso della fotocamera per leggere i codici a barre.")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Concedi permesso") }
                    }
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aggiungi come:", style = MaterialTheme.typography.labelLarge)
                listOf(ReadingStatus.TO_READ, ReadingStatus.READ, ReadingStatus.READING).forEach { s ->
                    FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s.label) })
                }
            }
            val added = items.count { it.state == ScanState.ADDED }
            if (items.isNotEmpty()) {
                Text(
                    "$added libri aggiunti in questa sessione",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                items(items, key = { it.isbn }) { item ->
                    ScanRow(
                        item = item,
                        onUndo = {
                            item.book?.let { vm.delete(it) }
                            items.remove(item)
                            seen.remove(item.isbn)
                        },
                        onOpen = { item.book?.let { onOpenBook(it.id) } },
                        onManual = { onManual(item.isbn) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanRow(item: ScanItem, onUndo: () -> Unit, onOpen: () -> Unit, onManual: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 40.dp, height = 58.dp), contentAlignment = Alignment.Center) {
            if (item.book != null) BookCover(item.book, Modifier.fillMaxSize(), small = true)
            else if (item.state == ScanState.LOOKING) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("?", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.book?.title ?: item.isbn,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when (item.state) {
                    ScanState.LOOKING -> "Cerco il libro…"
                    ScanState.ADDED -> "Sistemato sullo scaffale"
                    ScanState.DUPLICATE -> "Ce l'hai già!"
                    ScanState.NOT_FOUND -> "Non trovato (o niente rete) · ${item.isbn}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (item.state) {
            ScanState.ADDED -> TextButton(onClick = onUndo) { Text("Annulla") }
            ScanState.DUPLICATE -> TextButton(onClick = onOpen) { Text("Apri") }
            ScanState.NOT_FOUND -> TextButton(onClick = onManual) { Text("A mano") }
            ScanState.LOOKING -> Unit
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun BarcodeCamera(onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13)
                .build(),
        )
    }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            val p = providerFuture.get()
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null) {
                    proxy.close()
                    return@setAnalyzer
                }
                val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { codes -> codes.forEach { c -> c.rawValue?.let { currentOnCode(it) } } }
                    .addOnCompleteListener { proxy.close() }
            }
            runCatching {
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { provider?.unbindAll() }
    }
    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}
