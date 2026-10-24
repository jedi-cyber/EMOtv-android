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
import pe.unheval.emotv.data.remote.ConfirmRequestDto
import pe.unheval.emotv.domain.model.ConfirmationItem
import pe.unheval.emotv.domain.model.ParticipantKind
import pe.unheval.emotv.testing.MainDispatcherRule
import pe.unheval.emotv.testing.TestGraph
import pe.unheval.emotv.testing.create
import pe.unheval.emotv.testing.mobileError
import pe.unheval.emotv.testing.protocolDto
import pe.unheval.emotv.testing.startSessionDto

class SessionSetupViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val graph = TestGraph(mainRule.dispatcher)
    private val store = ViewModelStore()

    private fun viewModel() = store.create {
        SessionSetupViewModel("p-adulto", "P-RGNYTB", graph.sessionRepository)
    }

    @Before
    fun signIn() = runTest {
        graph.authRepository.login("usuario1@example.com", "valor-de-prueba")
    }

    @After
    fun clear() = store.clear()

    private fun SessionSetupViewModel.startAndCheckCamera() {
        startSession()
        onCameraReady(true)
        setLightingChecked(true)
    }

    @Test
    fun `carga los protocolos del participante y elige el único disponible`() {
        val vm = viewModel()

        assertEquals(listOf("p-adulto"), graph.api.protocolRequests)
        val state = vm.uiState.value
        assertFalse(state.isLoadingProtocols)
        assertEquals("protocolo-1", state.selectedProtocolId)
    }

    @Test
    fun `con varios protocolos espera a que quien conduce elija`() {
        graph.api.protocols = { listOf(protocolDto("a", "Uno"), protocolDto("b", "Dos")) }
        val vm = viewModel()

        assertNull(vm.uiState.value.selectedProtocolId)
        vm.startSession()
        assertTrue(graph.api.startRequests.isEmpty())

        vm.selectProtocol("b")
        vm.startSession()
        assertEquals("b", graph.api.startRequests.single().protocolId)
    }

    @Test
    fun `no inicia sin la confirmación previa`() {
        val vm = viewModel()
        vm.startAndCheckCamera()
        assertEquals(
            listOf(ConfirmationItem.CommitmentReminder, ConfirmationItem.AuthorizationConfirmed),
            vm.uiState.value.started?.confirmation?.required,
        )

        vm.handOver()
        assertTrue(graph.api.confirmRequests.isEmpty())

        vm.setChecked(ConfirmationItem.CommitmentReminder, true)
        vm.handOver()
        assertTrue(graph.api.confirmRequests.isEmpty())
        assertFalse(vm.uiState.value.canHandOver)
        assertNull(vm.uiState.value.locked)

        vm.setChecked(ConfirmationItem.AuthorizationConfirmed, true)
        vm.handOver()

        val (sessionId, body) = graph.api.confirmRequests.single()
        assertEquals("sesion-1", sessionId)
        assertEquals(
            ConfirmRequestDto(
                commitmentReminder = true,
                authorizationConfirmed = true,
                guardianPresent = false,
                childAssent = false,
            ),
            body,
        )
        assertEquals(LockedSessionArgs("sesion-1", ParticipantKind.Adult), vm.uiState.value.locked)
    }

    @Test
    fun `no entrega la tablet sin revisar la cámara y la iluminación`() {
        val vm = viewModel()
        vm.startSession()
        vm.setChecked(ConfirmationItem.CommitmentReminder, true)
        vm.setChecked(ConfirmationItem.AuthorizationConfirmed, true)

        // Sin vista previa no se puede marcar la iluminación.
        vm.setLightingChecked(true)
        vm.handOver()
        assertFalse(vm.uiState.value.lightingChecked)

        vm.onCameraReady(true)
        vm.handOver()
        assertTrue(graph.api.confirmRequests.isEmpty())

        vm.setLightingChecked(true)
        // Si la cámara se cierra, hay que volver a revisar.
        vm.onCameraReady(false)
        assertFalse(vm.uiState.value.lightingChecked)
        vm.handOver()
        assertTrue(graph.api.confirmRequests.isEmpty())
    }

    @Test
    fun `con un niño confirma la presencia del tutor y el asentimiento`() {
        graph.api.startSession = { startSessionDto(kind = "child") }
        val vm = viewModel()
        vm.startAndCheckCamera()
        vm.setChecked(ConfirmationItem.CommitmentReminder, true)
        vm.setChecked(ConfirmationItem.GuardianPresent, true)
        vm.handOver()
        assertTrue(graph.api.confirmRequests.isEmpty())

        vm.setChecked(ConfirmationItem.ChildAssent, true)
        vm.handOver()

        val body = graph.api.confirmRequests.single().second
        assertTrue(body.commitmentReminder && body.guardianPresent && body.childAssent)
        assertFalse(body.authorizationConfirmed)
        assertEquals(ParticipantKind.Child, vm.uiState.value.locked?.participantKind)
    }

    @Test
    fun `muestra el mensaje del servidor si falta una autorización`() {
        graph.api.startSession = { throw mobileError(409, "conflict", "Falta la autorización de cámara") }
        val vm = viewModel()
        vm.startSession()

        val state = vm.uiState.value
        assertNull(state.started)
        assertEquals(SessionError.Server("conflict", "Falta la autorización de cámara"), state.error)
    }

    @Test
    fun `si la confirmación falla no pasa al modo sesión`() {
        graph.api.confirm = { _, _ -> throw mobileError(422, "invalid", "Falta una confirmación") }
        val vm = viewModel()
        vm.startAndCheckCamera()
        vm.setChecked(ConfirmationItem.CommitmentReminder, true)
        vm.setChecked(ConfirmationItem.AuthorizationConfirmed, true)
        vm.handOver()

        assertNull(vm.uiState.value.locked)
        assertEquals(SessionError.Server("invalid", "Falta una confirmación"), vm.uiState.value.error)
    }

    @Test
    fun `cancelar tras crear la sesión la interrumpe para que no quede en curso`() {
        val vm = viewModel()
        vm.startSession()
        vm.cancel()

        val (sessionId, body) = graph.api.stopRequests.single()
        assertEquals("sesion-1", sessionId)
        assertEquals("conductor", body.reason)
        assertTrue(vm.uiState.value.closed)
    }

    @Test
    fun `cancelar antes de crear la sesión no llama al servidor`() {
        val vm = viewModel()
        vm.cancel()

        assertTrue(graph.api.stopRequests.isEmpty())
        assertTrue(vm.uiState.value.closed)
    }
}
