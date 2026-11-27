package it.libraccio.app.room3d

import it.libraccio.app.data.Book
import it.libraccio.app.data.BookFormat
import it.libraccio.app.data.ReadingStatus
import it.libraccio.app.data.bookFormat
import it.libraccio.app.data.readingStatus
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class MeshKind { BOX, CYL, CONE, SPHERE, POT }

/** Materiale. [tex] è una chiave delle texture della stanza (vedi [TexKeys]). Colori lineari. */
class Mat(
    val mode: Int,
    val tex: String? = null,
    val tint: FloatArray = floatArrayOf(1f, 1f, 1f),
    val texScale: Float = 1f,
    val grainVertical: Boolean = false,
    val spec: Float = 0.06f,
    val shininess: Float = 24f,
    val castShadow: Boolean = true,
    /** 0 sempre acceso, 1 lampadina (fioca di giorno), 2 cielo della finestra, 3 fiamma (solo di notte) */
    val glow: Int = 0,
)

object TexKeys {
    const val WOOD = "wood"
    const val WOOD_DARK = "woodDark"
    const val FLOOR = "floor"
    const val WALL = "wall"
    const val RUG = "rug"
    const val PAGES = "pages"
}

class Draw(val mesh: MeshKind, val model: FloatArray, val mat: Mat)

/** Un libro nella scena: centro, dimensioni (spessore, altezza, profondità) e rotazione. */
class BookDraw(
    val book: Book,
    val center: FloatArray,
    val size: FloatArray,
    val rot: FloatArray,
) {
    fun model(center: FloatArray = this.center, rot: FloatArray = this.rot): FloatArray = M.chain(
        M.translate(center[0], center[1], center[2]),
        Q.toMatrix(rot),
        M.scale(size[0], size[1], size[2]),
        M.translate(-0.5f, -0.5f, -0.5f),
    )
}

class SceneLayout(
    val props: List<Draw>,
    val books: List<BookDraw>,
    val cases: List<FloatArray>,
    /** y0 interno, passo ripiani, z fondo, passo campate */
    val caseParams: FloatArray,
    /** y cima, z fronte, spessore ripiano */
    val caseExtra: FloatArray,
    val boundsMin: FloatArray,
    val boundsMax: FloatArray,
    val homeTarget: FloatArray,
    val homeDistance: Float,
    val deskLamp: FloatArray,
    val floorLamp: FloatArray,
    val candles: List<FloatArray>,
)

/** Dimensioni reali (metri) e regole di una libreria vera. */
object SceneBuilder {
    private const val T = 0.022f       // spessore fianchi
    private const val S = 0.026f       // spessore ripiani
    private const val W = 0.80f        // larghezza interna di una campata
    private const val H = 0.335f       // altezza utile di un ripiano
    private const val D = 0.30f        // profondità
    private const val PLINTH = 0.085f
    private const val Z0 = 0.03f       // fondo della libreria (staccata dal muro)
    private const val BAYS = 3
    private const val ROWS = 6

    private fun rnd(seed: String, salt: Int): Float {
        val h = (seed.hashCode() * 31 + salt * 977).let { it xor (it ushr 13) } * 0x5bd1e995
        return ((h ushr 8) and 0xFFFF) / 65535f
    }

    fun bookSize(b: Book): FloatArray {
        val h = when (b.bookFormat) {
            BookFormat.POCKET -> 0.178f
            BookFormat.STANDARD -> 0.215f
            BookFormat.LARGE -> 0.272f
        } + (rnd(b.id, 1) - 0.5f) * 0.012f
        val thickness = if (b.pageCount > 0) (0.0042f + b.pageCount * 0.0000725f).coerceIn(0.007f, 0.075f)
        else 0.02f + rnd(b.id, 2) * 0.012f
        val depth = h * (0.64f + rnd(b.id, 3) * 0.06f)
        return floatArrayOf(thickness, h, depth)
    }

