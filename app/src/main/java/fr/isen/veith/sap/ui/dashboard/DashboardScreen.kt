package fr.isen.veith.sap.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.isen.veith.sap.domain.model.EnvironmentData
import fr.isen.veith.sap.domain.model.HistoryPoint
import fr.isen.veith.sap.domain.model.LightSpectrum
import fr.isen.veith.sap.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToScan: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SAP Dashboard", fontWeight = FontWeight.Bold) },
                actions = {
                    if (!state.isLoading) {
                        IconButton(onClick = viewModel::forceRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Rafraîchir")
                        }
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(12.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Green800,
                    titleContentColor = Color.White,
                    actionIconContentColor = Green200
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToScan,
                icon    = { Icon(Icons.Default.Add, contentDescription = null) },
                text    = { Text("Ajouter un pot") },
                containerColor = Green700,
                contentColor   = Color.White
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            // ── Plant selector ─────────────────────────────────────────────
            item {
                PlantSelectorRow(
                    plantIds   = state.plantIds,
                    selected   = state.selectedPlantId,
                    onSelect   = viewModel::selectPlant,
                    updatedMs  = state.lastUpdatedMs,
                    modifier   = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            // ── Error banner ───────────────────────────────────────────────
            if (state.error != null) {
                item {
                    ErrorBanner(
                        message  = state.error!!,
                        onDismiss = viewModel::dismissError,
                        modifier  = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ── Empty state ────────────────────────────────────────────────
            if (!state.isLoading && state.plantIds.isEmpty() && state.error == null) {
                item { EmptyState(onAdd = onNavigateToScan) }
                return@LazyColumn
            }

            // ── Environnement ──────────────────────────────────────────────
            item {
                SectionHeader("Environnement", modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                EnvCards(
                    env      = state.environment,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(8.dp))
            }

            // ── Spectre lumineux ───────────────────────────────────────────
            item {
                SectionHeader("Spectre lumineux", modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    SpectrumBars(
                        spectrum = state.spectrum,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Historique sol ─────────────────────────────────────────────
            item {
                SectionHeader("Humidité sol — 1h", modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                HistoryCard(
                    history    = state.history,
                    seriesA    = { it.soil1 },
                    seriesB    = { it.soil2 },
                    labelA     = "Sol 1",
                    labelB     = "Sol 2",
                    colorA     = Color(0xFF1976D2),
                    colorB     = Color(0xFF0097A7),
                    yMin       = 0f,
                    yMax       = 100f,
                    unit       = "%",
                    modifier   = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(8.dp))
            }

            // ── Historique température ─────────────────────────────────────
            item {
                SectionHeader("Température — 1h", modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                HistoryCard(
                    history    = state.history,
                    seriesA    = { it.tempShtc3 },
                    seriesB    = { it.tempBmp180 },
                    labelA     = "SHTC3",
                    labelB     = "BMP180",
                    colorA     = Color(0xFFE64A19),
                    colorB     = Color(0xFFF9A825),
                    yMin       = 10f,
                    yMax       = 40f,
                    unit       = "°C",
                    modifier   = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Plant selector (chips)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlantSelectorRow(
    plantIds: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    updatedMs: Long,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (plantIds.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(plantIds) { id ->
                    FilterChip(
                        selected = id == selected,
                        onClick  = { onSelect(id) },
                        label    = { Text(id) }
                    )
                }
            }
        }
        if (updatedMs > 0L) {
            val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
            Text(
                text  = "Mis à jour : ${fmt.format(Date(updatedMs))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text     = title.uppercase(),
        style    = MaterialTheme.typography.labelMedium,
        color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Error banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text     = message,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onDismiss) {
                Text("OK", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Environment sensor cards (2×2 grid)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EnvCards(env: EnvironmentData, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SensorCard(
                icon     = "💧",
                label    = "Sol 1",
                value    = "${env.soil1.toInt()} %",
                progress = env.soil1 / 100f,
                barColor = Color(0xFF1976D2),
                modifier = Modifier.weight(1f)
            )
            SensorCard(
                icon     = "💧",
                label    = "Sol 2",
                value    = "${env.soil2.toInt()} %",
                progress = env.soil2 / 100f,
                barColor = Color(0xFF0097A7),
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SensorCard(
                icon     = "🌡️",
                label    = "Temp. SHTC3",
                value    = "${"%.1f".format(env.tempShtc3)} °C",
                progress = ((env.tempShtc3 - 10f) / 30f).coerceIn(0f, 1f),
                barColor = Color(0xFFE64A19),
                modifier = Modifier.weight(1f)
            )
            SensorCard(
                icon     = "🌡️",
                label    = "Temp. BMP180",
                value    = "${"%.1f".format(env.tempBmp180)} °C",
                progress = ((env.tempBmp180 - 10f) / 30f).coerceIn(0f, 1f),
                barColor = Color(0xFFF9A825),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SensorCard(
    icon: String,
    label: String,
    value: String,
    progress: Float,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val animProg by animateFloatAsState(
        targetValue   = progress.coerceIn(0f, 1f),
        animationSpec = tween(800),
        label         = label
    )
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(icon, fontSize = 22.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text       = value,
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animProg)
                        .fillMaxHeight()
                        .background(barColor)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Spectrum bars (colored by wavelength)
// ─────────────────────────────────────────────────────────────────────────────

private val SPECTRUM_BANDS = listOf(
    Triple("Violet", Color(0xFF7B1FA2), { s: LightSpectrum -> s.violet }),
    Triple("Indigo",  Color(0xFF3949AB), { s: LightSpectrum -> s.indigo }),
    Triple("Bleu",    Color(0xFF1976D2), { s: LightSpectrum -> s.blue }),
    Triple("Cyan",    Color(0xFF0097A7), { s: LightSpectrum -> s.cyan }),
    Triple("Vert",    Color(0xFF388E3C), { s: LightSpectrum -> s.green }),
    Triple("Jaune",   Color(0xFFF9A825), { s: LightSpectrum -> s.yellow }),
    Triple("Orange",  Color(0xFFF57C00), { s: LightSpectrum -> s.orange }),
    Triple("Rouge",   Color(0xFFD32F2F), { s: LightSpectrum -> s.red }),
)

@Composable
private fun SpectrumBars(spectrum: LightSpectrum, modifier: Modifier = Modifier) {
    val maxVal = SPECTRUM_BANDS.maxOfOrNull { it.third(spectrum) }?.takeIf { it > 0f } ?: 1f
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        SPECTRUM_BANDS.forEach { (label, color, getter) ->
            val value    = getter(spectrum)
            val fraction = (value / maxVal).coerceIn(0f, 1f)
            val anim     by animateFloatAsState(fraction, tween(700), label = label)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text     = label,
                    style    = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(48.dp),
                    color    = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(anim)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(color)
                    )
                }
                Text(
                    text     = value.toInt().toString(),
                    style    = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(42.dp),
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text     = "Luminosité",
                style    = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(48.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text       = "${spectrum.luminosity.toInt()} lux",
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// History line chart — Canvas-based (replace with Vico if preferred)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryCard(
    history: List<HistoryPoint>,
    seriesA: (HistoryPoint) -> Float,
    seriesB: (HistoryPoint) -> Float,
    labelA: String,
    labelB: String,
    colorA: Color,
    colorB: Color,
    yMin: Float,
    yMax: Float,
    unit: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Legend
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(label = labelA, color = colorA)
                LegendDot(label = labelB, color = colorB)
            }
            Spacer(Modifier.height(12.dp))

            if (history.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Données insuffisantes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            } else {
                // Y-axis labels
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Y labels column
                    Column(
                        modifier = Modifier
                            .width(32.dp)
                            .height(140.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${yMax.toInt()}$unit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            fontSize = 9.sp
                        )
                        Text(
                            "${((yMin + yMax) / 2).toInt()}$unit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            fontSize = 9.sp
                        )
                        Text(
                            "${yMin.toInt()}$unit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            fontSize = 9.sp
                        )
                    }
                    // Canvas chart
                    LineChartCanvas(
                        pointsA  = history.map(seriesA),
                        pointsB  = history.map(seriesB),
                        colorA   = colorA,
                        colorB   = colorB,
                        yMin     = yMin,
                        yMax     = yMax,
                        modifier = Modifier
                            .weight(1f)
                            .height(140.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

@Composable
private fun LineChartCanvas(
    pointsA: List<Float>,
    pointsB: List<Float>,
    colorA: Color,
    colorB: Color,
    yMin: Float,
    yMax: Float,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val range = (yMax - yMin).takeIf { it > 0f } ?: 1f

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Grid lines
        listOf(0f, 0.5f, 1f).forEach { frac ->
            val y = h * (1f - frac)
            drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(w, y), strokeWidth = 1.dp.toPx())
        }

        fun buildPath(points: List<Float>): Path {
            val path = Path()
            val n = points.size
            points.forEachIndexed { i, v ->
                val x = i.toFloat() / (n - 1) * w
                val y = h * (1f - (v - yMin) / range)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            return path
        }

        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)

        if (pointsA.isNotEmpty()) drawPath(buildPath(pointsA), colorA, style = stroke)
        if (pointsB.isNotEmpty()) drawPath(buildPath(pointsB), colorB, style = stroke)

        // Last value dots
        listOf(pointsA to colorA, pointsB to colorB).forEach { (pts, color) ->
            if (pts.isNotEmpty()) {
                val x = w
                val y = h * (1f - (pts.last() - yMin) / range)
                drawCircle(color, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("🌱", fontSize = 56.sp)
        Text(
            "Aucun pot trouvé dans InfluxDB.",
            style     = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Button(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Ajouter un pot")
        }
    }
}
