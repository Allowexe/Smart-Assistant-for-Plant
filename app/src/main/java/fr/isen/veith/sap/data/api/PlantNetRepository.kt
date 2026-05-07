package fr.isen.veith.sap.data.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import fr.isen.veith.sap.domain.model.Plant
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import androidx.core.graphics.scale
import okhttp3.MediaType.Companion.toMediaTypeOrNull

// ── Résultat d'identification ─────────────────────────────────────────
data class IdentificationResult(
    val plant: Plant,
    val confidence: Float,
    val scientificName: String,
    val commonName: String,
    val family: String?
)

sealed class IdentificationState {
    object Idle                                        : IdentificationState()
    object Analyzing                                   : IdentificationState()
    data class Success(val results: List<IdentificationResult>) : IdentificationState()
    data class Error(val message: String)              : IdentificationState()
}

// ── Repository ────────────────────────────────────────────────────────
class PlantNetRepository {

    suspend fun identify(
        context: Context,
        imageUri: Uri,
        lang: String = "fr"
    ): IdentificationState {
        return try {
            val imageBytes = compressImage(context, imageUri)
                ?: return IdentificationState.Error("Impossible de lire l'image")

            val imagePart = MultipartBody.Part.createFormData(
                name     = "images",
                filename = "plant.jpg",
                body     = imageBytes.toRequestBody("image/jpeg".toMediaType())
            )
            val organBody = "auto".toRequestBody("text/plain".toMediaTypeOrNull())

            val response = PlantNetApi.service.identify(
                images = listOf(imagePart),
                organs = organBody,
                lang   = lang,
                apiKey = PlantNetApi.API_KEY
            )

            val results = response.results
                ?.take(5)
                ?.map { it.toIdentificationResult() }
                ?: emptyList()

            if (results.isEmpty()) {
                IdentificationState.Error("Aucune plante reconnue — réessayez avec une meilleure photo")
            } else {
                IdentificationState.Success(results)
            }

        } catch (e: retrofit2.HttpException) {
            when (e.code()) {
                401  -> IdentificationState.Error("Clé API invalide")
                404  -> IdentificationState.Error("Plante non trouvée dans la base")
                429  -> IdentificationState.Error("Quota API dépassé pour aujourd'hui")
                else -> IdentificationState.Error("Erreur serveur (${e.code()})")
            }
        } catch (e: java.io.IOException) {
            IdentificationState.Error("Pas de connexion internet")
        } catch (e: Exception) {
            IdentificationState.Error("Erreur inattendue: ${e.localizedMessage}")
        }
    }

    // ── Compression image (max 1MB pour l'API) ────────────────────────
    private fun compressImage(context: Context, uri: Uri): ByteArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val maxSize = 1024
            val scaled = if (bitmap.width > maxSize || bitmap.height > maxSize) {
                val ratio = minOf(
                    maxSize.toFloat() / bitmap.width,
                    maxSize.toFloat() / bitmap.height
                )
                bitmap.scale((bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt())
            } else bitmap

            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    // ── Mapper PlantNetResult → IdentificationResult ──────────────────
    private fun PlantNetResult.toIdentificationResult(): IdentificationResult {
        val sciName    = species.scientificNameWithoutAuthor
        val commonName = species.commonNames?.firstOrNull() ?: sciName
        val family     = species.family?.scientificNameWithoutAuthor

        val emoji = when (family?.lowercase()) {
            "cactaceae"           -> "🌵"
            "orchidaceae"         -> "🌸"
            "arecaceae"           -> "🌴"
            "musaceae"            -> "🍌"
            "lamiaceae"           -> "🌿"
            else                  -> "🌱"
        }

        return IdentificationResult(
            plant = Plant(
                id             = sciName.replace(" ", "_").lowercase(),
                commonName     = commonName,
                scientificName = sciName,
                emoji          = emoji
            ),
            confidence     = score.toFloat(),
            scientificName = sciName,
            commonName     = commonName,
            family         = family
        )
    }
}