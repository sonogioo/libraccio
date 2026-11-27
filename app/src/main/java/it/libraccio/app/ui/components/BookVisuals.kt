package it.libraccio.app.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import it.libraccio.app.data.Book
import it.libraccio.app.data.ReadingStatus
import it.libraccio.app.data.authorSurname
import it.libraccio.app.data.firstAuthor
import it.libraccio.app.data.readingStatus
import it.libraccio.app.ui.theme.darken
import it.libraccio.app.ui.theme.lighten
import kotlin.math.min

/** Spessore del dorso in dp, ricavato dal numero di pagine. */
fun bookThicknessDp(book: Book): Float =
    if (book.pageCount <= 0) 22f
    else 12f + (book.pageCount.coerceIn(60, 1000) - 60) / 940f * 34f

fun spineTextColor(base: Color): Color =
    if (base.luminance() > 0.5f) Color(0xFF1E1A16) else Color(0xFFF6EEDF)

private val Gold = Color(0xFFD9B45A)

/** Il dorso di un libro in piedi sullo scaffale, disegnato a mano. */
@Composable
fun BookSpine(
    book: Book,
    modifier: Modifier = Modifier,
    lifted: Boolean = false,
    lean: Boolean = false,
    showText: Boolean = true,
) {
    val lift by animateFloatAsState(if (lifted) 1f else 0f, label = "lift")
    val base = Color(book.spineColor)
    val textColor = spineTextColor(base)
    val style = (book.id.hashCode() and 0x7fffffff) % 3
    val reading = book.readingStatus == ReadingStatus.READING

    Canvas(
        modifier.graphicsLayer {
            translationY = -18.dp.toPx() * lift
            if (lean) {
                transformOrigin = TransformOrigin(0f, 1f)
                rotationZ = -9f
                translationX = size.height * 0.156f
            }
        },
    ) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.horizontalGradient(
                0f to base.darken(0.38f),
                0.18f to base,
                0.45f to base.lighten(0.14f),
                0.8f to base,
                1f to base.darken(0.42f),
            ),
        )
        // segno delle pagine in cima
        drawRect(Color.Black.copy(alpha = 0.18f), topLeft = Offset.Zero, size = Size(w, 1.5.dp.toPx()))

        val bandColor = if (style == 0) Gold.copy(alpha = 0.85f) else textColor.copy(alpha = 0.55f)
        when (style) {
            0 -> listOf(0.07f, 0.10f, 0.90f, 0.93f).forEach { y ->
                drawRect(bandColor, topLeft = Offset(0f, h * y), size = Size(w, 1.2.dp.toPx()))
            }
            1 -> {
                drawRect(
                    textColor.copy(alpha = 0.12f),
                    topLeft = Offset(w * 0.14f, h * 0.15f),
                    size = Size(w * 0.72f, h * 0.55f),
                )
                drawRect(bandColor, topLeft = Offset(0f, h * 0.05f), size = Size(w, 2.dp.toPx()))
            }
            else -> Unit
        }

        if (reading) {
            // nastrino segnalibro che sporge
            drawRect(
                Color(0xFFB3261E),
                topLeft = Offset(w * 0.6f, 0f),
                size = Size(3.dp.toPx(), h * 0.12f),
            )
        }
        if (book.favorite) {
            drawCircle(Gold, radius = min(w * 0.18f, 3.5.dp.toPx()), center = Offset(w / 2, h * 0.965f))
        }

        if (showText && w > 9.dp.toPx()) {
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textColor.toArgb()
                    textSize = min(w * 0.48f, 13.sp.toPx()).coerceAtLeast(7.sp.toPx())
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                }
                val title = TextUtils.ellipsize(book.title, paint, h * 0.6f, TextUtils.TruncateAt.END).toString()
                nc.save()
                nc.translate(w / 2 - paint.textSize * 0.35f, h * 0.15f)
                nc.rotate(90f)
                nc.drawText(title, 0f, 0f, paint)
                nc.restore()

                val surname = book.authorSurname
                if (surname.isNotBlank() && w > 16.dp.toPx()) {
                    val small = TextPaint(paint).apply {
                        textSize = paint.textSize * 0.72f
                        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                    }
                    val text = TextUtils.ellipsize(surname.uppercase(), small, h * 0.16f, TextUtils.TruncateAt.END).toString()
                    nc.save()
                    nc.translate(w / 2 - small.textSize * 0.35f, h * 0.78f)
                    nc.rotate(90f)
                    nc.drawText(text, 0f, 0f, small)
                    nc.restore()
                }
            }
        }
    }
}

/** Un libro sdraiato (per la pila sulla scrivania). */
@Composable
fun FlatBook(book: Book, modifier: Modifier = Modifier) {
    val base = Color(book.spineColor)
    val textColor = spineTextColor(base)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val pages = 5.dp.toPx()
        drawRect(
            Brush.verticalGradient(0f to base.lighten(0.12f), 0.5f to base, 1f to base.darken(0.4f)),
            size = Size(w - pages, h),
        )
        drawRect(Color(0xFFF3EBDD), topLeft = Offset(w - pages, h * 0.1f), size = Size(pages, h * 0.8f))
        drawRect(Color.Black.copy(alpha = 0.12f), topLeft = Offset(w - pages, h * 0.45f), size = Size(pages, 1f))
        drawIntoCanvas { canvas ->
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor.toArgb()
                textSize = min(h * 0.55f, 12.sp.toPx())
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            val text = TextUtils.ellipsize(book.title, paint, w - pages - 12.dp.toPx(), TextUtils.TruncateAt.END).toString()
            canvas.nativeCanvas.drawText(text, 6.dp.toPx(), h / 2 + paint.textSize * 0.35f, paint)
        }
    }
}

/** La copertina: foto reale se disponibile, altrimenti una copertina "tipografica" generata. */
@Composable
fun BookCover(book: Book, modifier: Modifier = Modifier, small: Boolean = false) {
    val base = Color(book.spineColor)
    val textColor = spineTextColor(base)
    Box(
        modifier
            .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp, topStart = 1.dp, bottomStart = 1.dp))
            .background(Brush.linearGradient(listOf(base.lighten(0.1f), base.darken(0.25f)))),
    ) {
        Column(
            Modifier.fillMaxSize().padding(if (small) 3.dp else 14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                book.title,
                color = textColor,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = if (small) 7.sp else 18.sp,
                lineHeight = if (small) 8.sp else 22.sp,
                textAlign = TextAlign.Center,
                maxLines = if (small) 4 else 6,
                overflow = TextOverflow.Ellipsis,
            )
            if (!small && book.firstAuthor.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(book.firstAuthor, color = textColor.copy(alpha = 0.85f), fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
        if (!book.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = book.coverUrl,
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxHeight()
                .width(if (small) 3.dp else 9.dp)
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent))),
        )
    }
}
