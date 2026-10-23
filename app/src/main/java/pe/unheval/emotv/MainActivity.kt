package pe.unheval.emotv

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import pe.unheval.emotv.ui.navigation.EmotvNavHost
import pe.unheval.emotv.ui.startup.StartupUiState
import pe.unheval.emotv.ui.startup.StartupViewModel
import pe.unheval.emotv.ui.theme.EMOtvTheme

class MainActivity : ComponentActivity() {

    private val startupViewModel: StartupViewModel by viewModels { StartupViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // El logo del sistema se mantiene mientras se consulta GET /mobile/status.
        splashScreen.setKeepOnScreenCondition {
            !startupViewModel.firstCheckDone &&
                startupViewModel.uiState.value == StartupUiState.Loading
        }
        // Fondo claro siempre: barras del sistema con iconos oscuros.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            EMOtvTheme {
                EmotvNavHost(startupViewModel = startupViewModel)
            }
        }
    }
}
