package it.libraccio.app.ui.room

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.libraccio.app.data.AppSettings
import it.libraccio.app.data.Book
import it.libraccio.app.data.ReadingStatus
import it.libraccio.app.data.SortMode
import it.libraccio.app.data.authorSurname
import it.libraccio.app.data.bookFormat
import it.libraccio.app.data.firstAuthor
import it.libraccio.app.data.readingStatus
import it.libraccio.app.sound.Sfx
import it.libraccio.app.ui.LibraryViewModel
import it.libraccio.app.ui.components.BookCover
import it.libraccio.app.ui.components.BookSpine
import it.libraccio.app.ui.components.FlatBook
import it.libraccio.app.ui.components.bookThicknessDp
import it.libraccio.app.ui.theme.RoomTheme
import it.libraccio.app.ui.theme.RoomThemes
import it.libraccio.app.ui.theme.darken
import kotlin.math.max

private val ShelfInner = 150.dp
private const val LAMP_X = 0.82f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    vm: LibraryViewModel,
    night: Boolean,
    onOpenBook: (String) -> Unit,
    onScan: () -> Unit,
    onSearch: () -> Unit,
    onManual: () -> Unit,
    onList: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    command: RoomCommand? = null,
    onCommandHandled: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val use3D = remember { supportsGles3(context) }
    val books by vm.books.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val theme = RoomThemes.byKey(settings.roomTheme)
    var selected by remember { mutableStateOf<Book?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val barColor = theme.wall.darken(if (night) 0.6f else 0.25f)

    Scaffold(
        containerColor = theme.wall,
        topBar = {
            TopAppBar(
                title = { Text("Libraccio", fontFamily = it.libraccio.app.ui.theme.LibFonts.family, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = barColor,
                    titleContentColor = theme.onWall,
                    actionIconContentColor = theme.onWall,
                ),
                actions = {
                    IconButton(onClick = onList) { Icon(Icons.AutoMirrored.Filled.List, "Elenco") }
                    IconButton(onClick = onStats) { Icon(Icons.Default.DateRange, "Il mio anno") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Impostazioni") }
                },
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { addMenu = true }) { Icon(Icons.Default.Add, "Aggiungi un libro") }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(text = { Text("📷  Scansiona ISBN") }, onClick = { addMenu = false; onScan() })
                    DropdownMenuItem(text = { Text("🔎  Cerca titolo o autore") }, onClick = { addMenu = false; onSearch() })
                    DropdownMenuItem(text = { Text("✍️  Inserisci a mano") }, onClick = { addMenu = false; onManual() })
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (use3D) {
                Room3D(
                    vm = vm,
                    books = books,
                    settings = settings,
                    night = night,
                    command = command,
                    onCommandHandled = onCommandHandled,
                    onOpenBook = onOpenBook,
                )
            } else {
                RoomScene(
                    books = books,
                    settings = settings,
                    theme = theme,
                    night = night,
                    liftedId = selected?.id,
                    onTap = { vm.play(Sfx.PAGE); selected = it },
                )
            }

            Box(Modifier.align(Alignment.TopStart).padding(10.dp)) {
                AssistChip(
                    onClick = { sortMenu = true },
                    label = { Text("Ordina: ${settings.sort.label} ▾") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = barColor.copy(alpha = 0.85f),
                        labelColor = theme.onWall,
                    ),
                )
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            onClick = {
                                sortMenu = false
                                vm.updateSettings { it.copy(sort = mode) }
                                vm.play(Sfx.THUMP)
                            },
                        )
                    }
                }
            }

            selected?.let { book ->
                BookPullOut(
                    book = book,
                    onOpen = { selected = null; onOpenBook(book.id) },
                    onDismiss = { vm.play(Sfx.THUMP); selected = null },
                )
            }
        }
    }
}

fun sortBooks(books: List<Book>, mode: SortMode): List<Book> = when (mode) {
    SortMode.AUTHOR -> books.sortedWith(compareBy({ it.authorSurname.lowercase().ifBlank { "~" } }, { it.title.lowercase() }))
    SortMode.TITLE -> books.sortedBy { it.title.lowercase() }
    SortMode.ADDED -> books.sortedBy { it.addedAt }
    SortMode.HEIGHT -> books.sortedWith(compareByDescending<Book> { it.bookFormat.heightFraction }.thenByDescending { it.pageCount })
    SortMode.GENRE -> books.sortedWith(compareBy({ it.genre.lowercase().ifBlank { "~" } }, { it.authorSurname.lowercase() }))
    SortMode.COLOR -> books.sortedBy { colorKey(it.spineColor) }
}

