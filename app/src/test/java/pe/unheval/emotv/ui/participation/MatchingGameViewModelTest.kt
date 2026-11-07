package pe.unheval.emotv.ui.participation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.unheval.emotv.data.remote.ActivityDto
import pe.unheval.emotv.data.remote.GameOptionDto
import pe.unheval.emotv.data.remote.ParticipantGameDto
import pe.unheval.emotv.data.remote.ParticipantStateDto
import pe.unheval.emotv.data.remote.StimulusDto
import pe.unheval.emotv.data.remote.SupportsDto
import pe.unheval.emotv.data.repository.toDomain
import pe.unheval.emotv.domain.model.AppError
import pe.unheval.emotv.domain.model.GameAction
import pe.unheval.emotv.domain.model.GameState
import pe.unheval.emotv.domain.model.GameType
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.ParticipantView
import pe.unheval.emotv.domain.repository.ParticipationRepository
import pe.unheval.emotv.testing.MainDispatcherRule

/**
 * Servidor falso con las reglas del juego web (guided_session_service._game_act):
 * dos opciones distintas muestran la respuesta, la ayuda quita una opción una vez
 * por ronda y una acción que no corresponde responde 409 o 422. Ninguna prueba
 * llama al servidor real.
 */
private class FakeGameServer(val rounds: Int = 2, val type: String = "matching") : ParticipationRepository {
    // Al buscar la misma emoción, la clave es el lugar de la cara y no dice su emoción.
    private val similar = type == "similar"
    private val target = if (similar) "2" else "happiness"
    private val keys = if (similar) listOf("1", "2", "3") else listOf("sadness", "happiness", "anger")
    var round = 0
    var state = "playing"
    val tried = mutableListOf<String>()
    var removed: String? = null
    var helped = false
    /** Cuántas acciones registró el servidor (cada una cuenta una sola vez). */
    val recorded = mutableListOf<Pair<GameAction, String?>>()
    val sent = mutableListOf<Pair<GameAction, String?>>()
    var stimulusCalls = 0
    val optionCalls = mutableListOf<String>()
    /** El servidor registra la acción, pero la respuesta se pierde en la red. */
    var loseNextResponse = false
    var offline = false

    fun dto(): ParticipantStateDto = ParticipantStateDto(
        phase = "stimuli",
        activity = ActivityDto("game", "Empareja la emoción", 1, 1),
        stimulus = StimulusDto("0-100-0-1-$round"),
        supports = SupportsDto(pictograms = true),
        game = ParticipantGameDto(
            round = "0-1-$round", number = round + 1, total = rounds, gameType = type,
            question = "¿Qué emoción muestra esta cara?", state = state,
            options = keys.map {
                GameOptionDto(it, if (similar) "Cara $it" else it.replaceFirstChar(Char::uppercase),
                    it !in tried && it != removed)
            },
            answer = target.takeIf { state == "matched" || state == "shown" },
            message = when (state) {
                "retry" -> "Mira otra vez la cara."
                "matched" -> "Lo encontraste: alegría."
                "shown" -> "Alegría."
                "finished" -> "Terminaste el juego."
                else -> null
            },
            explanation = "Las comisuras suben.".takeIf { state == "matched" || state == "shown" },
            canHelp = (state == "playing" || state == "retry") && !helped,
            lastRound = round + 1 == rounds,
        ),
    )

    override suspend fun claim(code: String): Outcome<String> = Outcome.Success("enlace-de-prueba")
    override suspend fun view(token: String): Outcome<ParticipantView> =
        if (offline) Outcome.Failure(AppError.Network) else Outcome.Success(dto().toDomain())
    override suspend fun answer(token: String, expression: String): Outcome<Unit> = Outcome.Success(Unit)
    override suspend fun stop(token: String): Outcome<Unit> = Outcome.Success(Unit)
    override suspend fun stimulus(token: String): Outcome<ByteArray> {
        stimulusCalls += 1
        return Outcome.Success(byteArrayOf(1, 2, 3))
    }

    override suspend fun gameOption(token: String, key: String): Outcome<ByteArray> {
        optionCalls += key
        return if (key in keys) Outcome.Success(byteArrayOf(key.last().code.toByte()))
        else Outcome.Failure(AppError.Server(404, null, null))
    }