    fun build(
        shelfBooks: List<Book>,
        pile: List<Book>,
        palette: RoomPalette,
    ): SceneLayout {
        val props = ArrayList<Draw>()
        val books = ArrayList<BookDraw>()
        val candles = ArrayList<FloatArray>()
        val pitch = H + S
        val y0 = PLINTH + S
        val caseWidth = BAYS * W + (BAYS + 1) * T
        val zFront = Z0 + D

        // Quante librerie servono?
        val perCaseCapacity = BAYS * ROWS * (W - 0.03f)
        val totalThick = shelfBooks.sumOf { (bookSize(it)[0] + 0.0012f).toDouble() }.toFloat()
        val caseCount = max(1, min(4, kotlin.math.ceil(totalThick / (perCaseCapacity * 0.92f)).toInt()))
        val caseGap = 0.06f
        val cases = ArrayList<FloatArray>()

        val wood = Mat(0, TexKeys.WOOD, palette.woodTint, texScale = 1.6f, spec = 0.12f, shininess = 40f)
        val woodV = Mat(0, TexKeys.WOOD, palette.woodTint, texScale = 1.6f, grainVertical = true, spec = 0.12f, shininess = 40f)
        val woodBack = Mat(0, TexKeys.WOOD_DARK, palette.woodTint, texScale = 2.2f, grainVertical = true, spec = 0.03f)

        var x = 0f
        for (c in 0 until caseCount) {
            val x0 = x
            val x1 = x0 + caseWidth
            cases += floatArrayOf(x0 + T, x1 - T)
            val top = y0 + ROWS * pitch
            // fondo, zoccolo, fianchi, divisori, ripiani, cornice
            box(props, x0, PLINTH, Z0 - 0.012f, x1, top, Z0, woodBack)
            box(props, x0 + 0.01f, 0f, Z0 + 0.03f, x1 - 0.01f, PLINTH, zFront - 0.015f, Mat(0, TexKeys.WOOD_DARK, palette.woodTint, 1.6f))
            box(props, x0, 0f, Z0, x0 + T, top, zFront, woodV)
            box(props, x1 - T, 0f, Z0, x1, top, zFront, woodV)
            for (b in 1 until BAYS) {
                val dx = x0 + T + b * (W + T) - T
                box(props, dx, PLINTH, Z0, dx + T, top, zFront - 0.004f, woodV)
            }
            for (r in 0..ROWS) {
                val ys = y0 + r * pitch
                box(props, x0 + T, ys - S, Z0, x1 - T, ys, zFront, wood)
            }
            box(props, x0 - 0.018f, top, Z0 - 0.012f, x1 + 0.018f, top + 0.028f, zFront + 0.022f, wood)
            box(props, x0 - 0.006f, top + 0.028f, Z0 - 0.012f, x1 + 0.006f, top + 0.052f, zFront + 0.008f, wood)
            x = x1 + caseGap
        }
        val caseEnd = x - caseGap

        // Riempimento: riga per riga dall'alto, campata per campata, come si fa davvero.
        val cells = ArrayList<FloatArray>() // xa, xb, yShelf
        for (r in ROWS - 1 downTo 0) {
            for (c in 0 until caseCount) {
                val x0 = c * (caseWidth + caseGap)
                for (b in 0 until BAYS) {
                    val xa = x0 + T + b * (W + T)
                    cells += floatArrayOf(xa, xa + W, y0 + r * pitch)
                }
            }
        }
        var cellIndex = 0
        var cursor = cells.firstOrNull()?.get(0) ?: 0f
        val placedInCell = ArrayList<BookDraw>()
        val gapsAtEnd = HashMap<Int, Float>()

        fun closeCell() {
            val cell = cells[cellIndex]
            val gap = cell[1] - cursor
            // Ultimo libro inclinato se c'è spazio
            if (placedInCell.size >= 4 && gap > 0.075f && rnd(placedInCell.last().book.id, 7) < 0.55f) {
                val last = placedInCell.removeAt(placedInCell.size - 1)
                books.remove(last)
                val neighborRight = placedInCell.last().let { it.center[0] + it.size[0] / 2 }
                val angle = 10f + rnd(last.book.id, 8) * 7f
                val sz = last.size
                val rad = Math.toRadians(angle.toDouble())
                val blX = neighborRight + sz[1] * sin(rad).toFloat() + 0.002f
                // centro = angolo in basso a sinistra + R*(w/2, h/2)
                val cx = blX + (sz[0] / 2) * kotlin.math.cos(rad).toFloat() - (sz[1] / 2) * sin(rad).toFloat()
                val cy = cell[2] + (sz[0] / 2) * sin(rad).toFloat() + (sz[1] / 2) * kotlin.math.cos(rad).toFloat()
                val leaned = BookDraw(last.book, floatArrayOf(cx, cy, last.center[2]), sz, Q.axisAngle(0f, 0f, 1f, angle))
                books += leaned
                cursor = blX + sz[0] * kotlin.math.cos(rad).toFloat() + sz[1] * 0.02f
            }
            gapsAtEnd[cellIndex] = cell[1] - cursor
            placedInCell.clear()
            cellIndex++
            cursor = cells.getOrNull(cellIndex)?.get(0) ?: 0f
        }

        for (b in shelfBooks) {
            if (cellIndex >= cells.size) break
            val sz = bookSize(b)
            if (cursor + sz[0] > cells[cellIndex][1] - 0.006f && placedInCell.isNotEmpty()) {
                closeCell()
                if (cellIndex >= cells.size) break
            }
            val cell = cells[cellIndex]
            val jitterZ = rnd(b.id, 4) * 0.018f
            val cz = zFront - 0.012f - jitterZ - sz[2] / 2
            val tilt = (rnd(b.id, 5) - 0.5f) * 1.2f
            val bd = BookDraw(
                b,
                floatArrayOf(cursor + sz[0] / 2, cell[2] + sz[1] / 2, cz),
                sz,
                Q.axisAngle(0f, 0f, 1f, tilt),
            )
            books += bd
            placedInCell += bd
            cursor += sz[0] + 0.0008f + rnd(b.id, 6) * 0.0012f
        }
        if (placedInCell.isNotEmpty() && cellIndex < cells.size) closeCell()

        // Oggetti negli spazi liberi e nelle celle vuote.
        for ((i, cell) in cells.withIndex()) {
            val gap = gapsAtEnd[i] ?: (cell[1] - cell[0])
            val empty = !gapsAtEnd.containsKey(i)
            if (gap < 0.17f) continue
            val seed = "cell$i"
            val kind = (i * 7 + 3) % 6
            if (empty && rnd(seed, 1) < 0.35f) continue
            val cx = if (empty) cell[0] + W * (0.3f + rnd(seed, 2) * 0.4f) else cell[1] - min(gap, 0.3f) / 2
            val cy = cell[2]
            val cz = Z0 + D * 0.55f
            when (kind) {
                0, 3 -> plant(props, cx, cy, cz, palette, small = true)
                1 -> { candle(props, cx, cy, cz); candles += floatArrayOf(cx, cy + 0.16f, cz + 0.02f) }
                2 -> globe(props, cx, cy, cz)
                4 -> frame(props, cx, cy, cz, palette)
                else -> vase(props, cx, cy, cz, palette)
            }
            if (!empty && cells.getOrNull(i) != null && gap > 0.22f) {
                // fermalibri metallico accanto all'ultimo libro
                val bx = cell[1] - gap + 0.004f
                box(props, bx, cy, cz - 0.05f, bx + 0.004f, cy + 0.13f, cz + 0.05f, Mat(4, tint = floatArrayOf(0.05f, 0.05f, 0.05f), spec = 0.5f, shininess = 60f))
            }
        }

        // Stanza: pavimento, parete, battiscopa, finestra, tappeto
        val roomMinX = -2.6f
        val deskX0 = caseEnd + 0.45f
        val deskX1 = deskX0 + 1.25f
        val roomMaxX = deskX1 + 1.4f
        box(props, roomMinX, -0.03f, -0.05f, roomMaxX, 0f, 4.5f, Mat(0, TexKeys.FLOOR, palette.floorTint, texScale = 0.85f, spec = 0.18f, shininess = 30f))
        box(props, roomMinX, 0f, -0.06f, roomMaxX, 3.1f, 0f, Mat(0, TexKeys.WALL, palette.wallTint, texScale = 1.1f, spec = 0.02f))
        box(props, roomMinX, 0f, 0f, roomMaxX, 0.11f, 0.016f, Mat(4, tint = palette.trim, spec = 0.15f))
        box(props, roomMinX, 2.95f, 0f, roomMaxX, 3.1f, 0.04f, Mat(4, tint = palette.trim, spec = 0.05f))
        window(props, -1.55f, 0.95f, -0.45f, 2.25f, palette)
        val rugCx = (0f + caseEnd) / 2
        box(props, rugCx - 1.25f, 0f, 0.55f, rugCx + 1.25f, 0.012f, 2.3f, Mat(3, TexKeys.RUG, floatArrayOf(1f, 1f, 1f), spec = 0.0f, castShadow = true))

        // Scrivania con la pila
        val deskTop = 0.76f
        val dz0 = 0.06f
        val dz1 = 0.72f
        val deskMat = Mat(0, TexKeys.WOOD, palette.woodTint, texScale = 1.4f, spec = 0.25f, shininess = 60f)
        box(props, deskX0, deskTop - 0.035f, dz0, deskX1, deskTop, dz1, deskMat)
        for ((lx, lz) in listOf(deskX0 + 0.04f to dz0 + 0.04f, deskX1 - 0.09f to dz0 + 0.04f, deskX0 + 0.04f to dz1 - 0.09f, deskX1 - 0.09f to dz1 - 0.09f)) {
            box(props, lx, 0f, lz, lx + 0.05f, deskTop - 0.035f, lz + 0.05f, Mat(0, TexKeys.WOOD, palette.woodTint, 1.4f, grainVertical = true))
        }
        box(props, deskX0 + 0.06f, deskTop - 0.15f, dz0 + 0.05f, deskX1 - 0.06f, deskTop - 0.035f, dz0 + 0.07f, deskMat)

        var stackY = deskTop
        val pileX = deskX0 + 0.38f
        for ((i, b) in pile.withIndex()) {
            val sz = bookSize(b)
            val yaw = (rnd(b.id, 11) - 0.5f) * 16f
            val off = (rnd(b.id, 12) - 0.5f) * 0.05f
            val rot = Q.mul(Q.axisAngle(0f, 1f, 0f, yaw), Q.axisAngle(0f, 0f, 1f, 90f))
            books += BookDraw(b, floatArrayOf(pileX + off, stackY + sz[0] / 2, 0.38f + (rnd(b.id, 13) - 0.5f) * 0.04f), sz, rot)
            stackY += sz[0] + 0.0005f
            if (i > 12) break
        }
        // Lampada da scrivania, tazza, pianta
        val lampX = deskX1 - 0.25f
        val lampZ = 0.2f
        val brass = Mat(4, tint = floatArrayOf(0.55f, 0.38f, 0.14f), spec = 0.9f, shininess = 90f)
        cyl(props, lampX, deskTop, lampZ, 0.075f, 0.018f, brass)
        cyl(props, lampX, deskTop + 0.018f, lampZ, 0.01f, 0.36f, brass)
        props += Draw(MeshKind.CONE, M.chain(M.translate(lampX, deskTop + 0.33f, lampZ + 0.02f), M.scale(0.13f, 0.13f, 0.13f)), Mat(4, tint = palette.lampShade, spec = 0.3f, shininess = 30f))
        sphere(props, lampX, deskTop + 0.34f, lampZ + 0.02f, 0.035f, 0.035f, 0.035f, Mat(2, tint = palette.bulb, castShadow = false, glow = 1))
        cyl(props, deskX0 + 0.85f, deskTop, 0.5f, 0.038f, 0.09f, Mat(4, tint = floatArrayOf(0.85f, 0.82f, 0.76f), spec = 0.6f, shininess = 80f))
        plant(props, roomMinX + 1.2f + 0.9f, 0f, 0.45f, palette, small = false)
        val deskLamp = floatArrayOf(lampX, deskTop + 0.28f, lampZ + 0.08f)

        // Lampada a stelo davanti alla libreria (luce calda di sera)
        val flX = -0.35f
        val flZ = 0.65f
        cyl(props, flX, 0f, flZ, 0.14f, 0.025f, Mat(4, tint = floatArrayOf(0.03f, 0.03f, 0.03f), spec = 0.6f, shininess = 70f))
        cyl(props, flX, 0.02f, flZ, 0.012f, 1.55f, Mat(4, tint = floatArrayOf(0.03f, 0.03f, 0.03f), spec = 0.6f, shininess = 70f))
        props += Draw(MeshKind.CONE, M.chain(M.translate(flX, 1.5f, flZ), M.scale(0.22f, 0.26f, 0.22f)), Mat(4, tint = palette.lampShade, spec = 0.1f))
        sphere(props, flX, 1.6f, flZ, 0.05f, 0.05f, 0.05f, Mat(2, tint = palette.bulb, castShadow = false, glow = 1))
        val floorLamp = floatArrayOf(flX + 0.05f, 1.62f, flZ + 0.1f)

        val topY = y0 + ROWS * pitch
        val center = floatArrayOf(caseEnd / 2, topY * 0.52f, zFront)
        val homeDist = max(2.4f, caseEnd * 0.95f + 0.6f).coerceAtMost(5.5f)

        return SceneLayout(
            props = props,
            books = books,
            cases = cases,
            caseParams = floatArrayOf(y0, pitch, Z0, W + T),
            caseExtra = floatArrayOf(topY, zFront, S),
            boundsMin = floatArrayOf(roomMinX, 0f, 0f),
            boundsMax = floatArrayOf(roomMaxX, 3.1f, 2.4f),
            homeTarget = center,
            homeDistance = homeDist,
            deskLamp = deskLamp,
            floorLamp = floorLamp,
            candles = candles,
        )
    }

