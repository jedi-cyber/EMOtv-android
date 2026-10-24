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
import pe.unheval.emotv.domain.model.GuidedPhase
import pe.unheval.emotv.domain.model.MissingRequirement
import pe.unheval.emotv.domain.model.ParticipantKind
import pe.unheval.emotv.domain.model.SessionState
import pe.unheval.emotv.domain.usecase.LoadDashboardUseCase
import pe.unheval.emotv.domain.usecase.LogoutUseCase
import pe.unheval.emotv.testing.MainDispatcherRule
import pe.unheval.emotv.testing.TestGraph
import pe.unheval.emotv.testing.mobileError
import pe.unheval.emotv.testing.networkError
import pe.unheval.emotv.testing.readyAdultDto
import pe.unheval.emotv.testing.sessionDto
import pe.unheval.emotv.testing.summaryDto
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class DashboardViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val graph = TestGraph(mainRule.dispatcher)

    // Perú (UTC-5), 10:00 hora local del 23 de octubre.
    private val zone = ZoneOffset.ofHours(-5)
    private val clock = Clock.fixed(Instant.parse("2026-10-23T15:00:00Z"), zone)

    private fun viewModel() = DashboardViewModel(
        loadDashboard = LoadDashboardUseCase(graph.summaryRepository, clock),
        logout = LogoutUseCase(graph.authRepository, graph.pinRepository),
        zone = zone,
    )

    @Before
    fun signIn() = runTest {
        graph.authRepository.login("usuario1@example.com", "valor-de-prueba")
        graph.pinRepository.savePin("1234")
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
        assertEquals(listOf("P-RGNYTB", "P-7KQ2MD"), content.participants.map { it.code })
        assertEquals(30, content.participants.first().ageYears)

        val today = content.todaySessions.single()
        assertEquals(SessionState.InProgress, today.state)
        assertEquals(GuidedPhase.Stimuli, today.guidedPhase)
        // 14:04 UTC son las 09:04 en Perú.
        assertEquals(1, content.activity.counts[9])
        assertEquals(1, content.activity.total)
    }

    @Test
    fun `participante sin asentimiento queda bloqueado con el aviso del servidor`() = runTest {
        val vm = viewModel()

        vm.selectParticipant("p-nino")

        val availability = vm.uiState.value.startAvailability
        assertTrue(availability is StartAvailability.Blocked)
        val child = (availability as StartAvailability.Blocked).participant
        assertEquals(ParticipantKind.Child, child.kind)
        assertFalse(child.ready)
        assertEquals(listOf(MissingRequirement.ChildAssent), child.missing)
        assertEquals(listOf("Falta el asentimiento del niño o la niña"), child.warnings)
        assertNull(child.lastSession)
    }

    @Test
    fun `participante sin autorización de cámara queda bloqueado`() = runTest {
        graph.api.summary = { _, _ ->
            summaryDto(
                participants = listOf(
                    readyAdultDto().copy(
                        ready = false,
                        missing = listOf("camera"),
                        warnings = listOf("Falta la autorización para usar la cámara"),
                    ),
                ),
            )
        }
        val vm = viewModel()

        vm.selectParticipant("p-adulto")

        val availability = vm.uiState.value.startAvailability
        assertTrue(availability is StartAvailability.Blocked)
        assertEquals(
            listOf(MissingRequirement.Camera),
            (availability as StartAvailability.Blocked).participant.missing,
        )
    }

    @Test
    fun `el inicio se habilita solo al elegir un participante listo`() = runTest {
        val vm = viewModel()
        assertEquals(StartAvailability.NothingSelected, vm.uiState.value.startAvailability)

        vm.selectParticipant("no-existe")
        assertEquals(StartAvailability.NothingSelected, vm.uiState.value.startAvailability)

        vm.selectParticipant("p-adulto")
        val availability = vm.uiState.value.startAvailability
        assertTrue(availability is StartAvailability.Ready)
        assertEquals("P-RGNYTB", (availability as StartAvailability.Ready).participant.code)
    }

    @Test
    fun `sin participantes ni sesiones muestra el estado vacío`() = runTest {
        graph.api.summary = { _, _ -> summaryDto(todaySessions = emptyList(), participants = emptyList()) }

        val vm = viewModel()

        val state = vm.uiState.value
        val content = assertNotNull(state.content)
        assertTrue(content.participants.isEmpty())
        assertTrue(content.todaySessions.isEmpty())
        assertEquals(0, content.activity.total)
        assertNull(content.activity.firstHour)
        assertEquals(StartAvailability.NoParticipants, state.startAvailability)
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
    fun `un error al actualizar conserva el resumen y la selección`() = runTest {
        val vm = viewModel()
        vm.selectParticipant("p-adulto")
        graph.api.summary = { _, _ -> throw networkError() }

        vm.refresh()

        val state = vm.uiState.value
        assertEquals(DashboardError.Network, state.error)
        assertFalse(state.isRefreshing)
        assertNotNull(state.content)
        assertEquals("p-adulto", state.selectedParticipantId)
    }

    @Test
    fun `actualizar descarta la selección si el participante ya no está`() = runTest {
        val vm = viewModel()
        vm.selectParticipant("p-nino")
        graph.api.summary = { _, _ -> summaryDto(participants = listOf(readyAdultDto())) }

        vm.refresh()

        assertNull(vm.uiState.value.selectedParticipantId)
        assertEquals(StartAvailability.NothingSelected, vm.uiState.value.startAvailability)
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
        assertFalse(graph.pinRepository.hasPin())
    }

    @Test
    fun `cerrar sesión borra todo dato en memoria, los tokens y el PIN`() = runTest {
        val vm = viewModel()
        vm.selectParticipant("p-adulto")

        vm.logOut()

        val state = vm.uiState.value
        assertTrue(state.loggedOut)
        assertNull(state.content)
        assertNull(state.selectedParticipantId)
        assertNull(state.error)
        assertEquals(1, graph.api.logoutCalls)
        assertNull(graph.tokenHolder.accessToken)
        assertNull(graph.refreshTokenStore.value)
        assertNull(graph.authRepository.currentUser.value)
        assertFalse(graph.pinRepository.hasPin())
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
        // Ejemplo de docs/mobile-api.md con un campo extra que el aplicativo debe ignorar.
        val json = """
            {"user": {"id": "user-1", "email": "usuario1@example.com", "role": "student", "role_name": "Usuario"},
             "date": "2026-10-23",
             "today_sessions": [],
             "participants": [
               {"id": "9a1c", "code": "P-7KQ2MD", "kind": "child", "age_years": 9, "nombre": "no se usa",
                "ready": false, "missing": ["child_assent"],
                "warnings": ["Falta el asentimiento del niño o la niña"], "last_session": null}
             ]}
        """.trimIndent()
        val dto = ApiJson.decodeFromString(SummaryDto.serializer(), json)
        graph.api.summary = { _, _ -> dto }

        val participant = assertNotNull(viewModel().uiState.value.content).participants.single()

        assertEquals("P-7KQ2MD", participant.code)
        assertEquals(9, participant.ageYears)
        assertFalse(participant.ready)
    }

    private fun <T> assertNotNull(value: T?): T {
        org.junit.Assert.assertNotNull(value)
        return value!!
    }
}
