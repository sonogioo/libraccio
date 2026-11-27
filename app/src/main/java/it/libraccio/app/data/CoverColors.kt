package it.libraccio.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest

/** Ricava il colore del dorso dalla copertina. */
object CoverColors {
    val swatches: List<Int> = listOf(
        0xFF7B2D26, 0xFF1F4E5F, 0xFF2E5E3A, 0xFF5B3A6B, 0xFF8C6D1F,
        0xFF3B3B58, 0xFF9C4A1A, 0xFF24403A, 0xFF6E2C3E, 0xFF35506B,
        0xFFD9C3A0, 0xFF1E1E1E,
    ).map { it.toInt() }

    fun fallback(seed: String): Int = swatches[(seed.hashCode() and 0x7fffffff) % 10]

    fun fromBitmap(bitmap: Bitmap): Int? = runCatching {
        val p = Palette.from(bitmap).maximumColorCount(16).generate()
        (p.dominantSwatch ?: p.vibrantSwatch ?: p.darkVibrantSwatch)?.rgb
    }.getOrNull()

    suspend fun fromUrl(context: Context, url: String?): Int? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(256)
                .build()
            val result = context.imageLoader.execute(request)
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return null
            fromBitmap(bitmap)
        }.getOrNull()
    }
}
