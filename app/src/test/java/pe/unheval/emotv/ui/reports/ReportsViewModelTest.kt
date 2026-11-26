package pe.unheval.emotv.ui.reports

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pe.unheval.emotv.domain.model.ReportStatus
import pe.unheval.emotv.domain.usecase.LogoutUseCase
import pe.unheval.emotv.testing.MainDispatcherRule
import pe.unheval.emotv.testing.TestGraph
import pe.unheval.emotv.testing.mobileError
import pe.unheval.emotv.testing.networkError
import pe.unheval.emotv.testing.reportDto
import pe.unheval.emotv.testing.sessionDto
import pe.unheval.emotv.ui.common.ScreenError

class ReportsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val graph = TestGraph()

    @Before
    fun signIn() = runTest {
        graph.authRepository.login("usuario1@example.com", "valor-de-prueba")
    }

    private fun reports() = ReportsViewModel(graph.sessionRepository, graph.reportRepository, LogoutUseCase(graph.authRepository))

    @Test
    fun `agrupa por estado lo que pide algo primero y deja fuera las sesiones sin reporte`() = runTest {
        graph.api.sessions = {
            listOf(
                sessionDto(id = "a", reportStatus = "approved"),
                sessionDto(id = "b", reportStatus = null),
                sessionDto(id = "c", reportStatus = "draft"),
                sessionDto(id = "d", reportStatus = "changes_requested"),
                sessionDto(id = "e", reportStatus = "approved"),
                sessionDto(id = "f", reportStatus = "submitted"),
            )
        }

        val groups = checkNotNull(reports().uiState.value.data.content)

        assertEquals(
            listOf(ReportStatus.ChangesRequested, ReportStatus.Draft, ReportStatus.Submitted, ReportStatus.Approved),
            groups.map { it.status },
        )
        // Cada grupo conserva el orden de la lista: la más reciente primero.
        assertEquals(listOf("a", "e"), groups.last().sessions.map { it.id })
        assertTrue(groups.none { group -> group.sessions.any { it.id == "b" } })
    }

    @Test
    fun `sin reportes la lista queda vacía`() = runTest {
        graph.api.sessions = { listOf(sessionDto(reportStatus = null)) }

        assertEquals(emptyList<ReportGroup>(), reports().uiState.value.data.content)
    }

    @Test
    fun `tocar una sesión busca su reporte vigente y pide abrirlo`() = runTest {
        graph.api.sessions = { listOf(sessionDto(id = "a", reportStatus = "submitted")) }
        graph.api.sessionReport = { reportDto(id = "reporte-7", status = "submitted") }
        val vm = reports()

        vm.open("a")

        assertTrue("GET sessions/a/report" in graph.api.calls)
        assertEquals("reporte-7", vm.uiState.value.openReport)
        assertNull(vm.uiState.value.opening)
        vm.onReportOpened()
        assertNull(vm.uiState.value.openReport)
    }

    @Test
    fun `si el reporte ya no está, actualiza la lista sin abrir nada`() = runTest {
        graph.api.sessions = { listOf(sessionDto(id = "a", reportStatus = "draft")) }
        val vm = reports()
        graph.api.sessions = { listOf(sessionDto(id = "a", reportStatus = null)) }

        vm.open("a")

        assertNull(vm.uiState.value.openReport)
        assertEquals(emptyList<ReportGroup>(), vm.uiState.value.data.content)
    }

    @Test
    fun `sin permiso muestra el mensaje del servidor`() = runTest {
        graph.api.sessions = { listOf(sessionDto(id = "a", reportStatus = "draft")) }
        graph.api.sessionReport = { throw mobileError(403, "forbidden", "No puedes ver este reporte.") }
        val vm = reports()

        vm.open("a")

        assertEquals(ScreenError.Server("No puedes ver este reporte."), vm.uiState.value.actionError)
        assertNull(vm.uiState.value.openReport)
    }

    @Test
    fun `sin conexión muestra el error y actualizar lo resuelve`() = runTest {
        graph.api.sessions = { throw networkError() }
        val vm = reports()
        assertEquals(ScreenError.Network, vm.uiState.value.data.error)

        graph.api.sessions = { listOf(sessionDto(reportStatus = "approved")) }
        vm.refresh()

        assertNull(vm.uiState.value.data.error)
        assertEquals(1, vm.uiState.value.data.content?.size)
    }
}
