package fr.isen.veith.sap

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import fr.isen.veith.sap.data.ble.BleManager
import fr.isen.veith.sap.data.plant.PlantDatabase
import fr.isen.veith.sap.notifications.PlantHealthWorker
import fr.isen.veith.sap.notifications.PlantNotifier
import java.util.concurrent.TimeUnit

class SapApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BleManager.init(this)
        PlantDatabase.load(this)
        PlantNotifier.ensureChannel(this)
        schedulePlantHealthChecks()
    }

    private fun schedulePlantHealthChecks() {
        val request = PeriodicWorkRequestBuilder<PlantHealthWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "plant_health_check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}