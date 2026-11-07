package pe.unheval.emotv.ui.participation

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pe.unheval.emotv.domain.model.ActivityKind
import pe.unheval.emotv.domain.model.AppError
import pe.unheval.emotv.domain.model.GameAction
import pe.unheval.emotv.domain.model.GameOption
import pe.unheval.emotv.domain.model.GameState
import pe.unheval.emotv.domain.model.GameType
import pe.unheval.emotv.domain.model.MatchingGame
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.ParticipantActivity
import pe.unheval.emotv.domain.model.ParticipantView
import pe.unheval.emotv.domain.repository.ParticipationRepository
import pe.unheval.emotv.ui.theme.EMOtvTheme

/** Servidor falso con las reglas del juego web; ninguna prueba llama al servidor real. */
private class FakeGame(val similar: Boolean = false) : ParticipationRepository {
    // Al buscar la misma emoción, la clave es el lugar de la cara («Cara 2») y no dice su emoción.
    private val target = if (similar) "2" else "happiness"
    private val labels = if (similar) listOf("1" to "Cara 1", "2" to "Cara 2", "3" to "Cara 3")
        else listOf("sadness" to "Tristeza", "happiness" to "Alegría", "anger" to "Enojo")
    val faces = mutableListOf<String>()
    var state = GameState.Playing
    val tried = mutableListOf<String>()
    var helped = false
    var round = 0
    val recorded = mutableListOf<GameAction>()
    var loseNextResponse = false

    private fun view() = ParticipantView(
        ended = false, message = null,
        activity = ParticipantActivity(ActivityKind.Game, "Empareja la emoción", 1, 1),
        drawing = null,
        game = MatchingGame(
            round = "0-1-$round", number = round + 1, total = 2,
            type = if (similar) GameType.Similar else GameType.Matching,
            question = if (similar) "¿Qué cara muestra la misma emoción que la de arriba?"
                else "¿Qué emoción muestra esta cara?",
            state = state,
            options = labels.map { (key, label) ->
                GameOption(key, label, key !in tried && !(helped && key == labels.last().first))
            },
            answer = target.takeIf { state.isRevealed },
            message = when (state) {
                GameState.Retry -> "Mira otra vez la cara."
                GameState.Matched -> "Lo encontraste: alegría."
                else -> null
            },
            explanation = "Las comisuras de la boca suben.".takeIf { state.isRevealed },
            canHelp = state.isPlaying && !helped, lastRound = round == 1,
        ),
        exposure = "0-100-$round", pictograms = true,
    )

    override suspend fun claim(code: String): Outcome<String> = Outcome.Success("enlace-de-prueba")
    override suspend fun view(token: String): Outcome<ParticipantView> = Outcome.Success(view())
    override suspend fun answer(token: String, expression: String): Outcome<Unit> = Outcome.Success(Unit)
    override suspend fun stop(token: String): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun stimulus(token: String): Outcome<ByteArray> = Outcome.Success(png())

    override suspend fun gameOption(token: String, key: String): Outcome<ByteArray> {
        faces += key
        return Outcome.Success(png())
    }

    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    override suspend fun game(token: String, action: GameAction, expression: String?): Outcome<Unit> {
        when (action) {
            GameAction.Answer -> {
                if (!state.isPlaying) return Outcome.Failure(AppError.Server(409, null, null))
                if (expression in tried) return Outcome.Failure(AppError.Server(422, null, null))
                if (expression == target) state = GameState.Matched
                else { tried += expression!!; state = GameState.Retry }
            }
            GameAction.Help -> helped = true
            GameAction.Next -> if (round == 1) state = GameState.Finished
                else { round += 1; state = GameState.Playing; tried.clear(); helped = false }
        }
        recorded += action
        if (loseNextResponse) { loseNextResponse = false; return Outcome.Failure(AppError.Network) }
        return Outcome.Success(Unit)
    }
}

