package it.libraccio.app.ui.add

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.libraccio.app.data.Book
import it.libraccio.app.data.BookFormat
import it.libraccio.app.data.CoverColors
import it.libraccio.app.data.Isbn
import it.libraccio.app.data.ReadingStatus
import it.libraccio.app.data.withStatus
import it.libraccio.app.ui.LibraryViewModel
import it.libraccio.app.ui.components.BookCover
import it.libraccio.app.ui.components.BookSpine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Inserimento manuale (bookId = null) o modifica dei dati di catalogo di un libro. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBookScreen(
    vm: LibraryViewModel,
    bookId: String?,
    prefillIsbn: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existingFlow = remember(bookId) { if (bookId != null) vm.book(bookId) else flowOf(null) }
    val existing by existingFlow.collectAsStateWithLifecycle(null)

    var initialized by remember { mutableStateOf(bookId == null) }
    var title by remember { mutableStateOf("") }
    var authors by remember { mutableStateOf("") }
    var publisher by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var pages by remember { mutableStateOf("") }
    var isbn by remember { mutableStateOf(prefillIsbn.orEmpty()) }
    var genre by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(BookFormat.STANDARD) }
    var coverUrl by remember { mutableStateOf<String?>(null) }
    var spineColor by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(existing) {
        val b = existing
        if (!initialized && b != null) {
            title = b.title; authors = b.authors; publisher = b.publisher; year = b.year
            pages = if (b.pageCount > 0) b.pageCount.toString() else ""
            isbn = b.isbn.orEmpty(); genre = b.genre; format = BookFormat.from(b.format)
            coverUrl = b.coverUrl; spineColor = b.spineColor
            initialized = true
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) scope.launch {
            coverUrl = vm.saveCover(bmp)
            spineColor = CoverColors.fromBitmap(bmp) ?: spineColor
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePicture.launch(null)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bmp = vm.loadBitmap(uri)
            if (bmp != null) {
                coverUrl = vm.saveCover(bmp)
                spineColor = CoverColors.fromBitmap(bmp) ?: spineColor
            }
        }
    }

    fun buildBook(): Book {
        val cleanIsbn = Isbn.normalize(isbn).takeIf { it.isNotEmpty() }?.let { if (Isbn.isValid10(it)) Isbn.to13(it) else it }
        val base = existing ?: Book(title = title.trim()).withStatus(ReadingStatus.TO_READ)
        return base.copy(
            title = title.trim(),
            authors = authors.trim(),
            publisher = publisher.trim(),
            year = year.trim(),
            pageCount = pages.trim().toIntOrNull() ?: 0,
            isbn = cleanIsbn,
            genre = genre.trim(),
            format = format.name,
            coverUrl = coverUrl,
            spineColor = spineColor ?: CoverColors.fallback(title),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (bookId == null) "Nuovo libro" else "Modifica libro") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                actions = {
                    TextButton(enabled = title.isNotBlank(), onClick = {
                        val b = buildBook()
                        vm.save(b)
                        onSaved(b.id)
                    }) { Text("Salva") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val preview = buildBook().copy(title = title.ifBlank { "Titolo" })
            Row(verticalAlignment = Alignment.Bottom) {
                BookCover(preview, Modifier.size(width = 110.dp, height = 165.dp))
                Spacer(Modifier.width(10.dp))
                BookSpine(preview, Modifier.size(width = 28.dp, height = 165.dp * format.heightFraction))
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            takePicture.launch(null)
                        } else {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    }) { Text("📷 Foto copertina") }
                    OutlinedButton(onClick = {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("🖼 Dalla galleria") }
                    if (coverUrl != null) TextButton(onClick = { coverUrl = null }) { Text("Togli copertina") }
                }
            }

            Text("Colore del dorso", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoverColors.swatches.forEach { c ->
                    Box(
                        Modifier
                            .size(32.dp)
                            .background(Color(c), CircleShape)
                            .border(
                                width = if (spineColor == c) 3.dp else 1.dp,
                                color = if (spineColor == c) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.2f),
                                shape = CircleShape,
                            )
                            .clickable { spineColor = c },
                    )
                }
            }

            Field("Titolo *", title) { title = it }
            Field("Autori (separati da virgola)", authors) { authors = it }
            Field("Editore", publisher) { publisher = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Anno", year, Modifier.weight(1f), KeyboardType.Number) { year = it }
                Field("Pagine", pages, Modifier.weight(1f), KeyboardType.Number) { pages = it }
            }
            Field("ISBN", isbn, keyboardType = KeyboardType.Number) { isbn = it }
            Field("Genere", genre) { genre = it }

            Text("Formato", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BookFormat.entries.forEach { f ->
                    FilterChip(selected = format == f, onClick = { format = f }, label = { Text(f.label) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    val b = buildBook()
                    vm.save(b)
                    onSaved(b.id)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (bookId == null) "Metti sullo scaffale" else "Salva modifiche") }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}
