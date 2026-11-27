package it.libraccio.app.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.libraccio.app.data.ReadingStatus
import it.libraccio.app.data.readingStatus
import it.libraccio.app.ui.LibraryViewModel
import it.libraccio.app.ui.components.BookCover
import it.libraccio.app.ui.sharedCover

private const val FAV = "FAV"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(vm: LibraryViewModel, onBack: () -> Unit, onOpenBook: (String) -> Unit) {
    val books by vm.books.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }

    val filtered = remember(books, query, filter) {
        val q = query.trim()
        books.filter { b ->
            val okFilter = when (filter) {
                null -> true
                FAV -> b.favorite
                else -> b.status == filter
            }
            okFilter && (q.isEmpty() || listOf(b.title, b.authors, b.tags, b.genre, b.isbn.orEmpty(), b.collection, b.location, b.publisher)
                .any { it.contains(q, ignoreCase = true) })
        }.sortedBy { it.title.lowercase() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("La mia libreria") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Cerca tra i tuoi libri, tag, posizioni…") },
                singleLine = true,
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Tutti (${books.size})") })
                FilterChip(selected = filter == FAV, onClick = { filter = FAV }, label = { Text("♥ Preferiti") })
                ReadingStatus.entries.forEach { s ->
                    val n = books.count { it.readingStatus == s }
                    FilterChip(selected = filter == s.name, onClick = { filter = s.name }, label = { Text("${s.label} ($n)") })
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filtered, key = { it.id }) { b ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenBook(b.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BookCover(b, Modifier.size(width = 44.dp, height = 66.dp).sharedCover(b.id), small = true)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (b.authors.isNotBlank()) Text(b.authors, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val extra = listOf(b.readingStatus.label, if (b.rating > 0) "★".repeat(b.rating) else "", b.location)
                                .filter { it.isNotBlank() }.joinToString(" · ")
                            Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (b.favorite) Text("♥", color = MaterialTheme.colorScheme.error)
                    }
                    HorizontalDivider(Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}
