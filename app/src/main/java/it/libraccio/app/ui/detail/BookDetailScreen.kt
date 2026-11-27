package it.libraccio.app.ui.detail

import android.app.DatePickerDialog
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.libraccio.app.data.Book
import it.libraccio.app.data.ReadingStatus
import it.libraccio.app.data.readingStatus
import it.libraccio.app.data.withStatus
import it.libraccio.app.ui.LibraryViewModel
import it.libraccio.app.ui.components.BookCover
import it.libraccio.app.ui.components.reflection
import it.libraccio.app.ui.sharedCover
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.luminance
import it.libraccio.app.ui.theme.lighten
import it.libraccio.app.ui.theme.darken
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale.ITALIAN)
fun formatDate(millis: Long): String = dateFormat.format(Date(millis))

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, FlowPreview::class)
@Composable
fun BookDetailScreen(
    vm: LibraryViewModel,
    bookId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val bookFlow = remember(bookId) { vm.book(bookId) }
    val bookOrNull by bookFlow.collectAsStateWithLifecycle(null)
    val quotesFlow = remember(bookId) { vm.quotes(bookId) }
    val quotes by quotesFlow.collectAsStateWithLifecycle(emptyList())
    val b = bookOrNull
    if (b == null) {
        Box(Modifier.fillMaxSize())
        return
    }

    val context = LocalContext.current
    var notes by remember(b.id) { mutableStateOf(b.notes) }
    var memory by remember(b.id) { mutableStateOf(b.memory) }
    var location by remember(b.id) { mutableStateOf(b.location) }
    var tags by remember(b.id) { mutableStateOf(b.tags) }
    var collection by remember(b.id) { mutableStateOf(b.collection) }
    val latest by rememberUpdatedState(b)
    var confirmDelete by remember { mutableStateOf(false) }
    var addQuote by remember { mutableStateOf(false) }
    var showPlot by remember { mutableStateOf(false) }

    fun current(): Book = latest.copy(notes = notes, memory = memory, location = location, tags = tags, collection = collection)

    LaunchedEffect(b.id) {
        snapshotFlow { listOf(notes, memory, location, tags, collection) }
            .drop(1)
            .debounce(500)
            .collect { vm.save(current()) }
    }
    DisposableEffect(b.id) {
        onDispose {
            val c = current()
            if (c != latest) vm.save(c)
        }
    }

    val base = Color(b.spineColor)
    val deep = base.darken(0.68f)
    val accent = if (base.luminance() < 0.35f) base.lighten(0.5f) else base
    val cream = Color(0xFFF3EADB)
    val pageScheme = darkColorScheme(
        primary = accent,
        onPrimary = if (accent.luminance() > 0.5f) Color(0xFF1C1712) else cream,
        primaryContainer = accent.copy(alpha = 0.3f),
        onPrimaryContainer = cream,
        secondaryContainer = accent.copy(alpha = 0.28f),
        onSecondaryContainer = cream,
        background = deep,
        surface = deep.lighten(0.04f),
        surfaceVariant = deep.lighten(0.1f),
        surfaceContainer = deep.lighten(0.07f),
        surfaceContainerHighest = deep.lighten(0.12f),
        onBackground = cream,
        onSurface = cream,
        onSurfaceVariant = cream.copy(alpha = 0.72f),
        outline = cream.copy(alpha = 0.35f),
        outlineVariant = cream.copy(alpha = 0.18f),
        error = Color(0xFFFF8A80),
    )

    MaterialTheme(colorScheme = pageScheme, typography = MaterialTheme.typography) {
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.background(Brush.verticalGradient(listOf(base.darken(0.45f), deep, deep.darken(0.4f)))),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Modifica") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Elimina") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = cream,
                    actionIconContentColor = cream,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                // alone di luce dietro la copertina
                Box(
                    Modifier
                        .size(340.dp)
                        .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent))),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BookCover(
                        b,
                        Modifier
                            .size(width = 220.dp, height = 330.dp)
                            .reflection()
                            .sharedCover(b.id)
                            .graphicsLayer { shadowElevation = 22.dp.toPx() },
                    )
                    Spacer(Modifier.height(96.dp))
                }
            }

            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(b.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                if (b.authors.isNotBlank()) {
                    Text(b.authors, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                val meta = listOf(
                    b.publisher, b.year,
                    if (b.pageCount > 0) "${b.pageCount} pagine" else "",
                    b.genre,
                    b.isbn?.let { "ISBN $it" }.orEmpty(),
                ).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    (1..5).forEach { star ->
                        IconButton(onClick = { vm.save(current().copy(rating = if (b.rating == star) 0 else star)) }) {
                            Icon(
                                if (star <= b.rating) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = "$star stelle",
                                tint = if (star <= b.rating) Color(0xFFE0A526) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { vm.save(current().copy(favorite = !b.favorite)) }) {
                        Icon(
                            if (b.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Il libro della vita",
                            tint = if (b.favorite) Color(0xFFC0392B) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (b.favorite) {
                    Text("♥ Uno dei libri della tua vita", style = MaterialTheme.typography.labelMedium, color = Color(0xFFC0392B), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }

                SectionTitle("Stato")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReadingStatus.entries.forEach { s ->
                        FilterChip(
                            selected = b.readingStatus == s,
                            onClick = { vm.save(current().withStatus(s), previous = b) },
                            label = { Text(s.label) },
                        )
                    }
                }
                b.startedAt?.let { Text("Iniziato il ${formatDate(it)}", style = MaterialTheme.typography.bodySmall) }
                if (b.readingStatus == ReadingStatus.READ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            b.finishedAt?.let { "Finito il ${formatDate(it)}" } ?: "Letto in passato (data sconosciuta)",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            val cal = Calendar.getInstance().apply { b.finishedAt?.let { timeInMillis = it } }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val c = Calendar.getInstance().apply { set(y, m, d, 12, 0, 0) }
                                    vm.save(current().copy(finishedAt = c.timeInMillis))
                                },
                                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
                            ).show()
                        }) { Text("Cambia data") }
                        if (b.finishedAt != null) {
                            TextButton(onClick = { vm.save(current().copy(finishedAt = null)) }) { Text("Non ricordo") }
                        }
                    }
                    if (b.readCount > 1) Text("Letto ${b.readCount} volte", style = MaterialTheme.typography.bodySmall)
                }

                SectionTitle("Il ricordo")
                OutlinedTextField(
                    value = memory,
                    onValueChange = { memory = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                    placeholder = { Text("Dove l'hai preso, chi te l'ha regalato, cosa succedeva nella tua vita mentre lo leggevi…") },
                )
                SectionTitle("Note")
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                    placeholder = { Text("Pensieri, impressioni, cosa ti è rimasto") },
                )
                OutlinedTextField(
                    value = location, onValueChange = { location = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Dove si trova (es. salotto, 2° ripiano · prestato a Marco)") },
                )
                OutlinedTextField(
                    value = tags, onValueChange = { tags = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Tag, separati da virgola") },
                )
                OutlinedTextField(
                    value = collection, onValueChange = { collection = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Collezione") },
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Citazioni", Modifier.weight(1f))
                    TextButton(onClick = { addQuote = true }) { Text("+ Aggiungi") }
                }
                if (quotes.isEmpty()) {
                    Text("Nessuna citazione salvata.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                quotes.forEach { q ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("“${q.text}”", style = MaterialTheme.typography.bodyLarge)
                                if (q.page.isNotBlank()) Text("p. ${q.page}", style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { vm.deleteQuote(q) }) { Icon(Icons.Default.Close, "Elimina citazione") }
                        }
                    }
                }

                if (b.description.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Trama", Modifier.weight(1f))
                        TextButton(onClick = { showPlot = !showPlot }) { Text(if (showPlot) "Nascondi" else "Mostra") }
                    }
                    if (showPlot) Text(b.description, style = MaterialTheme.typography.bodyMedium)
                }

                Text(
                    "Sullo scaffale dal ${formatDate(b.addedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Togli dalla libreria")
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Togliere questo libro?") },
            text = { Text("\"${b.title}\" e le sue citazioni verranno eliminati dalla libreria. Non si può annullare.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(b)
                    onBack()
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } },
        )
    }

    if (addQuote) {
        var text by remember { mutableStateOf("") }
        var page by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addQuote = false },
            title = { Text("Nuova citazione") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp), placeholder = { Text("La frase che vuoi ricordare") })
                    OutlinedTextField(value = page, onValueChange = { page = it }, singleLine = true, label = { Text("Pagina (facoltativa)") })
                }
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    vm.addQuote(b.id, text.trim(), page.trim())
                    addQuote = false
                }) { Text("Salva") }
            },
            dismissButton = { TextButton(onClick = { addQuote = false }) { Text("Annulla") } },
        )
    }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(top = 6.dp))
}
