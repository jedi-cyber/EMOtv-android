package pe.unheval.emotv.ui.pin

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.unheval.emotv.domain.usecase.SetPinUseCase
import pe.unheval.emotv.testing.MainDispatcherRule
import pe.unheval.emotv.testing.TestGraph

class PinSetupViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val graph = TestGraph(mainRule.dispatcher)
    private val vm = PinSetupViewModel(SetPinUseCase(graph.pinRepository))

    @Test
    fun `solo acepta dígitos y como máximo seis`() {
        vm.onPinChange("12a34-5678")

        assertEquals("123456", vm.uiState.value.pin)
    }

    @Test
    fun `PIN corto muestra error y no guarda`() = runTest {
        vm.onPinChange("123")
        vm.onConfirmationChange("123")

        vm.submit()

        assertEquals(PinError.Length, vm.uiState.value.pinError)
        assertNull(graph.pinStore.value)
    }

    @Test
    fun `PIN distintos muestran error`() = runTest {
        vm.onPinChange("1234")
        vm.onConfirmationChange("4321")

        vm.submit()

        assertEquals(PinError.Mismatch, vm.uiState.value.confirmationError)
        assertNull(graph.pinStore.value)
    }

    @Test
    fun `PIN válido guarda solo el hash y limpia la pantalla`() = runTest {
        vm.onPinChange("482916")
        vm.onConfirmationChange("482916")

        vm.submit()

        val state = vm.uiState.value
        assertTrue(state.saved)
        assertEquals("", state.pin)
        assertEquals("", state.confirmation)
        val stored = graph.pinStore.value!!
        assertFalse(stored.contains("482916"))
        assertTrue(graph.pinRepository.verifyPin("482916"))
        assertFalse(graph.pinRepository.verifyPin("482917"))
    }

    @Test
    fun `el mismo PIN produce hashes distintos por la sal`() = runTest {
        graph.pinRepository.savePin("1234")
        val first = graph.pinStore.value
        graph.pinRepository.savePin("1234")

        assertNotEquals(first, graph.pinStore.value)
    }
}
