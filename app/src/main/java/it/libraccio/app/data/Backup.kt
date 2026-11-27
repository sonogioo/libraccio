package it.libraccio.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Esportazione / importazione della libreria in un file JSON leggibile. */
object Backup {
    private const val VERSION = 1

    fun toJson(books: List<Book>, quotes: List<Quote>): String {
        val root = JSONObject()
        root.put("app", "Libraccio")
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("books", JSONArray().apply { books.forEach { put(it.toJson()) } })
        root.put("quotes", JSONArray().apply { quotes.forEach { put(it.toJson()) } })
        return root.toString(2)
    }

    fun fromJson(text: String): Pair<List<Book>, List<Quote>> {
        val root = JSONObject(text)
        val books = root.optJSONArray("books")?.let { a -> (0 until a.length()).map { bookFrom(a.getJSONObject(it)) } }.orEmpty()
        val quotes = root.optJSONArray("quotes")?.let { a -> (0 until a.length()).map { quoteFrom(a.getJSONObject(it)) } }.orEmpty()
        return books to quotes
    }

    private fun Book.toJson() = JSONObject().apply {
        put("id", id)
        put("isbn", isbn ?: JSONObject.NULL)
        put("title", title)
        put("authors", authors)
        put("publisher", publisher)
        put("year", year)
        put("pageCount", pageCount)
        put("description", description)
        put("genre", genre)
        put("language", language)
        put("coverUrl", coverUrl ?: JSONObject.NULL)
        put("spineColor", spineColor)
        put("format", format)
        put("status", status)
        put("rating", rating)
        put("favorite", favorite)
        put("notes", notes)
        put("memory", memory)
        put("location", location)
        put("tags", tags)
        put("collection", collection)
        put("addedAt", addedAt)
        put("startedAt", startedAt ?: JSONObject.NULL)
        put("finishedAt", finishedAt ?: JSONObject.NULL)
        put("readCount", readCount)
    }

    private fun Quote.toJson() = JSONObject().apply {
        put("id", id)
        put("bookId", bookId)
        put("text", text)
        put("page", page)
        put("createdAt", createdAt)
    }

    private fun JSONObject.str(key: String) = if (isNull(key)) "" else optString(key)
    private fun JSONObject.strOrNull(key: String) = if (isNull(key)) null else optString(key)
    private fun JSONObject.longOrNull(key: String) = if (isNull(key)) null else optLong(key)

    private fun bookFrom(o: JSONObject) = Book(
        id = o.str("id"),
        isbn = o.strOrNull("isbn"),
        title = o.str("title"),
        authors = o.str("authors"),
        publisher = o.str("publisher"),
        year = o.str("year"),
        pageCount = o.optInt("pageCount"),
        description = o.str("description"),
        genre = o.str("genre"),
        language = o.str("language"),
        coverUrl = o.strOrNull("coverUrl"),
        spineColor = o.optInt("spineColor", CoverColors.fallback(o.str("title"))),
        format = o.optString("format", BookFormat.STANDARD.name),
        status = o.optString("status", ReadingStatus.TO_READ.name),
        rating = o.optInt("rating"),
        favorite = o.optBoolean("favorite"),
        notes = o.str("notes"),
        memory = o.str("memory"),
        location = o.str("location"),
        tags = o.str("tags"),
        collection = o.str("collection"),
        addedAt = o.optLong("addedAt", System.currentTimeMillis()),
        startedAt = o.longOrNull("startedAt"),
        finishedAt = o.longOrNull("finishedAt"),
        readCount = o.optInt("readCount"),
    )

    private fun quoteFrom(o: JSONObject) = Quote(
        id = o.str("id"),
        bookId = o.str("bookId"),
        text = o.str("text"),
        page = o.str("page"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
    )
}
