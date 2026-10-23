package pe.unheval.emotv.ui.login

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
class LoginScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(state: LoginUiState, onSubmit: () -> Unit = {}) {
        compose.setContent {
            EMOtvTheme {
                LoginScreen(state = state, onEmailChange = {}, onPasswordChange = {}, onSubmit = onSubmit)
            }
        }
    }

    @Test
    fun muestraLosErroresDeLosCampos() {
        show(LoginUiState(emailError = FieldError.InvalidEmail, passwordError = FieldError.Required))

        compose.onNodeWithText("Escribe un correo válido, por ejemplo nombre@dominio.com.").assertIsDisplayed()
        compose.onNodeWithText("Escribe tu contraseña.").assertIsDisplayed()
    }

    @Test
    fun muestraElMensajeDelServidor() {
        show(LoginUiState(error = LoginError.Server("role_not_allowed", "Esta cuenta no puede usar el aplicativo")))

        compose.onNodeWithText("Esta cuenta no puede usar el aplicativo").assertIsDisplayed()
    }

    @Test
    fun elBotonEnviaElFormulario() {
        var submits = 0
        show(LoginUiState(), onSubmit = { submits++ })
        compose.onNodeWithTag(LoginTags.SUBMIT).performClick()
        assertEquals(1, submits)
    }

    @Test
    fun botonDeshabilitadoAlCargar() {
        show(LoginUiState(isLoading = true))
        compose.onNodeWithTag(LoginTags.SUBMIT).assertIsNotEnabled()
    }
}
