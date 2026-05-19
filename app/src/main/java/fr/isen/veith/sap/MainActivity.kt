package fr.isen.veith.sap

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import fr.isen.veith.sap.data.preferences.LocaleHelper
import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import fr.isen.veith.sap.data.preferences.AppTheme
import fr.isen.veith.sap.ui.MainViewModel
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
    private val mainViewModel: MainViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                onPaired = { navController.popBackStack() },
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
