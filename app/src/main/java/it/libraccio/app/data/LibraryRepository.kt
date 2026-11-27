package it.libraccio.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

class LibraryRepository(private val context: Context, private val dao: BookDao) {
    val books: Flow<List<Book>> = dao.observeAll()

    fun observeBook(id: String): Flow<Book?> = dao.observe(id)
    fun observeQuotes(bookId: String): Flow<List<Quote>> = dao.observeQuotes(bookId)

    suspend fun findByIsbn(isbn: String): Book? = dao.findByIsbn(isbn)

    suspend fun save(book: Book) = dao.upsert(book)

    suspend fun isEmpty(): Boolean = dao.getAll().isEmpty()

    suspend fun delete(book: Book) {
        dao.delete(book)
        val url = book.coverUrl
        if (url != null && url.startsWith("file:")) {
            withContext(Dispatchers.IO) { runCatching { Uri.parse(url).path?.let { File(it).delete() } } }
        }
    }

    suspend fun addFromCandidate(c: BookCandidate, status: ReadingStatus): Book {
        val color = CoverColors.fromUrl(context, c.coverUrl) ?: CoverColors.fallback(c.title)
        val book = Book(
            isbn = c.isbn,
            title = c.title,
            authors = c.authors,
            publisher = c.publisher,
            year = c.year,
            pageCount = c.pageCount,
            description = c.description,
            genre = c.genre,
            language = c.language,
            coverUrl = c.coverUrl,
            spineColor = color,
            format = guessFormat(c).name,
        ).withStatus(status)
        dao.upsert(book)
        return book
    }

    /** Non conosciamo le dimensioni fisiche: una stima varia ma stabile rende lo scaffale più vivo. */
    private fun guessFormat(c: BookCandidate): BookFormat {
        val h = (c.title.hashCode() and 0x7fffffff) % 100
        return when {
            h < 22 -> BookFormat.POCKET
            h < 85 -> BookFormat.STANDARD
            else -> BookFormat.LARGE
        }
    }

    suspend fun addQuote(quote: Quote) = dao.upsertQuote(quote)
    suspend fun deleteQuote(quote: Quote) = dao.deleteQuote(quote)

    suspend fun saveCoverBitmap(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val file = File(dir, UUID.randomUUID().toString() + ".jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        Uri.fromFile(file).toString()
    }

    suspend fun loadBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: return@runCatching null
            val longest = max(bmp.width, bmp.height)
            if (longest <= 900) bmp
            else Bitmap.createScaledBitmap(bmp, bmp.width * 900 / longest, bmp.height * 900 / longest, true)
        }.getOrNull()
    }

    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val books = dao.getAll()
        val quotes = dao.getAllQuotes()
        val out = context.contentResolver.openOutputStream(uri) ?: error("Impossibile scrivere il file")
        out.use { it.write(Backup.toJson(books, quotes).toByteArray()) }
        books.size
    }

    suspend fun importFrom(uri: Uri): Int = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: error("Impossibile leggere il file")
        val text = input.use { it.bufferedReader().readText() }
        val (books, quotes) = Backup.fromJson(text)
        dao.upsertAll(books.filter { it.id.isNotBlank() && it.title.isNotBlank() })
        val ids = books.map { it.id }.toSet()
        dao.upsertQuotes(quotes.filter { it.bookId in ids })
        books.size
    }
}
