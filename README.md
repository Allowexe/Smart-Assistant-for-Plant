# 🌱 SAP — Smart Assistant for Plant

An Android app for monitoring connected plant pots. It commissions smart pots over **BLE / Wi-Fi**, streams their sensor data from **InfluxDB**, and uses the **Pl@ntNet API** to identify plants and diagnose diseases.

> ISEN student project — Kotlin + Jetpack Compose.

---

## Features

- 🔌 **Pot commissioning** — pair a connected pot over BLE and provision its Wi-Fi credentials
- 📡 **Live dashboard** — real-time soil moisture, temperature and full light spectrum from InfluxDB
- 🔍 **Plant identification** — snap a photo, identify the species via Pl@ntNet, save it to a pot
- 🩺 **Disease diagnosis** — dedicated camera scan via the Pl@ntNet *diseases* API, with treatment tips and a link to the EPPO datasheet
- 🔔 **Notifications** — background check (every 15 min via WorkManager) that alerts you when a plant needs attention
- 🏆 **Achievements** — unlockable goals (pots paired, plants identified, watering/light/temperature/health streaks)
- ☀️ **Light source detection** — spectral analysis (AS734X) distinguishes sunlight from artificial light
- 📊 **Smart sensor display** — averages dual soil/temperature probes, adaptive lux formatting, plant-specific alert thresholds
- 🌐 **Bilingual** — French / English

---

## Tech stack

| Area | Library |
|------|---------|
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Async | Kotlin Coroutines / Flow |
| Auth & cloud | Firebase Auth, Firestore, Analytics |
| Networking | Retrofit + Gson (Pl@ntNet), OkHttp (InfluxDB) |
| Bluetooth | Nordic Android BLE library |
| Camera | CameraX |
| Storage | DataStore (preferences), Room |
| Background | WorkManager |

**Min SDK** 29 (Android 10) · **Target SDK** 36 · **Kotlin** 2.2

---

## Architecture

Layered MVVM:

```
ui/        Compose screens + ViewModels (auth, home, scan, pairing, recognition, dashboard, settings)
domain/    Models & business logic (Plant, SensorData, PlantMood, LightSource, Achievements, DiseaseAdvice)
data/      Repositories & data sources
  ├─ ble/           BLE scan & commissioning
  ├─ influxdb/      InfluxDB HTTP client + CSV parser
  ├─ api/           Pl@ntNet identification & disease detection
  ├─ preferences/   DataStore (theme, language, saved plants…)
  ├─ achievements/  Achievement unlock tracking
  └─ plant/         Ideal-condition database
notifications/  WorkManager worker + notification helper
```

---

## Getting started

### Prerequisites
- Android Studio (latest stable)
- A Pl@ntNet API key — https://my.plantnet.org
- A Firebase project (`google-services.json`)

### Configuration

1. **`google-services.json`** — place your Firebase config in `app/google-services.json`.

2. **`local.properties`** (git-ignored) — add your keys:
   ```properties
   sdk.dir=/path/to/Android/Sdk
   PLANTNET_API_KEY=your_plantnet_key
   ```

3. **InfluxDB** — the endpoint/org/token are configured in
   `data/influxdb/InfluxRepository.kt`.

### Build & run
```bash
./gradlew :app:assembleDebug      # debug APK (installable, debug-signed)
./gradlew installDebug            # build + install on a connected device
```

### Signed release
Add the signing credentials to `local.properties` (keystore kept out of git):
```properties
RELEASE_STORE_FILE=sap-release.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=sap
RELEASE_KEY_PASSWORD=...
```
Then:
```bash
./gradlew :app:assembleRelease    # → app/build/outputs/apk/release/app-release.apk
```

---

## Hardware

The companion pot reports to InfluxDB:
- 2× soil moisture probes
- 2× temperature sensors (SHTC3, BMP180)
- AS734X spectral light sensor (8 visible channels + lux)

The app reads these via Flux queries, computes a health score and mood per plant, and raises alerts against species-specific ideal ranges.

---

## License

Educational project — ISEN. Not affiliated with Pl@ntNet or EPPO.
