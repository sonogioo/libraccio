package it.libraccio.app.ui.room

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import it.libraccio.app.ui.theme.RoomTheme
import it.libraccio.app.ui.theme.darken
import it.libraccio.app.ui.theme.lighten

/** La parete con una carta da parati a righe. */
@Composable
fun Wall(theme: RoomTheme, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(theme.wall)
        val step = 28.dp.toPx()
        var x = -step
        while (x < size.width + step) {
            drawRect(theme.wallPattern, topLeft = Offset(x, 0f), size = Size(10.dp.toPx(), size.height))
            x += step
        }
        // battiscopa / pavimento
        drawRect(
            Brush.verticalGradient(listOf(theme.woodDark.darken(0.2f), theme.woodDark.darken(0.5f))),
            topLeft = Offset(0f, size.height - 40.dp.toPx()),
            size = Size(size.width, 40.dp.toPx()),
        )
    }
}

/** Ripiano visto leggermente dall'alto: faccia superiore + bordo frontale. */
@Composable
fun Plank(theme: RoomTheme, modifier: Modifier = Modifier) {
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().height(5.dp)
                .background(Brush.verticalGradient(listOf(theme.woodDark, theme.woodLight))),
        )
        Box(
            Modifier.fillMaxWidth().height(14.dp)
                .background(Brush.verticalGradient(listOf(theme.wood, theme.woodDark)))
                .drawBehind {
                    // venature
                    for (i in 1..3) {
                        val y = size.height * i / 4f
                        drawLine(
                            Color.Black.copy(alpha = 0.08f),
                            Offset(size.width * 0.05f * i, y),
                            Offset(size.width * (0.6f + 0.1f * i), y + 1f),
                            strokeWidth = 1f,
                        )
                    }
                },
        )
    }
}

@Composable
fun CaseSide(theme: RoomTheme, modifier: Modifier = Modifier) {
    Box(modifier.background(Brush.horizontalGradient(listOf(theme.woodDark, theme.wood, theme.woodLight.darken(0.1f)))))
}

@Composable
fun Cornice(theme: RoomTheme, modifier: Modifier = Modifier) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(8.dp).background(theme.woodLight))
        Box(Modifier.fillMaxWidth().height(14.dp).background(Brush.verticalGradient(listOf(theme.wood, theme.woodDark))))
    }
}

@Composable
fun Plinth(theme: RoomTheme, modifier: Modifier = Modifier) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(10.dp).background(theme.woodLight.darken(0.1f)))
        Box(Modifier.fillMaxWidth().height(24.dp).background(Brush.verticalGradient(listOf(theme.woodDark, theme.woodDark.darken(0.35f)))))
    }
}

