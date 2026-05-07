package fr.isen.veith.sap.ui.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import fr.isen.veith.sap.ui.theme.*


/**
 * Logo Sap — feuille double + pot en terre cuite.
 * Dessiné entièrement en Canvas Compose, sans ressource externe.
 */
@Composable
fun SapLogo(modifier: Modifier = Modifier) {

    val infiniteTransition = rememberInfiniteTransition(label = "logo_sway")
    val sway by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue  = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sway"
    )

    Canvas(modifier = modifier.size(96.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.42f

        drawLine(
            color = Green600,
            start = Offset(cx, cy + 4f),
            end   = Offset(cx, h * 0.72f),
            strokeWidth = 3.5f,
            cap = StrokeCap.Round
        )

        drawLine(
            color = Green400.copy(alpha = 0.6f),
            start = Offset(cx, cy - 8f),
            end   = Offset(cx - w * 0.14f, cy - h * 0.14f),
            strokeWidth = 1.8f,
            cap = StrokeCap.Round
        )

        rotate(degrees = -18f + sway * 0.5f, pivot = Offset(cx, cy)) {
            drawLeaf(
                cx = cx - w * 0.06f,
                cy = cy,
                radiusX = w * 0.28f,
                radiusY = h * 0.38f,
                color = Green600.copy(alpha = 0.75f),
                angle = -20f
            )
        }

        rotate(degrees = 18f - sway * 0.5f, pivot = Offset(cx, cy)) {
            drawLeaf(
                cx = cx + w * 0.06f,
                cy = cy,
                radiusX = w * 0.28f,
                radiusY = h * 0.38f,
                color = Green400.copy(alpha = 0.85f),
                angle = 20f
            )
        }

        val potTop    = h * 0.72f
        val potBottom = h * 0.95f
        val potW      = w * 0.52f
        val potNarrow = w * 0.38f

        val potPath = Path().apply {
            moveTo(cx - potNarrow / 2f, potTop)
            lineTo(cx - potW / 2f, potBottom - 6f)
            quadraticTo(cx - potW / 2f, potBottom, cx - potW / 2f + 8f, potBottom)
            lineTo(cx + potW / 2f - 8f, potBottom)
            quadraticTo(cx + potW / 2f, potBottom, cx + potW / 2f, potBottom - 6f)
            lineTo(cx + potNarrow / 2f, potTop)
            close()
        }
        drawPath(potPath, color = Orange400)

        val rimHeight = h * 0.055f
        val rimPath = Path().apply {
            moveTo(cx - potNarrow / 2f - 5f, potTop)
            lineTo(cx + potNarrow / 2f + 5f, potTop)
            lineTo(cx + potNarrow / 2f + 5f, potTop + rimHeight)
            lineTo(cx - potNarrow / 2f - 5f, potTop + rimHeight)
            close()
        }
        drawPath(rimPath, color = Orange600)

        drawLine(
            color = Orange200.copy(alpha = 0.35f),
            start = Offset(cx - potW * 0.25f, potTop + h * 0.04f),
            end   = Offset(cx - potW * 0.22f, potBottom - h * 0.06f),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )
    }
}

/** Dessine une feuille elliptique inclinée. */
private fun DrawScope.drawLeaf(
    cx: Float, cy: Float,
    radiusX: Float, radiusY: Float,
    color: Color,
    angle: Float
) {
    rotate(degrees = angle, pivot = Offset(cx, cy)) {
        drawOval(
            color  = color,
            topLeft = Offset(cx - radiusX / 2f, cy - radiusY / 2f),
            size    = Size(radiusX, radiusY)
        )
        drawLine(
            color = color.copy(alpha = 0.45f),
            start = Offset(cx, cy - radiusY * 0.42f),
            end   = Offset(cx, cy + radiusY * 0.42f),
            strokeWidth = 1.2f
        )
    }
}