    // ---- primitive ----

    private fun box(list: MutableList<Draw>, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, mat: Mat) {
        list += Draw(MeshKind.BOX, M.chain(M.translate(x0, y0, z0), M.scale(x1 - x0, y1 - y0, z1 - z0)), mat)
    }

    private fun cyl(list: MutableList<Draw>, cx: Float, y: Float, cz: Float, r: Float, h: Float, mat: Mat) {
        list += Draw(MeshKind.CYL, M.chain(M.translate(cx, y, cz), M.scale(r, h, r)), mat)
    }

    private fun sphere(list: MutableList<Draw>, cx: Float, cy: Float, cz: Float, rx: Float, ry: Float, rz: Float, mat: Mat) {
        list += Draw(MeshKind.SPHERE, M.chain(M.translate(cx, cy, cz), M.scale(rx, ry, rz)), mat)
    }

    private fun plant(list: MutableList<Draw>, cx: Float, y: Float, cz: Float, p: RoomPalette, small: Boolean) {
        val k = if (small) 1f else 2.6f
        val pot = Mat(4, tint = p.pot, spec = 0.15f)
        list += Draw(MeshKind.POT, M.chain(M.translate(cx, y, cz), M.scale(0.042f * k, 0.085f * k, 0.042f * k)), Mat(4, tint = p.pot, spec = 0.15f))
        cyl(list, cx, y + 0.08f * k, cz, 0.052f * k, 0.012f * k, pot)
        val leaf = Mat(4, tint = floatArrayOf(0.05f, 0.16f, 0.04f), spec = 0.25f, shininess = 30f)
        val leaf2 = Mat(4, tint = floatArrayOf(0.09f, 0.24f, 0.06f), spec = 0.25f, shininess = 30f)
        for (i in 0 until 9) {
            val a = i * 2.39996f
            val r = 0.025f * k * (0.6f + (i % 3) * 0.3f)
            val lx = cx + kotlin.math.cos(a) * r
            val lz = cz + kotlin.math.sin(a) * r
            val ly = y + (0.11f + (i % 4) * 0.03f) * k
            val rot = M.chain(M.rotate(Math.toDegrees(a.toDouble()).toFloat(), 0f, 1f, 0f), M.rotate(35f + (i % 3) * 15f, 0f, 0f, 1f))
            list += Draw(MeshKind.SPHERE, M.chain(M.translate(lx, ly, lz), rot, M.scale(0.05f * k, 0.012f * k, 0.022f * k)), if (i % 2 == 0) leaf else leaf2)
        }
    }

