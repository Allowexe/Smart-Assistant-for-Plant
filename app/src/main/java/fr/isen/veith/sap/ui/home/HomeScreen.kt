package fr.isen.veith.sap.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.isen.veith.sap.domain.model.PlantMood
import fr.isen.veith.sap.ui.theme.*

/**
 * Écran d'accueil Sap.
 *
 * @param onNavigateToSettings    Vers l'écran réglages
 * @param onNavigateToPairing     Vers l'écran d'appairage BLE
 * @param onNavigateToRecognition Vers l'écran de reconnaissance de plante
 */
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToPairing: () -> Unit,
    onNavigateToRecognition: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Header vert foncé avec visage + météo plante ──────────
            HomeHeader(
                userName      = state.userName,
                mood          = state.mood,
                plantName     = state.selectedPlant.commonName,
                plantEmoji    = state.selectedPlant.emoji,
                plants        = state.plants,
                selectedPlant = state.selectedPlant,
                isMenuOpen    = state.isPlantMenuOpen,
                onToggleMenu  = viewModel::togglePlantMenu,
                onSelectPlant = viewModel::selectPlant,
                onDismissMenu = viewModel::closePlantMenu,
                onSettings    = onNavigateToSettings
            )

            Spacer(Modifier.height(16.dp))

            // ── Cartes capteurs ───────────────────────────────────────
            SensorSection(
                humidity    = state.sensorData.humidity,
                luminosity  = state.sensorData.luminosity,
                temperature = state.sensorData.temperature,
                plant       = state.selectedPlant
            )

            Spacer(Modifier.height(20.dp))

            // ── Carte plante + santé ───────────────────────────────────
            PlantHealthCard(
                plant       = state.selectedPlant,
                healthScore = state.healthScore,
                mood        = state.mood,
                modifier    = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(20.dp))

            // ── Conseils dynamiques ────────────────────────────────────
            TipsSection(
                plant = state.selectedPlant,
                sensorData = state.sensorData,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(20.dp))

            // ── Boutons d'action ─────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PairingButton(
                    onClick  = onNavigateToPairing,
                    modifier = Modifier.weight(1f)
                )
                RecognitionButton(
                    onClick  = onNavigateToRecognition,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(32.dp))
        }

        // Fermer le menu en tapant en dehors — uniquement la zone SOUS le header
        // (ne pas intercepter les clics sur le menu lui-même)
        if (state.isPlantMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 160.dp)   // laisser libre la zone du header + menu
                    .clickable(
                        indication         = null,
                        interactionSource  = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        onClick            = viewModel::closePlantMenu
                    )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun HomeHeader(
    userName: String,
    mood: PlantMood,
    plantName: String,
    plantEmoji: String,
    plants: List<fr.isen.veith.sap.domain.model.Plant>,
    selectedPlant: fr.isen.veith.sap.domain.model.Plant,
    isMenuOpen: Boolean,
    onToggleMenu: () -> Unit,
    onSelectPlant: (fr.isen.veith.sap.domain.model.Plant) -> Unit,
    onDismissMenu: () -> Unit,
    onSettings: () -> Unit
) {
    val greeting = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
        in 5..11  -> "Bonjour"
        in 12..17 -> "Bon après-midi"
        else      -> "Bonsoir"
    }

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Green900, Color(0xFF2A5A2A))
                    )
                )
                .padding(top = 20.dp, bottom = 24.dp, start = 20.dp, end = 20.dp)
        ) {
            // Ligne supérieure : greeting + bouton settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = "$greeting, $userName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Green200.copy(alpha = 0.8f)
                    )
                    Text(
                        text  = "Voici comment va ta plante",
                        style = MaterialTheme.typography.labelSmall,
                        color = Green400.copy(alpha = 0.6f)
                    )
                }
                IconButton(onClick = onSettings) {
                    Icon(
                        imageVector        = Icons.Default.Settings,
                        contentDescription = "Réglages",
                        tint               = Green200.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Visage + sélecteur de plante
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Nom de la plante cliquable
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = "Plante active",
                        style = MaterialTheme.typography.labelSmall,
                        color = Green400.copy(alpha = 0.65f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onToggleMenu)
                            .padding(vertical = 4.dp, horizontal = 0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text     = "$plantEmoji  $plantName",
                            style    = MaterialTheme.typography.titleLarge,
                            color    = Green50,
                            fontWeight = FontWeight.Normal
                        )
                        Spacer(Modifier.width(6.dp))
                        // Flèche animée
                        val arrowAngle by animateFloatAsState(
                            targetValue = if (isMenuOpen) 180f else 0f,
                            animationSpec = tween(250),
                            label = "arrow"
                        )
                        Icon(
                            imageVector        = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Changer de plante",
                            tint               = Orange200,
                            modifier           = Modifier
                                .size(20.dp)
                                .rotate(arrowAngle)
                        )
                    }
                }

                // Visage expressif
                MoodFace(
                    mood     = mood,
                    modifier = Modifier.size(72.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Label humeur
            Text(
                text  = when (mood) {
                    PlantMood.HAPPY     -> "Ta plante est heureuse ! 🌟"
                    PlantMood.NEUTRAL   -> "Ta plante va bien"
                    PlantMood.CONCERNED -> "Ta plante a besoin d'attention"
                    PlantMood.SAD       -> "Ta plante souffre, agis vite !"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when (mood) {
                    PlantMood.HAPPY     -> Green200
                    PlantMood.NEUTRAL   -> Green400
                    PlantMood.CONCERNED -> Orange200
                    PlantMood.SAD       -> Color(0xFFFF9999)
                }
            )
        }

        // Menu déroulant des plantes (positionné sous le nom)
        PlantDropdownMenu(
            plants        = plants,
            selectedPlant = selectedPlant,
            isOpen        = isMenuOpen,
            onSelectPlant = onSelectPlant,
            onDismiss     = onDismissMenu,
            modifier      = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .offset(y = 0.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// Section capteurs
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun SensorSection(
    humidity: Float,
    luminosity: Float,
    temperature: Float,
    plant: fr.isen.veith.sap.domain.model.Plant
) {
    val humidAlert = humidity < plant.humidityMin || humidity > plant.humidityMax
    val luxAlert   = luminosity < plant.luxMin
    val tempAlert  = temperature < plant.tempMin || temperature > plant.tempMax

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SensorCard(
            label    = "Humidité",
            value    = "${humidity.toInt()}%",
            icon     = "💧",
            progress = humidity / 100f,
            type     = SensorType.HUMIDITY,
            isAlert  = humidAlert,
            modifier = Modifier.weight(1f),
            animationDelay = 0
        )
        SensorCard(
            label    = "Lumière",
            value    = "${(luminosity / 1000).let { "%.1f".format(it) }}k lux",
            icon     = "☀️",
            progress = (luminosity / 10000f).coerceIn(0f, 1f),
            type     = SensorType.LUMINOSITY,
            isAlert  = luxAlert,
            modifier = Modifier.weight(1f),
            animationDelay = 150
        )
        SensorCard(
            label    = "Temp.",
            value    = "${"%.1f".format(temperature)}°C",
            icon     = "🌡️",
            progress = ((temperature - 0f) / 40f).coerceIn(0f, 1f),
            type     = SensorType.TEMPERATURE,
            isAlert  = tempAlert,
            modifier = Modifier.weight(1f),
            animationDelay = 300
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// Carte santé de la plante
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun PlantHealthCard(
    plant: fr.isen.veith.sap.domain.model.Plant,
    healthScore: Float,
    mood: PlantMood,
    modifier: Modifier = Modifier
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        started = true
    }
    val animHealth by animateFloatAsState(
        targetValue   = if (started) healthScore / 100f else 0f,
        animationSpec = tween(1000, easing = EaseOutCubic),
        label         = "health"
    )

    val healthColor = when {
        healthScore >= 70f -> Green400
        healthScore >= 40f -> Orange400
        else               -> Color(0xFFCF6679)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border   = BorderStroke(1.dp, Green600.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji plante dans un cercle
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Green800.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = plant.emoji, fontSize = 26.sp)
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = plant.commonName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text  = plant.scientificName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    fontWeight = FontWeight.Normal
                )

                Spacer(Modifier.height(8.dp))

                // Barre de santé
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(healthColor.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animHealth)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(healthColor)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text  = "${healthScore.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = healthColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Section conseils
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun TipsSection(
    plant: fr.isen.veith.sap.domain.model.Plant,
    sensorData: fr.isen.veith.sap.domain.model.SensorData,
    modifier: Modifier = Modifier
) {
    val tips = buildList {
        if (sensorData.humidity < plant.humidityMin)
            add("💧" to "Sol trop sec — il est temps d'arroser !")
        if (sensorData.humidity > plant.humidityMax)
            add("💦" to "Sol trop humide — attends avant d'arroser.")
        if (sensorData.luminosity < plant.luxMin)
            add("☀️" to "Manque de lumière — rapproche du fenêtre.")
        if (sensorData.temperature < plant.tempMin)
            add("🥶" to "Trop froid — éloigne des courants d'air.")
        if (sensorData.temperature > plant.tempMax)
            add("🥵" to "Trop chaud — aère la pièce.")
        if (isEmpty()) {
            add("🌿" to plant.wateringTip)
            add("💡" to plant.lightTip)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text  = "Conseils du moment",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        tips.forEachIndexed { index, (icon, tip) ->
            TipChip(icon = icon, text = tip, delay = index * 100)
            if (index < tips.lastIndex) Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun TipChip(icon: String, text: String, delay: Int) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delay.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter   = slideInHorizontally { -it / 3 } + fadeIn()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                text  = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Bouton reconnaissance de plante
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun RecognitionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick  = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape    = RoundedCornerShape(16.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor = Orange400,
            contentColor   = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Icon(
            imageVector        = Icons.Default.Add,
            contentDescription = null,
            modifier           = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text  = "Identifier une plante",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
// ─────────────────────────────────────────────────────────────────────
// Bouton appairage BLE
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun PairingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick  = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape    = RoundedCornerShape(16.dp),
        border   = BorderStroke(1.5.dp, Green600),
        colors   = ButtonDefaults.outlinedButtonColors(
            contentColor = Green400
        )
    ) {
        Icon(
            imageVector        = Icons.Default.Bluetooth,
            contentDescription = null,
            modifier           = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text  = "Appairer un pot",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}