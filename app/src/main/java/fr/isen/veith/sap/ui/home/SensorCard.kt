package fr.isen.veith.sap.ui.home

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.isen.veith.sap.ui.theme.*

enum class SensorType { HUMIDITY, LUMINOSITY, TEMPERATURE }

/**
 * Carte capteur avec barre de progression animée.
 *
 * @param label     Nom affiché en bas (ex : "Humidité")
 * @param value     Valeur formatée à afficher (ex : "68%")
 * @param icon      Emoji icône (ex: "💧")
 * @param progress  Remplissage de la barre 0f–1f
 * @param type      Détermine la couleur d'accent
 * @param isAlert   Si vrai, la carte clignote légèrement en orange
 */
@Composable
fun SensorCard(
    label: String,
    value: String,
    icon: String,
    progress: Float,
    type: SensorType,
    isAlert: Boolean = false,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    animationDelay: Int = 0
) {
    // Couleurs selon le type
    val accentColor = when (type) {
        SensorType.HUMIDITY    -> Green400
        SensorType.LUMINOSITY  -> Color(0xFFE8B84B)
        SensorType.TEMPERATURE -> Orange400
    }
    val bgTint = when (type) {
        SensorType.HUMIDITY    -> Green800.copy(alpha = 0.35f)
        SensorType.LUMINOSITY  -> Color(0xFF4A3A10).copy(alpha = 0.35f)
        SensorType.TEMPERATURE -> Color(0xFF4A2010).copy(alpha = 0.35f)
    }

    // Animation de la barre au chargement
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(animationDelay.toLong())
        started = true
    }
    val animatedProgress by animateFloatAsState(
        targetValue = if (started) progress.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(900, easing = EaseOutCubic),
        label = "bar_${label}"
    )

    // Clignotement alerte
    val infiniteTransition = rememberInfiniteTransition(label = "alert_$label")
    val alertAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = if (isAlert) 0.55f else 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alert_alpha"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgTint)
            .border(
                width = 1.dp,
                color = if (isAlert) Orange400.copy(alpha = alertAlpha)
                else accentColor.copy(alpha = 0.25f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icône
        Text(text = icon, fontSize = 22.sp)

        Spacer(Modifier.height(6.dp))

        // Valeur principale
        Text(
            text  = value,
            style = MaterialTheme.typography.titleLarge,
            color = accentColor,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(6.dp))

        // Barre de progression
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
        }

        Spacer(Modifier.height(5.dp))

        // Label
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
    }
}