/** Piccoli oggetti negli spazi liberi degli scaffali. */
@Composable
fun Decoration(kind: Int, night: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "flame")
    val flicker by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "flicker",
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (kind % 4) {
            0 -> { // pianta
                val leaf = Color(0xFF4E7D3A)
                for (i in 0 until 7) {
                    rotate(degrees = -60f + i * 20f, pivot = Offset(w / 2, h * 0.66f)) {
                        drawOval(
                            if (i % 2 == 0) leaf else leaf.lighten(0.15f),
                            topLeft = Offset(w / 2 - w * 0.08f, h * 0.66f - h * 0.45f),
                            size = Size(w * 0.16f, h * 0.45f),
                        )
                    }
                }
                val pot = Path().apply {
                    moveTo(w * 0.25f, h * 0.68f); lineTo(w * 0.75f, h * 0.68f)
                    lineTo(w * 0.68f, h); lineTo(w * 0.32f, h); close()
                }
                drawPath(pot, Color(0xFFB5643C))
                drawRect(Color(0xFF9A5030), topLeft = Offset(w * 0.22f, h * 0.64f), size = Size(w * 0.56f, h * 0.07f))
            }
            1 -> { // candela
                if (night) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color(0x66FFC46B), Color.Transparent),
                            center = Offset(w / 2, h * 0.42f),
                            radius = w * 0.9f * flicker,
                        ),
                        radius = w * 0.9f * flicker,
                        center = Offset(w / 2, h * 0.42f),
                    )
                }
                drawRect(Color(0xFFF1E6CF), topLeft = Offset(w * 0.4f, h * 0.5f), size = Size(w * 0.2f, h * 0.42f))
                drawLine(Color(0xFF2B2B2B), Offset(w / 2, h * 0.5f), Offset(w / 2, h * 0.46f), strokeWidth = 2f)
                if (night) {
                    drawOval(
                        Color(0xFFFFB347),
                        topLeft = Offset(w / 2 - w * 0.06f, h * 0.46f - h * 0.12f * flicker),
                        size = Size(w * 0.12f, h * 0.12f * flicker),
                    )
                }
                drawOval(Color(0xFFB08D57), topLeft = Offset(w * 0.22f, h * 0.9f), size = Size(w * 0.56f, h * 0.1f))
            }
            2 -> { // mappamondo
                drawLine(Color(0xFF3A2E24), Offset(w / 2, h * 0.86f), Offset(w / 2, h), strokeWidth = 4f)
                drawRect(Color(0xFF3A2E24), topLeft = Offset(w * 0.28f, h * 0.95f), size = Size(w * 0.44f, h * 0.05f))
                val r = w * 0.32f
                val c = Offset(w / 2, h * 0.86f - r)
                drawCircle(Color(0xFF2F6E8E), radius = r, center = c)
                drawOval(Color(0xFF6B9A4A), topLeft = Offset(c.x - r * 0.6f, c.y - r * 0.5f), size = Size(r * 0.7f, r * 0.55f))
                drawOval(Color(0xFF6B9A4A), topLeft = Offset(c.x + r * 0.05f, c.y + r * 0.1f), size = Size(r * 0.5f, r * 0.6f))
                drawArc(
                    Color(0xFFB08D57), startAngle = 200f, sweepAngle = 220f, useCenter = false,
                    topLeft = Offset(c.x - r * 1.15f, c.y - r * 1.15f), size = Size(r * 2.3f, r * 2.3f),
                    style = Stroke(width = 3f),
                )
            }
            else -> { // cornice con foto
                rotate(-6f, pivot = Offset(w / 2, h)) {
                    drawRect(Color(0xFF6B4226), topLeft = Offset(w * 0.12f, h * 0.38f), size = Size(w * 0.76f, h * 0.62f))
                    drawRect(Color(0xFF9DC3D8), topLeft = Offset(w * 0.2f, h * 0.45f), size = Size(w * 0.6f, h * 0.48f))
                    val hill = Path().apply {
                        moveTo(w * 0.2f, h * 0.93f); lineTo(w * 0.2f, h * 0.78f)
                        quadraticTo(w * 0.42f, h * 0.6f, w * 0.8f, h * 0.8f); lineTo(w * 0.8f, h * 0.93f); close()
                    }
                    drawPath(hill, Color(0xFF5E8C4A))
                    drawCircle(Color(0xFFF3D36B), radius = w * 0.06f, center = Offset(w * 0.66f, h * 0.55f))
                }
            }
        }
    }
}

/** Lampada appesa: di notte è l'unica fonte di luce della stanza. */
@Composable
fun HangingLamp(night: Boolean, centerX: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = 90.dp.toPx()
        val h = size.height
        val cx = size.width * centerX
        drawLine(Color(0xFF222222), Offset(cx, 0f), Offset(cx, h * 0.4f), strokeWidth = 2f)
        if (night) {
            drawCircle(
                Brush.radialGradient(listOf(Color(0x99FFD08A), Color.Transparent), center = Offset(cx, h * 0.75f), radius = w * 0.6f),
                radius = w * 0.6f,
                center = Offset(cx, h * 0.75f),
            )
        }
        val shade = Path().apply {
            moveTo(cx - w * 0.12f, h * 0.4f); lineTo(cx + w * 0.12f, h * 0.4f)
            lineTo(cx + w * 0.3f, h * 0.7f); lineTo(cx - w * 0.3f, h * 0.7f); close()
        }
        drawPath(shade, Color(0xFF2F4A3A))
        drawCircle(if (night) Color(0xFFFFE3A8) else Color(0xFFE8E0D0), radius = w * 0.08f, center = Offset(cx, h * 0.73f))
    }
}

/** Velo notturno con alone caldo attorno alla lampada. */
@Composable
fun NightOverlay(lampCenterX: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val center = Offset(size.width * lampCenterX, 60.dp.toPx())
        drawRect(
            Brush.radialGradient(
                0f to Color(0x30FFB25C),
                0.3f to Color(0x26000000),
                1f to Color(0xB0000000),
                center = center,
                radius = size.maxDimension * 0.9f,
            ),
        )
    }
}
