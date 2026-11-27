package it.libraccio.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Un risultato di ricerca, non ancora salvato in libreria. */
data class BookCandidate(
    val isbn: String?,
    val title: String,
    val authors: String,
    val publisher: String = "",
    val year: String = "",
    val pageCount: Int = 0,
    val description: String = "",
    val genre: String = "",
    val language: String = "",
    val coverUrl: String? = null,
)

object Isbn {
    fun normalize(raw: String): String = raw.uppercase().filter { it.isDigit() || it == 'X' }

    fun isValid13(s: String): Boolean {
        if (s.length != 13 || !s.all { it.isDigit() }) return false
        val sum = s.take(12).mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
        return (10 - sum % 10) % 10 == s[12] - '0'
    }

    fun isValid10(s: String): Boolean {
        if (s.length != 10) return false
        var sum = 0
        for (i in 0 until 10) {
            val c = s[i]
            val v = when {
                c == 'X' && i == 9 -> 10
                c.isDigit() -> c - '0'
                else -> return false
            }
            sum += v * (10 - i)
        }
        return sum % 11 == 0
    }

    fun isIsbn(s: String) = isValid13(s) || isValid10(s)

    fun to13(s: String): String {
        if (s.length != 10) return s
        val core = "978" + s.take(9)
        val sum = core.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
        return core + ((10 - sum % 10) % 10)
    }

    /** I codici a barre dei libri sono EAN-13 che iniziano con 978 o 979. */
    fun isBookEan(s: String) = isValid13(s) && (s.startsWith("978") || s.startsWith("979"))
}

/** Interroga Google Books e, in alternativa, Open Library. Nessuna chiave API necessaria. */
object BookLookup {
    private const val USER_AGENT = "Libraccio-Android/1.0"

    suspend fun byIsbn(isbn: String): BookCandidate? = withContext(Dispatchers.IO) {
        val fromGoogle = runCatching { google("isbn:$isbn", 1) }.getOrDefault(emptyList()).firstOrNull()
        val found = fromGoogle ?: runCatching { openLibrary(isbn) }.getOrNull()
        found?.let {
            it.copy(
                isbn = it.isbn ?: isbn,
                coverUrl = it.coverUrl ?: "https://covers.openlibrary.org/b/isbn/$isbn-M.jpg?default=false",
            )
        }
    }

    /** Chiave API di Google Books facoltativa (senza, Google condivide una quota che si esaurisce spesso). */
    @Volatile
    var googleApiKey: String = ""

    /**
     * Cerca prima su Google Books; se non risponde (quota esaurita, rete) o non trova nulla,
     * passa a Open Library. Lancia un'eccezione solo se falliscono entrambe.
     */
    suspend fun search(query: String): List<BookCandidate> = withContext(Dispatchers.IO) {
        val q = query.trim()
        val digits = Isbn.normalize(q)
        if (digits.length >= 10 && Isbn.isIsbn(digits)) {
            return@withContext listOfNotNull(byIsbn(Isbn.to13(digits)))
        }
        val fromGoogle = runCatching { google(q, 25) }
        val googleResults = fromGoogle.getOrDefault(emptyList())
        if (googleResults.isNotEmpty()) return@withContext googleResults
        val fromOpenLibrary = runCatching { openLibrarySearch(q) }
        if (fromGoogle.isFailure && fromOpenLibrary.isFailure) throw fromOpenLibrary.exceptionOrNull()!!
        fromOpenLibrary.getOrDefault(emptyList())
    }

