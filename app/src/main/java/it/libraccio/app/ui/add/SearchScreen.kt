package it.libraccio.app.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import it.libraccio.app.data.BookCandidate
import it.libraccio.app.data.CoverColors
import it.libraccio.app.ui.LibraryViewModel
import kotlinx.coroutines.launch

private fun BookCandidate.key() = isbn ?: (title + "|" + authors)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    vm: LibraryViewModel,
    onBack: () -> Unit,
    onManual: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val books by vm.books.collectAsStateWithLifecycle()
    val ownedByIsbn = remember(books) { books.filter { it.isbn != null }.associateBy { it.isbn!! } }

    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<BookCandidate>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val added = remember { mutableStateMapOf<String, String>() }
    var showListDialog by remember { mutableStateOf(false) }

    fun doSearch() {
        if (query.isBlank()) return
        focus.clearFocus()
        scope.launch {
            loading = true
            message = null
            results = runCatching { vm.search(query) }.getOrElse {
                message = "Problema di rete, riprova."
                emptyList()
            }
            if (results.isEmpty() && message == null) message = "Nessun risultato. Prova con l'ISBN o inseriscilo a mano."
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cerca un libro") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                actions = { TextButton(onClick = { showListDialog = true }) { Text("Da lista") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Titolo, autore o ISBN") },
                singleLine = true,
                trailingIcon = { IconButton(onClick = { doSearch() }) { Icon(Icons.Default.Search, "Cerca") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch() }),
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            message?.let {
                Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                itemsIndexed(results, key = { i, c -> c.key() + i }) { _, c ->
                    val owned = c.isbn?.let { ownedByIsbn[it] }
                    CandidateRow(
                        candidate = c,
                        trailing = {
                            when {
                                added.containsKey(c.key()) ->
                                    TextButton(onClick = { onOpenBook(added.getValue(c.key())) }) { Text("✓ Aggiunto") }
                                owned != null ->
                                    TextButton(onClick = { onOpenBook(owned.id) }) { Text("Ce l'hai") }
                                else -> Button(onClick = {
                                    scope.launch { added[c.key()] = vm.addCandidate(c).id }
                                }) { Text("Aggiungi") }
                            }
                        },
                    )
                }
            }
            OutlinedButton(
                onClick = onManual,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text("Non lo trovi? Inseriscilo a mano") }
        }
    }

    if (showListDialog) {
        ImportListDialog(vm = vm, onDismiss = { showListDialog = false })
    }
}

@Composable
fun CandidateRow(candidate: BookCandidate, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 48.dp, height = 72.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(CoverColors.fallback(candidate.title))),
        ) {
            if (candidate.coverUrl != null) {
                AsyncImage(
                    model = candidate.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(candidate.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (candidate.authors.isNotBlank()) {
                Text(candidate.authors, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val meta = listOf(candidate.year, candidate.publisher, if (candidate.pageCount > 0) "${candidate.pageCount} pp." else "")
                .filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

/** Incolla una lista di titoli o ISBN (uno per riga): Libraccio li cerca e li aggiunge. */
@Composable
private fun ImportListDialog(vm: LibraryViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text("Aggiungi da una lista") },
        text = {
            Column {
                if (report != null) {
                    Text(report!!)
                } else {
                    Text(
                        "Un libro per riga: titolo (meglio con l'autore) oppure ISBN.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        enabled = !running,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                        placeholder = { Text("Il nome della rosa Eco\n9788804668237\n…") },
                    )
                    if (running) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(progress, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (report != null) {
                TextButton(onClick = onDismiss) { Text("Chiudi") }
            } else {
                TextButton(
                    enabled = !running && text.isNotBlank(),
                    onClick = {
                        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        running = true
                        scope.launch {
                            var ok = 0
                            val missing = mutableListOf<String>()
                            lines.forEachIndexed { i, line ->
                                progress = "${i + 1}/${lines.size}: $line"
                                val c = runCatching { vm.search(line) }.getOrDefault(emptyList()).firstOrNull()
                                val already = c?.isbn?.let { vm.findByIsbn(it) } != null
                                if (c != null && !already) {
                                    vm.addCandidate(c)
                                    ok++
                                } else if (c == null) {
                                    missing += line
                                }
                            }
                            running = false
                            report = buildString {
                                append("Aggiunti $ok libri.")
                                if (missing.isNotEmpty()) append("\n\nNon trovati:\n" + missing.joinToString("\n"))
                            }
                        }
                    },
                ) { Text("Importa") }
            }
        },
        dismissButton = {
            if (report == null) TextButton(enabled = !running, onClick = onDismiss) { Text("Annulla") }
        },
    )
}
