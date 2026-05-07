package fr.isen.veith.sap.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import fr.isen.veith.sap.domain.model.PlantMood
import fr.isen.veith.sap.ui.theme.*
import kotlin.math.abs

/**
 * Visage expressif animé reflétant l'état de la plante.
 *
 * HAPPY → grand sourire, yeux ronds
 * NEUTRAL → bouche plate, yeux ronds
 * CONCERNED→ léger froncement, bouche légèrement vers le bas
 * SAD → bouche courbée vers le bas, larme
 */
@Composable
fun MoodFace(
    mood: PlantMood,
    modifier: Modifier = Modifier
) {
    // Animation douce de la courbure de la bouche
    val mouthCurve by animateFloatAsState(
        targetValue = when (mood) {
            PlantMood.HAPPY     ->  1f
            PlantMood.NEUTRAL   ->  0f
            PlantMood.CONCERNED -> -0.4f
            PlantMood.SAD       -> -1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessLow
        ),
        label = "mouth_curve"
    )

    // Animation des yeux (taille pupille)
    val eyeScale by animateFloatAsState(
        targetValue = if (mood == PlantMood.SAD) 0.7f else 1f,
        animationSpec = tween(400),
        label = "eye_scale"
    )

    // Pulsation légère du visage quand HAPPY
    val infiniteTransition = rememberInfiniteTransition(label = "face_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = if (mood == PlantMood.HAPPY) 1.04f else 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier.size(72.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r  = size.width / 2f * pulse

        // Cercle de fond
        val faceColor = when (mood) {
            PlantMood.HAPPY     -> Green400
            PlantMood.NEUTRAL   -> Green600
            PlantMood.CONCERNED -> Color(0xFFB8860B)
            PlantMood.SAD       -> Color(0xFF8B4513)
        }
        drawCircle(color = faceColor.copy(alpha = 0.2f), radius = r, center = Offset(cx, cy))
        drawCircle(
            color  = faceColor,
            radius = r * 0.92f,
            center = Offset(cx, cy),
            style  = Stroke(width = 2.5f)
        )

        // Yeux
        drawEyes(cx, cy, r, eyeScale, mood)

        // Bouche
        drawMouth(cx, cy, r, mouthCurve)

        // Larme si SAD
        if (mood == PlantMood.SAD) {
            drawTear(cx, cy, r)
        }
    }
}

private fun DrawScope.drawEyes(
    cx: Float, cy: Float, r: Float, scale: Float, mood: PlantMood
) {
    val eyeY    = cy - r * 0.18f
    val eyeOffX = r * 0.30f
    val eyeR    = r * 0.10f * scale

    val eyeColor = when (mood) {
        PlantMood.HAPPY     -> Green800
        PlantMood.NEUTRAL   -> Green700
        PlantMood.CONCERNED -> Color(0xFF8B6914)
        PlantMood.SAD       -> Color(0xFF5C3317)
    }

    // Œil gauche
    drawCircle(color = eyeColor, radius = eyeR, center = Offset(cx - eyeOffX, eyeY))
    // Œil droit
    drawCircle(color = eyeColor, radius = eyeR, center = Offset(cx + eyeOffX, eyeY))

    // Sourcils froncés si CONCERNED ou SAD
    if (mood == PlantMood.CONCERNED || mood == PlantMood.SAD) {
        val frown = if (mood == PlantMood.SAD) 0.06f else 0.03f
        drawLine(
            color = eyeColor,
            start = Offset(cx - eyeOffX - eyeR * 1.5f, eyeY - eyeR * 2f - r * frown),
            end   = Offset(cx - eyeOffX + eyeR * 1.5f, eyeY - eyeR * 2f + r * frown),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = eyeColor,
            start = Offset(cx + eyeOffX - eyeR * 1.5f, eyeY - eyeR * 2f + r * frown),
            end   = Offset(cx + eyeOffX + eyeR * 1.5f, eyeY - eyeR * 2f - r * frown),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawMouth(cx: Float, cy: Float, r: Float, curve: Float) {
    val mouthY  = cy + r * 0.28f
    val mouthW  = r * 0.50f
    val mouthH  = r * 0.22f * curve  // positif = sourire, négatif = grimace

    val mouthColor = Green800

    if (abs(curve) < 0.05f) {
        // Bouche plate (NEUTRAL)
        drawLine(
            color = mouthColor,
            start = Offset(cx - mouthW, mouthY),
            end   = Offset(cx + mouthW, mouthY),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )
    } else {
        // Courbe de Bézier cubique
        val path = Path().apply {
            moveTo(cx - mouthW, mouthY)
            cubicTo(
                cx - mouthW, mouthY + mouthH * 2f,
                cx + mouthW, mouthY + mouthH * 2f,
                cx + mouthW, mouthY
            )
        }
        drawPath(
            path  = path,
            color = mouthColor,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawTear(cx: Float, cy: Float, r: Float) {
    // Petite larme sous l'œil droit
    val tearX = cx + r * 0.30f
    val tearY = cy - r * 0.02f
    val tearPath = Path().apply {
        moveTo(tearX, tearY)
        cubicTo(
            tearX - r * 0.07f, tearY + r * 0.10f,
            tearX - r * 0.07f, tearY + r * 0.18f,
            tearX,              tearY + r * 0.18f
        )
        cubicTo(
            tearX + r * 0.07f, tearY + r * 0.18f,
            tearX + r * 0.07f, tearY + r * 0.10f,
            tearX, tearY
        )
        close()
    }
    drawPath(tearPath, color = Color(0xFF87CEEB).copy(alpha = 0.85f))
}