private fun colorKey(argb: Int): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    return if (hsv[1] < 0.15f) 400f + (1f - hsv[2]) * 100f else hsv[0]
}

fun packRows(books: List<Book>, widthDp: Float): List<List<Book>> {
    val rows = mutableListOf<List<Book>>()
    var current = mutableListOf<Book>()
    var used = 0f
    val usable = widthDp - 12f
    for (b in books) {
        val t = bookThicknessDp(b) + 1f
        if (used + t > usable && current.isNotEmpty()) {
            rows += current
            current = mutableListOf()
            used = 0f
        }
        current += b
        used += t
    }
    if (current.isNotEmpty()) rows += current
    return rows
}

@Composable
private fun RoomScene(
    books: List<Book>,
    settings: AppSettings,
    theme: RoomTheme,
    night: Boolean,
    liftedId: String?,
    onTap: (Book) -> Unit,
) {
    val pileAll = remember(books, settings.pileOnDesk) {
        if (!settings.pileOnDesk) emptyList()
        else books
            .filter { it.readingStatus == ReadingStatus.READING || it.readingStatus == ReadingStatus.TO_READ }
            .sortedWith(compareBy({ if (it.readingStatus == ReadingStatus.READING) 0 else 1 }, { -it.addedAt }))
    }
    val pile = pileAll.take(9)
    val shelfBooks = remember(books, settings.sort, pile) {
        val pileIds = pile.map { it.id }.toSet()
        sortBooks(books.filter { it.id !in pileIds }, settings.sort)
    }
    var tilt by remember { mutableFloatStateOf(0f) }
    val animTilt by animateFloatAsState(tilt, label = "tilt")

    Box(Modifier.fillMaxSize()) {
        Wall(theme, Modifier.fillMaxSize().graphicsLayer { translationX = animTilt * 2f })
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragEnd = { tilt *= 0.5f }) { change, dx ->
                        change.consume()
                        tilt = (tilt + dx / 10f).coerceIn(-22f, 22f)
                    }
                }
                .graphicsLayer {
                    rotationY = animTilt
                    cameraDistance = 14f * density
                },
        ) {
            val caseWidth = maxWidth * 0.94f
            val innerWidth = caseWidth - 28.dp
            val rows = remember(shelfBooks, innerWidth) { packRows(shelfBooks, innerWidth.value) }
            val totalRows = max(rows.size + 1, 4)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 60.dp, bottom = 110.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item { Cornice(theme, Modifier.width(caseWidth + 8.dp)) }
                items(totalRows) { i ->
                    ShelfRow(
                        books = rows.getOrElse(i) { emptyList() },
                        rowIndex = i,
                        theme = theme,
                        night = night,
                        innerWidthDp = innerWidth.value,
                        liftedId = liftedId,
                        onTap = onTap,
                        modifier = Modifier.width(caseWidth),
                    )
                }
                item { Plinth(theme, Modifier.width(caseWidth + 8.dp)) }
                item {
                    Desk(
                        pile = pile,
                        hidden = pileAll.size - pile.size,
                        theme = theme,
                        night = night,
                        onTap = onTap,
                        modifier = Modifier.width(caseWidth),
                    )
                }
            }
        }
        if (night) NightOverlay(LAMP_X, Modifier.fillMaxSize())
        HangingLamp(
            night = night,
            centerX = LAMP_X,
            modifier = Modifier.fillMaxWidth().height(70.dp),
        )
    }
}

@Composable
private fun ShelfRow(
    books: List<Book>,
    rowIndex: Int,
    theme: RoomTheme,
    night: Boolean,
    innerWidthDp: Float,
    liftedId: String?,
    onTap: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    val used = books.sumOf { (bookThicknessDp(it) + 1f).toDouble() }.toFloat()
    val free = innerWidthDp - used - 8f
    val lean = books.size >= 3 && rowIndex % 3 == 1 && free >= 40f
    Row(modifier.height(ShelfInner + 19.dp)) {
        CaseSide(theme, Modifier.width(14.dp).fillMaxHeight())
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ShelfInner)
                    .background(
                        Brush.verticalGradient(
                            0f to theme.shelfBack.darken(0.55f),
                            0.25f to theme.shelfBack,
                            1f to theme.shelfBack.darken(0.15f),
                        ),
                    ),
            ) {
                Row(
                    Modifier.align(Alignment.BottomStart).padding(start = 4.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    books.forEachIndexed { i, b ->
                        BookSpine(
                            book = b,
                            lifted = b.id == liftedId,
                            lean = lean && i == books.lastIndex,
                            modifier = Modifier
                                .width(bookThicknessDp(b).dp)
                                .height(ShelfInner * b.bookFormat.heightFraction)
                                .clickable { onTap(b) },
                        )
                    }
                    if (free >= 70f) {
                        Spacer(Modifier.width(if (lean) 34.dp else 12.dp))
                        Decoration(rowIndex, night, Modifier.size(width = 56.dp, height = ShelfInner * 0.72f))
                    }
                }
            }
            Plank(theme)
        }
        CaseSide(theme, Modifier.width(14.dp).fillMaxHeight())
    }
}

