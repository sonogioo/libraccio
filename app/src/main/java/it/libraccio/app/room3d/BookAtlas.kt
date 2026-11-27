package it.libraccio.app.room3d

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.LruCache
import coil.imageLoader
import coil.request.ImageRequest
import it.libraccio.app.data.Book
import it.libraccio.app.data.authorSurname
import it.libraccio.app.data.firstAuthor
import it.libraccio.app.ui.theme.LibFonts
import java.util.Random
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Texture di un libro: dorso | copertina | retro affiancati, con i rettangoli uv di ciascuno. */
class BookAtlas(val bitmap: Bitmap, val spine: FloatArray, val front: FloatArray, val back: FloatArray)

object BookAtlasFactory {
    private val cache = object : LruCache<String, BookAtlas>(80 * 1024 * 1024) {
        override fun sizeOf(key: String, value: BookAtlas) = value.bitmap.byteCount
    }

    private fun key(b: Book, h: Int) = "${b.id}|${b.title}|${b.authors}|${b.spineColor}|${b.coverUrl}|${b.format}|${b.pageCount}|${b.favorite}|$h"

    fun cached(b: Book, h: Int): BookAtlas? = cache.get(key(b, h))

    suspend fun build(context: Context, b: Book, size: FloatArray, h: Int): BookAtlas {
        cached(b, h)?.let { return it }
        val cover = loadCover(context, b.coverUrl, h)
        val atlas = draw(b, size, h, cover)
        cache.put(key(b, h), atlas)
        return atlas
    }

