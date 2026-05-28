package fr.isen.veith.sap.ui.recognition

import android.Manifest
import android.app.Application
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.isen.veith.sap.data.api.IdentificationResult
import fr.isen.veith.sap.data.api.IdentificationState
import fr.isen.veith.sap.data.api.PlantNetRepository
import fr.isen.veith.sap.data.preferences.AppPreferencesRepository
import fr.isen.veith.sap.domain.model.Plant
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

data class RecognitionUiState(
    // Caméra
    val hasCameraPermission: Boolean    = false,
    val capturedImageUri: Uri?          = null,
    val isCapturing: Boolean            = false,

    // Identification
    val identificationState: IdentificationState = IdentificationState.Idle,

    // Résultat sauvegardé
    val savedPlant: Plant?              = null,
    val showSaveSuccess: Boolean        = false,

    // UI
    val selectedResultIndex: Int        = 0
)

class RecognitionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlantNetRepository()

    private val _uiState = MutableStateFlow(RecognitionUiState())
    val uiState: StateFlow<RecognitionUiState> = _uiState.asStateFlow()

    init {
        checkCameraPermission()
    }

    // ── Permissions ───────────────────────────────────────────────────
    fun checkCameraPermission() {
        val granted = ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        _uiState.update { it.copy(hasCameraPermission = granted) }
    }

    fun onCameraPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = granted) }
    }

    // ── Capture photo ─────────────────────────────────────────────────
    fun capturePhoto(imageCapture: ImageCapture) {
        val context = getApplication<Application>()
        _uiState.update { it.copy(isCapturing = true) }

        @Suppress("SpellCheckingInspection") val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE)
            .format(System.currentTimeMillis()) + ".jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Sap")
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val uri = output.savedUri
                    _uiState.update {
                        it.copy(
                            capturedImageUri = uri,
                            isCapturing      = false,
                            identificationState = IdentificationState.Idle
                        )
                    }
                    // Lancer l'identification automatiquement
                    uri?.let { identify(it) }
                }

                override fun onError(exception: ImageCaptureException) {
                    _uiState.update {
                        it.copy(
                            isCapturing = false,
                            identificationState = IdentificationState.Error(
                                "Erreur photo: ${exception.localizedMessage}"
                            )
                        )
                    }
                }
            }
        )
    }

    // ── Identification PlantNet ───────────────────────────────────────
    fun identify(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(identificationState = IdentificationState.Analyzing)
            }
            val result = repository.identify(getApplication(), uri)
            _uiState.update { it.copy(identificationState = result) }
        }
    }

    fun retryIdentification() {
        _uiState.value.capturedImageUri?.let { identify(it) }
    }

    fun resetCapture() {
        _uiState.update {
            it.copy(
                capturedImageUri    = null,
                identificationState = IdentificationState.Idle,
                selectedResultIndex = 0
            )
        }
    }

    // ── Sélection résultat ────────────────────────────────────────────
    fun selectResult(index: Int) {
        _uiState.update { it.copy(selectedResultIndex = index) }
    }

    // ── Sauvegarder la plante identifiée (associée au potId commissioning) ─
    fun savePlant(potId: String) {
        val state = _uiState.value
        val results = (state.identificationState as? IdentificationState.Success)
            ?.results ?: return
        val selected = results.getOrNull(state.selectedResultIndex) ?: return

        viewModelScope.launch {
            AppPreferencesRepository(getApplication()).savePlant(potId, selected.plant)
            _uiState.update {
                it.copy(
                    savedPlant      = selected.plant,
                    showSaveSuccess = true
                )
            }
            kotlinx.coroutines.delay(2500)
            _uiState.update { it.copy(showSaveSuccess = false) }
        }
    }

    // ── Résultat sélectionné (raccourci) ──────────────────────────────
    val selectedResult: IdentificationResult?
        get() {
            val results = (_uiState.value.identificationState as? IdentificationState.Success)
                ?.results ?: return null
            return results.getOrNull(_uiState.value.selectedResultIndex)
        }
}