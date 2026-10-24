package pe.unheval.emotv.ui.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pe.unheval.emotv.ui.theme.EMOtvTheme

@RunWith(AndroidJUnit4::class)
class CameraPermissionRequestTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun explicaParaQueSeUsaLaCamaraAntesDePedirla() {
        var requests = 0
        compose.setContent {
            EMOtvTheme { CameraPermissionRequest(denied = false, onAllow = { requests++ }, onOpenSettings = {}) }
        }

        compose.onNodeWithText("Para qué se usa la cámara").assertIsDisplayed()
        compose.onNodeWithText("No se graba video ni se guarda ninguna imagen", substring = true).assertIsDisplayed()
        // El permiso no se pide hasta que quien conduce lo decide.
        assertEquals(0, requests)
        compose.onNodeWithTag(SessionSetupTags.ALLOW_CAMERA).performClick()
        assertEquals(1, requests)
    }

    @Test
    fun siSeNegoOfreceAbrirLosAjustes() {
        var settings = 0
        compose.setContent {
            EMOtvTheme { CameraPermissionRequest(denied = true, onAllow = {}, onOpenSettings = { settings++ }) }
        }

        compose.onNodeWithText("La cámara no tiene permiso").assertIsDisplayed()
        compose.onNodeWithText("Abrir los ajustes").performClick()
        assertEquals(1, settings)
    }
}