    private suspend fun loadCover(context: Context, url: String?, h: Int): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val req = ImageRequest.Builder(context).data(url).allowHardware(false).size(h).build()
            (context.imageLoader.execute(req).drawable as? BitmapDrawable)?.bitmap
        }.getOrNull()
    }

    private fun isLight(c: Int) = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) > 150

    fun draw(b: Book, size: FloatArray, h: Int, cover: Bitmap?): BookAtlas {
        val sw = (h * size[0] / size[1]).roundToInt().coerceIn(12, 220)
        val cw = (h * size[2] / size[1]).roundToInt().coerceIn(40, 600)
        val totalW = sw + cw * 2
        val bmp = Bitmap.createBitmap(totalW, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = Random(b.id.hashCode().toLong())
        val base = b.spineColor or 0xFF000000.toInt()
        val light = isLight(base)
        val ink = if (light) Color.rgb(32, 26, 20) else Color.rgb(242, 232, 210)
        val gold = Color.rgb(212, 172, 92)
        val design = (b.id.hashCode() and 0x7fffffff) % 4
        val foil = if (design == 0 || design == 2) gold else ink

        // ---- dorso ----
        c.save()
        c.clipRect(0, 0, sw, h)
        c.drawColor(base)
        cloth(c, sw, h, rnd, light)
        // leggero arrotondamento del dorso
        val shade = Paint().apply {
            shader = LinearGradient(0f, 0f, sw.toFloat(), 0f,
                intArrayOf(Color.argb(70, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(30, 255, 255, 255), Color.argb(0, 0, 0, 0), Color.argb(80, 0, 0, 0)),
                floatArrayOf(0f, 0.2f, 0.45f, 0.75f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, sw.toFloat(), h.toFloat(), shade)
        val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = foil }
        when (design) {
            0 -> for (y in listOf(0.06f, 0.085f, 0.915f, 0.94f)) c.drawRect(0f, h * y, sw.toFloat(), h * y + max(1f, h * 0.006f), bandPaint)
            1 -> {
                val lbl = Paint().apply { color = Color.rgb(28, 24, 20) }
                c.drawRect(sw * 0.08f, h * 0.12f, sw * 0.92f, h * 0.32f, lbl)
                bandPaint.color = gold
                c.drawRect(sw * 0.08f, h * 0.12f, sw * 0.92f, h * 0.124f, bandPaint)
                c.drawRect(sw * 0.08f, h * 0.316f, sw * 0.92f, h * 0.32f, bandPaint)
            }
            2 -> {
                for (k in 0 until 5) c.drawRect(0f, h * (0.18f + k * 0.15f), sw.toFloat(), h * (0.18f + k * 0.15f) + max(1f, h * 0.004f), bandPaint)
            }
            else -> {
                val top = Paint().apply { color = blend(base, Color.BLACK, 0.35f) }
                c.drawRect(0f, 0f, sw.toFloat(), h * 0.1f, top)
                c.drawRect(0f, h * 0.9f, sw.toFloat(), h.toFloat(), top)
            }
        }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (design == 1) gold else foil
            typeface = LibFonts.spineBold
            textSize = min(sw * 0.56f, h * 0.055f).coerceAtLeast(6f)
            letterSpacing = 0.02f
        }
        if (design == 1) {
            // etichetta in testa con il titolo orizzontale (dorsi larghi) o verticale
            vertical(c, b.title, textPaint, sw, h * 0.36f, h * 0.5f)
        } else {
            vertical(c, b.title, textPaint, sw, h * 0.13f, h * 0.62f)
        }
        val surname = b.authorSurname.uppercase()
        if (surname.isNotBlank() && sw > 18) {
            val small = TextPaint(textPaint).apply { typeface = LibFonts.spineRegular; textSize = textPaint.textSize * 0.68f; letterSpacing = 0.12f }
            vertical(c, surname, small, sw, h * 0.78f, h * 0.12f)
        }
        // marchio editoriale
        val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = foil; style = Paint.Style.STROKE; strokeWidth = max(1f, h * 0.004f) }
        if (sw > 16) c.drawCircle(sw / 2f, h * 0.955f, min(sw * 0.22f, h * 0.014f), mark)
        if (b.favorite) {
            bandPaint.color = gold
            c.drawRect(0f, h * 0.5f, sw.toFloat(), h * 0.5f + max(1f, h * 0.008f), bandPaint)
        }
        c.restore()

        // ---- copertina ----
        c.save()
        c.translate(sw.toFloat(), 0f)
        c.clipRect(0, 0, cw, h)
        if (cover != null) {
            val src = centerCrop(cover.width, cover.height, cw, h)
            c.drawBitmap(cover, src, RectF(0f, 0f, cw.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            typographicCover(c, b, cw, h, base, ink, gold, light, rnd)
        }
        // cerniera vicino al dorso
        c.drawRect(0f, 0f, cw * 0.05f, h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, cw * 0.05f, 0f, Color.argb(90, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        })
        c.restore()

        // ---- retro ----
        c.save()
        c.translate((sw + cw).toFloat(), 0f)
        c.clipRect(0, 0, cw, h)
        c.drawColor(blend(base, Color.BLACK, 0.12f))
        cloth(c, cw, h, rnd, light)
        val lines = Paint().apply { color = Color.argb(60, Color.red(ink), Color.green(ink), Color.blue(ink)) }
        for (k in 0 until 9) {
            val y = h * (0.2f + k * 0.045f)
            c.drawRect(cw * 0.15f, y, cw * (0.85f - (k % 3) * 0.08f), y + max(1f, h * 0.008f), lines)
        }
        c.drawRect(cw * 0.55f, h * 0.8f, cw * 0.85f, h * 0.9f, Paint().apply { color = Color.WHITE })
        val bar = Paint().apply { color = Color.BLACK }
        for (k in 0 until 26) if (rnd.nextBoolean()) {
            val x = cw * 0.57f + k * (cw * 0.26f / 26f)
            c.drawRect(x, h * 0.81f, x + max(1f, cw * 0.006f), h * 0.88f, bar)
        }
        c.restore()

        val fw = totalW.toFloat()
        return BookAtlas(
            bmp,
            spine = floatArrayOf(0f, 0f, sw / fw, 1f),
            front = floatArrayOf(sw / fw, 0f, cw / fw, 1f),
            back = floatArrayOf((sw + cw) / fw, 0f, cw / fw, 1f),
        )
    }

    private fun vertical(c: Canvas, text: String, paint: TextPaint, sw: Int, start: Float, maxLen: Float) {
        val t = TextUtils.ellipsize(text, paint, maxLen, TextUtils.TruncateAt.END).toString()
        c.save()
        // dopo la rotazione le lettere "crescono" verso destra: centro la loro altezza sul dorso
        c.translate(sw / 2f - paint.textSize * 0.34f, start)
        c.rotate(90f)
        c.drawText(t, 0f, 0f, paint)
        c.restore()
    }

    private fun cloth(c: Canvas, w: Int, h: Int, rnd: Random, light: Boolean) {
        val p = Paint()
        val a = if (light) 10 else 14
        var y = 0f
        while (y < h) {
            p.color = Color.argb(a + rnd.nextInt(a), 0, 0, 0)
            c.drawRect(0f, y, w.toFloat(), y + 0.7f, p)
            y += 2f + rnd.nextFloat() * 1.5f
        }
        var x = 0f
        while (x < w) {
            p.color = Color.argb(a / 2 + rnd.nextInt(a), 255, 255, 255)
            c.drawRect(x, 0f, x + 0.7f, h.toFloat(), p)
            x += 2f + rnd.nextFloat() * 1.5f
        }
        // usura ai bordi
        p.color = Color.argb(40, 255, 255, 255)
        c.drawRect(0f, 0f, w.toFloat(), 1.5f, p)
        c.drawRect(0f, h - 1.5f, w.toFloat(), h.toFloat(), p)
    }

    private fun typographicCover(c: Canvas, b: Book, w: Int, h: Int, base: Int, ink: Int, gold: Int, light: Boolean, rnd: Random) {
        c.drawColor(base)
        cloth(c, w, h, rnd, light)
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; style = Paint.Style.STROKE; strokeWidth = max(1f, h * 0.005f) }
        c.drawRect(w * 0.08f, h * 0.06f, w * 0.92f, h * 0.94f, frame)
        c.drawRect(w * 0.1f, h * 0.075f, w * 0.9f, h * 0.925f, frame.apply { strokeWidth = max(1f, h * 0.002f) })
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; typeface = LibFonts.spineBold; textSize = h * 0.07f }
        val layout = StaticLayout.Builder.obtain(b.title, 0, b.title.length, tp, (w * 0.72f).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(5).setEllipsize(TextUtils.TruncateAt.END).build()
        c.save()
        c.translate(w * 0.14f, h * 0.3f - layout.height / 2f + h * 0.05f)
        layout.draw(c)
        c.restore()
        val ap = TextPaint(tp).apply { typeface = LibFonts.spineItalic; textSize = h * 0.045f }
        val author = TextUtils.ellipsize(b.firstAuthor, ap, w * 0.75f, TextUtils.TruncateAt.END).toString()
        c.drawText(author, (w - ap.measureText(author)) / 2f, h * 0.8f, ap)
        c.drawCircle(w / 2f, h * 0.65f, h * 0.02f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold })
    }

    private fun centerCrop(sw: Int, sh: Int, dw: Int, dh: Int): Rect {
        val target = dw.toFloat() / dh
        val src = sw.toFloat() / sh
        return if (src > target) {
            val nw = (sh * target).toInt()
            Rect((sw - nw) / 2, 0, (sw + nw) / 2, sh)
        } else {
            val nh = (sw / target).toInt()
            Rect(0, (sh - nh) / 2, sw, (sh + nh) / 2)
        }
    }

    private fun blend(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt(),
    )
}
