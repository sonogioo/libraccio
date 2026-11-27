package it.libraccio.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.libraccio.app.data.Book
import it.libraccio.app.data.ReadingStatus
import it.libraccio.app.data.firstAuthor
import it.libraccio.app.data.readingStatus
import it.libraccio.app.ui.LibraryViewModel
import it.libraccio.app.ui.components.BookSpine
import it.libraccio.app.ui.detail.formatDate
import java.util.Calendar

private fun yearOf(millis: Long) = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)
private fun monthOf(millis: Long) = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.MONTH)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(vm: LibraryViewModel, onBack: () -> Unit, onOpenBook: (String) -> Unit) {
    val books by vm.books.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val thisYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    var year by remember { mutableIntStateOf(thisYear) }
    val goal = settings.yearlyGoal

    val readThisYear = remember(books, year) {
        books.filter { b -> b.finishedAt?.let { yearOf(it) == year } == true }.sortedBy { it.finishedAt }
    }
    val perMonth = remember(readThisYear) {
        IntArray(12).also { arr -> readThisYear.forEach { b -> arr[monthOf(b.finishedAt!!)]++ } }.toList()
    }
    val pagesRead = readThisYear.sumOf { it.pageCount }
    val rated = readThisYear.filter { it.rating > 0 }
    val toRead = books.filter { it.readingStatus == ReadingStatus.TO_READ }
    val oldestWaiting = toRead.minByOrNull { it.addedAt }
    val topAuthors = remember(books) {
        books.groupBy { it.firstAuthor }.filterKeys { it.isNotBlank() }.mapValues { it.value.size }
            .entries.sortedByDescending { it.value }.take(5)
    }
    val topGenres = remember(books) {
        books.groupBy { it.genre }.filterKeys { it.isNotBlank() }.mapValues { it.value.size }
            .entries.sortedByDescending { it.value }.take(5)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Il mio anno di letture") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = { year-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Anno precedente") }
                Text("$year", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = { if (year < thisYear) year++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Anno successivo") }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Lo scaffale del $year", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${readThisYear.size} di $goal libri" + if (readThisYear.size >= goal) " · obiettivo raggiunto! 🎉" else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (readThisYear.size.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        readThisYear.take(80).forEach { b ->
                            BookSpine(
                                b,
                                Modifier.size(width = 14.dp, height = 62.dp).clickable { onOpenBook(b.id) },
                                showText = false,
                            )
                        }
                        repeat((goal - readThisYear.size).coerceIn(0, 80)) {
                            Box(
                                Modifier
                                    .size(width = 14.dp, height = 62.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Libri letti", "${readThisYear.size}", Modifier.weight(1f))
                StatTile("Pagine", "$pagesRead", Modifier.weight(1f))
                StatTile(
                    "Voto medio",
                    if (rated.isEmpty()) "–" else String.format("%.1f★", rated.map { it.rating }.average()),
                    Modifier.weight(1f),
                )
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Mese per mese", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    MonthBars(perMonth)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("In libreria", "${books.size}", Modifier.weight(1f))
                StatTile("Pila da leggere", "${toRead.size}", Modifier.weight(1f))
                StatTile("Preferiti", "${books.count { it.favorite }}", Modifier.weight(1f))
            }

            if (toRead.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Cosa leggo stasera?", style = MaterialTheme.typography.titleMedium)
                        Text("Lascia scegliere alla tua pila.", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { onOpenBook(toRead.random().id) }) { Text("🎲  Pesca un libro") }
                        oldestWaiting?.let { b ->
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Aspetta da più tempo: «${b.title}», sullo scaffale dal ${formatDate(b.addedAt)}.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.clickable { onOpenBook(b.id) },
                            )
                        }
                    }
                }
            }

            if (topAuthors.isNotEmpty()) RankCard("Autori più presenti", topAuthors.map { it.key to it.value })
            if (topGenres.isNotEmpty()) RankCard("Generi", topGenres.map { it.key to it.value })
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MonthBars(values: List<Int>) {
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val slot = size.width / 12f
            val barW = slot * 0.6f
            values.forEachIndexed { i, v ->
                val x = i * slot + (slot - barW) / 2
                drawRoundRect(track, topLeft = Offset(x, 0f), size = Size(barW, size.height), cornerRadius = CornerRadius(4.dp.toPx()))
                val h = size.height * v / max
                if (v > 0) {
                    drawRoundRect(color, topLeft = Offset(x, size.height - h), size = Size(barW, h), cornerRadius = CornerRadius(4.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("G", "F", "M", "A", "M", "G", "L", "A", "S", "O", "N", "D").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RankCard(title: String, rows: List<Pair<String, Int>>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            rows.forEach { (name, n) ->
                Row {
                    Text(name, Modifier.weight(1f), maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Text("$n")
                }
            }
        }
    }
}

@Suppress("unused")
private fun List<Book>.finishedIn(year: Int) = filter { b -> b.finishedAt?.let { yearOf(it) == year } == true }
