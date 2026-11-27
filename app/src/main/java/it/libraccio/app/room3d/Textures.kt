package it.libraccio.app.room3d

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/** Colori e materiali di una stanza (i tint sono in spazio lineare). */
class RoomPalette(
    val key: String,
    val woodLight: Int,
    val woodDark: Int,
    val floorLight: Int,
    val floorDark: Int,
    val wallBase: Int,
    val wallMotif: Int,
    val rugField: Int,
    val rugBorder: Int,
    val rugAccent: Int,
    val woodTint: FloatArray = floatArrayOf(1f, 1f, 1f),
    val floorTint: FloatArray = floatArrayOf(1f, 1f, 1f),
    val wallTint: FloatArray = floatArrayOf(1f, 1f, 1f),
    val trim: FloatArray,
    val lampShade: FloatArray,
    val bulb: FloatArray = floatArrayOf(9f, 6.4f, 3.6f),
    val pot: FloatArray = floatArrayOf(0.42f, 0.14f, 0.06f),
    val ceramic: FloatArray = floatArrayOf(0.12f, 0.2f, 0.25f),
    val sky: FloatArray = floatArrayOf(2.6f, 2.9f, 3.3f),
    val curtain: FloatArray,
)

object RoomPalettes {
    fun of(key: String): RoomPalette = when (key) {
        "MINIMAL" -> RoomPalette(
            key, woodLight = 0xFFE4CFAE.toInt(), woodDark = 0xFFBF9F78.toInt(),
            floorLight = 0xFFD8C2A0.toInt(), floorDark = 0xFFB09272.toInt(),
            wallBase = 0xFFEDE7DC.toInt(), wallMotif = 0xFFE3DBCD.toInt(),
            rugField = 0xFFD9D3C7.toInt(), rugBorder = 0xFF8C8173.toInt(), rugAccent = 0xFFB8AE9E.toInt(),
            trim = floatArrayOf(0.85f, 0.83f, 0.8f), lampShade = floatArrayOf(0.82f, 0.8f, 0.75f),
            curtain = floatArrayOf(0.8f, 0.78f, 0.72f),
        )
        "SOFFITTA" -> RoomPalette(
            key, woodLight = 0xFFA58C6C.toInt(), woodDark = 0xFF6E5A44.toInt(),
            floorLight = 0xFF9C8466.toInt(), floorDark = 0xFF5E4C3A.toInt(),
            wallBase = 0xFF8C9A9C.toInt(), wallMotif = 0xFF7E8C8F.toInt(),
            rugField = 0xFF6B3B2E.toInt(), rugBorder = 0xFF2C3E4A.toInt(), rugAccent = 0xFFC9A66B.toInt(),
            trim = floatArrayOf(0.7f, 0.7f, 0.66f), lampShade = floatArrayOf(0.12f, 0.2f, 0.16f),
            curtain = floatArrayOf(0.35f, 0.38f, 0.3f),
        )
        "GOTICA" -> RoomPalette(
            key, woodLight = 0xFF5A3422.toInt(), woodDark = 0xFF2A140B.toInt(),
            floorLight = 0xFF4A3020.toInt(), floorDark = 0xFF24150C.toInt(),
            wallBase = 0xFF1E3329.toInt(), wallMotif = 0xFF2C4537.toInt(),
            rugField = 0xFF5A1018.toInt(), rugBorder = 0xFF1A1424.toInt(), rugAccent = 0xFFB08A3E.toInt(),
            trim = floatArrayOf(0.06f, 0.035f, 0.02f), lampShade = floatArrayOf(0.05f, 0.14f, 0.08f),
            curtain = floatArrayOf(0.2f, 0.02f, 0.03f),
        )
        else -> RoomPalette(
            "STUDIO", woodLight = 0xFF9A6438.toInt(), woodDark = 0xFF4E2C15.toInt(),
            floorLight = 0xFF8E6440.toInt(), floorDark = 0xFF4A3018.toInt(),
            wallBase = 0xFF6E5444.toInt(), wallMotif = 0xFF7C6150.toInt(),
            rugField = 0xFF7A2A22.toInt(), rugBorder = 0xFF1F2B3A.toInt(), rugAccent = 0xFFD2A85E.toInt(),
            trim = floatArrayOf(0.62f, 0.58f, 0.5f), lampShade = floatArrayOf(0.08f, 0.2f, 0.12f),
            curtain = floatArrayOf(0.25f, 0.06f, 0.05f),
        )
    }
}

