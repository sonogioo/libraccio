package it.libraccio.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.libraccio.app.LibraccioApp
import it.libraccio.app.data.AppSettings
import it.libraccio.app.data.Book
import it.libraccio.app.data.BookCandidate
import it.libraccio.app.data.BookLookup
import it.libraccio.app.data.Quote
import it.libraccio.app.data.ReadingStatus
import it.libraccio.app.data.readingStatus
import it.libraccio.app.sound.Sfx
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as LibraccioApp).container
    private val repo = container.repository
    private val settingsStore = container.settings
    private val sounds = container.sounds

    val books: StateFlow<List<Book>> =
        repo.books.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<AppSettings> = settingsStore.state

    init {
        viewModelScope.launch {
            settings.collect { BookLookup.googleApiKey = it.googleApiKey }
        }
    }

    fun book(id: String): Flow<Book?> = repo.observeBook(id)
    fun quotes(bookId: String): Flow<List<Quote>> = repo.observeQuotes(bookId)

    fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsStore.update(transform)

    fun play(sfx: Sfx) = sounds.play(sfx)

    /** Salva il libro; se è appena diventato "Letto" suona il campanello. */
    fun save(book: Book, previous: Book? = null) {
        if (previous != null && previous.readingStatus != ReadingStatus.READ && book.readingStatus == ReadingStatus.READ) {
            sounds.play(Sfx.CHIME)
        }
        viewModelScope.launch { repo.save(book) }
    }

    fun delete(book: Book) {
        viewModelScope.launch { repo.delete(book) }
    }

    suspend fun lookupIsbn(isbn: String): BookCandidate? = BookLookup.byIsbn(isbn)
    suspend fun search(query: String): List<BookCandidate> = BookLookup.search(query)
    suspend fun findByIsbn(isbn: String): Book? = repo.findByIsbn(isbn)

    suspend fun addCandidate(candidate: BookCandidate, status: ReadingStatus = ReadingStatus.TO_READ): Book {
        val book = repo.addFromCandidate(candidate, status)
        sounds.play(Sfx.THUMP)
        return book
    }

    fun addQuote(bookId: String, text: String, page: String) {
        viewModelScope.launch { repo.addQuote(Quote(bookId = bookId, text = text, page = page)) }
    }

    fun deleteQuote(quote: Quote) {
        viewModelScope.launch { repo.deleteQuote(quote) }
    }

    suspend fun saveCover(bitmap: Bitmap): String = repo.saveCoverBitmap(bitmap)
    suspend fun loadBitmap(uri: Uri): Bitmap? = repo.loadBitmap(uri)

    suspend fun exportTo(uri: Uri): String = runCatching { repo.exportTo(uri) }
        .fold({ "Esportati $it libri." }, { "Esportazione non riuscita: ${it.message}" })

    suspend fun importFrom(uri: Uri): String = runCatching { repo.importFrom(uri) }
        .fold({ "Importati $it libri." }, { "Importazione non riuscita: ${it.message}" })
}