    private fun candle(list: MutableList<Draw>, cx: Float, y: Float, cz: Float) {
        cyl(list, cx, y, cz, 0.04f, 0.012f, Mat(4, tint = floatArrayOf(0.5f, 0.34f, 0.12f), spec = 0.8f, shininess = 80f))
        cyl(list, cx, y + 0.012f, cz, 0.022f, 0.13f, Mat(4, tint = floatArrayOf(0.85f, 0.8f, 0.68f), spec = 0.1f))
        sphere(list, cx, y + 0.155f, cz, 0.008f, 0.018f, 0.008f, Mat(2, tint = floatArrayOf(6f, 3.2f, 1.0f), castShadow = false, glow = 3))
    }

    private fun globe(list: MutableList<Draw>, cx: Float, y: Float, cz: Float) {
        val wood = Mat(4, tint = floatArrayOf(0.12f, 0.06f, 0.025f), spec = 0.3f, shininess = 40f)
        cyl(list, cx, y, cz, 0.05f, 0.015f, wood)
        cyl(list, cx, y + 0.015f, cz, 0.006f, 0.05f, wood)
        sphere(list, cx, y + 0.14f, cz, 0.085f, 0.085f, 0.085f, Mat(4, tint = floatArrayOf(0.12f, 0.22f, 0.3f), spec = 0.5f, shininess = 70f))
        sphere(list, cx - 0.03f, y + 0.17f, cz + 0.06f, 0.035f, 0.04f, 0.03f, Mat(4, tint = floatArrayOf(0.35f, 0.3f, 0.12f), spec = 0.4f))
        sphere(list, cx + 0.04f, y + 0.11f, cz + 0.055f, 0.03f, 0.03f, 0.035f, Mat(4, tint = floatArrayOf(0.35f, 0.3f, 0.12f), spec = 0.4f))
    }

