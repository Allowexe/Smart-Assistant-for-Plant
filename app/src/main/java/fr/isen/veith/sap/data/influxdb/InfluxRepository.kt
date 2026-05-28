package fr.isen.veith.sap.data.influxdb

import fr.isen.veith.sap.domain.model.EnvironmentData
import fr.isen.veith.sap.domain.model.HistoryPoint
import fr.isen.veith.sap.domain.model.LightSpectrum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val INFLUX_URL   = "http://82.66.22.223:8086"
private const val INFLUX_ORG   = "SAP_Project"
private const val INFLUX_TOKEN =
    "rARqmypxAGpua0Yf4huCBcF5PaSQExTWjvOCa-qBomBmtujQRishHMmpJWbxcu1L3eDCK1LfC4dyU0xReADvuQ=="

class InfluxRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private suspend fun query(flux: String): String = withContext(Dispatchers.IO) {
        val body = flux.toRequestBody("application/vnd.flux".toMediaType())
        val req  = Request.Builder()
            .url("$INFLUX_URL/api/v2/query?org=$INFLUX_ORG")
            .addHeader("Authorization", "Token $INFLUX_TOKEN")
            .addHeader("Accept", "application/csv")
            .post(body)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("InfluxDB ${resp.code}: ${resp.body?.string()}")
            resp.body?.string() ?: ""
        }
    }

    suspend fun fetchEnvironment(plantId: String): EnvironmentData {
        val flux = """
            from(bucket: "sensor_data")
              |> range(start: -5m)
              |> filter(fn: (r) => r._measurement == "environment")
              |> filter(fn: (r) => r._field == "soil_1" or r._field == "soil_2" or r._field == "temp_shtc3" or r._field == "temp_bmp180")
              |> filter(fn: (r) => r.plant_id == "$plantId")
              |> last()
        """.trimIndent()
        val v = InfluxCsvParser.parseLastValues(query(flux))
        return EnvironmentData(
            soil1      = v["soil_1"]      ?: 0f,
            soil2      = v["soil_2"]      ?: 0f,
            tempShtc3  = v["temp_shtc3"]  ?: 0f,
            tempBmp180 = v["temp_bmp180"] ?: 0f
        )
    }

    suspend fun fetchSpectrum(plantId: String): LightSpectrum {
        val flux = """
            from(bucket: "sensor_data")
              |> range(start: -5m)
              |> filter(fn: (r) => r._measurement == "light_spectrum")
              |> filter(fn: (r) => r.plant_id == "$plantId")
              |> last()
        """.trimIndent()
        val v = InfluxCsvParser.parseLastValues(query(flux))
        return LightSpectrum(
            violet     = v["violet"]     ?: 0f,
            indigo     = v["indigo"]     ?: 0f,
            blue       = v["blue"]       ?: 0f,
            cyan       = v["cyan"]       ?: 0f,
            green      = v["green"]      ?: 0f,
            yellow     = v["yellow"]     ?: 0f,
            orange     = v["orange"]     ?: 0f,
            red        = v["red"]        ?: 0f,
            luminosity = v["luminosity"] ?: 0f
        )
    }

    suspend fun fetchHistory(plantId: String): List<HistoryPoint> {
        val flux = """
            from(bucket: "sensor_data")
              |> range(start: -1h)
              |> filter(fn: (r) => r._measurement == "environment")
              |> filter(fn: (r) => r._field == "soil_1" or r._field == "soil_2" or r._field == "temp_shtc3" or r._field == "temp_bmp180")
              |> filter(fn: (r) => r.plant_id == "$plantId")
              |> aggregateWindow(every: 1m, fn: mean, createEmpty: false)
        """.trimIndent()
        return InfluxCsvParser.parseHistory(query(flux))
    }

    suspend fun fetchPlantIds(): List<String> {
        val flux = """
            from(bucket: "sensor_data")
              |> range(start: -24h)
              |> filter(fn: (r) => r._measurement == "environment")
              |> keep(columns: ["plant_id"])
              |> distinct(column: "plant_id")
        """.trimIndent()
        return InfluxCsvParser.parsePlantIds(query(flux))
    }
}