    private fun openLibrarySearch(q: String): List<BookCandidate> {
        val url = "https://openlibrary.org/search.json?q=" + URLEncoder.encode(q, "UTF-8") +
            "&limit=25&fields=title,author_name,isbn,cover_i,first_publish_year,number_of_pages_median,publisher,subject,language"
        val docs = JSONObject(get(url)).optJSONArray("docs") ?: return emptyList()
        val out = mutableListOf<BookCandidate>()
        for (i in 0 until docs.length()) {
            val d = docs.optJSONObject(i) ?: continue
            val title = d.clean("title")
            if (title.isBlank()) continue
            val isbns = d.optJSONArray("isbn").strings().map { Isbn.normalize(it) }
            val isbn = isbns.firstOrNull { it.length == 13 && it.startsWith("97888") } // edizione italiana, se c'è
                ?: isbns.firstOrNull { Isbn.isValid13(it) }
                ?: isbns.firstOrNull { Isbn.isValid10(it) }?.let { Isbn.to13(it) }
            val coverId = d.optLong("cover_i", 0L)
            out += BookCandidate(
                isbn = isbn,
                title = title,
                authors = d.optJSONArray("author_name").strings().take(3).joinToString(", "),
                publisher = d.optJSONArray("publisher").strings().firstOrNull().orEmpty(),
                year = if (d.has("first_publish_year")) d.optInt("first_publish_year").toString() else "",
                pageCount = d.optInt("number_of_pages_median", 0),
                genre = d.optJSONArray("subject").strings().firstOrNull().orEmpty(),
                coverUrl = if (coverId > 0) "https://covers.openlibrary.org/b/id/$coverId-M.jpg" else null,
            )
        }
        return out
    }

    private fun google(q: String, max: Int): List<BookCandidate> {
        val key = googleApiKey.trim()
        val url = "https://www.googleapis.com/books/v1/volumes?q=" +
            URLEncoder.encode(q, "UTF-8") + "&maxResults=$max&printType=books" +
            (if (key.isNotEmpty()) "&key=" + URLEncoder.encode(key, "UTF-8") else "")
        val items = JSONObject(get(url)).optJSONArray("items") ?: return emptyList()
        val out = mutableListOf<BookCandidate>()
        for (i in 0 until items.length()) {
            val v = items.optJSONObject(i)?.optJSONObject("volumeInfo") ?: continue
            val title = v.clean("title")
            if (title.isBlank()) continue
            var isbn13: String? = null
            var isbn10: String? = null
            val ids = v.optJSONArray("industryIdentifiers")
            if (ids != null) {
                for (j in 0 until ids.length()) {
                    val o = ids.optJSONObject(j) ?: continue
                    when (o.optString("type")) {
                        "ISBN_13" -> isbn13 = o.optString("identifier")
                        "ISBN_10" -> isbn10 = o.optString("identifier")
                    }
                }
            }
            val links = v.optJSONObject("imageLinks")
            val cover = links?.let { it.clean("thumbnail").ifBlank { it.clean("smallThumbnail") } }
                ?.takeIf { it.isNotBlank() }
                ?.replace("http://", "https://")
                ?.replace("&edge=curl", "")
            out += BookCandidate(
                isbn = isbn13 ?: isbn10?.let { Isbn.to13(it) },
                title = title,
                authors = v.optJSONArray("authors").strings().joinToString(", "),
                publisher = v.clean("publisher"),
                year = v.clean("publishedDate").take(4),
                pageCount = v.optInt("pageCount", 0),
                description = v.clean("description"),
                genre = v.optJSONArray("categories").strings().firstOrNull().orEmpty(),
                language = v.clean("language"),
                coverUrl = cover,
            )
        }
        return out
    }

    private fun openLibrary(isbn: String): BookCandidate? {
        val root = JSONObject(get("https://openlibrary.org/api/books?bibkeys=ISBN:$isbn&format=json&jscmd=data"))
        val o = root.optJSONObject("ISBN:$isbn") ?: return null
        val authors = o.optJSONArray("authors")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.clean("name") }
        }.orEmpty()
        val publishers = o.optJSONArray("publishers")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.clean("name") }
        }.orEmpty()
        val cover = o.optJSONObject("cover")?.let { it.clean("medium").ifBlank { it.clean("large") } }
        return BookCandidate(
            isbn = isbn,
            title = o.clean("title").ifBlank { return null },
            authors = authors.joinToString(", "),
            publisher = publishers.firstOrNull().orEmpty(),
            year = Regex("\\d{4}").find(o.clean("publish_date"))?.value.orEmpty(),
            pageCount = o.optInt("number_of_pages", 0),
            genre = o.optJSONArray("subjects")?.optJSONObject(0)?.clean("name").orEmpty(),
            coverUrl = cover?.takeIf { it.isNotBlank() }?.replace("http://", "https://"),
        )
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", USER_AGENT)
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun JSONObject.clean(key: String): String =
        if (isNull(key)) "" else optString(key).trim()

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList()
        else (0 until length()).map { optString(it).trim() }.filter { it.isNotBlank() && it != "null" }
}
