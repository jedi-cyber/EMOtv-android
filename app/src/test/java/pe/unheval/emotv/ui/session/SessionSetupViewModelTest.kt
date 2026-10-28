package pe.unheval.emotv.ui.session

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pe.unheval.emotv.data.remote.StartSessionRequestDto
import pe.unheval.emotv.domain.model.SessionAudience
import pe.unheval.emotv.domain.usecase.LoadDashboardUseCase
import pe.unheval.emotv.testing.ADULT_CONFIRMATION
import pe.unheval.emotv.testing.CHILD_CONFIRMATION
import pe.unheval.emotv.testing.MainDispatcherRule
import pe.unheval.emotv.testing.TestGraph
import pe.unheval.emotv.testing.create
import pe.unheval.emotv.testing.mobileError
import pe.unheval.emotv.testing.protocolDto
import pe.unheval.emotv.testing.startSessionDto
import pe.unheval.emotv.testing.summaryDto
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SessionSetupViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val graph = TestGraph(mainRule.dispatcher)
    private val store = ViewModelStore()
    private val clock = Clock.fixed(Instant.parse("2026-10-23T15:00:00Z"), ZoneOffset.ofHours(-5))

    private fun viewModel() = store.create {
        SessionSetupViewModel(LoadDashboardUseCase(graph.summaryRepository, clock), graph.sessionRepository)
    }

    @Before
    fun signIn() = runTest {
        graph.authRepository.login("usuario1@example.com", "valor-de-prueba")
    }

    @After
    fun clear() = store.clear()

    private fun SessionSetupViewModel.checkCamera() {
        onCameraReady(true)
        setLightingChecked(true)
    }

    @Test
    fun `carga los protocolos y las opciones sin protocolo, sin elegir por quien conduce`() {
        val vm = viewModel()

        val state = vm.uiState.value
        assertFalse(state.isLoadingOptions)
        assertEquals(listOf("protocolo-1"), state.protocols.map { it.id })
        assertEquals(
            listOf(SessionAudience.Own, SessionAudience.Adult, SessionAudience.Child),
            state.audiences.map { it.audience },
        )
        assertNull(state.choice)
        assertNull(state.confirmationText)
    }

    @Test
    fun `sin protocolos utilizables queda elegida la sesión sin protocolo`() {
        graph.api.summary = { _, _ -> summaryDto(protocols = emptyList()) }
        val vm = viewModel()

        assertEquals(SetupChoice.NoProtocol, vm.uiState.value.choice)
        // Falta decir con quién es la sesión.
        assertNull(vm.uiState.value.plan)
    }

    @Test
    fun `con protocolo crea la sesión solo con la casilla marcada y la cámara revisada`() {
        val vm = viewModel()
        vm.choose(SetupChoice.Protocol("protocolo-1"))
        assertEquals(ADULT_CONFIRMATION, vm.uiState.value.confirmationText)
        vm.checkCamera()

        vm.handOver()
        assertTrue(graph.api.startRequests.isEmpty())
        assertFalse(vm.uiState.value.canHandOver)

        vm.setConfirmed(true)
        vm.handOver()

        assertEquals(
            StartSessionRequestDto(protocolId = "protocolo-1", authorizationConfirmed = true),
            graph.api.startRequests.single(),
        )
        assertEquals(
            LockedSessionArgs("sesion-1", "S-4F7K2Q", SessionAudience.Adult),
            vm.uiState.value.locked,
        )
    }

    @Test
    fun `sin protocolo pide con quién es la sesión y envía ese público`() {
        graph.api.startSession = { startSessionDto(audience = "infantil", hasProtocol = false) }
        val vm = viewModel()
        vm.choose(SetupChoice.NoProtocol)
        vm.checkCamera()
        assertNull(vm.uiState.value.confirmationText)

        vm.chooseAudience(SessionAudience.Child)
        assertEquals(CHILD_CONFIRMATION, vm.uiState.value.confirmationText)
        vm.setConfirmed(true)
        vm.handOver()

        assertEquals(
            StartSessionRequestDto(sessionType = "infantil", authorizationConfirmed = true),
            graph.api.startRequests.single(),
        )
        assertEquals(SessionAudience.Child, vm.uiState.value.locked?.audience)
    }

    @Test
    fun `cambiar la elección desmarca la confirmación`() {
        val vm = viewModel()
        vm.choose(SetupChoice.NoProtocol)
        vm.chooseAudience(SessionAudience.Adult)
        vm.setConfirmed(true)

        vm.chooseAudience(SessionAudience.Child)
        assertFalse(vm.uiState.value.confirmed)

        vm.setConfirmed(true)
        vm.choose(SetupChoice.Protocol("protocolo-1"))
        assertFalse(vm.uiState.value.confirmed)
        assertNull(vm.uiState.value.audience)
    }

    @Test
    fun `no se puede marcar la confirmación sin elegir qué aplicar`() {
        val vm = viewModel()
        vm.setConfirmed(true)

        assertFalse(vm.uiState.value.confirmed)
    }

    @Test
    fun `no entrega la tablet sin revisar la cámara y la iluminación`() {
        val vm = viewModel()
        vm.choose(SetupChoice.Protocol("protocolo-1"))
        vm.setConfirmed(true)

        // Sin vista previa no se puede marcar la iluminación.
        vm.setLightingChecked(true)
        vm.handOver()
        assertFalse(vm.uiState.value.lightingChecked)

        vm.onCameraReady(true)
        vm.handOver()
        assertTrue(graph.api.startRequests.isEmpty())

        vm.setLightingChecked(true)
        // Si la cámara se cierra, hay que volver a revisar.
        vm.onCameraReady(false)
        assertFalse(vm.uiState.value.lightingChecked)
        vm.handOver()
        assertTrue(graph.api.startRequests.isEmpty())
    }

    @Test
    fun `con varios protocolos espera a que quien conduce elija`() {
        graph.api.summary = { _, _ ->
            summaryDto(protocols = listOf(protocolDto("a", "Uno"), protocolDto("b", "Dos", forChildren = true)))
        }
        val vm = viewModel()
        assertNull(vm.uiState.value.choice)

        vm.choose(SetupChoice.Protocol("no-existe"))
        assertNull(vm.uiState.value.choice)

        vm.choose(SetupChoice.Protocol("b"))
        assertEquals(CHILD_CONFIRMATION, vm.uiState.value.confirmationText)
        assertEquals(SessionAudience.Child, vm.uiState.value.selectedProtocol?.audience)
    }

    @Test
    fun `muestra el mensaje del servidor si falta el aviso sobre datos de menores`() {
        graph.api.startSession = {
            throw mobileError(428, "child_notice_required", "Acepta el aviso sobre datos de menores en la web")
        }
        val vm = viewModel()
        vm.choose(SetupChoice.NoProtocol)
        vm.chooseAudience(SessionAudience.Child)
        vm.checkCamera()
        vm.setConfirmed(true)
        vm.handOver()

        val state = vm.uiState.value
        assertNull(state.locked)
        assertFalse(state.isStarting)
        assertEquals(
            SessionError.Server("child_notice_required", "Acepta el aviso sobre datos de menores en la web"),
            state.error,
        )
    }

    @Test
    fun `cancelar no llama al servidor porque la sesión aún no existe`() {
        val vm = viewModel()
        vm.choose(SetupChoice.Protocol("protocolo-1"))
        vm.cancel()

        assertTrue(graph.api.startRequests.isEmpty())
        assertTrue(graph.api.stopRequests.isEmpty())
        assertTrue(vm.uiState.value.closed)
    }
}