/** La scrivania con la pila dei libri in lettura e da leggere. */
@Composable
private fun Desk(
    pile: List<Book>,
    hidden: Int,
    theme: RoomTheme,
    night: Boolean,
    onTap: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(top = 36.dp)) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = 20.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                pile.forEach { b ->
                    val shift = ((b.id.hashCode() and 0x7fffffff) % 16) - 8
                    FlatBook(
                        b,
                        Modifier
                            .offset(x = shift.dp)
                            .width((96 + b.bookFormat.heightFraction * 50).dp)
                            .height((bookThicknessDp(b) * 0.62f).dp)
                            .clickable { onTap(b) },
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.padding(bottom = 4.dp)) {
                Text(
                    when {
                        pile.isEmpty() -> "Scrivania libera"
                        pile.first().readingStatus == ReadingStatus.READING -> "Stai leggendo\n${pile.first().title}"
                        else -> "Da leggere"
                    },
                    color = theme.onWall,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 3,
                )
                if (pile.isNotEmpty() && pile.first().firstAuthor.isNotBlank() && pile.first().readingStatus == ReadingStatus.READING) {
                    Text(pile.first().firstAuthor, color = theme.onWall.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                }
                if (hidden > 0) {
                    Text("+$hidden in attesa sugli scaffali", color = theme.onWall.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.weight(1f))
            Decoration(kind = if (night) 1 else 0, night = night, modifier = Modifier.size(width = 48.dp, height = 70.dp))
            Spacer(Modifier.width(16.dp))
        }
        Box(Modifier.fillMaxWidth().height(10.dp).background(theme.woodLight))
        Box(
            Modifier.fillMaxWidth().height(46.dp)
                .background(Brush.verticalGradient(listOf(theme.wood, theme.woodDark))),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 46.dp, height = 6.dp).background(theme.woodLight, RoundedCornerShape(3.dp)))
        }
    }
}

/** Il libro esce dallo scaffale, ruota e mostra la copertina. */
@Composable
private fun BookPullOut(book: Book, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val anim = remember(book.id) { Animatable(0f) }
    LaunchedEffect(book.id) { anim.animateTo(1f, tween(750, easing = FastOutSlowInEasing)) }
    val p = anim.value
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f * p))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(width = 200.dp, height = 300.dp), contentAlignment = Alignment.Center) {
                if (p < 0.5f) {
                    BookSpine(
                        book,
                        Modifier
                            .width((bookThicknessDp(book) * 2f).dp)
                            .height(300.dp)
                            .graphicsLayer {
                                rotationY = p / 0.5f * 90f
                                cameraDistance = 12f * density
                                val s = 0.7f + 0.3f * p
                                scaleX = s; scaleY = s
                            },
                    )
                } else {
                    BookCover(
                        book,
                        Modifier
                            .size(width = 200.dp, height = 300.dp)
                            .graphicsLayer {
                                rotationY = -90f + (p - 0.5f) / 0.5f * 90f
                                cameraDistance = 12f * density
                                val s = 0.7f + 0.3f * p
                                scaleX = s; scaleY = s
                                shadowElevation = 16.dp.toPx() * p
                            }
                            .clickable { onOpen() },
                    )
                }
            }
            if (p > 0.95f) {
                Spacer(Modifier.height(18.dp))
                Text(book.title, color = Color.White, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                if (book.authors.isNotBlank()) Text(book.authors, color = Color.White.copy(alpha = 0.8f))
                Text(book.readingStatus.label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("Rimetti a posto", color = Color.White) }
                    Button(onClick = onOpen) { Text("Apri la scheda") }
                }
            }
        }
    }
}
