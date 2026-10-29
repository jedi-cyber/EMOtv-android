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

    @Test
    fun sinConexionExplicaQueRevisar() {
        show(LoginUiState(error = LoginError.Network))

        compose.onNodeWithText("No hay conexión con el servidor").assertIsDisplayed()
        compose.onNodeWithText("Revisa la conexión a internet de la tablet y vuelve a intentarlo.").assertIsDisplayed()
    }

    @Test
    fun trasDemasiadosIntentosDiceCuandoReintentar() {
        show(LoginUiState(error = LoginError.TooManyAttempts(message = null, retryAfterSeconds = 120)))

        compose.onNodeWithText("Demasiados intentos fallidos").assertIsDisplayed()
        compose.onNodeWithText("Podrás intentarlo de nuevo en 2 minutos.", substring = true).assertIsDisplayed()
    }

    @Test
    fun cambioDeContrasenaObligatorioLlevaALaWeb() {
        var backs = 0
        compose.setContent {
            EMOtvTheme {
                PasswordChangeRequiredScreen(serverMessage = null, onBackToLogin = { backs++ })
            }
        }

        compose.onNodeWithText("Cambia tu contraseña").assertIsDisplayed()
        compose.onNodeWithText("Volver a iniciar sesión").performClick()
        assertEquals(1, backs)
    }
}
