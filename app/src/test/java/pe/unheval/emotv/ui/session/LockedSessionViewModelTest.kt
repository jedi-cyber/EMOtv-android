package pe.unheval.emotv.ui.session

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pe.unheval.emotv.data.remote.CalibrationDto
import pe.unheval.emotv.data.remote.FrameResultDto
import pe.unheval.emotv.data.remote.PhaseDto
import pe.unheval.emotv.data.remote.QuestionDto
import pe.unheval.emotv.data.remote.QuestionnaireDto
import pe.unheval.emotv.data.remote.SessionStepDto
import pe.unheval.emotv.data.remote.StimulusDto
import pe.unheval.emotv.domain.model.Answer
import pe.unheval.emotv.domain.model.CaptureIssue
import pe.unheval.emotv.domain.model.GuidedPhase
import pe.unheval.emotv.domain.model.SessionAudience
import pe.unheval.emotv.domain.model.StopReason
import pe.unheval.emotv.domain.usecase.VerifyPinUseCase
import pe.unheval.emotv.testing.FakeEmotvApi
import pe.unheval.emotv.testing.FakeFrameSource
import pe.unheval.emotv.testing.MainDispatcherRule
import pe.unheval.emotv.testing.TestGraph
import pe.unheval.emotv.testing.create
import pe.unheval.emotv.testing.mobileError
import pe.unheval.emotv.testing.networkError
import pe.unheval.emotv.testing.stepDto

