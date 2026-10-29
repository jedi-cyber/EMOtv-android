package pe.unheval.emotv.ui.dashboard

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pe.unheval.emotv.data.remote.ApiJson
import pe.unheval.emotv.data.remote.SummaryDto
import pe.unheval.emotv.domain.model.ReportStatus
import pe.unheval.emotv.domain.model.SessionAudience
import pe.unheval.emotv.domain.model.SessionState
import pe.unheval.emotv.domain.usecase.LoadDashboardUseCase
import pe.unheval.emotv.domain.usecase.LogoutUseCase
import pe.unheval.emotv.testing.MainDispatcherRule
import pe.unheval.emotv.testing.TestGraph
import pe.unheval.emotv.testing.docenteUser
import pe.unheval.emotv.testing.mobileError
import pe.unheval.emotv.testing.networkError
import pe.unheval.emotv.testing.sessionDto
import pe.unheval.emotv.testing.summaryDto
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class DashboardViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val graph = TestGraph()

    // Perú (UTC-5), 10:00 hora local del 23 de octubre.
    private val zone = ZoneOffset.ofHours(-5)
    private val clock = Clock.fixed(Instant.parse("2026-10-23T15:00:00Z"), zone)

    private fun viewModel() = DashboardViewModel(
        loadDashboard = LoadDashboardUseCase(graph.summaryRepository, clock),
        logout = LogoutUseCase(graph.authRepository),
        zone = zone,
    )

    @Before
    fun signIn() = runTest {
        graph.authRepository.login("usuario1@example.com", "valor-de-prueba")
    }

    @Test
    fun `carga el resumen del día con la fecha y el desfase del dispositivo`() = runTest {
        val vm = viewModel()

        assertEquals(listOf("2026-10-23" to -300), graph.api.summaryRequests)
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        val content = assertNotNull(state.content)
        assertEquals("Usuario", content.roleName)
        assertEquals(LocalDate.of(2026, 10, 23), content.date)
        // Las recientes, de la más nueva a la más antigua, identificadas solo por su código.
        assertEquals(listOf("S-4F7K2Q", "S-9QW3ZD"), content.recentSessions.map { it.code })
        assertEquals(SessionAudience.Own, content.recentSessions.last().audience)
        assertNull(content.recentSessions.last().protocolName)
        assertEquals(ReportStatus.Draft, content.recentSessions.last().reportStatus)
        assertFalse(content.isDocente)

        val today = content.todaySessions.single()
        assertEquals("S-4F7K2Q", today.code)
        assertEquals(SessionAudience.Adult, today.audience)
        assertEquals(SessionState.InProgress, today.state)
        assertNull(today.reportStatus)
        // 14:04 UTC son las 09:04 en Perú.
        assertEquals(1, content.activity.counts[9])
        assertEquals(1, content.activity.total)
    }

    @Test
    fun `sin sesiones muestra el estado vacío`() = runTest {
        graph.api.summary = { _, _ -> summaryDto(todaySessions = emptyList(), recentSessions = emptyList()) }

        val vm = viewModel()

        val content = assertNotNull(vm.uiState.value.content)
        assertTrue(content.recentSessions.isEmpty())
        assertTrue(content.todaySessions.isEmpty())
        assertEquals(0, content.activity.total)
        assertNull(content.activity.firstHour)
    }

    @Test
    fun `sin conexión en la primera carga muestra el error y actualizar lo resuelve`() = runTest {
        graph.api.summary = { _, _ -> throw networkError() }
        val vm = viewModel()

        assertEquals(DashboardError.Network, vm.uiState.value.error)
        assertNull(vm.uiState.value.content)
        assertFalse(vm.uiState.value.isLoading)

        graph.api.summary = { _, _ -> summaryDto() }
        vm.refresh()

        assertNull(vm.uiState.value.error)
        assertNotNull(vm.uiState.value.content)
        assertEquals(2, graph.api.summaryRequests.size)
    }

    @Test
    fun `un error al actualizar conserva el último resumen`() = runTest {
        val vm = viewModel()
        graph.api.summary = { _, _ -> throw networkError() }

        vm.refresh()

        val state = vm.uiState.value
        assertEquals(DashboardError.Network, state.error)
        assertFalse(state.isRefreshing)
        assertEquals(2, assertNotNull(state.content).recentSessions.size)
    }

    @Test
    fun `el error del servidor muestra su mensaje`() = runTest {
        graph.api.summary = { _, _ ->
            throw mobileError(428, "commitment_required", "Acepta el compromiso de confidencialidad en la web")
        }

        val vm = viewModel()

        assertEquals(
            DashboardError.Server("Acepta el compromiso de confidencialidad en la web"),
            vm.uiState.value.error,
        )
        assertFalse(vm.uiState.value.loggedOut)
    }

    @Test
    fun `token de acceso vencido se renueva una vez y se repite la consulta`() = runTest {
        var calls = 0
        graph.api.summary = { _, _ ->
            calls++
            if (calls == 1) throw mobileError(401, "invalid_token", "Sesión vencida") else summaryDto()
        }

        val vm = viewModel()

        assertEquals(1, graph.api.refreshRequests.size)
        assertEquals(2, graph.api.summaryRequests.size)
        assertEquals("acceso-renovado", graph.tokenHolder.accessToken)
        assertNotNull(vm.uiState.value.content)
        assertFalse(vm.uiState.value.loggedOut)
    }

    @Test
    fun `renovación rechazada cierra la sesión y borra los datos`() = runTest {
        graph.api.summary = { _, _ -> throw mobileError(401, "invalid_token", "Sesión vencida") }
        graph.api.refresh = { throw mobileError(401, "invalid_refresh_token", "Vuelve a iniciar sesión") }

        val vm = viewModel()

        val state = vm.uiState.value
        assertTrue(state.loggedOut)
        assertNull(state.content)
        assertNull(graph.refreshTokenStore.value)
        assertNull(graph.tokenHolder.accessToken)
    }

    @Test
    fun `cerrar sesión borra todo dato en memoria y los tokens`() = runTest {
        val vm = viewModel()

        vm.logOut()

        val state = vm.uiState.value
        assertTrue(state.loggedOut)
        assertNull(state.content)
        assertNull(state.error)
        assertEquals(1, graph.api.logoutCalls)
        assertNull(graph.tokenHolder.accessToken)
        assertNull(graph.refreshTokenStore.value)
        assertNull(graph.authRepository.currentUser.value)
    }

    @Test
    fun `la actividad agrupa las sesiones por hora local`() {
        val sessions = listOf(
            "2026-10-23T14:04:00+00:00",
            "2026-10-23T14:40:00+00:00",
            "2026-10-23T20:10:00+00:00",
        ).mapIndexed { index, startedAt ->
            sessionDto(id = "s-$index", startedAt = startedAt)
        }
        val summary = summaryDto(todaySessions = sessions)
        graph.api.summary = { _, _ -> summary }

        val activity = assertNotNull(viewModel().uiState.value.content).activity

        assertEquals(2, activity.counts[9])
        assertEquals(1, activity.counts[15])
        assertEquals(9, activity.firstHour)
        assertEquals(15, activity.lastHour)
        assertEquals(9, activity.busiestHour)
    }

    @Test
    fun `el resumen de la documentación se interpreta sin datos de más`() = runTest {
        // Ejemplo de docs/mobile-api.md (revisión 4) con un campo extra que el aplicativo ignora.
        val json = """
            {"user": {"id": "user-1", "email": "usuario1@example.com", "role": "student", "role_name": "Usuario"},
             "date": "2026-10-26",
             "today_sessions": [],
             "recent_sessions": [
               {"id": "221e2a72", "code": "S-4F7K2Q", "started_at": "2026-10-26T14:04:10.922662+00:00",
                "state": "completed", "audience": "adulto", "protocol_name": "Secuencia móvil",
                "mine": true, "report_status": "submitted", "nombre": "no se usa"}
             ],
             "pending_reviews": 0}
        """.trimIndent()
        val dto = ApiJson.decodeFromString(SummaryDto.serializer(), json)
        graph.api.summary = { _, _ -> dto }

        val session = assertNotNull(viewModel().uiState.value.content).recentSessions.single()

        assertEquals("S-4F7K2Q", session.code)
        assertEquals(SessionAudience.Adult, session.audience)
        assertEquals(ReportStatus.Submitted, session.reportStatus)
    }

    @Test
    fun `el Docente ve cuántos reportes esperan su revisión`() = runTest {
        graph.api.summary = { _, _ -> summaryDto(user = docenteUser, pendingReviews = 2) }

        val content = assertNotNull(viewModel().uiState.value.content)

        assertTrue(content.isDocente)
        assertEquals(2, content.pendingReviews)
    }

    @Test
    fun `al volver a la pantalla se actualiza, la primera vez no`() = runTest {
        val vm = viewModel()

        vm.onResume()
        assertEquals(1, graph.api.summaryRequests.size)

        vm.onResume()
        assertEquals(2, graph.api.summaryRequests.size)
    }

    private fun <T> assertNotNull(value: T?): T {
        org.junit.Assert.assertNotNull(value)
        return value!!
    }
}
