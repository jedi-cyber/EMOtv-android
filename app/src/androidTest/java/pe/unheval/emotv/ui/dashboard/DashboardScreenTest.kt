package pe.unheval.emotv.ui.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import pe.unheval.emotv.domain.model.MissingRequirement
import pe.unheval.emotv.domain.model.Participant
import pe.unheval.emotv.domain.model.ParticipantKind
import pe.unheval.emotv.ui.theme.EMOtvTheme
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val adult = Participant(
        id = "p-adulto",
        code = "P-RGNYTB",
        kind = ParticipantKind.Adult,
        ageYears = 30,
        ready = true,
        missing = emptyList(),
        warnings = emptyList(),
        lastSession = null,
    )

    private val child = Participant(
        id = "p-nino",
        code = "P-7KQ2MD",
        kind = ParticipantKind.Child,
        ageYears = 9,
        ready = false,
        missing = listOf(MissingRequirement.ChildAssent),
        warnings = listOf("Falta el asentimiento del niño o la niña"),
        lastSession = null,
    )

    private fun content() = DashboardContent(
        roleName = "Usuario",
        email = "usuario1@example.com",
        date = LocalDate.of(2026, 10, 23),
        todaySessions = emptyList(),
        participants = listOf(adult, child),
        activity = HourlyActivity(List(24) { 0 }),
    )

    private fun show(
        state: DashboardUiState,
        onStartSession: (Participant) -> Unit = {},
        onLogout: () -> Unit = {},
    ) {
        compose.setContent {
            EMOtvTheme {
                DashboardScreen(
                    state = state,
                    onRefresh = {},
                    onSelectParticipant = {},
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
    fun muestraLosParticipantesSoloPorCodigoYEdad() {
        show(DashboardUiState(isLoading = false, content = content()))

        scrollTo(DashboardTags.participant(adult.id))
        compose.onNodeWithText("P-RGNYTB").assertIsDisplayed()
        compose.onNodeWithText("Adulto · 30 años").assertIsDisplayed()
        scrollTo(DashboardTags.participant(child.id))
        compose.onNodeWithText("Niño o niña · 9 años").assertIsDisplayed()
    }

    @Test
    fun sinElegirParticipanteNoSePuedeIniciar() {
        show(DashboardUiState(isLoading = false, content = content()))

        compose.onNodeWithText("Elige un participante de la lista.").assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.START).assertIsNotEnabled()
    }

    @Test
    fun conUnParticipanteListoIniciaLaSesion() {
        var started: Participant? = null
        show(
            DashboardUiState(isLoading = false, content = content(), selectedParticipantId = adult.id),
            onStartSession = { started = it },
        )

        compose.onNodeWithTag(DashboardTags.START).assertIsEnabled().performClick()
        assertEquals(adult, started)
    }

    @Test
    fun unParticipanteSinAsentimientoQuedaBloqueado() {
        show(DashboardUiState(isLoading = false, content = content(), selectedParticipantId = child.id))

        compose.onNodeWithTag(DashboardTags.START).assertIsNotEnabled()
        compose.onNodeWithText("No se puede iniciar una sesión con P-7KQ2MD", substring = true).assertIsDisplayed()
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
