package it.libraccio.app.data

/** Libreria di esempio: usata solo nella build di test per gli screenshot automatici. */
object DemoData {
    private val palette = listOf(
        0xFF7B1E1E, 0xFF1D3557, 0xFF2D4A2B, 0xFFE9DFC7, 0xFF151515, 0xFFB8862B, 0xFF5A2A4A,
        0xFF2F5D6B, 0xFFC9C1AE, 0xFF8C2F1B, 0xFF3D3B30, 0xFF0F4C5C, 0xFFD4A373, 0xFF6B705C,
        0xFF9B2226, 0xFF283618, 0xFFF1E9D2, 0xFF432818, 0xFF354F52, 0xFFA44A3F,
    ).map { it.toInt() }

    private val titles = listOf(
        "Il nome della rosa" to "Umberto Eco", "Se questo è un uomo" to "Primo Levi", "Il Gattopardo" to "Giuseppe Tomasi di Lampedusa",
        "La coscienza di Zeno" to "Italo Svevo", "Le città invisibili" to "Italo Calvino", "Il barone rampante" to "Italo Calvino",
        "I promessi sposi" to "Alessandro Manzoni", "Cent'anni di solitudine" to "Gabriel García Márquez", "Delitto e castigo" to "Fëdor Dostoevskij",
        "Anna Karenina" to "Lev Tolstoj", "Orgoglio e pregiudizio" to "Jane Austen", "Il grande Gatsby" to "F. Scott Fitzgerald",
        "Moby Dick" to "Herman Melville", "La metamorfosi" to "Franz Kafka", "1984" to "George Orwell", "Il piccolo principe" to "Antoine de Saint-Exupéry",
        "L'amica geniale" to "Elena Ferrante", "Il deserto dei Tartari" to "Dino Buzzati", "La luna e i falò" to "Cesare Pavese",
        "Il fu Mattia Pascal" to "Luigi Pirandello", "Uno, nessuno e centomila" to "Luigi Pirandello", "Lessico famigliare" to "Natalia Ginzburg",
        "Seta" to "Alessandro Baricco", "Novecento" to "Alessandro Baricco", "Siddharta" to "Hermann Hesse", "Il maestro e Margherita" to "Michail Bulgakov",
        "Madame Bovary" to "Gustave Flaubert", "Il ritratto di Dorian Gray" to "Oscar Wilde", "Cime tempestose" to "Emily Brontë",
        "Jane Eyre" to "Charlotte Brontë", "Don Chisciotte" to "Miguel de Cervantes", "La Divina Commedia" to "Dante Alighieri",
        "Il Decameron" to "Giovanni Boccaccio", "Ulisse" to "James Joyce", "Gita al faro" to "Virginia Woolf", "Lolita" to "Vladimir Nabokov",
        "Il vecchio e il mare" to "Ernest Hemingway", "Furore" to "John Steinbeck", "Il giovane Holden" to "J. D. Salinger",
        "Il signore degli anelli" to "J. R. R. Tolkien", "Lo Hobbit" to "J. R. R. Tolkien", "Dune" to "Frank Herbert",
        "Fahrenheit 451" to "Ray Bradbury", "La strada" to "Cormac McCarthy", "Norwegian Wood" to "Haruki Murakami",
        "Kafka sulla spiaggia" to "Haruki Murakami", "Il giardino dei Finzi-Contini" to "Giorgio Bassani", "Marcovaldo" to "Italo Calvino",
        "Il visconte dimezzato" to "Italo Calvino", "La Storia" to "Elsa Morante", "L'isola di Arturo" to "Elsa Morante",
        "Gomorra" to "Roberto Saviano", "Io non ho paura" to "Niccolò Ammaniti", "La solitudine dei numeri primi" to "Paolo Giordano",
        "Va' dove ti porta il cuore" to "Susanna Tamaro", "Il sentiero dei nidi di ragno" to "Italo Calvino",
    )

    fun books(): List<Book> {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        return titles.mapIndexed { i, (t, a) ->
            val pages = listOf(96, 140, 180, 220, 260, 320, 380, 450, 520, 640, 760, 980)[(i * 7 + t.length) % 12]
            val status = when {
                i % 9 == 0 -> ReadingStatus.READING
                i % 3 == 0 -> ReadingStatus.TO_READ
                else -> ReadingStatus.READ
            }
            Book(
                id = "demo-$i",
                title = t,
                authors = a,
                pageCount = pages,
                spineColor = palette[(i * 5 + t.length) % palette.size],
                format = when ((i * 3 + t.length) % 7) { 0 -> BookFormat.POCKET; 1, 2 -> BookFormat.LARGE; else -> BookFormat.STANDARD }.name,
                status = status.name,
                rating = if (status == ReadingStatus.READ) 3 + i % 3 else 0,
                favorite = i % 11 == 0,
                addedAt = now - i * 5 * day,
                finishedAt = if (status == ReadingStatus.READ) now - (i % 10) * 25 * day else null,
                readCount = if (status == ReadingStatus.READ) 1 else 0,
                memory = if (i == 0) "Comprato in una libreria di Bologna, un pomeriggio di pioggia." else "",
                coverUrl = if (i == 0) "https://covers.openlibrary.org/b/isbn/9788845210662-M.jpg?default=false" else null,
            )
        }
    }
}
