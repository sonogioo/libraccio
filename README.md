<p align="center"><img src="branding/logo.jpeg" width="160" alt="Logo di Libraccio"></p>

# Libraccio

Una libreria dentro una stanza: ogni libro che possiedi diventa un dorso sugli scaffali di una stanza virtuale.

## Cosa fa (v1)

**La stanza**
- Libreria a scaffali con prospettiva 3D: trascina col dito a destra e a sinistra per ruotarla
- Dorsi generati in automatico: spessore dalle pagine, altezza dal formato, colore preso dalla copertina
- Tocchi un libro e lui esce dallo scaffale, ruota e ti mostra la copertina
- Ordinamento per autore, titolo, colore, data di arrivo, altezza o genere
- Scrivania con la pila dei libri in lettura e da leggere
- Decorazioni negli spazi vuoti: pianta, candela, mappamondo, cornice
- 4 stanze: Studio in legno, Minimal chiaro, Soffitta, Biblioteca gotica
- Modalità notte con lampada e candele accese (automatica dalle 20 alle 7)
- Effetti sonori sintetizzati: pagine, libro sullo scaffale, scansione, libro finito

**Aggiungere libri**
- Scansione ISBN a raffica con la fotocamera (ML Kit, funziona offline)
- Ricerca per titolo, autore o ISBN (Google Books + Open Library)
- Importazione da una lista incollata (un titolo o un ISBN per riga)
- Inserimento a mano, con foto della copertina dalla fotocamera o dalla galleria e scelta del colore del dorso
- Avviso dei doppioni

**Il catalogo**
- Stati: da leggere, in lettura, letto, abbandonato, da rileggere, con date di inizio e fine
- Voto, "libro della vita", note, **il ricordo**, posizione fisica, tag, collezioni
- Citazioni preferite
- Elenco ricercabile con filtri

**Il tuo anno**
- Obiettivo annuale mostrato come uno scaffale che si riempie
- Libri e pagine letti, voto medio, grafico mese per mese
- Autori e generi più presenti
- "Cosa leggo stasera?" e il libro che aspetta da più tempo

**Backup**: esporta e importa tutto in JSON.

## Installare

Scarica l'APK dall'ultima [Release](../../releases) e aprilo sul telefono (Android 8.0 o superiore).
Al primo avvio Android ti chiederà di permettere l'installazione da questa fonte.

## Compilare

Ogni push su `main` compila l'APK su GitHub Actions (artifact `libraccio-apk`).
Un tag `vX.Y.Z` crea una Release con l'APK firmato:

```
git tag v1.0.1 && git push origin v1.0.1
```

In locale, con Android Studio: apri la cartella e avvia `app`.

## Tecnologia

Kotlin · Jetpack Compose · Room · CameraX + ML Kit Barcode · Coil · Palette.
Per il 3D si usano trasformazioni prospettiche di Compose (`graphicsLayer`), senza un motore esterno.
Un vero motore 3D (SceneView/Filament) è previsto per le stanze esplorabili.
Schema dei dati: [docs/SCHEMA.md](docs/SCHEMA.md).

## Prossimi passi

- Stanze multiple e libri da spostare a mano sugli scaffali
- Prestiti con promemoria
- OCR della copertina per i libri senza ISBN
- Widget per la schermata Home
- Riepilogo animato di fine anno
- Più avanti: sincronizzazione, visite alla stanza degli amici, community