@RunWith(AndroidJUnit4::class)
class MatchingGameScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var fake = FakeGame()
    private var viewModel = ParticipationViewModel(fake)

    private fun show(similar: Boolean = false) {
        if (similar) {
            fake = FakeGame(similar = true)
            viewModel = ParticipationViewModel(fake)
        }
        compose.setContent {
            EMOtvTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val revision by viewModel.boardRevision.collectAsStateWithLifecycle()
                val image by viewModel.roundImage.collectAsStateWithLifecycle()
                val faces by viewModel.optionImages.collectAsStateWithLifecycle()
                ParticipationScreen(
                    state = state, board = viewModel.board, revision = revision, roundImage = image,
                    optionImages = faces,
                    actions = ParticipationActions(
                        onCodeChange = viewModel::onCodeChange, onSubmitCode = viewModel::submitCode,
                        onGame = viewModel::play,
                    ),
                )
            }
        }
        compose.onNodeWithTag(ParticipationTags.CODE).performTextInput("K7MP2QXR")
        compose.onNodeWithTag(ParticipationTags.OPEN).performClick()
        compose.waitForIdle()
    }

    private fun state(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    @Test
    fun seJuegaConToquesComoEnLaWeb() {
        show()
        compose.onNodeWithTag(ParticipationTags.GAME_ROUND).assertIsDisplayed()
        compose.onNodeWithText("Ronda 1 de 2").assertIsDisplayed()
        compose.onNodeWithTag(ParticipationTags.GAME_IMAGE).assertContentDescriptionEquals("Cara de la ronda")
        compose.onNodeWithTag(ParticipationTags.GAME_HELP).assertIsEnabled()

        compose.onNodeWithTag(ParticipationTags.gameOption("sadness")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Mira otra vez la cara.").assertIsDisplayed()
        compose.onNodeWithTag(ParticipationTags.gameOption("sadness")).assertIsNotEnabled()
            .assert(state("No disponible"))

        compose.onNodeWithTag(ParticipationTags.gameOption("happiness")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Lo encontraste: alegría.").assertIsDisplayed()
        compose.onNodeWithText("Las comisuras de la boca suben.").assertIsDisplayed()
        compose.onNodeWithTag(ParticipationTags.gameOption("happiness")).assert(state("Es esta"))
        compose.onNodeWithTag(ParticipationTags.GAME_NEXT).assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ronda 2 de 2").assertIsDisplayed()
        assertEquals(listOf(GameAction.Answer, GameAction.Answer, GameAction.Next), fake.recorded)
    }

    @Test
    fun talkBackLeeNombreYEstadoSinPictogramas() {
        show()
        // El mensaje de la ronda está en una región viva: TalkBack lo lee al cambiar.
        compose.onNodeWithTag(ParticipationTags.GAME_FEEDBACK)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        // El botón se lee como «Alegría»: el pictograma no se anuncia.
        compose.onNodeWithTag(ParticipationTags.gameOption("happiness"), useUnmergedTree = false)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Text,
                listOf(androidx.compose.ui.text.AnnotatedString("Alegría"))))
        compose.onNodeWithText("😊").assertDoesNotExistInMergedTree()
    }

    @Test
    fun unToqueRepetidoTrasUnCorteNoDuplicaLaRespuesta() {
        show()
        fake.loseNextResponse = true
        compose.onNodeWithTag(ParticipationTags.gameOption("sadness")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("No se pudo enviar. Toca otra vez o avisa a quien conduce la sesión.").assertIsDisplayed()
        compose.onNodeWithTag(ParticipationTags.gameOption("sadness")).performClick()
        compose.waitForIdle()
        assertEquals(listOf(GameAction.Answer), fake.recorded)
        compose.onNodeWithText("Mira otra vez la cara.").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class SimilarGameScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val fake = FakeGame(similar = true)
    private val viewModel = ParticipationViewModel(fake)

    private fun show() {
        compose.setContent {
            EMOtvTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val revision by viewModel.boardRevision.collectAsStateWithLifecycle()
                val image by viewModel.roundImage.collectAsStateWithLifecycle()
                val faces by viewModel.optionImages.collectAsStateWithLifecycle()
                ParticipationScreen(
                    state = state, board = viewModel.board, revision = revision, roundImage = image,
                    optionImages = faces,
                    actions = ParticipationActions(
                        onCodeChange = viewModel::onCodeChange, onSubmitCode = viewModel::submitCode,
                        onGame = viewModel::play,
                    ),
                )
            }
        }
        compose.onNodeWithTag(ParticipationTags.CODE).performTextInput("K7MP2QXR")
        compose.onNodeWithTag(ParticipationTags.OPEN).performClick()
        compose.waitForIdle()
    }

    @Test
    fun seEligeLaCaraDeLaMismaEmocionHastaTerminar() {
        show()
        compose.onNodeWithText("Ronda 1 de 2").assertIsDisplayed()
        compose.onNodeWithTag(ParticipationTags.GAME_IMAGE)
            .assertContentDescriptionEquals("Cara de arriba, la que hay que comparar")
        assertEquals(listOf("1", "2", "3"), fake.faces)
        // Cada botón se lee «Cara 1», sin decir su emoción y sin la imagen.
        compose.onNodeWithTag(ParticipationTags.gameOption("1"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Text,
                listOf(androidx.compose.ui.text.AnnotatedString("Cara 1"))))

        compose.onNodeWithTag(ParticipationTags.gameOption("1")).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(ParticipationTags.gameOption("1")).assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "No disponible"))
        compose.onNodeWithTag(ParticipationTags.gameOption("2")).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(ParticipationTags.gameOption("2"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Es esta"))
        compose.onNodeWithTag(ParticipationTags.GAME_NEXT).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ronda 2 de 2").assertIsDisplayed()
        compose.onNodeWithTag(ParticipationTags.gameOption("2")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Terminar el juego").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(GameState.Finished, fake.state)
        assertEquals(listOf(GameAction.Answer, GameAction.Answer, GameAction.Next, GameAction.Answer, GameAction.Next),
            fake.recorded)
    }
}

/** El pictograma está fuera de la semántica: no aparece en el árbol que lee TalkBack. */
private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistInMergedTree() = assertDoesNotExist()
