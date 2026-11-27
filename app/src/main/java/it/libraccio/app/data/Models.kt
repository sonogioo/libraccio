package it.libraccio.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class ReadingStatus(val label: String) {
    TO_READ("Da leggere"),
    READING("In lettura"),
    READ("Letto"),
    ABANDONED("Abbandonato"),
    TO_REREAD("Da rileggere");

    companion object {
        fun from(value: String?): ReadingStatus = entries.firstOrNull { it.name == value } ?: TO_READ
    }
}

enum class BookFormat(val label: String, val heightFraction: Float) {
    POCKET("Tascabile", 0.70f),
    STANDARD("Standard", 0.84f),
    LARGE("Grande", 0.97f);

    companion object {
        fun from(value: String?): BookFormat = entries.firstOrNull { it.name == value } ?: STANDARD
    }
}

/**
 * Una copia fisica di un libro che possiedi.
 * I campi "di catalogo" (titolo, autori, ISBN...) e quelli "personali" (stato, ricordo,
 * posizione...) convivono qui per semplicità; vedi docs/SCHEMA.md per l'evoluzione prevista.
 */
@Entity(tableName = "books", indices = [Index("isbn")])
data class Book(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val isbn: String? = null,
    val title: String,
    val authors: String = "",
    val publisher: String = "",
    val year: String = "",
    val pageCount: Int = 0,
    val description: String = "",
    val genre: String = "",
    val language: String = "",
    val coverUrl: String? = null,
    val spineColor: Int = 0xFF7B2D26.toInt(),
    val format: String = BookFormat.STANDARD.name,
    val status: String = ReadingStatus.TO_READ.name,
    val rating: Int = 0,
    val favorite: Boolean = false,
    val notes: String = "",
    val memory: String = "",
    val location: String = "",
    val tags: String = "",
    val collection: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val readCount: Int = 0,
)

@Entity(
    tableName = "quotes",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class Quote(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val bookId: String,
    val text: String,
    val page: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

val Book.readingStatus: ReadingStatus get() = ReadingStatus.from(status)
val Book.bookFormat: BookFormat get() = BookFormat.from(format)
val Book.firstAuthor: String get() = authors.split(",").firstOrNull()?.trim().orEmpty()
val Book.authorSurname: String get() = firstAuthor.split(" ").lastOrNull()?.trim().orEmpty()
val Book.tagList: List<String> get() = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

fun Book.withStatus(newStatus: ReadingStatus): Book {
    val now = System.currentTimeMillis()
    return when (newStatus) {
        ReadingStatus.READING -> copy(status = newStatus.name, startedAt = startedAt ?: now)
        ReadingStatus.READ -> copy(
            status = newStatus.name,
            startedAt = startedAt ?: now,
            finishedAt = if (readingStatus == ReadingStatus.READ) finishedAt else now,
            readCount = if (readingStatus == ReadingStatus.READ) readCount else readCount + 1,
        )
        else -> copy(status = newStatus.name)
    }
}
