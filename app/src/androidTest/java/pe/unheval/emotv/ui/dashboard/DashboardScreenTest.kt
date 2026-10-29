package pe.unheval.emotv.ui.dashboard

import androidx.compose.ui.test.assertIsDisplayed
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
import pe.unheval.emotv.domain.model.ReportStatus
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
        protocolName = "Secuencia móvil",
        mine = true,
        reportStatus = ReportStatus.Submitted,
    )

    private val withoutProtocol = SessionSummary(
        id = "sesion-0",
        code = "S-9QW3ZD",
        startedAt = Instant.parse("2026-10-21T16:30:00Z"),
        state = SessionState.Completed,
        audience = SessionAudience.Own,
        protocolName = null,
        mine = true,
        reportStatus = null,
    )

    private fun content(
        recent: List<SessionSummary> = listOf(withProtocol, withoutProtocol),
        isDocente: Boolean = false,
        pendingReviews: Int = 0,
    ) = DashboardContent(
        roleName = if (isDocente) "Docente" else "Usuario",
        email = "usuario1@example.com",
        date = LocalDate.of(2026, 10, 23),
        todaySessions = emptyList(),
        recentSessions = recent,
        activity = HourlyActivity(List(24) { 0 }),
        isDocente = isDocente,
        pendingReviews = pendingReviews,
    )

    private fun show(
        state: DashboardUiState,
        onOpenSession: (String) -> Unit = {},
        onOpenReviews: () -> Unit = {},
        onLogout: () -> Unit = {},
    ) {
        compose.setContent {
            EMOtvTheme {
                DashboardScreen(
                    state = state,
                    actions = DashboardActions(
                        onRefresh = {},
                        onOpenSession = onOpenSession,
                        onOpenSessions = {},
                        onOpenStatistics = {},
                        onOpenReviews = onOpenReviews,
                        onLogout = onLogout,
                    ),
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
        compose.onNodeWithText("Reporte en revisión").assertIsDisplayed()
        scrollTo(DashboardTags.recentSession(withoutProtocol.id))
        compose.onNodeWithText("Conmigo · Sin protocolo").assertIsDisplayed()
    }

    @Test
    fun sinSesionesLoAvisa() {
        show(DashboardUiState(isLoading = false, content = content(recent = emptyList())))

        compose.onNodeWithText("Todavía no tienes sesiones.").assertIsDisplayed()
    }

    @Test
    fun tocarUnaSesionAbreSuDetalle() {
        val opened = mutableListOf<String>()
        show(DashboardUiState(isLoading = false, content = content()), onOpenSession = { opened += it })

        scrollTo(DashboardTags.recentSession(withProtocol.id))
        compose.onNodeWithTag(DashboardTags.recentSession(withProtocol.id)).performClick()
        assertEquals(listOf("sesion-1"), opened)
    }

    @Test
    fun elDocenteVeSusPendientes() {
        var reviews = 0
        show(
            DashboardUiState(isLoading = false, content = content(isDocente = true, pendingReviews = 2)),
            onOpenReviews = { reviews++ },
        )

        compose.onNodeWithTag(DashboardTags.REVIEWS).performClick()
        assertEquals(1, reviews)
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