@OptIn(ExperimentalCoroutinesApi::class)
class LockedSessionViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val graph = TestGraph(mainRule.dispatcher)
    private val store = ViewModelStore()
    private val server = ScriptedServer(graph.api)
    private val camera = FakeFrameSource()

    private fun viewModel(audience: SessionAudience = SessionAudience.Adult) = store.create {
        LockedSessionViewModel(
            sessionId = "sesion-1",
            code = "S-4F7K2Q",
            audience = audience,
            sessions = graph.sessionRepository,
            verifyPin = VerifyPinUseCase(graph.pinRepository),
        )
    }.also { it.attachFrameSource(camera) }

    @Before
    fun signIn() = runTest {
        graph.authRepository.login("usuario1@example.com", "valor-de-prueba")
        graph.pinRepository.savePin(PIN)
        server.install()
    }

    @After
    fun clear() = store.clear()

    private val LockedSessionViewModel.state get() = uiState.value

    /**
     * El ViewModel consulta al servidor sin parar mientras la sesión sigue; se cancela al
     * final para que 
unTest no espere a que el planificador quede libre.
     */
    private fun sessionTest(body: suspend TestScope.() -> Unit) = runTest {
        try {
            body()
        } finally {
            store.clear()
        }
    }

    private fun assertNoCaptureLeft() {
        assertTrue(camera.captured.isNotEmpty())
        assertTrue(
            "Cada cuadro se borra de la memoria al enviarlo",
            camera.captured.all { frame -> frame.all { it == 0.toByte() } },
        )
    }

    @Test
    fun `sigue el flujo del protocolo`() = sessionTest {
        val vm = viewModel()

        // Cuestionario previo: no se envía sin las obligatorias ni se captura nada.
        assertEquals(GuidedPhase.PreQuestionnaire, vm.state.step?.phase)
        assertFalse(vm.state.canSubmitQuestionnaire)
        vm.answer("animo", Answer.Number(3))
        assertTrue(vm.state.canSubmitQuestionnaire)
        vm.submitQuestionnaire()

        val (moment, body) = graph.api.questionnaireRequests.single()
        assertEquals("pre", moment)
        assertEquals("participant", body.entryMode)
        assertEquals(JsonObject(mapOf("animo" to JsonPrimitive(3))), body.answers)
        assertTrue("Las respuestas se descartan al enviarlas", vm.state.answers.isEmpty())

        // Calibración: espera a que la persona esté lista.
        assertEquals(GuidedPhase.Calibration, vm.state.step?.phase)
        advanceTimeBy(1_000)
        assertTrue(camera.captured.isEmpty())
        vm.startCalibration()
        assertEquals(1, graph.api.calibrationCalls)

        // Tres cuadros de calibración y el servidor pasa a los estímulos.
        advanceTimeBy(1_500)
        assertEquals(GuidedPhase.Stimuli, vm.state.step?.phase)
        assertEquals(listOf(server.imageUrl), graph.api.imageRequests)
        assertEquals("0:/mobile/stimulus-images/sesion-1/estimulo-1", vm.state.stimulusImage?.key)

        // Dos cuadros con el estímulo y el servidor pasa al cuestionario posterior.
        advanceTimeBy(1_000)
        assertEquals(GuidedPhase.PostQuestionnaire, vm.state.step?.phase)
        assertNull(vm.state.stimulusImage)
        assertEquals(5, graph.api.receivedFrames.size)

        vm.answer("animo", Answer.Number(4))
        vm.answer("lugar", Answer.Choice("Aula"))
        vm.submitQuestionnaire()
        assertEquals("post", graph.api.questionnaireRequests.last().first)

        assertEquals(GuidedPhase.Finished, vm.state.step?.phase)
        assertTrue(vm.state.isTerminal)
        val sent = graph.api.frameAttempts
        advanceTimeBy(5_000)
        assertEquals("Al terminar no se envían más cuadros", sent, graph.api.frameAttempts)
        assertNoCaptureLeft()
    }

    @Test
    fun `ninguna captura queda en memoria ni se reenvía`() = sessionTest {
        server.phase = "stimuli"
        server.advanceAfterFrames = Int.MAX_VALUE
        val vm = viewModel()
        advanceTimeBy(2_000)

        // Lo que llegó al servidor era el JPEG; lo que queda en el dispositivo, ceros.
        assertTrue(graph.api.receivedFrames.isNotEmpty())
        assertTrue(graph.api.receivedFrames.all { it[0] == 0xFF.toByte() && it[1] == 0xD8.toByte() })
        assertEquals(camera.captured.size, graph.api.frameAttempts)
        assertNoCaptureLeft()
        assertEquals(GuidedPhase.Stimuli, vm.state.step?.phase)
    }

    @Test
    fun `sin conexión pausa, avisa y no guarda capturas para después`() = sessionTest {
        server.phase = "stimuli"
        server.advanceAfterFrames = Int.MAX_VALUE
        val vm = viewModel()
        advanceTimeBy(600)
        val sentOnline = graph.api.receivedFrames.size
        assertTrue(sentOnline > 0)

        graph.api.sendFrame = { _, _ -> throw networkError() }
        graph.api.next = { throw networkError() }
        advanceTimeBy(600)
        assertEquals(SessionPause.Offline, vm.state.pause)
        assertFalse(vm.state.isCapturing)

        val capturedWhenOffline = camera.captured.size
        val nextCallsWhenOffline = graph.api.nextCalls
        advanceTimeBy(10_000)
        assertEquals("Sin conexión no se toman cuadros", capturedWhenOffline, camera.captured.size)
        assertTrue("Se reintenta la conexión", graph.api.nextCalls > nextCallsWhenOffline)

        server.install()
        advanceTimeBy(LockedSessionViewModel.OFFLINE_RETRY_MS + 1_000)
        assertNull(vm.state.pause)
        assertTrue(camera.captured.size > capturedWhenOffline)

        // Cada cuadro se intentó enviar una sola vez: el que falló no se guardó para después.
        assertEquals(camera.captured.size, graph.api.frameAttempts)
        assertEquals(camera.captured.size - 1, graph.api.receivedFrames.size)
        assertNoCaptureLeft()
    }

    @Test
    fun `quiero parar detiene las capturas y pide el PIN`() = sessionTest {
        server.phase = "stimuli"
        server.advanceAfterFrames = Int.MAX_VALUE
        val vm = viewModel()
        advanceTimeBy(1_000)

        vm.requestStop()
        val captured = camera.captured.size
        val nextCalls = graph.api.nextCalls
        advanceTimeBy(10_000)

        assertEquals(SessionPause.ParticipantRequest, vm.state.pause)
        assertEquals(StopReason.Participant, vm.state.stopReason)
        assertEquals(captured, camera.captured.size)
        assertEquals(nextCalls, graph.api.nextCalls)

        // Sin PIN no se puede volver ni salir.
        vm.resume()
        vm.endSession()
        assertEquals(SessionPause.ParticipantRequest, vm.state.pause)
        assertFalse(vm.state.exited)
        assertTrue(graph.api.stopRequests.isEmpty())
    }

    @Test
    fun `la salida exige el PIN correcto`() = sessionTest {
        val vm = viewModel()
        vm.requestStop()

        vm.onPinChange("9999")
        vm.submitPin()
        assertEquals(PinCheckError.Wrong, vm.state.pinError)
        assertFalse(vm.state.unlocked)
        assertEquals("", vm.state.pin)
        vm.endSession()
        assertFalse(vm.state.exited)
        assertTrue(graph.api.stopRequests.isEmpty())

        vm.onPinChange(PIN)
        vm.submitPin()
        assertTrue(vm.state.unlocked)

        vm.endSession()
        val (sessionId, body) = graph.api.stopRequests.single()
        assertEquals("sesion-1", sessionId)
        assertEquals("participant", body.reason)
        assertTrue(vm.state.exited)
        assertNull(vm.state.step)
        assertTrue(vm.state.answers.isEmpty())
    }

    @Test
    fun `tras varios PIN incorrectos espera antes de volver a pedirlo`() = sessionTest {
        val vm = viewModel()
        vm.requestExit()
        repeat(LockedSessionViewModel.MAX_PIN_FAILURES) {
            vm.onPinChange("0000")
            vm.submitPin()
        }
        assertEquals(PinCheckError.LockedOut(LockedSessionViewModel.PIN_LOCKOUT_SECONDS), vm.state.pinError)

        vm.onPinChange(PIN)
        vm.submitPin()
        assertFalse(vm.state.unlocked)

        advanceTimeBy(LockedSessionViewModel.PIN_LOCKOUT_SECONDS * 1_000L + 1)
        assertNull(vm.state.pinError)
        vm.onPinChange(PIN)
        vm.submitPin()
        assertTrue(vm.state.unlocked)
    }

    @Test
    fun `con el PIN se puede devolver la tablet y la sesión sigue`() = sessionTest {
        server.phase = "stimuli"
        server.advanceAfterFrames = Int.MAX_VALUE
        val vm = viewModel(SessionAudience.Child)
        vm.requestStop()
        vm.onPinChange(PIN)
        vm.submitPin()
        assertTrue(vm.state.canResume)

        vm.resume()
        assertNull(vm.state.pause)
        assertFalse(vm.state.unlocked)
        val captured = camera.captured.size
        advanceTimeBy(1_000)
        assertTrue(camera.captured.size > captured)
    }

    @Test
    fun `el tutor puede pedir terminar la sesión de un niño`() = sessionTest {
        val vm = viewModel(SessionAudience.Child)
        vm.requestStop()
        vm.onPinChange(PIN)
        vm.submitPin()
        vm.setStopReason(StopReason.Guardian)
        vm.endSession()

        assertEquals("guardian", graph.api.stopRequests.single().second.reason)
    }

    @Test
    fun `sin protocolo calibra, captura en vivo y quien conduce la termina con su código`() = sessionTest {
        server.hasProtocol = false
        server.phase = "calibration"
        val vm = viewModel(SessionAudience.Own)
        vm.startCalibration()

        // Tres cuadros de calibración y el servidor pasa a la lectura en vivo.
        advanceTimeBy(1_500)
        assertEquals(GuidedPhase.Live, vm.state.step?.phase)
        val captured = graph.api.receivedFrames.size
        advanceTimeBy(1_000)
        assertTrue("En vivo sigue enviando cuadros", graph.api.receivedFrames.size > captured)

        vm.requestExit()
        vm.onPinChange(PIN)
        vm.submitPin()
        assertEquals(StopReason.Conductor, vm.state.stopReason)
        vm.endSession()

        assertEquals(listOf("sesion-1"), graph.api.finishRequests)
        assertTrue("Terminar no la cancela", graph.api.stopRequests.isEmpty())
        // Queda terminada y en pantalla con su código, sin capturar, hasta volver al inicio.
        assertEquals(GuidedPhase.Finished, vm.state.step?.phase)
        assertFalse(vm.state.isCapturing)
        assertFalse(vm.state.exited)
        assertEquals("S-4F7K2Q", vm.state.code)
        val sent = graph.api.receivedFrames.size
        advanceTimeBy(1_000)
        assertEquals(sent, graph.api.receivedFrames.size)

        vm.endSession()
        assertTrue(vm.state.exited)
        assertNoCaptureLeft()
    }

    @Test
    fun `sin protocolo, si la persona pide parar, la sesión se interrumpe`() = sessionTest {
        server.hasProtocol = false
        server.phase = "live"
        val vm = viewModel()
        vm.requestStop()
        vm.onPinChange(PIN)
        vm.submitPin()
        vm.endSession()

        assertEquals("participant", graph.api.stopRequests.single().second.reason)
        assertTrue(graph.api.finishRequests.isEmpty())
        assertTrue(vm.state.exited)
    }

    @Test
    fun `al terminar la sesión la salida no la vuelve a interrumpir`() = sessionTest {
        server.phase = "finished"
        val vm = viewModel()
        assertTrue(vm.state.isTerminal)

        vm.requestExit()
        vm.onPinChange(PIN)
        vm.submitPin()
        assertFalse(vm.state.canResume)
        vm.endSession()

        assertTrue(vm.state.exited)
        assertTrue(graph.api.stopRequests.isEmpty())
    }

    @Test
    fun `la calidad informada por el servidor orienta a la persona`() = sessionTest {
        server.phase = "stimuli"
        server.advanceAfterFrames = Int.MAX_VALUE
        server.captureStatus = listOf("too_dark")
        val vm = viewModel()
        advanceTimeBy(600)

        assertEquals(listOf(CaptureIssue.TooDark), vm.state.captureIssues)
        // Se sigue enviando: el servidor decide cuándo el cuadro sirve.
        advanceTimeBy(1_000)
        assertTrue(graph.api.frameAttempts >= 2)
    }

    @Test
    fun `si el cuadro es demasiado grande el siguiente va con menos calidad`() = sessionTest {
        server.phase = "stimuli"
        server.advanceAfterFrames = Int.MAX_VALUE
        var first = true
        graph.api.sendFrame = { _, _ ->
            if (first) {
                first = false
                throw mobileError(413, "too_large", "Cuadro demasiado grande")
            }
            FrameResultDto("stimuli")
        }
        viewModel()
        advanceTimeBy(1_000)

        assertEquals(
            listOf(
                LockedSessionViewModel.DEFAULT_JPEG_QUALITY,
                LockedSessionViewModel.DEFAULT_JPEG_QUALITY - LockedSessionViewModel.JPEG_QUALITY_STEP,
            ),
            camera.qualities.take(2),
        )
    }

    @Test
    fun `si la sesión ya no es accesible se detiene y solo permite salir con el PIN`() = sessionTest {
        graph.api.next = { throw mobileError(403, "forbidden", "No tienes acceso a esta sesión") }
        val vm = viewModel()

        val pause = vm.state.pause as SessionPause.Failed
        assertEquals(SessionError.Server("forbidden", "No tienes acceso a esta sesión"), pause.error)
        vm.onPinChange(PIN)
        vm.submitPin()
        assertFalse(vm.state.canResume)
        vm.endSession()
        assertTrue(vm.state.exited)
        assertTrue(graph.api.stopRequests.isEmpty())
    }

    @Test
    fun `un cuestionario ya respondido en la web continúa con advance`() = sessionTest {
        server.preAnswered = true
        val vm = viewModel()
        assertTrue(vm.state.canSubmitQuestionnaire)
        vm.submitQuestionnaire()

        assertEquals(1, graph.api.advanceCalls)
        assertTrue(graph.api.questionnaireRequests.isEmpty())
    }

    private companion object {
        const val PIN = "1234"
    }
}

