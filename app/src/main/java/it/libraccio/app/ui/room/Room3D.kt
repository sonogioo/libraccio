package it.libraccio.app.ui.room

import android.app.ActivityManager
import android.content.Context
import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import it.libraccio.app.data.AppSettings
import it.libraccio.app.data.Book
import it.libraccio.app.data.readingStatus
import it.libraccio.app.room3d.BookAtlasFactory
import it.libraccio.app.room3d.LibraryGLView
import it.libraccio.app.room3d.RoomPalettes
import it.libraccio.app.room3d.SceneBuilder
import it.libraccio.app.room3d.TexKeys
import it.libraccio.app.room3d.TextureGen
import it.libraccio.app.sound.Sfx
import it.libraccio.app.ui.LibraryViewModel
import it.libraccio.app.ui.components.BookCover
import it.libraccio.app.ui.sharedCover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

fun supportsGles3(context: Context): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return am.deviceConfigurationInfo.reqGlEsVersion >= 0x30000
}

/** Comandi esterni alla stanza (usati dalla modalità dimostrativa per gli screenshot). */
sealed interface RoomCommand {
    data object PickFirst : RoomCommand
    data object CloseUp : RoomCommand
    data object Home : RoomCommand
}

@Composable
fun Room3D(
    vm: LibraryViewModel,
    books: List<Book>,
    settings: AppSettings,
    night: Boolean,
    command: RoomCommand?,
    onCommandHandled: () -> Unit,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = remember { LibraryGLView(context) }
    var pulled by remember { mutableStateOf<Pair<Book, RectF>?>(null) }
    var hint by remember { mutableStateOf(true) }
    val booksNow by rememberUpdatedState(books)
    val openBook by rememberUpdatedState(onOpenBook)

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    DisposableEffect(view) {
        view.listener = object : LibraryGLView.Listener {
            override fun onBookTapped(id: String) { vm.play(Sfx.PAGE); hint = false }
            override fun onPulledOut(id: String, coverRect: RectF) {
                booksNow.firstOrNull { it.id == id }?.let { pulled = it to coverRect }
            }
            override fun onReturned() { vm.play(Sfx.THUMP) }
        }
        onDispose { view.listener = null }
    }

    LaunchedEffect(night) { view.renderer.setNight(night) }

    // Costruzione della scena e delle texture (in background)
    var lastTheme by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(books, settings.sort, settings.pileOnDesk, settings.roomTheme) {
        val palette = RoomPalettes.of(settings.roomTheme)
        val pile = if (settings.pileOnDesk) SceneBuilder.pileOf(books) else emptyList()
        val pileIds = pile.map { it.id }.toSet()
        val shelf = sortBooks(books.filter { it.id !in pileIds }, settings.sort)
        val layout = withContext(Dispatchers.Default) { SceneBuilder.build(shelf, pile, palette) }
        val themeMaps = if (lastTheme != settings.roomTheme) {
            withContext(Dispatchers.Default) {
                val seed = palette.key.hashCode().toLong()
                mapOf(
                    TexKeys.WOOD to TextureGen.wood(palette.woodLight, palette.woodDark, seed),
                    TexKeys.WOOD_DARK to TextureGen.wood(palette.woodDark, palette.woodDark, seed + 1),
                    TexKeys.FLOOR to TextureGen.floor(palette.floorLight, palette.floorDark, seed + 2),
                    TexKeys.WALL to TextureGen.wallpaper(palette.wallBase, palette.wallMotif, seed + 3),
                    TexKeys.RUG to TextureGen.rug(palette.rugField, palette.rugBorder, palette.rugAccent, seed + 4),
                )
            }
        } else null
        lastTheme = settings.roomTheme
        view.renderer.setScene(layout, palette, themeMaps)

        val h = if (layout.books.size > 260) 192 else 256
        val gate = Semaphore(4)
        coroutineScope {
            layout.books.map { bd ->
                async(Dispatchers.Default) {
                    gate.withPermit {
                        val atlas = BookAtlasFactory.build(context, bd.book, bd.size, h)
                        view.renderer.setBookAtlas(bd.book.id, atlas)
                    }
                }
            }.awaitAll()
        }
    }

    LaunchedEffect(Unit) { delay(7000); hint = false }

    LaunchedEffect(command) {
        when (command) {
            RoomCommand.PickFirst -> {
                delay(400)
                val candidate = books.firstOrNull { it.readingStatus.name == "READ" } ?: books.firstOrNull()
                candidate?.let { view.renderer.pullOut(it.id); vm.play(Sfx.PAGE) }
            }
            RoomCommand.CloseUp -> view.renderer.closeUp()
            RoomCommand.Home -> view.renderer.home()
            null -> Unit
        }
        if (command != null) onCommandHandled()
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())

        AnimatedVisibility(
            visible = hint && pulled == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
        ) {
            Surface(color = Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(50)) {
                Text(
                    "Trascina per girare · due dita per spostarti · pizzica per avvicinarti · tocca un libro",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        pulled?.let { (book, rect) ->
            val wDp = with(density) { rect.width().toDp() }
            val hDp = with(density) { rect.height().toDp() }
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { pulled = null; view.renderer.putBack() },
            )
            BookCover(
                book,
                Modifier
                    .offset { IntOffset(rect.left.toInt(), rect.top.toInt()) }
                    .size(wDp, hDp)
                    .sharedCover(book.id)
                    .clickable { openBook(book.id) },
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 28.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(book.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                if (book.authors.isNotBlank()) {
                    Text(book.authors, color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                }
                Text(book.readingStatus.label, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { pulled = null; view.renderer.putBack() }) { Text("Rimetti a posto", color = Color.White) }
                    Button(onClick = { openBook(book.id) }) { Text("Apri la scheda") }
                }
            }
        }
    }
}
