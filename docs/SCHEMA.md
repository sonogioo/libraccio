# Schema del database

Libraccio salva tutto in locale con **Room** (SQLite), nel file `libraccio.db`.
Le impostazioni stanno nelle `SharedPreferences` (`libraccio_settings`), le foto delle copertine in `files/covers/`.

## Tabella `books`

Ogni riga è una **copia fisica** di un libro che possiedi.

| Colonna | Tipo | Note |
|---|---|---|
| `id` | TEXT PK | UUID: stabile anche per una futura sincronizzazione |
| `isbn` | TEXT? | ISBN-13 normalizzato (indicizzato, per trovare i doppioni) |
| `title` | TEXT | |
| `authors` | TEXT | autori separati da virgola |
| `publisher`, `year`, `genre`, `language` | TEXT | dati di catalogo |
| `pageCount` | INT | decide lo **spessore del dorso** sullo scaffale |
| `description` | TEXT | trama |
| `coverUrl` | TEXT? | URL remoto oppure `file://` per una foto scattata |
| `spineColor` | INT | colore ARGB del dorso, ricavato dalla copertina (Palette) |
| `format` | TEXT | `POCKET` / `STANDARD` / `LARGE` → **altezza del dorso** |
| `status` | TEXT | `TO_READ`, `READING`, `READ`, `ABANDONED`, `TO_REREAD` |
| `rating` | INT | 0–5 |
| `favorite` | BOOL | "il libro della vita" |
| `notes` | TEXT | note libere |
| `memory` | TEXT | **il ricordo**: dove l'hai preso, chi te l'ha regalato, quando l'hai letto |
| `location` | TEXT | dove si trova fisicamente (o a chi l'hai prestato) |
| `tags` | TEXT | tag separati da virgola |
| `collection` | TEXT | collezione personale |
| `addedAt` | LONG | data di arrivo sullo scaffale |
| `startedAt`, `finishedAt` | LONG? | date di lettura (`finishedAt` alimenta le statistiche) |
| `readCount` | INT | quante volte l'hai letto |

## Tabella `quotes`

| Colonna | Tipo | Note |
|---|---|---|
| `id` | TEXT PK | UUID |
| `bookId` | TEXT FK → `books.id` | `ON DELETE CASCADE` |
| `text` | TEXT | la citazione |
| `page` | TEXT | pagina (facoltativa) |
| `createdAt` | LONG | |

## Evoluzione prevista

Quando arriveranno prestiti, edizioni multiple e funzioni social, conviene separare:

- `works`: l'opera astratta (titolo, autori, trama)
- `copies`: la tua copia (edizione, ISBN, formato, posizione, ricordo)
- `readings`: una riga per ogni lettura (inizio, fine, voto), per gestire bene le riletture
- `loans`: prestiti (copia, persona, data, restituito)
- `rooms` / `placements`: stanze multiple e posizione libera dei libri

Gli UUID e il backup JSON (Impostazioni → Esporta) rendono indolore la migrazione.
