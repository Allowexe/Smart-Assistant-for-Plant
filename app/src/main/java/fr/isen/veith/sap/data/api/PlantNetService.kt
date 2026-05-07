package fr.isen.veith.sap.data.api

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// ── Modèles de réponse PlantNet ───────────────────────────────────────

data class PlantNetResponse(
    val query: PlantNetQuery?,
    val language: String?,
    val preferredReferential: String?,
    val results: List<PlantNetResult>?,
    val remainingIdentificationRequests: Int?
)

data class PlantNetQuery(
    val project: String?,
    val images: List<String>?,
    val organs: List<String>?,
    val includeRelatedImages: Boolean?
)

data class PlantNetResult(
    val score: Double,
    val species: PlantNetSpecies,
    val gbif: PlantNetGbif?
)

data class PlantNetSpecies(
    val scientificNameWithoutAuthor: String,
    val scientificNameAuthorship: String?,
    val genus: PlantNetGenus?,
    val family: PlantNetFamily?,
    val commonNames: List<String>?,
    val scientificName: String?
)

data class PlantNetGenus(val scientificNameWithoutAuthor: String)
data class PlantNetFamily(val scientificNameWithoutAuthor: String)
data class PlantNetGbif(val id: String?)

// ── Interface Retrofit ────────────────────────────────────────────────
interface PlantNetService {

    @Multipart
    @POST("v2/identify/{project}")
    suspend fun identify(
        @Path("project")    project: String = "all",
        @Part             images: List<MultipartBody.Part>,
        @Part("organs") organs: okhttp3.RequestBody,
        @Query("lang")    lang: String   = "fr",
        @Query("api-key") apiKey: String
    ): PlantNetResponse
}

// ── Singleton Retrofit ────────────────────────────────────────────────
object PlantNetApi {

    private const val BASE_URL = "https://my-api.plantnet.org/"

    const val API_KEY = fr.isen.veith.sap.BuildConfig.PLANTNET_API_KEY

    private val client = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
        )
        .build()

    val service: PlantNetService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PlantNetService::class.java)
    }
}