package fr.isen.veith.sap

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import fr.isen.veith.sap.data.preferences.LocaleHelper
import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import fr.isen.veith.sap.data.preferences.AppTheme
import fr.isen.veith.sap.ui.MainViewModel
import fr.isen.veith.sap.ui.auth.AuthScreen
import fr.isen.veith.sap.ui.dashboard.DashboardScreen
import fr.isen.veith.sap.ui.home.HomeScreen
import fr.isen.veith.sap.ui.pairing.PairingScreen
import fr.isen.veith.sap.ui.recognition.RecognitionScreen
import fr.isen.veith.sap.ui.scan.ScanScreen
import fr.isen.veith.sap.ui.settings.SettingsScreen
import fr.isen.veith.sap.ui.theme.SapTheme

// ── Destinations de navigation ────────────────────────────────────────
object Routes {
    const val AUTH        = "auth"
    const val HOME        = "home"        // legacy BLE home
    const val DASHBOARD   = "dashboard"   // InfluxDB dashboard (main)
    const val SCAN        = "scan"        // BLE commissioning
    const val SETTINGS    = "settings"
    const val PAIRING     = "pairing"     // legacy BLE pairing
    const val RECOGNITION = "recognition"
}

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        enableEdgeToEdge()
        setContent {
            val theme by mainViewModel.theme.collectAsStateWithLifecycle()
            val darkTheme = when (theme) {
                AppTheme.LIGHT  -> false
                AppTheme.DARK   -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }
            SapTheme(darkTheme = darkTheme) {
                SapApp()
            }
        }
    }
}

@Composable
fun SapApp() {
    val navController = rememberNavController()

    val start = if (FirebaseAuth.getInstance().currentUser != null) Routes.HOME else Routes.AUTH

    NavHost(
        navController    = navController,
        startDestination = start,
        enterTransition  = { fadeIn() + slideInHorizontally { it / 4 } },
        exitTransition   = { fadeOut() + slideOutHorizontally { -it / 4 } },
        popEnterTransition  = { fadeIn() + slideInHorizontally { -it / 4 } },
        popExitTransition   = { fadeOut() + slideOutHorizontally { it / 4 } }
    ) {
        // ── Auth ─────────────────────────────────────────────────
        composable(Routes.AUTH) {
            AuthScreen(
                onAuthSuccess = { _ ->
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                }
            )
        }

        // ── Dashboard (main) ──────────────────────────────────────
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToScan = { navController.navigate(Routes.SCAN) }
            )
        }

        // ── BLE Scan / Commissioning ──────────────────────────────
        composable(Routes.SCAN) {
            ScanScreen(
                onDone = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Home (main) ───────────────────────────────────────────
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToSettings    = { navController.navigate(Routes.SETTINGS) },
                onNavigateToScan        = { navController.navigate(Routes.SCAN) },
                onNavigateToDashboard   = { navController.navigate(Routes.DASHBOARD) },
                onNavigateToRecognition = { potId ->
                    navController.navigate("${Routes.RECOGNITION}/$potId")
                }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onLogout = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.PAIRING) {
            PairingScreen(
                onPaired = { navController.popBackStack() },
                onBack   = { navController.popBackStack() }
            )
        }
        composable(
            route     = "${Routes.RECOGNITION}/{potId}",
            arguments = listOf(navArgument("potId") {
                type         = NavType.StringType
                defaultValue = ""
            })
        ) { backStackEntry ->
            RecognitionScreen(
                potId        = backStackEntry.arguments?.getString("potId") ?: "",
                onPlantSaved = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onBack       = { navController.popBackStack() }
            )
        }
    }
}
