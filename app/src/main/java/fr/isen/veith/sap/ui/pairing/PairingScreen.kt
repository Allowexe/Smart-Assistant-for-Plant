package fr.isen.veith.sap.ui.pairing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.isen.veith.sap.R
import fr.isen.veith.sap.data.ble.BleDevice
import fr.isen.veith.sap.data.ble.ConnectionState
import fr.isen.veith.sap.data.ble.ScanState
import fr.isen.veith.sap.ui.theme.*

/**
 * Écran d'appairage BLE.
 *
 * @param onPaired   Callback appelé quand l'appairage est réussi
 * @param onBack     Retour à l'écran précédent
 */
@Composable
fun PairingScreen(
    onPaired: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: PairingViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Naviguer automatiquement si connexion réussie
    LaunchedEffect(state.connectionState) {
        if (state.connectionState is ConnectionState.Connected) {
            kotlinx.coroutines.delay(1500)
            onPaired()
        }
    }

    // Launcher de permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.onPermissionsResult(results.values.all { it })
    }

    // Demander les permissions si nécessaire
    LaunchedEffect(state.missingPermissions) {
        if (state.missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(state.missingPermissions.toTypedArray())
        }
    }

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
            // ── Header ────────────────────────────────────────────────
            PairingHeader(onBack = onBack)

            Spacer(Modifier.height(24.dp))

            // ── Animation radar ───────────────────────────────────────
            RadarAnimation(
                isScanning = state.scanState is ScanState.Scanning,
                modifier   = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(8.dp))

            // ── Texte état du scan ────────────────────────────────────
            ScanStatusText(state.scanState)

            Spacer(Modifier.height(20.dp))

            // ── Bouton scan ───────────────────────────────────────────
            ScanButton(
                scanState = state.scanState,
                onScan    = viewModel::checkAndStartScan,
                onStop    = viewModel::stopScan,
                modifier  = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(20.dp))

            // ── Liste des appareils ───────────────────────────────────
            AnimatedVisibility(
                visible = state.devices.isNotEmpty(),
                enter   = fadeIn() + expandVertically()
            ) {
                DeviceList(
                    devices        = state.devices,
                    selectedDevice = state.selectedDevice,
                    onSelect       = viewModel::selectDevice,
                    modifier       = Modifier.padding(horizontal = 20.dp)
                )
            }

            // Message "aucun appareil" si scan terminé sans résultat
            AnimatedVisibility(
                visible = state.scanState is ScanState.Stopped && state.devices.isEmpty(),
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                EmptyDevicesMessage(
                    onRetry  = viewModel::checkAndStartScan,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Bouton appairer ───────────────────────────────────────
            AnimatedVisibility(
                visible = state.selectedDevice != null,
                enter   = slideInVertically { it } + fadeIn(),
                exit    = slideOutVertically { it } + fadeOut()
            ) {
                PairButton(
                    device          = state.selectedDevice,
                    connectionState = state.connectionState,
                    onPair          = viewModel::pairSelectedDevice,
                    modifier        = Modifier.padding(horizontal = 20.dp)
                )
            }

            Spacer(Modifier.height(32.dp))
        }

        // ── Banner Bluetooth désactivé ─────────────────────────────
        AnimatedVisibility(
            visible = state.showBluetoothOffBanner,
            enter   = slideInVertically { -it } + fadeIn(),
            exit    = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            BluetoothOffBanner(onDismiss = viewModel::dismissBluetoothBanner)
        }

        // ── Overlay connexion réussie ──────────────────────────────
        AnimatedVisibility(
            visible  = state.connectionState is ConnectionState.Connected,
            enter    = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            ConnectedOverlay(device = (state.connectionState as? ConnectionState.Connected)?.device)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun PairingHeader(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Green900, Color(0xFF253D25))
                )
            )
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = Green200
            )
        }
        Text(
            text     = stringResource(R.string.pairing_title),
            style    = MaterialTheme.typography.titleLarge,
            color    = Green50,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// Animation radar
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun RadarAnimation(isScanning: Boolean, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")

    // 3 expanding rings
    val scales = (0..2).map { index ->
        infiniteTransition.animateFloat(
            initialValue  = 0.4f,
            targetValue   = if (isScanning) 1f else 0.4f,
            animationSpec = infiniteRepeatable(
                animation  = tween(2000, delayMillis = index * 600, easing = EaseOutCubic),
                repeatMode = RepeatMode.Restart
            ),
            label = "ring_$index"
        )
    }
    val ringAlphas = (0..2).map { index ->
        infiniteTransition.animateFloat(
            initialValue  = if (isScanning) 0.5f else 0.2f,
            targetValue   = 0f,
            animationSpec = infiniteRepeatable(
                animation  = tween(2000, delayMillis = index * 600),
                repeatMode = RepeatMode.Restart
            ),
            label = "alpha_$index"
        )
    }

    // Sweep rotation — full circle in 2.5s
    val sweepRotation by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_rot"
    )

    // Center pulse
    val centerScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = if (isScanning) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "center"
    )

    // Sweep fades in when scanning starts
    val sweepAlpha by animateFloatAsState(
        targetValue   = if (isScanning) 1f else 0f,
        animationSpec = tween(400),
        label         = "sweep_alpha"
    )

    Box(
        modifier         = modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        // Radar sweep — 90° arc trail rotating clockwise
        Canvas(
            modifier = Modifier
                .size(130.dp)
                .rotate(sweepRotation)
                .alpha(sweepAlpha)
        ) {
            val slices = 24
            repeat(slices) { i ->
                val sliceAlpha = ((slices - i).toFloat() / slices) * 0.5f
                drawArc(
                    color      = Green400.copy(alpha = sliceAlpha),
                    startAngle = -(i * (90f / slices)),
                    sweepAngle = 90f / slices,
                    useCenter  = true
                )
            }
            // Bright leading edge
            drawArc(
                color      = Green200.copy(alpha = 0.85f * sweepAlpha),
                startAngle = -1.5f,
                sweepAngle = 3f,
                useCenter  = true
            )
        }

        // Expanding rings
        scales.forEachIndexed { index, scale ->
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(scale.value)
                    .clip(CircleShape)
                    .background(Green400.copy(alpha = ringAlphas[index].value))
            )
        }

        // Center circle + Bluetooth icon
        Box(
            modifier = Modifier
                .size(54.dp)
                .scale(centerScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(colors = listOf(Green600, Green800))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.Default.Bluetooth,
                contentDescription = null,
                tint               = Green100,
                modifier           = Modifier.size(24.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Texte d'état
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun ScanStatusText(scanState: ScanState) {
    val idleText    = stringResource(R.string.scan_idle)
    val scanningText = stringResource(R.string.scan_scanning)
    val stoppedText = stringResource(R.string.scan_stopped)
    val (text, color) = when (scanState) {
        is ScanState.Idle     -> idleText    to MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        is ScanState.Scanning -> scanningText to Green400
        is ScanState.Stopped  -> stoppedText to MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        is ScanState.Error    -> scanState.message to Color(0xFFCF6679)
    }

    Text(
        text      = text,
        style     = MaterialTheme.typography.bodyMedium,
        color     = color,
        textAlign = TextAlign.Center,
        modifier  = Modifier.fillMaxWidth()
    )
}

// ─────────────────────────────────────────────────────────────────────
// Bouton scan / stop
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun ScanButton(
    scanState: ScanState,
    onScan: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isScanning = scanState is ScanState.Scanning

    OutlinedButton(
        onClick  = if (isScanning) onStop else onScan,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        shape    = RoundedCornerShape(14.dp),
        border   = BorderStroke(
            1.5.dp,
            if (isScanning) Orange400 else Green600
        ),
        colors   = ButtonDefaults.outlinedButtonColors(
            contentColor = if (isScanning) Orange400 else Green400
        )
    ) {
        AnimatedContent(
            targetState = isScanning,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "scan_btn"
        ) { scanning ->
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (scanning) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(16.dp),
                        color       = Orange400,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_stop_scan))
                } else {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_start_scan))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Liste des appareils détectés
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun DeviceList(
    devices: List<BleDevice>,
    selectedDevice: BleDevice?,
    onSelect: (BleDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text     = stringResource(R.string.devices_header, devices.size),
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        devices.forEachIndexed { index, device ->
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(device.address) {
                kotlinx.coroutines.delay(index * 80L)
                visible = true
            }
            AnimatedVisibility(
                visible = visible,
                enter   = slideInHorizontally { -it / 3 } + fadeIn()
            ) {
                DeviceItem(
                    device     = device,
                    isSelected = device.address == selectedDevice?.address,
                    onClick    = { onSelect(device) }
                )
            }
            if (index < devices.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DeviceItem(
    device: BleDevice,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue   = if (isSelected) Orange400 else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        animationSpec = tween(200),
        label         = "border"
    )
    val bgColor by animateColorAsState(
        targetValue   = if (isSelected) Green800.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(200),
        label         = "bg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icône appareil
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (device.isSap) Orange400.copy(alpha = 0.2f)
                    else Green800.copy(alpha = 0.3f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text     = if (device.isSap) "🪴" else "📡",
                fontSize = 20.sp
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = device.name,
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = if (isSelected) Green100
                    else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                )
                if (device.isSap) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Orange400.copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text  = "Sap",
                            style = MaterialTheme.typography.labelSmall,
                            color = Orange400,
                            fontSize = 9.sp
                        )
                    }
                }
            }
            Text(
                text  = "RSSI: ${device.rssi} dBm · ${device.address}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }

        // Barres de signal
        SignalBars(bars = device.signalBars, isSelected = isSelected)
    }
}

@Composable
private fun SignalBars(bars: Int, isSelected: Boolean) {
    val color = if (isSelected) Green400 else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf(6, 10, 14, 18).forEachIndexed { index, height ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (index < bars) color
                        else color.copy(alpha = 0.2f)
                    )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Message aucun appareil
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun EmptyDevicesMessage(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier          = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("😕", fontSize = 36.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            text      = stringResource(R.string.no_devices_title),
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text      = stringResource(R.string.no_devices_hint),
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.btn_retry), color = Green400)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Bouton appairer
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun PairButton(
    device: BleDevice?,
    connectionState: ConnectionState,
    onPair: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnecting = connectionState is ConnectionState.Connecting

    Button(
        onClick  = onPair,
        enabled  = !isConnecting && device != null,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape    = RoundedCornerShape(16.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor         = Green800,
            contentColor           = Color.White,
            disabledContainerColor = Green800.copy(alpha = 0.5f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        AnimatedContent(
            targetState = isConnecting,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "pair_btn"
        ) { connecting ->
            if (connecting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(18.dp),
                        color       = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.connecting))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.BluetoothConnected,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = device?.let { stringResource(R.string.pair_device, it.name.take(20)) }
                            ?: stringResource(R.string.pair_no_device)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Banner Bluetooth désactivé
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun BluetoothOffBanner(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF5C2D00))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.BluetoothDisabled,
            contentDescription = null,
            tint = Orange200,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text     = stringResource(R.string.bluetooth_off),
            style    = MaterialTheme.typography.bodyMedium,
            color    = Orange100,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dialog_close), tint = Orange200)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Overlay connexion réussie
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun ConnectedOverlay(device: BleDevice?) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Green800)
            .border(2.dp, Green400, RoundedCornerShape(24.dp))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Green600.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint     = Green200,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text  = stringResource(R.string.device_connected),
            style = MaterialTheme.typography.titleLarge,
            color = Green50
        )
        device?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text  = it.name,
                style = MaterialTheme.typography.bodyMedium,
                color = Green200.copy(alpha = 0.7f)
            )
        }
    }
}