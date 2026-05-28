package fr.isen.veith.sap.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.isen.veith.sap.data.ble.BleDevice
import fr.isen.veith.sap.data.ble.ScanState
import fr.isen.veith.sap.data.ble.WifiAP
import fr.isen.veith.sap.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: BleViewModel = viewModel()
) {
    val state   by viewModel.uiState.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val scanSt  by viewModel.scanState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val blePermissions = if (android.os.Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) viewModel.startScan()
    }

    fun launchScan() {
        val missing = blePermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) viewModel.startScan()
        else permLauncher.launch(missing.toTypedArray())
    }

    LaunchedEffect(Unit) {
        if (state.phase == ScanPhase.SCANNING) launchScan()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (state.phase) {
                            ScanPhase.SCANNING    -> "Recherche de pots"
                            ScanPhase.CONNECTING,
                            ScanPhase.WIFI_TRIGGER -> "Connexion…"
                            ScanPhase.WIFI_LIST   -> "Choisir un réseau"
                            ScanPhase.CONFIGURE   -> "Configurer le Wi-Fi"
                            ScanPhase.SENDING,
                            ScanPhase.STATUS      -> "Configuration en cours…"
                            ScanPhase.DONE        -> "Pot configuré !"
                            ScanPhase.ERROR       -> "Erreur"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.reset()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Green800,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Green200
                )
            )
        }
    ) { pad ->
        Box(modifier = Modifier.fillMaxSize().padding(pad)) {
            AnimatedContent(
                targetState = state.phase,
                transitionSpec = {
                    fadeIn() + slideInHorizontally { it / 4 } togetherWith
                    fadeOut() + slideOutHorizontally { -it / 4 }
                },
                label = "phase"
            ) { phase ->
                when (phase) {
                    ScanPhase.SCANNING ->
                        ScanningPhase(
                            devices   = devices,
                            scanState = scanSt,
                            onSelect  = viewModel::connectDevice,
                            onRescan  = { launchScan() }
                        )

                    ScanPhase.CONNECTING ->
                        CenterProgress("Connexion au pot…")

                    ScanPhase.WIFI_TRIGGER ->
                        WifiTriggerPhase(
                            deviceName     = state.selectedDevice?.name ?: "",
                            isWifiScanning = state.isWifiScanning,
                            onScan         = viewModel::triggerWifiScan
                        )

                    ScanPhase.WIFI_LIST ->
                        WifiListPhase(
                            wifiAPs  = state.wifiAPs,
                            onSelect = viewModel::selectAP
                        )

                    ScanPhase.CONFIGURE ->
                        ConfigurePhase(
                            ssid               = state.selectedAP?.ssid ?: "",
                            password           = state.password,
                            passwordVisible    = state.passwordVisible,
                            onPasswordChange   = viewModel::updatePassword,
                            onToggleVisibility = viewModel::togglePasswordVisible,
                            onSend             = viewModel::sendCredentials
                        )

                    ScanPhase.SENDING ->
                        CenterProgress("Envoi des identifiants…")

                    ScanPhase.STATUS ->
                        StatusPhase(status = state.statusMessage)

                    ScanPhase.DONE ->
                        DonePhase(onDone = {
                            viewModel.reset()
                            onDone()
                        })

                    ScanPhase.ERROR ->
                        ErrorPhase(
                            message = state.errorMessage,
                            onRetry = {
                                viewModel.reset()
                                launchScan()
                            },
                            onBack  = {
                                viewModel.reset()
                                onBack()
                            }
                        )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Phase 1 : liste des pots BLE détectés
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScanningPhase(
    devices: List<BleDevice>,
    scanState: ScanState,
    onSelect: (BleDevice) -> Unit,
    onRescan: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Appareils détectés",
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            when (scanState) {
                is ScanState.Scanning ->
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else ->
                    TextButton(onClick = onRescan) { Text("Scanner") }
            }
        }

        if (devices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📡", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Aucun pot détecté.\nAllumez votre pot SAP.",
                        style     = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(devices, key = { it.address }) { device ->
                    DeviceRow(device = device, onClick = { onSelect(device) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: BleDevice, onClick: () -> Unit) {
    ListItem(
        headlineContent   = { Text(device.name, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(device.address, style = MaterialTheme.typography.labelSmall) },
        leadingContent    = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (device.isSap) Green700 else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(if (device.isSap) "🪴" else "📶", fontSize = 18.sp)
            }
        },
        trailingContent = {
            Text(
                "${device.rssi} dBm",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Phase 2 : déclencher le scan Wi-Fi sur le pot
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WifiTriggerPhase(deviceName: String, isWifiScanning: Boolean, onScan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🔗", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Connecté à $deviceName",
            style     = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (isWifiScanning) "Scan des réseaux Wi-Fi en cours…"
            else "Touchez le bouton pour que le pot scanne les réseaux Wi-Fi disponibles.",
            style     = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
        )
        Spacer(Modifier.height(32.dp))
        if (isWifiScanning) {
            CircularProgressIndicator(color = Green400)
        } else {
            Button(
                onClick  = onScan,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = Green700)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scanner les réseaux Wi-Fi")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Phase 3 : liste des SSID retournés par le pot
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WifiListPhase(wifiAPs: List<WifiAP>, onSelect: (WifiAP) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Choisissez votre réseau Wi-Fi",
            style    = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        LazyColumn {
            items(wifiAPs, key = { it.ssid }) { ap ->
                ListItem(
                    headlineContent   = { Text(ap.ssid, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text(ap.security.label, style = MaterialTheme.typography.labelSmall) },
                    leadingContent    = { Icon(Icons.Default.Wifi, contentDescription = null) },
                    trailingContent   = {
                        Text(
                            "${ap.rssi} dBm",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    },
                    modifier = Modifier.clickable { onSelect(ap) }
                )
                HorizontalDivider()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Phase 4 : saisie password + plant_id
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConfigurePhase(
    ssid: String,
    password: String,
    passwordVisible: Boolean,
    onPasswordChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onSend: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AssistChip(
            onClick     = {},
            label       = { Text(ssid) },
            leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )

        OutlinedTextField(
            value         = password,
            onValueChange = onPasswordChange,
            label         = { Text("Mot de passe Wi-Fi") },
            singleLine    = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon  = {
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff
                        else Icons.Default.Visibility,
                        contentDescription = null
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick  = onSend,
            enabled  = password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Green700)
        ) {
            Icon(Icons.Default.Send, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Envoyer la configuration", fontSize = 16.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Phase 5 : suivi du statut
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusPhase(status: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Green400)
        Spacer(Modifier.height(24.dp))
        Text(
            status.replaceFirstChar { it.titlecase() },
            style     = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            "Le pot se connecte à votre réseau Wi-Fi…",
            style     = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier  = Modifier.padding(top = 8.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Phase terminée
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DonePhase(onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("✅", fontSize = 72.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Pot configuré !",
            style     = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            "Le pot va maintenant se connecter à votre réseau et commencer à publier les données capteurs.",
            style     = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            modifier  = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )
        Button(
            onClick  = onDone,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Green700)
        ) {
            Text("Voir le dashboard", fontSize = 16.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Erreur
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorPhase(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("⚠️", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Réessayer") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Retour") }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading centre
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CenterProgress(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Green400)
            Spacer(Modifier.height(16.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
