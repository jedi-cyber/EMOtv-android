package pe.unheval.emotv.ui.startup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pe.unheval.emotv.ui.theme.EMOtvTheme

@RunWith(AndroidJUnit4::class)
class StartupScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(state: StartupUiState, onRetry: () -> Unit = {}) {
        compose.setContent {
            EMOtvTheme { StartupScreen(state = state, onRetry = onRetry) }
        }
    }

    @Test
    fun mientrasCargaCompruebaLaConexion() {
        show(StartupUiState.Loading)
        compose.onNodeWithText("Comprobando la conexión con el servidor…").assertIsDisplayed()
    }

    @Test
    fun sinConexionPermiteReintentar() {
        var retries = 0
        show(StartupUiState.Offline, onRetry = { retries++ })

        compose.onNodeWithText("No hay conexión con el servidor").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun versionAntiguaPideActualizar() {
        show(StartupUiState.UpdateRequired(currentVersion = "1.0.0", minVersion = "1.2.0"))

        compose.onNodeWithText("Actualiza EMOtv").assertIsDisplayed()
        compose.onNodeWithText("1.2.0", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Volver a comprobar").assertIsDisplayed()
    }

    @Test
    fun servidorSinInicioDeSesionLoAvisa() {
        show(StartupUiState.LoginUnavailable)
        compose.onNodeWithText("El inicio de sesión no está disponible").assertIsDisplayed()
    }
}