/** Servidor falso que avanza por las fases de un protocolo como lo haría la API. */
private class ScriptedServer(private val api: FakeEmotvApi) {
    var phase = "pre_questionnaire"
    var calibration = "none"
    var advanceAfterFrames = 0
    var captureStatus: List<String> = emptyList()
    var preAnswered = false
    var hasProtocol = true
    private var framesInPhase = 0
    val imageUrl = "/mobile/stimulus-images/sesion-1/estimulo-1?expires=1792764370&signature=abc"

    fun install() {
        api.next = { step() }
        api.submitQuestionnaire = { _, moment, _ -> moveTo(if (moment == "pre") "calibration" else "finished") }
        api.advance = { moveTo("calibration") }
        api.startCalibration = {
            calibration = "calibrating"
            PhaseDto("calibration")
        }
        api.sendFrame = { _, _ ->
            framesInPhase++
            val limit = if (advanceAfterFrames > 0) advanceAfterFrames else if (phase == "calibration") 3 else 2
            if (framesInPhase >= limit) {
                when (phase) {
                    "calibration" -> moveTo(if (hasProtocol) "stimuli" else "live")
                    "stimuli" -> moveTo("post_questionnaire")
                }
            }
            FrameResultDto(phase, captureStatus)
        }
    }

    private fun moveTo(next: String): PhaseDto {
        phase = next
        framesInPhase = 0
        return PhaseDto(next)
    }

