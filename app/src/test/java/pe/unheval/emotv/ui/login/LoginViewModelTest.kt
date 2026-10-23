package pe.unheval.emotv.ui.login

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.unheval.emotv.domain.usecase.LoginUseCase
import pe.unheval.emotv.testing.MainDispatcherRule
import pe.unheval.emotv.testing.TestGraph
import pe.unheval.emotv.testing.mobileError
import pe.unheval.emotv.testing.networkError

class LoginViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val graph = TestGraph(mainRule.dispatcher)
    private val vm = LoginViewModel(LoginUseCase(graph.authRepository, graph.pinRepository))

    private fun fillIn(email: String = "usuario1@example.com", password: String = "valor-de-prueba") {
        vm.onEmailChange(email)
        vm.onPasswordChange(password)
    }

    @Test
    fun `campos vacíos muestran errores y no llaman a la API`() = runTest {
        vm.submit()

        val state = vm.uiState.value
        assertEquals(FieldError.Required, state.emailError)
        assertEquals(FieldError.Required, state.passwordError)
        assertTrue(graph.api.loginRequests.isEmpty())
    }

    @Test
    fun `correo mal escrito muestra error`() = runTest {
        fillIn(email = "usuario1@example")

        vm.submit()

        assertEquals(FieldError.InvalidEmail, vm.uiState.value.emailError)
        assertTrue(graph.api.loginRequests.isEmpty())
    }

    @Test
    fun `escribir de nuevo limpia el error del campo`() = runTest {
        vm.submit()
        vm.onEmailChange("u")

        assertNull(vm.uiState.value.emailError)
    }

    @Test
    fun `inicio correcto guarda la sesión, pide el PIN y olvida la contraseña`() = runTest {
        graph.pinRepository.savePin("1234")
        fillIn(email = "  usuario1@example.com ")

        vm.submit()

        val state = vm.uiState.value
        assertEquals(LoginNavigation.PinSetup, state.navigation)
        assertFalse(state.isLoading)
        assertEquals("", state.password)
        assertEquals("usuario1@example.com", graph.api.loginRequests.single().email)
        assertEquals("renovacion-de-prueba", graph.refreshTokenStore.value)
        assertEquals("acceso-de-prueba", graph.tokenHolder.accessToken)
        assertEquals("Usuario", graph.authRepository.currentUser.value?.roleName)
        // Cada inicio de sesión define un PIN nuevo.
        assertFalse(graph.pinRepository.hasPin())

        vm.onNavigated()
        assertNull(vm.uiState.value.navigation)
    }

    @Test
    fun `credenciales incorrectas muestran el mensaje del servidor`() = runTest {
        graph.api.login = { throw mobileError(401, "invalid_credentials", "Correo o contraseña incorrectos") }
        fillIn()

        vm.submit()

        val state = vm.uiState.value
        assertEquals(
            LoginError.Server("invalid_credentials", "Correo o contraseña incorrectos"),
            state.error,
        )
        assertFalse(state.isLoading)
        assertNull(state.navigation)
        assertNull(graph.refreshTokenStore.value)
    }

    @Test
    fun `rol no permitido muestra el mensaje del servidor`() = runTest {
        graph.api.login = {
            throw mobileError(403, "role_not_allowed", "Esta cuenta no puede usar el aplicativo")
        }
        fillIn()

        vm.submit()

        assertEquals(
            LoginError.Server("role_not_allowed", "Esta cuenta no puede usar el aplicativo"),
            vm.uiState.value.error,
        )
        assertNull(graph.refreshTokenStore.value)
    }

    @Test
    fun `contraseña provisional lleva a la pantalla de cambio`() = runTest {
        graph.api.login = {
            throw mobileError(403, "password_change_required", "Cambia tu contraseña en la web")
        }
        fillIn()

        vm.submit()

        val state = vm.uiState.value
        assertEquals(
            LoginNavigation.PasswordChangeRequired("Cambia tu contraseña en la web"),
            state.navigation,
        )
        assertNull(state.error)
        assertEquals("", state.password)
    }

    @Test
    fun `demasiados intentos incluye la espera indicada por el servidor`() = runTest {
        graph.api.login = {
            throw mobileError(429, "too_many_attempts", "Demasiados intentos", retryAfterSeconds = 300)
        }
        fillIn()

        vm.submit()

        assertEquals(LoginError.TooManyAttempts("Demasiados intentos", 300), vm.uiState.value.error)
    }

    @Test
    fun `sin conexión muestra el error de red`() = runTest {
        graph.api.login = { throw networkError() }
        fillIn()

        vm.submit()

        assertEquals(LoginError.Network, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `editar un campo oculta el error anterior`() = runTest {
        graph.api.login = { throw networkError() }
        fillIn()
        vm.submit()

        vm.onPasswordChange("otra")

        assertNull(vm.uiState.value.error)
    }
}
