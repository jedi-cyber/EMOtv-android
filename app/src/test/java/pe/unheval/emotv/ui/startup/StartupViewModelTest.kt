package pe.unheval.emotv.ui.startup

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.unheval.emotv.domain.usecase.CheckServerStatusUseCase
import pe.unheval.emotv.domain.usecase.RestoreSessionUseCase
import pe.unheval.emotv.domain.usecase.StartDestination
import pe.unheval.emotv.testing.MainDispatcherRule
import pe.unheval.emotv.testing.TestGraph
import pe.unheval.emotv.testing.mobileError
import pe.unheval.emotv.testing.networkError
import pe.unheval.emotv.testing.statusDto

class StartupViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val graph = TestGraph()

    private fun viewModel(appVersion: String = "1.0.0") = StartupViewModel(
        checkServerStatus = CheckServerStatusUseCase(graph.statusRepository, appVersion),
        restoreSession = RestoreSessionUseCase(graph.authRepository),
        appVersionName = appVersion,
    )

    @Test
    fun `servidor disponible y sin sesión guardada va al inicio de sesión`() = runTest {
        val vm = viewModel()

        assertEquals(StartupUiState.Ready(StartDestination.Login), vm.uiState.value)
        assertTrue(vm.firstCheckDone)
        assertEquals(0, graph.api.refreshRequests.size)
    }

    @Test
    fun `versión menor que la mínima pide actualizar`() = runTest {
        graph.api.status = { statusDto(minAppVersion = "1.2.0") }

        val vm = viewModel(appVersion = "1.1.9")

        assertEquals(StartupUiState.UpdateRequired("1.1.9", "1.2.0"), vm.uiState.value)
    }

    @Test
    fun `versión igual o mayor que la mínima continúa`() = runTest {
        graph.api.status = { statusDto(minAppVersion = "1.0.0") }

        val vm = viewModel(appVersion = "1.0.1")

        assertTrue(vm.uiState.value is StartupUiState.Ready)
    }

    @Test
    fun `sin conexión muestra el aviso y reintentar vuelve a consultar`() = runTest {
        graph.api.status = { throw networkError() }
        val vm = viewModel()
        assertEquals(StartupUiState.Offline, vm.uiState.value)

        graph.api.status = { statusDto() }
        vm.retry()

        assertEquals(2, graph.api.statusCalls)
        assertEquals(StartupUiState.Ready(StartDestination.Login), vm.uiState.value)
    }

    @Test
    fun `inicio de sesión desactivado en el servidor`() = runTest {
        graph.api.status = { statusDto(login = false) }

        val vm = viewModel()

        assertEquals(StartupUiState.LoginUnavailable, vm.uiState.value)
    }

    @Test
    fun `error del servidor muestra el aviso de error`() = runTest {
        graph.api.status = { throw mobileError(503, "unavailable", "Servicio no disponible") }

        val vm = viewModel()

        assertEquals(StartupUiState.ServerError, vm.uiState.value)
    }

    @Test
    fun `sesión guardada se renueva y va al inicio`() = runTest {
        graph.refreshTokenStore.value = "guardado"

        val vm = viewModel()

        assertEquals(StartupUiState.Ready(StartDestination.Home), vm.uiState.value)
        assertEquals("guardado", graph.api.refreshRequests.single().refreshToken)
        // El token de renovación cambia en cada renovación.
        assertEquals("renovado", graph.refreshTokenStore.value)
        assertEquals("acceso-renovado", graph.tokenHolder.accessToken)
    }

    @Test
    fun `renovación rechazada borra la sesión y va al inicio de sesión`() = runTest {
        graph.refreshTokenStore.value = "vencido"
        graph.api.refresh = {
            throw mobileError(401, "invalid_refresh_token", "Vuelve a iniciar sesión")
        }

        val vm = viewModel()

        assertEquals(StartupUiState.Ready(StartDestination.Login), vm.uiState.value)
        assertNull(graph.refreshTokenStore.value)
        assertNull(graph.tokenHolder.accessToken)
    }

    @Test
    fun `renovación sin conexión conserva la sesión guardada`() = runTest {
        graph.refreshTokenStore.value = "guardado"
        graph.api.refresh = { throw networkError() }

        val vm = viewModel()

        assertEquals(StartupUiState.Offline, vm.uiState.value)
        assertEquals("guardado", graph.refreshTokenStore.value)
    }
}
