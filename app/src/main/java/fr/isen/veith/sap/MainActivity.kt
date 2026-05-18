package fr.isen.veith.sap

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.isen.veith.sap.data.preferences.LocaleHelper
import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.isen.veith.sap.ui.auth.AuthScreen
import fr.isen.veith.sap.ui.home.HomeScreen
import fr.isen.veith.sap.ui.pairing.PairingScreen
import fr.isen.veith.sap.ui.recognition.RecognitionScreen
import fr.isen.veith.sap.ui.settings.SettingsScreen
import fr.isen.veith.sap.ui.theme.SapTheme

// ── Destinations de navigation ────────────────────────────────────────
object Routes {
    const val AUTH   = "auth"
    const val HOME   = "home"
    const val SETTINGS  = "settings"
    const val PAIRING   = "pairing"
    const val RECOGNITION = "recognition"
}

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SapTheme {
                SapApp()
            }
        }
    }
}

@Composable
fun SapApp() {
    val navController = rememberNavController()

    NavHost(
        navController    = navController,
        startDestination = Routes.AUTH,
        enterTransition  = { fadeIn() + slideInHorizontally { it / 4 } },
        exitTransition   = { fadeOut() + slideOutHorizontally { -it / 4 } },
        popEnterTransition  = { fadeIn() + slideInHorizontally { -it / 4 } },
        popExitTransition   = { fadeOut() + slideOutHorizontally { it / 4 } }
    ) {
        // ── Auth ─────────────────────────────────────────────────
        composable(Routes.AUTH) {
            AuthScreen(
                onAuthSuccess = { user ->
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                }
            )
        }

        // ── Home ───────────
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToSettings    = { navController.navigate(Routes.SETTINGS) },
                onNavigateToPairing     = { navController.navigate(Routes.PAIRING) },
                onNavigateToRecognition = { navController.navigate(Routes.RECOGNITION) }
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
                onPaired = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onBack   = { navController.popBackStack() }
            )
        }
        composable(Routes.RECOGNITION) {
            RecognitionScreen(
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