    private fun frame(list: MutableList<Draw>, cx: Float, y: Float, cz: Float, p: RoomPalette) {
        val back = M.chain(M.translate(cx, y, cz - 0.04f), M.rotate(-8f, 1f, 0f, 0f))
        list += Draw(MeshKind.BOX, M.chain(back, M.translate(-0.075f, 0f, 0f), M.scale(0.15f, 0.2f, 0.018f)), Mat(4, tint = p.woodTint.map { it * 0.35f }.toFloatArray(), spec = 0.3f))
        list += Draw(MeshKind.BOX, M.chain(back, M.translate(-0.058f, 0.02f, 0.018f), M.scale(0.116f, 0.16f, 0.002f)), Mat(4, tint = floatArrayOf(0.55f, 0.5f, 0.42f), spec = 0.6f, shininess = 90f))
    }

    private fun vase(list: MutableList<Draw>, cx: Float, y: Float, cz: Float, p: RoomPalette) {
        val m = Mat(4, tint = p.ceramic, spec = 0.7f, shininess = 90f)
        sphere(list, cx, y + 0.07f, cz, 0.055f, 0.07f, 0.055f, m)
        cyl(list, cx, y + 0.11f, cz, 0.022f, 0.07f, m)
    }

    private fun window(list: MutableList<Draw>, x0: Float, y0: Float, x1: Float, y1: Float, p: RoomPalette) {
        // vetro emissivo (il cielo), telaio, davanzale
        list += Draw(MeshKind.BOX, M.chain(M.translate(x0, y0, -0.03f), M.scale(x1 - x0, y1 - y0, 0.01f)), Mat(2, tint = p.sky, castShadow = false, glow = 2))
        val frame = Mat(4, tint = p.trim, spec = 0.2f)
        val f = 0.05f
        box(list, x0 - f, y0 - f, -0.03f, x1 + f, y0, 0.04f, frame)
        box(list, x0 - f, y1, -0.03f, x1 + f, y1 + f, 0.04f, frame)
        box(list, x0 - f, y0, -0.03f, x0, y1, 0.04f, frame)
        box(list, x1, y0, -0.03f, x1 + f, y1, 0.04f, frame)
        val mid = (x0 + x1) / 2
        box(list, mid - 0.018f, y0, -0.025f, mid + 0.018f, y1, 0.0f, frame)
        box(list, x0, (y0 + y1) / 2 - 0.018f, -0.025f, x1, (y0 + y1) / 2 + 0.018f, 0f, frame)
        box(list, x0 - 0.09f, y0 - f - 0.035f, -0.03f, x1 + 0.09f, y0 - f, 0.14f, frame)
        // tende
        val curtain = Mat(4, tint = p.curtain, spec = 0.02f)
        for (side in listOf(x0 - 0.32f, x1 + 0.08f)) {
            for (k in 0 until 4) {
                val cx = side + 0.03f + k * 0.06f
                list += Draw(MeshKind.CYL, M.chain(M.translate(cx, 0.4f, 0.08f), M.scale(0.04f, y1 + 0.35f - 0.4f, 0.03f)), curtain)
            }
        }
        box(list, x0 - 0.4f, y1 + 0.32f, 0.05f, x1 + 0.4f, y1 + 0.35f, 0.08f, Mat(4, tint = floatArrayOf(0.1f, 0.07f, 0.04f), spec = 0.5f))
    }

    @Suppress("unused")
    private val tau = (2 * PI).toFloat()

    fun pileOf(books: List<Book>): List<Book> = books
        .filter { it.readingStatus == ReadingStatus.READING || it.readingStatus == ReadingStatus.TO_READ }
        .sortedWith(compareBy({ if (it.readingStatus == ReadingStatus.READING) 1 else 0 }, { it.addedAt }))
        .takeLast(10)
}
