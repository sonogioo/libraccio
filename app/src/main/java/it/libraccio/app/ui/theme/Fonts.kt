@file:OptIn(ExperimentalTextApi::class)

package it.libraccio.app.ui.theme

import android.content.res.AssetManager
import android.graphics.Typeface
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/** EB Garamond (SIL OFL), incluso negli assets: per l'interfaccia e per i dorsi in 3D. */
object LibFonts {
    private const val REGULAR = "fonts/EBGaramond.ttf"
    private const val ITALIC = "fonts/EBGaramond-Italic.ttf"

    lateinit var family: FontFamily
        private set
    lateinit var spineBold: Typeface
        private set
    lateinit var spineRegular: Typeface
        private set
    lateinit var spineItalic: Typeface
        private set

    fun init(assets: AssetManager) {
        family = FontFamily(
            Font(REGULAR, assets, FontWeight.Normal),
            Font(REGULAR, assets, FontWeight.Medium),
            Font(REGULAR, assets, FontWeight.SemiBold),
            Font(REGULAR, assets, FontWeight.Bold),
            Font(ITALIC, assets, FontWeight.Normal, FontStyle.Italic),
        )
        spineBold = typeface(assets, REGULAR, 700)
        spineRegular = typeface(assets, REGULAR, 500)
        spineItalic = typeface(assets, ITALIC, 500)
    }

    private fun typeface(assets: AssetManager, path: String, weight: Int): Typeface = runCatching {
        Typeface.Builder(assets, path).setFontVariationSettings("'wght' $weight").build()
    }.getOrElse { Typeface.create(Typeface.SERIF, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL) }
}
