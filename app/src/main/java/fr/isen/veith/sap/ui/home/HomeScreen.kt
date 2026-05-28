package fr.isen.veith.sap.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.isen.veith.sap.R
import fr.isen.veith.sap.domain.model.Plant
import fr.isen.veith.sap.domain.model.PlantMood
import fr.isen.veith.sap.ui.theme.*

@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToScan: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToRecognition: (potId: String) -> Unit,
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
            HomeHeader(
                userName         = state.userName,
                mood             = state.mood,
                activePlant      = state.activePlant,
                availablePotIds  = state.availablePotIds,
                selectedPotId    = state.selectedPotId,
                isPotMenuOpen    = state.isPotMenuOpen,
                onTogglePotMenu  = viewModel::togglePotMenu,
                onSelectPotId    = viewModel::selectPotId,
                onDismissPotMenu = viewModel::closePotMenu,
                isInfluxLoading  = state.isInfluxLoading,
                onSettings       = onNavigateToSettings,
                onDashboard      = onNavigateToDashboard
            )

            Spacer(Modifier.height(16.dp))

            SensorSection(
                humidity    = state.sensorData.humidity,
                luminosity  = state.sensorData.luminosity,
                temperature = state.sensorData.temperature,
                plant       = state.activePlant
            )

            Spacer(Modifier.height(20.dp))

            if (state.activePlant != null) {
                PlantHealthCard(
                    plant       = state.activePlant,
                    healthScore = state.healthScore,
                    mood        = state.mood,
                    modifier    = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(20.dp))
                TipsSection(
                    plant      = state.activePlant,
                    sensorData = state.sensorData,
                    modifier   = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(20.dp))
            } else {
                NoPlantCard(
                    onIdentify = { onNavigateToRecognition(state.selectedPotId) },
                    modifier   = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(20.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PairingButton(
                    onClick  = onNavigateToScan,
                    modifier = Modifier.weight(1f)
                )
                RecognitionButton(
                    onClick  = { onNavigateToRecognition(state.selectedPotId) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(32.dp))
        }

        if (state.isPotMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 160.dp)
                    .clickable(
                        indication        = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        onClick           = viewModel::closePotMenu
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
    activePlant: Plant?,
    availablePotIds: List<String>,
    selectedPotId: String,
    isPotMenuOpen: Boolean,
    onTogglePotMenu: () -> Unit,
    onSelectPotId: (String) -> Unit,
    onDismissPotMenu: () -> Unit,
    isInfluxLoading: Boolean,
    onSettings: () -> Unit,
    onDashboard: () -> Unit
) {
    val greeting = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
        in 5..11  -> stringResource(R.string.greeting_morning)
        in 12..17 -> stringResource(R.string.greeting_afternoon)
        else      -> stringResource(R.string.greeting_evening)
    }

    val isDark = LocalSapDarkTheme.current
    val headerColors = if (isDark)
        listOf(Green900, Color(0xFF2A5A2A))
    else
        listOf(Green800, Green600)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(colors = headerColors))
            .padding(top = 20.dp, bottom = 24.dp, start = 20.dp, end = 20.dp)
    ) {
        // Top row: greeting + settings/dashboard
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
                    text  = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = Green100.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = onDashboard) {
                Icon(
                    imageVector        = Icons.Default.BarChart,
                    contentDescription = "Voir le dashboard",
                    tint               = Green200.copy(alpha = 0.7f)
                )
            }
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector        = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                    tint               = Green200.copy(alpha = 0.7f)
                )
            }
        }

        // Pot selector (InfluxDB)
        if (availablePotIds.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Default.Sensors,
                    contentDescription = null,
                    tint               = Green400.copy(alpha = 0.7f),
                    modifier           = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text  = "Pot : ",
                    style = MaterialTheme.typography.labelSmall,
                    color = Green400.copy(alpha = 0.65f)
                )
                if (availablePotIds.size == 1) {
                    Text(
                        text  = selectedPotId,
                        style = MaterialTheme.typography.labelSmall,
                        color = Green200
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onTogglePotMenu)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text  = selectedPotId,
                            style = MaterialTheme.typography.labelSmall,
                            color = Green200
                        )
                        Icon(
                            imageVector        = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint               = Orange200,
                            modifier           = Modifier.size(14.dp)
                        )
                    }
                    DropdownMenu(
                        expanded         = isPotMenuOpen,
                        onDismissRequest = onDismissPotMenu
                    ) {
                        availablePotIds.forEach { id ->
                            DropdownMenuItem(
                                text    = { Text(id) },
                                onClick = { onSelectPotId(id) },
                                leadingIcon = if (id == selectedPotId) ({
                                    Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(16.dp))
                                }) else null
                            )
                        }
                    }
                }
                if (isInfluxLoading) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier    = Modifier.size(10.dp),
                        strokeWidth = 1.5.dp,
                        color       = Green400.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Plant name + mood face
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = stringResource(R.string.active_plant),
                    style = MaterialTheme.typography.labelSmall,
                    color = Green400.copy(alpha = 0.65f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = if (activePlant != null) "${activePlant.emoji}  ${activePlant.commonName}"
                            else "🌱  Aucune plante",
                    style = MaterialTheme.typography.titleLarge,
                    color = Green50,
                    fontWeight = FontWeight.Normal
                )
                if (activePlant != null) {
                    Text(
                        text  = activePlant.scientificName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Green200.copy(alpha = 0.5f)
                    )
                }
            }

            MoodFace(
                mood     = mood,
                modifier = Modifier.size(72.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text  = when (mood) {
                PlantMood.HAPPY     -> stringResource(R.string.mood_happy)
                PlantMood.NEUTRAL   -> stringResource(R.string.mood_neutral)
                PlantMood.CONCERNED -> stringResource(R.string.mood_concerned)
                PlantMood.SAD       -> stringResource(R.string.mood_sad)
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
}

// ─────────────────────────────────────────────────────────────────────
// Section capteurs
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun SensorSection(
    humidity: Float,
    luminosity: Float,
    temperature: Float,
    plant: Plant?
) {
    val humidAlert = plant != null && (humidity < plant.humidityMin || humidity > plant.humidityMax)
    val luxAlert   = plant != null && luminosity < plant.luxMin
    val tempAlert  = plant != null && (temperature < plant.tempMin || temperature > plant.tempMax)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SensorCard(
            label    = stringResource(R.string.sensor_humidity),
            value    = "${humidity.toInt()}%",
            icon     = "💧",
            progress = humidity / 100f,
            type     = SensorType.HUMIDITY,
            isAlert  = humidAlert,
            modifier = Modifier.weight(1f),
            animationDelay = 0
        )
        SensorCard(
            label    = stringResource(R.string.sensor_light),
            value    = "${(luminosity / 1000).let { "%.1f".format(it) }}k lux",
            icon     = "☀️",
            progress = (luminosity / 10000f).coerceIn(0f, 1f),
            type     = SensorType.LUMINOSITY,
            isAlert  = luxAlert,
            modifier = Modifier.weight(1f),
            animationDelay = 150
        )
        SensorCard(
            label    = stringResource(R.string.sensor_temp),
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
// Carte santé
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun PlantHealthCard(
    plant: Plant?,
    healthScore: Float,
    mood: PlantMood,
    modifier: Modifier = Modifier
) {
    if (plant == null) return

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
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border   = androidx.compose.foundation.BorderStroke(1.dp, Green600.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
    plant: Plant?,
    sensorData: fr.isen.veith.sap.domain.model.SensorData,
    modifier: Modifier = Modifier
) {
    if (plant == null) return

    val tipTooDry   = stringResource(R.string.tip_too_dry)
    val tipTooWet   = stringResource(R.string.tip_too_wet)
    val tipLowLight = stringResource(R.string.tip_low_light)
    val tipTooCold  = stringResource(R.string.tip_too_cold)
    val tipTooHot   = stringResource(R.string.tip_too_hot)
    val tips = buildList {
        if (sensorData.humidity < plant.humidityMin)    add("💧" to tipTooDry)
        if (sensorData.humidity > plant.humidityMax)    add("💦" to tipTooWet)
        if (sensorData.luminosity < plant.luxMin)       add("☀️" to tipLowLight)
        if (sensorData.temperature < plant.tempMin)     add("🥶" to tipTooCold)
        if (sensorData.temperature > plant.tempMax)     add("🥵" to tipTooHot)
        if (isEmpty()) {
            add("🌿" to plant.wateringTip)
            add("💡" to plant.lightTip)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text  = stringResource(R.string.tips_title),
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
// Carte "aucune plante identifiée"
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun NoPlantCard(onIdentify: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border   = androidx.compose.foundation.BorderStroke(1.dp, Green600.copy(alpha = 0.2f))
    ) {
        Column(
            modifier            = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🌱", fontSize = 40.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                text  = "Aucune plante identifiée",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text  = "Photographiez votre plante pour l'identifier et personnaliser les alertes capteurs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )
            Button(
                onClick  = onIdentify,
                colors   = ButtonDefaults.buttonColors(containerColor = Green700),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Identifier ma plante")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Boutons
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun RecognitionButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick  = onClick,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape    = RoundedCornerShape(16.dp),
        colors   = ButtonDefaults.buttonColors(containerColor = Orange400, contentColor = Color.White),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = stringResource(R.string.btn_identify_plant), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PairingButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick  = onClick,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape    = RoundedCornerShape(16.dp),
        border   = androidx.compose.foundation.BorderStroke(1.5.dp, Green600),
        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Green400)
    ) {
        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = stringResource(R.string.btn_pair_pot), style = MaterialTheme.typography.bodyLarge)
    }
}
