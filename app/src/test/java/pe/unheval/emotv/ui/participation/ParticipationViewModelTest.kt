package pe.unheval.emotv.ui.participation

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.unheval.emotv.data.remote.ActivityDto
import pe.unheval.emotv.data.remote.DrawingOptionDto
import pe.unheval.emotv.data.remote.ParticipantDrawingDto
import pe.unheval.emotv.data.remote.ParticipantStateDto
import pe.unheval.emotv.data.repository.toDomain
import pe.unheval.emotv.domain.model.ActivityKind
import pe.unheval.emotv.domain.model.AppError
import pe.unheval.emotv.domain.model.GameAction
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.ParticipantView
import pe.unheval.emotv.domain.repository.ParticipationRepository
import pe.unheval.emotv.testing.MainDispatcherRule

/** Repositorio falso: ninguna prueba llama al servidor real. */
private class FakeParticipation : ParticipationRepository {
    var claimResult: Outcome<String> = Outcome.Success("enlace-de-prueba")
    var view: () -> Outcome<ParticipantView> = { Outcome.Success(drawingView()) }
    var answerResult: Outcome<Unit> = Outcome.Success(Unit)
    var stopResult: Outcome<Unit> = Outcome.Success(Unit)
    val claimed = mutableListOf<String>()
    val answers = mutableListOf<Pair<String, String>>()
    var views = 0
    var stops = 0

    override suspend fun claim(code: String): Outcome<String> = claimResult.also { claimed += code }
    override suspend fun view(token: String): Outcome<ParticipantView> { views += 1; return view() }
    override suspend fun answer(token: String, expression: String): Outcome<Unit> =
        answerResult.also { answers += token to expression }
    override suspend fun stop(token: String): Outcome<Unit> = stopResult.also { stops += 1 }
    override suspend fun game(token: String, action: GameAction, expression: String?): Outcome<Unit> =
        Outcome.Success(Unit)
    override suspend fun stimulus(token: String): Outcome<ByteArray> = Outcome.Success(ByteArray(0))
    override suspend fun gameOption(token: String, key: String): Outcome<ByteArray> =
        Outcome.Success(ByteArray(0))
}

private fun stateDto(
    phase: String = "stimuli",
    round: String? = "0-1",
    finished: Boolean = false,
    chosen: String? = null,
    message: String? = null,
) = ParticipantStateDto(
    phase = phase,
    activity = ActivityDto(kind = if (round != null) "drawing" else "recognition",
        name = if (round != null) "Dibuja una cara" else "¿Qué emoción es?", number = 1, total = 2),
    drawing = round?.let {
        ParticipantDrawingDto(
            instruction = "Dibuja una cara que muestre una emoción.",
            question = "¿Qué emoción quisiste dibujar?",
            options = listOf(DrawingOptionDto("happiness", "Alegría"), DrawingOptionDto("sadness", "Tristeza")),
            round = it, finished = finished, chosen = chosen, message = message,
        )
    },
)

private fun drawingView(round: String = "0-1", finished: Boolean = false, chosen: String? = null,
                        message: String? = null) =
    stateDto(round = round, finished = finished, chosen = chosen, message = message).toDomain()

private fun server(status: Int) = Outcome.Failure(AppError.Server(status, null, null))

class ParticipationViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val fake = FakeParticipation()

    private fun linked(): ParticipationViewModel {
        val vm = ParticipationViewModel(fake)
        vm.onCodeChange("k7mp2qxr")
        vm.submitCode()
        return vm
    }

    private fun ParticipationViewModel.drawLine() {
        assertTrue(strokeStart(1, 0.2f, 0.2f))
        strokeMove(1, 0.4f, 0.4f)
        strokeEnd(1)
    }

    private val ParticipationViewModel.active get() = uiState.value as ParticipationUiState.Active

    @Test
    fun `el codigo abre la actividad asignada`() {
        val vm = linked()
        assertEquals(listOf("K7MP2QXR"), fake.claimed)
        val activity = checkNotNull(vm.active.view.activity)
        assertEquals(ActivityKind.Drawing, activity.kind)
        assertEquals(1, activity.number)
        assertEquals(DrawingPhase.Drawing, vm.active.phase)
    }

    @Test
    fun `un codigo vencido muestra el mensaje del servidor`() {
        fake.claimResult = Outcome.Failure(AppError.Server(404, "invalid_code", "El código no es válido o ya venció."))
        val vm = linked()
        val state = vm.uiState.value as ParticipationUiState.CodeEntry
        assertEquals("El código no es válido o ya venció.", (state.error as AppError.Server).message)
        assertFalse(state.sending)
    }

    @Test
    fun `dibuja, elige la emocion y solo envia la emocion`() = runTest {
        val vm = linked()
        assertFalse(vm.board.hasDrawing)
        vm.finishDrawing()
        assertEquals(DrawingPhase.Drawing, vm.active.phase)      // sin dibujo no se termina
        vm.drawLine()
        vm.finishDrawing()
        assertEquals(DrawingPhase.Choosing, vm.active.phase)
        assertFalse(vm.strokeStart(2, 0.5f, 0.5f))              // al elegir, el lienzo no dibuja
        fake.view = { Outcome.Success(drawingView(finished = true, chosen = "happiness", message = "Dibujaste alegría.")) }
        vm.choose("happiness")
        assertEquals(listOf("enlace-de-prueba" to "happiness"), fake.answers)
        assertEquals(DrawingPhase.Done, vm.active.phase)
        assertEquals("Dibujaste alegría.", vm.active.view.drawing?.message)
        // El dibujo se borró de la memoria al terminar el paso.
        assertTrue(vm.board.strokes.isEmpty())
    }

    @Test
    fun `una respuesta ya registrada cuenta como recibida`() = runTest {
        val vm = linked()
        vm.drawLine()
        vm.finishDrawing()
        fake.answerResult = server(409)
        vm.choose("sadness")
        assertEquals(DrawingPhase.Done, vm.active.phase)
        assertTrue(vm.board.strokes.isEmpty())
    }

    @Test
    fun `sin conexion al enviar se conserva el dibujo para reintentar`() = runTest {
        val vm = linked()
        vm.drawLine()
        vm.finishDrawing()
        fake.answerResult = Outcome.Failure(AppError.Network)
        vm.choose("happiness")
        assertEquals(DrawingPhase.Choosing, vm.active.phase)
        assertTrue(vm.active.sendFailed)
        assertTrue(vm.board.hasDrawing)
        fake.answerResult = Outcome.Success(Unit)
        vm.choose("happiness")
        assertEquals(2, fake.answers.size)
        assertEquals(DrawingPhase.Done, vm.active.phase)
    }

    @Test
    fun `repetir el paso empieza un lienzo nuevo`() = runTest {
        val vm = linked()
        vm.drawLine()
        fake.view = { Outcome.Success(drawingView(round = "0-2")) }
        vm.refresh()
        assertTrue(vm.board.strokes.isEmpty())
        assertEquals(DrawingPhase.Drawing, vm.active.phase)
    }

    @Test
    fun `si termina el tiempo o responde quien conduce se borra el dibujo`() = runTest {
        val vm = linked()
        vm.drawLine()
        fake.view = { Outcome.Success(drawingView(finished = true)) }
        vm.refresh()
        assertEquals(DrawingPhase.Done, vm.active.phase)
        assertTrue(vm.board.strokes.isEmpty())
        assertNull(vm.active.view.drawing?.chosen)
    }

    @Test
    fun `al pasar a otra actividad no queda dibujo`() = runTest {
        val vm = linked()
        vm.drawLine()
        fake.view = { Outcome.Success(stateDto(round = null).toDomain()) }
        vm.refresh()
        assertTrue(vm.board.strokes.isEmpty())
        assertEquals(ActivityKind.Recognition, vm.active.view.activity?.kind)
        assertNull(vm.active.view.drawing)
    }

    @Test
    fun `al cortarse la red se reconecta con el mismo enlace y conserva el dibujo`() = runTest {
        val vm = linked()
        vm.drawLine()
        fake.view = { Outcome.Failure(AppError.Network) }
        vm.refresh()
        assertTrue(vm.active.reconnecting)
        assertTrue(vm.board.hasDrawing)
        fake.view = { Outcome.Success(drawingView()) }
        vm.refresh()
        assertFalse(vm.active.reconnecting)
        assertTrue(vm.board.hasDrawing)
    }

    @Test
    fun `al terminar la sesion se cierra y se borra todo`() = runTest {
        val vm = linked()
        vm.drawLine()
        fake.view = { server(404) }
        vm.refresh()
        assertEquals(ParticipationUiState.Ended, vm.uiState.value)
        assertTrue(vm.board.strokes.isEmpty())
        val before = fake.views
        vm.refresh()                                           // sin enlace ya no consulta
        assertEquals(before, fake.views)
    }

    @Test
    fun `consulta cada segundo solo mientras la pantalla se ve`() = runTest(mainRule.dispatcher) {
        val vm = linked()
        vm.onStart()
        val first = fake.views
        advanceTimeBy(3_100)
        assertEquals(first + 3, fake.views)
        vm.onStop()
        advanceTimeBy(5_000)
        assertEquals(first + 3, fake.views)
        vm.onStart()
        advanceTimeBy(1_100)
        assertEquals(first + 4, fake.views)
        vm.onStop()
    }

    @Test
    fun `quiero parar termina la sesion y si falla lo avisa`() = runTest {
        val vm = linked()
        fake.stopResult = Outcome.Failure(AppError.Network)
        vm.stopSession()
        assertTrue(vm.active.stopFailed)
        fake.stopResult = Outcome.Success(Unit)
        vm.stopSession()
        assertEquals(ParticipationUiState.Ended, vm.uiState.value)
        assertEquals(2, fake.stops)
    }

    @Test
    fun `salir borra el dibujo y olvida el enlace`() = runTest {
        val vm = linked()
        vm.drawLine()
        vm.leave()
        assertTrue(vm.board.strokes.isEmpty())
        val before = fake.views
        vm.refresh()
        assertEquals(before, fake.views)
    }

    @Test
    fun `un tipo de actividad desconocido se muestra como actividad`() {
        assertEquals(ActivityKind.Other, ActivityKind.from("nuevo"))
        assertTrue(stateDto(phase = "finished").toDomain().ended)
        assertTrue(stateDto(phase = "stopped").toDomain().ended)
    }
}
