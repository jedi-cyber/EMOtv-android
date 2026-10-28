package pe.unheval.emotv.ui.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pe.unheval.emotv.domain.model.GuidedPhase
import pe.unheval.emotv.domain.model.SessionAudience
import pe.unheval.emotv.domain.model.SessionStep
import pe.unheval.emotv.ui.theme.EMOtvTheme

@RunWith(AndroidJUnit4::class)
class LockedSessionScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun step(phase: GuidedPhase) = SessionStep(
        sessionId = "sesion-1",
        code = "S-4F7K2Q",
        phase = phase,
        stopReason = null,
        sendFrames = false,
        frameIntervalMs = null,
        captureIssues = emptyList(),
        questionnaire = null,
        calibration = null,
        stimulus = null,
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
            LockedSessionUiState(code = "S-4F7K2Q", audience = SessionAudience.Adult, step = step(GuidedPhase.Calibration)),
            LockedSessionActions(onRequestStop = { stops++ }),
        )

        compose.onNodeWithTag(LockedSessionTags.STOP).assertIsDisplayed().performClick()
        assertEquals(1, stops)
    }

    @Test
    fun enPausaPideElPinYNoDejaContinuarSinEl() {
        show(
            LockedSessionUiState(
                code = "S-4F7K2Q",
                audience = SessionAudience.Adult,
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
                code = "S-4F7K2Q",
                audience = SessionAudience.Adult,
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
                code = "S-4F7K2Q",
                audience = SessionAudience.Child,
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

    @Test
    fun laSalidaEnviaElPinIngresado() {
        var typed = ""
        var submits = 0
        show(
            LockedSessionUiState(
                code = "S-4F7K2Q",
                audience = SessionAudience.Adult,
                step = step(GuidedPhase.Stimuli),
                pause = SessionPause.ExitRequest,
                pin = "1234",
            ),
            LockedSessionActions(onPinChange = { typed = it }, onSubmitPin = { submits++ }),
        )

        compose.onNodeWithText("Salir del modo sesión").assertIsDisplayed()
        compose.onNodeWithTag(LockedSessionTags.PIN).performTextInput("5")
        assertEquals("12345", typed)
        compose.onNodeWithTag(LockedSessionTags.UNLOCK).assertIsEnabled().performClick()
        assertEquals(1, submits)
    }

    @Test
    fun unPinIncorrectoSeAvisaYNoMuestraLasOpciones() {
        show(
            LockedSessionUiState(
                code = "S-4F7K2Q",
                audience = SessionAudience.Adult,
                step = step(GuidedPhase.Stimuli),
                pause = SessionPause.ExitRequest,
                pinError = PinCheckError.Wrong,
            ),
        )

        compose.onNodeWithText("PIN incorrecto.").assertIsDisplayed()
        compose.onNodeWithTag(LockedSessionTags.END).assertDoesNotExist()
    }

    @Test
    fun trasVariosIntentosBloqueaElPin() {
        show(
            LockedSessionUiState(
                code = "S-4F7K2Q",
                audience = SessionAudience.Adult,
                step = step(GuidedPhase.Stimuli),
                pause = SessionPause.ExitRequest,
                pin = "1234",
                pinError = PinCheckError.LockedOut(30),
            ),
        )

        compose.onNodeWithText("Demasiados intentos. Espera 30 segundos.").assertIsDisplayed()
        compose.onNodeWithTag(LockedSessionTags.UNLOCK).assertIsNotEnabled()
    }

    @Test
    fun alTerminarSoloOfreceVolverAlInicio() {
        var ends = 0
        show(
            LockedSessionUiState(
                code = "S-4F7K2Q",
                audience = SessionAudience.Adult,
                step = step(GuidedPhase.Finished),
                pause = SessionPause.ExitRequest,
                unlocked = true,
            ),
            LockedSessionActions(onEnd = { ends++ }),
        )

        compose.onNodeWithTag(LockedSessionTags.RESUME).assertDoesNotExist()
        // El código queda a la vista para anotarlo en el formato firmado.
        compose.onNodeWithTag(LockedSessionTags.CODE).assertIsDisplayed()
        compose.onNodeWithText("Volver al inicio").performClick()
        assertEquals(1, ends)
    }

    @Test
    fun enVivoNoMuestraResultadosYSiguePermitiendoParar() {
        show(LockedSessionUiState(code = "S-4F7K2Q", audience = SessionAudience.Own, step = step(GuidedPhase.Live)))

        compose.onNodeWithText("Sesión en curso").assertIsDisplayed()
        compose.onNodeWithTag(LockedSessionTags.STOP).assertIsDisplayed()
    }

    @Test
    fun alTerminarMuestraElCodigoDeLaSesion() {
        show(LockedSessionUiState(code = "S-4F7K2Q", audience = SessionAudience.Adult, step = step(GuidedPhase.Finished)))

        compose.onNodeWithText("Código de la sesión: S-4F7K2Q").assertIsDisplayed()
    }
}