    private fun step(): SessionStepDto = when (phase) {
        "pre_questionnaire" -> stepDto(phase = phase, questionnaire = questionnaire("pre", answered = preAnswered))
        "calibration" -> stepDto(
            phase = phase,
            sendFrames = calibration == "calibrating",
            frameIntervalMs = 500,
            calibration = CalibrationDto(state = calibration, progress = 0.3f),
        )
        "stimuli" -> stepDto(
            phase = phase,
            sendFrames = true,
            frameIntervalMs = 500,
            captureStatus = captureStatus,
            stimulus = StimulusDto(
                stepIndex = 0,
                kind = "emotional",
                exposureSeconds = 6.0,
                remainingSeconds = 5.0,
                imageUrl = imageUrl,
            ),
        )
        "post_questionnaire" -> stepDto(phase = phase, questionnaire = questionnaire("post"))
        "live" -> stepDto(phase = phase, sendFrames = true, frameIntervalMs = 500, captureStatus = captureStatus, stepCount = 0)
        else -> stepDto(phase = phase)
    }

    private fun questionnaire(moment: String, answered: Boolean = false) = QuestionnaireDto(
        moment = moment,
        id = "cuestionario-$moment",
        title = "Antes de empezar",
        answered = answered,
        questions = listOf(
            QuestionDto(id = "animo", kind = "faces", text = "¿Cómo te sientes?", required = true),
            QuestionDto(id = "lugar", kind = "single_choice", text = "¿Dónde estás?", options = listOf("Casa", "Aula")),
        ),
    )
}
