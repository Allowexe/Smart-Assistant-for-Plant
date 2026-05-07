package fr.isen.veith.sap

import android.app.Application
import fr.isen.veith.sap.data.ble.BleManager

class SapApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BleManager.init(this)
    }
}