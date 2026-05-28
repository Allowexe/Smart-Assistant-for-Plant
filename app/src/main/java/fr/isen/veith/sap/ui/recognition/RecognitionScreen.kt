package fr.isen.veith.sap.ui.recognition

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.isen.veith.sap.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.isen.veith.sap.data.api.IdentificationResult
import fr.isen.veith.sap.data.api.IdentificationState
import fr.isen.veith.sap.ui.theme.*
import kotlin.math.roundToInt

/**
 * Écran de reconnaissance de plante via caméra + API PlantNet.
 *
 * @param onPlantSaved  Callback appelé quand la plante est sauvegardée
 * @param onBack        Retour à l'écran précédent
 */
@Composable
fun RecognitionScreen(
    potId: String = "",
    onPlantSaved: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: RecognitionViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Launcher permission caméra
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onCameraPermissionResult(granted) }

    // Vérifier permission au démarrage
    LaunchedEffect(Unit) {
        viewModel.checkCameraPermission()
        if (!state.hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Navigation si plante sauvegardée
    LaunchedEffect(state.showSaveSuccess) {
        if (state.showSaveSuccess && state.savedPlant != null) {
            kotlinx.coroutines.delay(2000)
            onPlantSaved()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.statusBars)) {

        if (!state.hasCameraPermission) {
            // ── Permission refusée ─────────────────────────────────
            PermissionDeniedScreen(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onBack    = onBack
            )
        } else if (state.capturedImageUri == null) {
            // ── Viewfinder caméra ──────────────────────────────────
            CameraViewfinder(
                onCapture = viewModel::capturePhoto,
                isCapturing = state.isCapturing,
                onBack    = onBack
            )
        } else {
            // ── Résultats d'identification ─────────────────────────
            ResultsScreen(
                imageUri            = state.capturedImageUri!!,
                identificationState = state.identificationState,
                selectedIndex       = state.selectedResultIndex,
                onSelectResult      = viewModel::selectResult,
                onRetry             = viewModel::retryIdentification,
                onRetake            = viewModel::resetCapture,
                onSave              = { viewModel.savePlant(potId) },
                onBack              = onBack
            )
        }

        // ── Toast sauvegarde réussie ───────────────────────────────
        AnimatedVisibility(
            visible  = state.showSaveSuccess,
            enter    = slideInVertically { it } + fadeIn(),
            exit     = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        ) {
            SaveSuccessToast(plantName = state.savedPlant?.commonName ?: "")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Viewfinder CameraX
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun CameraViewfinder(
    onCapture: (ImageCapture) -> Unit,
    isCapturing: Boolean,
    onBack: () -> Unit
) {
    val context       = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val imageCapture  = remember { ImageCapture.Builder().build() }

    // Animation du bouton capture
    val captureScale by animateFloatAsState(
        targetValue   = if (isCapturing) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "capture_scale"
    )

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Aperçu caméra ─────────────────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // ── Overlay UI ────────────────────────────────────────────

        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
                .padding(horizontal = 8.dp, vertical = 16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Text(
                text     = stringResource(R.string.recognition_title),
                color    = Color.White,
                style    = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Cadre de mise au point
        FocusFrame(modifier = Modifier.align(Alignment.Center))

        // Conseil
        Text(
            text     = stringResource(R.string.recognition_hint),
            color    = Color.White.copy(alpha = 0.75f),
            style    = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 110.dp)
        )

        // Bouton capture
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            // Anneau extérieur
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .border(3.dp, Color.White, CircleShape)
            )
            // Bouton central
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .scale(captureScale)
                    .clip(CircleShape)
                    .background(Color.White)
                    .align(Alignment.Center)
                    .clickable(enabled = !isCapturing) { onCapture(imageCapture) }
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier  = Modifier.size(28.dp).align(Alignment.Center),
                        color     = Green800,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Cadre de mise au point
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun FocusFrame(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "focus")
    val cornerAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.6f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corner_alpha"
    )

    Box(
        modifier = modifier
            .size(220.dp)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        val cornerColor = Orange400.copy(alpha = cornerAlpha)
        val cornerSize  = 24.dp
        val stroke      = 3.dp

        // Coin haut-gauche
        Box(modifier = Modifier.size(cornerSize).align(Alignment.TopStart)
            .border(BorderStroke(stroke, Brush.linearGradient(listOf(cornerColor, Color.Transparent))))
        )
        // Coin haut-droit (rotation simulée via padding)
        Box(modifier = Modifier
            .size(cornerSize).align(Alignment.TopEnd)
            .border(BorderStroke(stroke, Brush.linearGradient(listOf(Color.Transparent, cornerColor))))
        )
        // Coin bas-gauche
        Box(modifier = Modifier.size(cornerSize).align(Alignment.BottomStart)
            .border(BorderStroke(stroke, Brush.linearGradient(listOf(cornerColor, Color.Transparent))))
        )
        // Coin bas-droit
        Box(modifier = Modifier.size(cornerSize).align(Alignment.BottomEnd)
            .border(BorderStroke(stroke, Brush.linearGradient(listOf(Color.Transparent, cornerColor))))
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// Écran de résultats
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun ResultsScreen(
    imageUri: Uri,
    identificationState: IdentificationState,
    selectedIndex: Int,
    onSelectResult: (Int) -> Unit,
    onRetry: () -> Unit,
    onRetake: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Green900, Color(0xFF253D25))))
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Green200)
            }
            Text(
                text     = stringResource(R.string.results_title),
                style    = MaterialTheme.typography.titleLarge,
                color    = Green50,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        when (identificationState) {
            is IdentificationState.Idle,
            is IdentificationState.Analyzing -> {
                AnalyzingIndicator(
                    modifier = Modifier.fillMaxWidth().padding(48.dp)
                )
            }

            is IdentificationState.Error -> {
                ErrorCard(
                    message  = identificationState.message,
                    onRetry  = onRetry,
                    onRetake = onRetake,
                    modifier = Modifier.padding(20.dp)
                )
            }

            is IdentificationState.Success -> {
                val results = identificationState.results

                Spacer(Modifier.height(16.dp))

                // Résultat principal (sélectionné)
                results.getOrNull(selectedIndex)?.let { result ->
                    MainResultCard(
                        result   = result,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Autres résultats
                if (results.size > 1) {
                    Text(
                        text     = stringResource(R.string.other_results),
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                    results.forEachIndexed { index, result ->
                        if (index != selectedIndex) {
                            AlternativeResultRow(
                                result     = result,
                                isSelected = false,
                                onClick    = { onSelectResult(index) },
                                modifier   = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Boutons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick  = onRetake,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape    = RoundedCornerShape(14.dp),
                        border   = BorderStroke(1.dp, Green600)
                    ) {
                        Icon(Icons.Default.CameraAlt, null,
                            tint = Green400, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_retake), color = Green400)
                    }
                    Button(
                        onClick  = onSave,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = Orange400,
                            contentColor   = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_save))
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Carte résultat principal
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun MainResultCard(result: IdentificationResult, modifier: Modifier = Modifier) {
    val confidencePct = (result.confidence * 100).roundToInt()
    val confidenceColor = when {
        confidencePct >= 70 -> Green400
        confidencePct >= 40 -> Orange400
        else                -> Color(0xFFCF6679)
    }

    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val animConf by animateFloatAsState(
        targetValue   = if (started) result.confidence else 0f,
        animationSpec = tween(1000, easing = EaseOutCubic),
        label         = "conf"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border   = BorderStroke(1.dp, Green600.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Emoji plante
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Green800.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(result.plant.emoji, fontSize = 28.sp)
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = result.commonName,
                        style      = MaterialTheme.typography.titleLarge,
                        color      = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text  = result.scientificName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Normal
                    )
                    result.family?.let {
                        Text(
                            text  = stringResource(R.string.family_label, it),
                            style = MaterialTheme.typography.labelSmall,
                            color = Green400.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Barre de confiance
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text  = stringResource(R.string.confidence_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.width(80.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(confidenceColor.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animConf)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(confidenceColor)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text       = "$confidencePct%",
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = confidenceColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Ligne résultat alternatif
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun AlternativeResultRow(
    result: IdentificationResult,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                0.5.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(result.plant.emoji, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = result.commonName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text  = result.scientificName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
        Text(
            text  = "${(result.confidence * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// Indicateur d'analyse
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun AnalyzingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "analyze")
    val rotation by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label         = "rotation"
    )

    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🌿", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        CircularProgressIndicator(color = Green400, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            text      = stringResource(R.string.analyzing_title),
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text      = stringResource(R.string.analyzing_subtitle),
            style     = MaterialTheme.typography.labelSmall,
            color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// Carte d'erreur
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier            = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("😕", fontSize = 40.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text      = message,
            style     = MaterialTheme.typography.bodyMedium,
            color     = Color(0xFFCF6679),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onRetake) {
                Text(stringResource(R.string.btn_retake_photo), color = Green400)
            }
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.btn_retry), color = Orange400)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Écran permission refusée
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun PermissionDeniedScreen(onRequest: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📷", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            text      = stringResource(R.string.camera_permission_title),
            style     = MaterialTheme.typography.titleLarge,
            color     = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = stringResource(R.string.camera_permission_message),
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRequest,
            colors  = ButtonDefaults.buttonColors(containerColor = Green800),
            shape   = RoundedCornerShape(14.dp)
        ) {
            Text(stringResource(R.string.btn_allow_camera), color = Color.White)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.cd_back), color = Green400)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Toast sauvegarde
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun SaveSuccessToast(plantName: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Green800)
            .border(1.dp, Green400.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint     = Green200,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text  = stringResource(R.string.save_success, plantName),
            style = MaterialTheme.typography.bodyMedium,
            color = Green50
        )
    }
}