package pe.unheval.emotv.ui.sessions

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import pe.unheval.emotv.testing.PRACTICE_NOTICE
import pe.unheval.emotv.testing.networkError
import pe.unheval.emotv.testing.practiceDto
import pe.unheval.emotv.testing.reportDto
import pe.unheval.emotv.testing.sessionDetailDto
import pe.unheval.emotv.testing.sessionDto
import pe.unheval.emotv.ui.common.ScreenError

class SessionsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val graph = TestGraph()

    @Before
    fun signIn() = runTest {
        graph.authRepository.login("usuario1@example.com", "valor-de-prueba")
    }

    private fun list() = SessionsViewModel(graph.sessionRepository, LogoutUseCase(graph.authRepository))

    private fun detail(sessionId: String = "sesion-1") = SessionDetailViewModel(
        sessionId = sessionId,
        sessions = graph.sessionRepository,
        reports = graph.reportRepository,
        logout = LogoutUseCase(graph.authRepository),
    )

    @Test
    fun `lista las sesiones visibles con el estado de su reporte`() = runTest {
        graph.api.sessions = {
            listOf(
                sessionDto(id = "a", reportStatus = "approved"),
                sessionDto(id = "b", mine = false, reportStatus = null),
            )
        }

        val sessions = checkNotNull(list().uiState.value.content)

        assertEquals(listOf("a", "b"), sessions.map { it.id })
        assertEquals(ReportStatus.Approved, sessions[0].reportStatus)
        assertFalse(sessions[1].mine)
        assertNull(sessions[1].reportStatus)
    }

    @Test
    fun `sin conexión muestra el error y actualizar lo resuelve`() = runTest {
        graph.api.sessions = { throw networkError() }
        val vm = list()
        assertEquals(ScreenError.Network, vm.uiState.value.error)

        graph.api.sessions = { listOf(sessionDto()) }
        vm.refresh()

        assertNull(vm.uiState.value.error)
        assertEquals(1, vm.uiState.value.content?.size)
    }

    @Test
    fun `renovación rechazada cierra la sesión`() = runTest {
        graph.api.sessions = { throw mobileError(401, "invalid_token", "Sesión vencida") }
        graph.api.refresh = { throw mobileError(401, "invalid_refresh_token", "Vuelve a iniciar sesión") }

        val vm = list()

        assertTrue(vm.loggedOut.value)
        assertNull(vm.uiState.value.error)
        assertNull(graph.tokenHolder.accessToken)
    }

    @Test
    fun `el detalle trae la línea de tiempo y, sin reporte, lo deja en blanco`() = runTest {
        val vm = detail()

        val content = checkNotNull(vm.uiState.value.data.content)
        assertEquals("S-4F7K2Q", content.detail.session.code)
        assertEquals("Se registraron 3 capturas.", content.detail.metrics.sentence)
        assertNull(content.report)
        assertTrue("GET sessions/sesion-1/report" in graph.api.calls)
    }

    @Test
    fun `el detalle muestra el reporte existente`() = runTest {
        graph.api.sessionReport = { reportDto(id = "reporte-9", status = "submitted") }

        val report = checkNotNull(detail().uiState.value.data.content?.report)

        assertEquals("reporte-9", report.id)
        assertEquals(ReportStatus.Submitted, report.status)
    }

    @Test
    fun `sesión ajena sin permiso muestra el mensaje del servidor`() = runTest {
        graph.api.sessionDetail = { throw mobileError(403, "forbidden", "No puedes ver esta sesión.") }

        val state = detail().uiState.value

        assertEquals(ScreenError.Server("No puedes ver esta sesión."), state.data.error)
        assertNull(state.data.content)
    }

    @Test
    fun `empezar el reporte lo crea y pide abrirlo`() = runTest {
        graph.api.createReport = { reportDto(id = "reporte-nuevo", canEdit = true, authoredByMe = true) }
        val vm = detail()

        vm.createReport()

        assertEquals("reporte-nuevo", vm.uiState.value.openReport)
        assertEquals("reporte-nuevo", vm.uiState.value.data.content?.report?.id)
        assertTrue("POST sessions/sesion-1/report" in graph.api.calls)

        vm.onReportOpened()
        assertNull(vm.uiState.value.openReport)
    }

    @Test
    fun `una sesión sin ejercicios de práctica no trae la sección`() = runTest {
        assertNull(detail().uiState.value.data.content?.detail?.metrics?.practice)
    }

    @Test
    fun `una sesión con práctica trae las frases, el desglose por expresión pedida y el aviso`() = runTest {
        graph.api.sessionDetail = { sessionDetailDto(sessionDto(id = it), practice = practiceDto()) }

        val practice = checkNotNull(detail().uiState.value.data.content?.detail?.metrics?.practice)

        assertEquals("Logró la expresión pedida en 1 de 2 ejercicios.", practice.totals.sentence)
        assertEquals(listOf("happiness", "surprise"), practice.byTarget.map { it.targetExpression })
        assertEquals(1, practice.byTarget[1].counts.skipped)
        assertEquals(PRACTICE_NOTICE, practice.notice)
    }
}