/** Texture procedurali disegnate con Canvas: nessun file da includere. */
object TextureGen {
    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * tt).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * tt).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * tt).toInt(),
        )
    }

    private fun noise(c: Canvas, size: Int, rnd: Random, count: Int, alpha: Int) {
        val p = Paint()
        repeat(count) {
            p.color = if (rnd.nextBoolean()) Color.argb(alpha, 0, 0, 0) else Color.argb(alpha, 255, 255, 255)
            val x = rnd.nextFloat() * size
            val y = rnd.nextFloat() * size
            c.drawRect(x, y, x + 1.5f, y + 1.5f, p)
        }
    }

    /** Venature lungo x, ripetibili in orizzontale. */
    fun wood(light: Int, dark: Int, seed: Long, size: Int = 512): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = Random(seed)
        // bande larghe di tono
        var y = 0f
        while (y < size) {
            val h = 6f + rnd.nextFloat() * 26f
            c.drawRect(0f, y, size.toFloat(), y + h, Paint().apply { color = lerpColor(dark, light, 0.45f + rnd.nextFloat() * 0.45f) })
            y += h
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        repeat(260) {
            val baseY = rnd.nextFloat() * size
            val amp = 1f + rnd.nextFloat() * 5f
            val freq = (1 + rnd.nextInt(3)).toFloat()
            val phase = rnd.nextFloat() * 6.28f
            p.strokeWidth = 0.6f + rnd.nextFloat() * 2.2f
            p.color = if (rnd.nextFloat() < 0.75f) Color.argb(25 + rnd.nextInt(60), Color.red(dark), Color.green(dark), Color.blue(dark))
            else Color.argb(20 + rnd.nextInt(40), Color.red(light), Color.green(light), Color.blue(light))
            val path = Path()
            var x = 0f
            path.moveTo(0f, baseY + amp * sin(phase))
            while (x <= size) {
                path.lineTo(x, baseY + amp * sin(phase + 2 * PI.toFloat() * freq * x / size))
                x += 8f
            }
            c.drawPath(path, p)
        }
        // nodi
        repeat(2) {
            val kx = rnd.nextFloat() * size
            val ky = rnd.nextFloat() * size
            for (r in 0 until 7) {
                p.strokeWidth = 1.2f
                p.color = Color.argb(70 - r * 8, Color.red(dark), Color.green(dark), Color.blue(dark))
                c.drawOval(kx - 6f - r * 5f, ky - 2f - r * 1.6f, kx + 6f + r * 5f, ky + 2f + r * 1.6f, p)
            }
        }
        noise(c, size, rnd, 9000, 10)
        return bmp
    }

    /** Parquet a listoni. */
    fun floor(light: Int, dark: Int, seed: Long, size: Int = 1024): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = Random(seed)
        val plankH = size / 8f
        val grain = wood(light, dark, seed + 7, 512)
        val gap = Paint().apply { color = Color.argb(160, 20, 12, 6) }
        for (row in 0 until 8) {
            var x = -rnd.nextFloat() * size * 0.5f
            val top = row * plankH
            while (x < size) {
                val len = size * (0.45f + rnd.nextFloat() * 0.55f)
                val sx = rnd.nextInt(256)
                val sy = rnd.nextInt(512 - plankH.toInt())
                val tint = Paint().apply {
                    colorFilter = android.graphics.PorterDuffColorFilter(
                        Color.argb(rnd.nextInt(60), 0, 0, 0),
                        android.graphics.PorterDuff.Mode.SRC_ATOP,
                    )
                }
                c.drawBitmap(
                    grain,
                    android.graphics.Rect(sx, sy, sx + 256, sy + plankH.toInt()),
                    android.graphics.RectF(x, top, x + len, top + plankH),
                    tint,
                )
                c.drawRect(x + len - 1.5f, top, x + len + 1.5f, top + plankH, gap)
                x += len
            }
            c.drawRect(0f, top + plankH - 2f, size.toFloat(), top + plankH + 1f, gap)
        }
        grain.recycle()
        return bmp
    }

    /** Carta da parati a righe con piccolo motivo. */
    fun wallpaper(base: Int, motif: Int, seed: Long, size: Int = 512): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = Random(seed)
        c.drawColor(base)
        val stripe = Paint().apply { color = motif }
        val step = size / 8f
        for (i in 0 until 8) c.drawRect(i * step, 0f, i * step + step * 0.34f, size.toFloat(), stripe)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lerpColor(base, motif, 2.2f) ; alpha = 90 }
        for (i in 0 until 8) for (j in 0 until 4) {
            val cx = i * step + step * 0.67f
            val cy = j * (size / 4f) + (if (i % 2 == 0) 0f else size / 8f) + 30f
            val path = Path().apply {
                moveTo(cx, cy - 12f); lineTo(cx + 6f, cy); lineTo(cx, cy + 12f); lineTo(cx - 6f, cy); close()
            }
            c.drawPath(path, p)
            c.drawCircle(cx, cy - 18f, 2.2f, p)
            c.drawCircle(cx, cy + 18f, 2.2f, p)
        }
        noise(c, size, rnd, 14000, 8)
        return bmp
    }

    /** Tappeto persiano stilizzato (uv locali 0..1). */
    fun rug(field: Int, border: Int, accent: Int, seed: Long, size: Int = 1024): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = Random(seed)
        val s = size.toFloat()
        c.drawColor(border)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = accent
        c.drawRect(s * 0.05f, s * 0.05f, s * 0.95f, s * 0.95f, p)
        p.color = border
        c.drawRect(s * 0.07f, s * 0.07f, s * 0.93f, s * 0.93f, p)
        // motivo del bordo
        p.color = accent
        val n = 18
        for (i in 0 until n) {
            val t = (i + 0.5f) / n
            for ((x, y) in listOf(s * 0.11f to s * (0.1f + 0.8f * t), s * 0.89f to s * (0.1f + 0.8f * t), s * (0.1f + 0.8f * t) to s * 0.11f, s * (0.1f + 0.8f * t) to s * 0.89f)) {
                val path = Path().apply { moveTo(x, y - 12f); lineTo(x + 10f, y); lineTo(x, y + 12f); lineTo(x - 10f, y); close() }
                c.drawPath(path, p)
            }
        }
        p.color = field
        c.drawRect(s * 0.15f, s * 0.15f, s * 0.85f, s * 0.85f, p)
        // medaglione
        p.color = border
        val med = Path().apply { moveTo(s / 2, s * 0.25f); lineTo(s * 0.72f, s / 2); lineTo(s / 2, s * 0.75f); lineTo(s * 0.28f, s / 2); close() }
        c.drawPath(med, p)
        p.color = accent
        val med2 = Path().apply { moveTo(s / 2, s * 0.32f); lineTo(s * 0.65f, s / 2); lineTo(s / 2, s * 0.68f); lineTo(s * 0.35f, s / 2); close() }
        c.drawPath(med2, p)
        p.color = field
        c.drawCircle(s / 2, s / 2, s * 0.07f, p)
        // angoli
        p.color = border
        for ((x, y) in listOf(s * 0.15f to s * 0.15f, s * 0.85f to s * 0.15f, s * 0.15f to s * 0.85f, s * 0.85f to s * 0.85f)) {
            c.drawCircle(x, y, s * 0.09f, p)
        }
        // lana
        repeat(60000) {
            val x = rnd.nextFloat() * s
            val y = rnd.nextFloat() * s
            p.color = if (rnd.nextBoolean()) Color.argb(18, 0, 0, 0) else Color.argb(14, 255, 255, 255)
            c.drawRect(x, y, x + 2f, y + 1f, p)
        }
        // usura al centro
        p.shader = android.graphics.RadialGradient(s / 2, s / 2, s * 0.5f, Color.argb(40, 255, 240, 220), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, s, s, p)
        return bmp
    }

    /** Taglio delle pagine: righe sottili color carta. */
    fun pages(seed: Long): Bitmap {
        val w = 256
        val h = 32
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = Random(seed)
        c.drawColor(Color.rgb(236, 226, 205))
        val p = Paint()
        for (x in 0 until w) {
            val g = 200 + rnd.nextInt(45)
            p.color = Color.argb(40 + rnd.nextInt(60), g - 40, g - 50, g - 70)
            c.drawRect(x.toFloat(), 0f, x + 0.6f, h.toFloat(), p)
        }
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.argb(30, 120, 90, 40), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        return bmp
    }
}
