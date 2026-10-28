package pe.unheval.emotv.ui.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pe.unheval.emotv.domain.model.GuidedPhase
import pe.unheval.emotv.domain.model.SessionAudience
import pe.unheval.emotv.domain.model.SessionState
import pe.unheval.emotv.domain.model.SessionSummary
import pe.unheval.emotv.ui.theme.EMOtvTheme
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val withProtocol = SessionSummary(
        id = "sesion-1",
        code = "S-4F7K2Q",
        startedAt = Instant.parse("2026-10-22T14:04:00Z"),
        state = SessionState.Completed,
        audience = SessionAudience.Adult,
        guidedPhase = GuidedPhase.Finished,
        protocolName = "Secuencia móvil",
    )

    private val withoutProtocol = SessionSummary(
        id = "sesion-0",
        code = "S-9QW3ZD",
        startedAt = Instant.parse("2026-10-21T16:30:00Z"),
        state = SessionState.Completed,
        audience = SessionAudience.Own,
        guidedPhase = GuidedPhase.Finished,
        protocolName = null,
    )

    private fun content(recent: List<SessionSummary> = listOf(withProtocol, withoutProtocol)) = DashboardContent(
        roleName = "Usuario",
        email = "usuario1@example.com",
        date = LocalDate.of(2026, 10, 23),
        todaySessions = emptyList(),
        recentSessions = recent,
        activity = HourlyActivity(List(24) { 0 }),
    )

    private fun show(
        state: DashboardUiState,
        onStartSession: () -> Unit = {},
        onLogout: () -> Unit = {},
    ) {
        compose.setContent {
            EMOtvTheme {
                DashboardScreen(
                    state = state,
                    onRefresh = {},
                    onStartSession = onStartSession,
                    onLogout = onLogout,
                )
            }
        }
    }

    private fun scrollTo(tag: String) {
        compose.onNodeWithTag(DashboardTags.LIST).performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun muestraLasSesionesRecientesSoloPorCodigo() {
        show(DashboardUiState(isLoading = false, content = content()))

        scrollTo(DashboardTags.recentSession(withProtocol.id))
        compose.onNodeWithText("S-4F7K2Q").assertIsDisplayed()
        compose.onNodeWithText("Un adulto · Secuencia móvil").assertIsDisplayed()
        scrollTo(DashboardTags.recentSession(withoutProtocol.id))
        compose.onNodeWithText("Conmigo · Sin protocolo").assertIsDisplayed()
    }

    @Test
    fun sinSesionesLoAvisa() {
        show(DashboardUiState(isLoading = false, content = content(recent = emptyList())))

        compose.onNodeWithText("Todavía no tienes sesiones.").assertIsDisplayed()
    }

    @Test
    fun iniciarUnaSesionNoPideElegirAntes() {
        var starts = 0
        show(DashboardUiState(isLoading = false, content = content()), onStartSession = { starts++ })

        compose.onNodeWithTag(DashboardTags.START).assertIsEnabled().performClick()
        assertEquals(1, starts)
    }

    @Test
    fun cierraSesionDesdeElMenuDeLaCuenta() {
        var logouts = 0
        show(DashboardUiState(isLoading = false, content = content()), onLogout = { logouts++ })

        compose.onNodeWithTag(DashboardTags.ACCOUNT_MENU).performClick()
        compose.onNodeWithText("Sesión iniciada como usuario1@example.com").assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.LOGOUT).performClick()
        assertEquals(1, logouts)
    }
}
