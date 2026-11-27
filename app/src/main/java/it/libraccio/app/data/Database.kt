package it.libraccio.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: String): Flow<Book?>

    @Query("SELECT * FROM books WHERE isbn = :isbn LIMIT 1")
    suspend fun findByIsbn(isbn: String): Book?

    @Query("SELECT * FROM books")
    suspend fun getAll(): List<Book>

    @Upsert
    suspend fun upsert(book: Book)

    @Upsert
    suspend fun upsertAll(books: List<Book>)

    @Delete
    suspend fun delete(book: Book)

    @Query("SELECT * FROM quotes WHERE bookId = :bookId ORDER BY createdAt")
    fun observeQuotes(bookId: String): Flow<List<Quote>>

    @Query("SELECT * FROM quotes")
    suspend fun getAllQuotes(): List<Quote>

    @Upsert
    suspend fun upsertQuote(quote: Quote)

    @Upsert
    suspend fun upsertQuotes(quotes: List<Quote>)

    @Delete
    suspend fun deleteQuote(quote: Quote)
}

@Database(entities = [Book::class, Quote::class], version = 1, exportSchema = false)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun dao(): BookDao
}