    override suspend fun game(token: String, action: GameAction, expression: String?): Outcome<Unit> {
        sent += action to expression
        if (offline) return Outcome.Failure(AppError.Network)
        val playing = state == "playing" || state == "retry"
        when (action) {
            GameAction.Answer -> {
                if (!playing) return conflict()
                if (expression !in keys || expression in tried || expression == removed) {
                    return Outcome.Failure(AppError.Server(422, null, "Esa opción no se muestra ahora"))
                }
                if (expression == target) state = "matched" else {
                    tried += expression!!
                    state = if (tried.size >= 2) "shown" else "retry"
                }
            }
            GameAction.Help -> {
                if (!playing) return conflict()
                if (!helped) { helped = true; removed = keys.first { it != target && it !in tried } }
            }
            GameAction.Next -> {
                if (playing) return conflict()
                if (round + 1 < rounds) {
                    round += 1; state = "playing"; tried.clear(); removed = null; helped = false
                } else state = "finished"
            }
        }
        recorded += action to expression
        if (loseNextResponse) { loseNextResponse = false; return Outcome.Failure(AppError.Network) }
        return Outcome.Success(Unit)
    }

    private fun conflict() = Outcome.Failure(AppError.Server(409, null, "Esta ronda ya terminó"))
}

class MatchingGameViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun linked(server: FakeGameServer): ParticipationViewModel {
        val vm = ParticipationViewModel(server)
        vm.onCodeChange("k7mp2qxr")
        vm.submitCode()
        return vm
    }

    private fun ParticipationViewModel.active() = uiState.value as ParticipationUiState.Active

    @Test
    fun `se lee la ronda con sus opciones, pictogramas y la imagen en memoria`() = runTest {
        val server = FakeGameServer()
        val vm = linked(server)
        val game = vm.active().view.game!!
        assertEquals(GameType.Matching, game.type)
        assertEquals(GameState.Playing, game.state)
        assertEquals(listOf("sadness", "happiness", "anger"), game.options.map { it.key })
        assertNull(game.answer)
        assertTrue(vm.active().view.pictograms)
        assertEquals("0-100-0-1-0", vm.roundImage.value?.exposure)
        assertEquals(1, server.stimulusCalls)
    }

    @Test
    fun `elegir otra invita a mirar otra vez y tras dos se muestra la respuesta`() = runTest {
        val server = FakeGameServer()
        val vm = linked(server)
        vm.play(GameAction.Answer, "sadness")
        assertEquals(GameState.Retry, vm.active().view.game!!.state)
        assertFalse(vm.active().view.game!!.options.first { it.key == "sadness" }.available)
        vm.play(GameAction.Answer, "anger")
        val game = vm.active().view.game!!
        assertEquals(GameState.Shown, game.state)
        assertEquals("happiness", game.answer)
        assertEquals("Las comisuras suben.", game.explanation)
    }

    @Test
    fun `una opcion ya elegida o quitada por la ayuda no se envia`() = runTest {
        val server = FakeGameServer()
        val vm = linked(server)
        vm.play(GameAction.Help)
        assertFalse(vm.active().view.game!!.canHelp)
        vm.play(GameAction.Help)
        vm.play(GameAction.Answer, "sadness")
        assertEquals(listOf(GameAction.Help to null), server.sent)
    }

    @Test
    fun `siguiente solo despues de terminar la ronda y cambia la imagen`() = runTest {
        val server = FakeGameServer()
        val vm = linked(server)
        vm.play(GameAction.Next)
        assertTrue(server.sent.isEmpty())
        vm.play(GameAction.Answer, "happiness")
        assertEquals(GameState.Matched, vm.active().view.game!!.state)
        vm.play(GameAction.Next)
        assertEquals(2, vm.active().view.game!!.number)
        assertEquals("0-100-0-1-1", vm.roundImage.value?.exposure)
        assertEquals(2, server.stimulusCalls)
        vm.play(GameAction.Answer, "happiness")
        vm.play(GameAction.Next)
        assertEquals(GameState.Finished, vm.active().view.game!!.state)
    }

    @Test
    fun `si se pierde la respuesta, tocar otra vez no registra dos veces`() = runTest {
        val server = FakeGameServer()
        val vm = linked(server)
        server.loseNextResponse = true
        vm.play(GameAction.Answer, "sadness")
        assertTrue(vm.active().gameFailed)
        // La pantalla aún no sabe que llegó: la persona toca otra vez la misma.
        vm.play(GameAction.Answer, "sadness")
        assertEquals(listOf(GameAction.Answer to "sadness"), server.recorded)
        assertFalse(vm.active().gameFailed)
        assertEquals(GameState.Retry, vm.active().view.game!!.state)
    }

    @Test
    fun `un siguiente repetido tras un corte no salta dos rondas`() = runTest {
        val server = FakeGameServer(rounds = 3)
        val vm = linked(server)
        vm.play(GameAction.Answer, "happiness")
        server.loseNextResponse = true
        vm.play(GameAction.Next)
        vm.play(GameAction.Next)
        assertEquals(1, server.round)
        assertEquals(1, server.recorded.count { it.first == GameAction.Next })
        assertEquals(2, vm.active().view.game!!.number)
    }

    @Test
    fun `sin conexion se reconecta con el mismo enlace y sigue en la misma ronda`() = runTest {
        val server = FakeGameServer()
        val vm = linked(server)
        vm.play(GameAction.Answer, "sadness")
        server.offline = true
        vm.refresh()
        assertTrue(vm.active().reconnecting)
        vm.play(GameAction.Answer, "anger")
        assertTrue(vm.active().gameFailed)
        server.offline = false
        vm.refresh()
        assertFalse(vm.active().reconnecting)
        assertEquals(GameState.Retry, vm.active().view.game!!.state)
        vm.play(GameAction.Answer, "happiness")
        assertEquals(GameState.Matched, vm.active().view.game!!.state)
    }

    @Test
    fun `al emparejar no se piden imagenes por opcion`() = runTest {
        val server = FakeGameServer()
        val vm = linked(server)
        assertTrue(server.optionCalls.isEmpty())
        assertTrue(vm.optionImages.value.isEmpty())
    }

    @Test
    fun `busca la misma emocion pide la modelo y cada cara de la ronda`() = runTest {
        val server = FakeGameServer(type = "similar")
        val vm = linked(server)
        val game = vm.active().view.game!!
        assertEquals(GameType.Similar, game.type)
        assertEquals(listOf("Cara 1", "Cara 2", "Cara 3"), game.options.map { it.label })
        assertEquals("0-100-0-1-0", vm.roundImage.value?.exposure)
        assertEquals(listOf("1", "2", "3"), server.optionCalls)
        assertEquals(setOf("1", "2", "3"), vm.optionImages.value.keys)
        // Las caras no se vuelven a pedir en cada consulta del estado.
        vm.refresh()
        assertEquals(3, server.optionCalls.size)
    }

    @Test
    fun `busca la misma emocion se juega con las mismas reglas hasta terminar`() = runTest {
        val server = FakeGameServer(type = "similar")
        val vm = linked(server)
        vm.play(GameAction.Help)
        vm.play(GameAction.Answer, "1")                          // la quitó la ayuda: no se envía
        assertEquals(listOf(GameAction.Help to null), server.sent)
        vm.play(GameAction.Answer, "3")
        assertEquals(GameState.Retry, vm.active().view.game!!.state)
        vm.play(GameAction.Answer, "2")
        assertEquals("2", vm.active().view.game!!.answer)
        val firstFaces = vm.optionImages.value
        vm.play(GameAction.Next)
        // Ronda nueva: imágenes nuevas, las anteriores ya no están.
        assertEquals("0-100-0-1-1", vm.roundImage.value?.exposure)
        assertEquals(6, server.optionCalls.size)
        assertTrue(firstFaces !== vm.optionImages.value)
        vm.play(GameAction.Answer, "2")
        vm.play(GameAction.Next)
        assertEquals(GameState.Finished, vm.active().view.game!!.state)
        assertEquals(
            listOf(GameAction.Help to null, GameAction.Answer to "3", GameAction.Answer to "2", GameAction.Next to null,
                GameAction.Answer to "2", GameAction.Next to null),
            server.recorded,
        )
        vm.play(GameAction.Next)                                // terminado: no se envía nada más
        assertEquals(6, server.sent.size)
    }

    @Test
    fun `busca la misma emocion no duplica un toque tras un corte`() = runTest {
        val server = FakeGameServer(type = "similar")
        val vm = linked(server)
        server.loseNextResponse = true
        vm.play(GameAction.Answer, "3")
        vm.play(GameAction.Answer, "3")
        assertEquals(listOf(GameAction.Answer to "3"), server.recorded)
        assertEquals(GameState.Retry, vm.active().view.game!!.state)
    }

    @Test
    fun `un juego desconocido no se juega ni pide imagenes`() = runTest {
        val server = FakeGameServer(type = "nuevo")
        val vm = linked(server)
        vm.play(GameAction.Answer, "happiness")
        assertTrue(server.sent.isEmpty())
        assertNull(vm.roundImage.value)
        assertTrue(server.optionCalls.isEmpty())
    }

    @Test
    fun `al salir se borra la imagen de la ronda`() = runTest {
        val vm = linked(FakeGameServer())
        assertNotNull(vm.roundImage.value)
        vm.leave()
        assertNull(vm.roundImage.value)
        assertTrue(vm.optionImages.value.isEmpty())
    }

    @Test
    fun `un estado de juego desconocido no se puede jugar`() {
        assertEquals(GameState.Other, GameState.from("nuevo"))
        assertEquals(GameType.Other, GameType.from("nuevo"))
        assertFalse(GameState.Other.isPlaying)
        assertFalse(GameState.Other.isRevealed)
    }
}
