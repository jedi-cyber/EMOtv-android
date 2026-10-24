package pe.unheval.emotv.ui.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pe.unheval.emotv.domain.model.GuidedPhase
import pe.unheval.emotv.domain.model.ParticipantKind
import pe.unheval.emotv.domain.model.SessionStep
import pe.unheval.emotv.ui.theme.EMOtvTheme

@RunWith(AndroidJUnit4::class)
class LockedSessionScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun step(phase: GuidedPhase) = SessionStep(
        sessionId = "sesion-1",
        phase = phase,
        stopReason = null,
        sendFrames = false,
        frameIntervalMs = null,
        captureIssues = emptyList(),
        questionnaire = null,
        calibration = null,
        stimulus = null,
        confirmation = null,
        stepCount = 2,
    )

    private fun show(state: LockedSessionUiState, actions: LockedSessionActions = LockedSessionActions()) {
        compose.setContent {
            EMOtvTheme { LockedSessionContent(state = state, actions = actions) }
        }
    }

    @Test
    fun quieroPararEstaVisibleYPausaLaSesion() {
        var stops = 0
        show(
            LockedSessionUiState(participantKind = ParticipantKind.Adult, step = step(GuidedPhase.Calibration)),
            LockedSessionActions(onRequestStop = { stops++ }),
        )

        compose.onNodeWithTag(LockedSessionTags.STOP).assertIsDisplayed().performClick()
        assertEquals(1, stops)
    }

    @Test
    fun enPausaPideElPinYNoDejaContinuarSinEl() {
        show(
            LockedSessionUiState(
                participantKind = ParticipantKind.Adult,
                step = step(GuidedPhase.Stimuli),
                pause = SessionPause.ParticipantRequest,
            ),
        )

        compose.onNodeWithText("Sesión en pausa").assertIsDisplayed()
        compose.onNodeWithTag(LockedSessionTags.PIN).assertIsDisplayed()
        compose.onNodeWithTag(LockedSessionTags.UNLOCK).assertIsNotEnabled()
        compose.onNodeWithTag(LockedSessionTags.RESUME).assertDoesNotExist()
    }

    @Test
    fun sinConexionAvisaAQuienConduce() {
        show(
            LockedSessionUiState(
                participantKind = ParticipantKind.Adult,
                step = step(GuidedPhase.Stimuli),
                pause = SessionPause.Offline,
            ),
        )

        compose.onNodeWithText("Sin conexión con el servidor").assertIsDisplayed()
        compose.onNodeWithText("Reintentar ahora").assertIsDisplayed()
    }

    @Test
    fun conElPinMuestraLasOpcionesDeQuienConduce() {
        var ends = 0
        show(
            LockedSessionUiState(
                participantKind = ParticipantKind.Child,
                step = step(GuidedPhase.Stimuli),
                pause = SessionPause.ParticipantRequest,
                unlocked = true,
            ),
            LockedSessionActions(onEnd = { ends++ }),
        )

        compose.onNodeWithTag(LockedSessionTags.RESUME).assertIsDisplayed()
        compose.onNodeWithText("El padre, la madre o el tutor").assertIsDisplayed()
        compose.onNodeWithTag(LockedSessionTags.END).performClick()
        assertEquals(1, ends)
    }
}
