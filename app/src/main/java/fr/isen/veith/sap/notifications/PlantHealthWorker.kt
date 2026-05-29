package fr.isen.veith.sap.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.isen.veith.sap.R
import fr.isen.veith.sap.data.influxdb.InfluxRepository
import fr.isen.veith.sap.data.preferences.AppPreferencesRepository
import fr.isen.veith.sap.domain.model.PlantMood
import fr.isen.veith.sap.domain.model.SensorData
import kotlinx.coroutines.flow.first

/**
 * Background check: for each saved pot, pull latest InfluxDB readings,
 * derive PlantMood, notify when CONCERNED/SAD. Runs even when app closed.
 */
class PlantHealthWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val influx = InfluxRepository()
    private val prefs  = AppPreferencesRepository(applicationContext)

    override suspend fun doWork(): Result {
        val p = prefs.preferences.first()
        if (!p.notificationsEnabled) return Result.success()
        if (p.savedPlants.isEmpty())  return Result.success()

        for ((potId, plant) in p.savedPlants) {
            try {
                val env      = influx.fetchEnvironment(potId)
                val spectrum = influx.fetchSpectrum(potId)
                val data = SensorData(
                    humidity    = (env.soil1 + env.soil2) / 2f,
                    temperature = env.tempShtc3,
                    luminosity  = spectrum.luminosity
                )
                val mood = PlantMood.from(data, plant)
                if (mood == PlantMood.CONCERNED || mood == PlantMood.SAD) {
                    val name = plant.commonName.ifBlank { potId }
                    val textRes = if (mood == PlantMood.SAD)
                        R.string.notif_help_sad else R.string.notif_help_concerned
                    PlantNotifier.notify(
                        applicationContext,
                        notifId = potId.hashCode(),
                        title   = applicationContext.getString(R.string.notif_help_title, name),
                        text    = applicationContext.getString(textRes, name)
                    )
                }
            } catch (_: Exception) {
                // one pot's network failure shouldn't abort the rest; retry next cycle
            }
        }
        return Result.success()
    }
}
