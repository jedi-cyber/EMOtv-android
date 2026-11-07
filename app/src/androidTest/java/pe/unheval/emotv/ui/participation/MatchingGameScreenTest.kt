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
private class FakeGame : ParticipationRepository {
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
            round = "0-1-$round", number = round + 1, total = 2, type = GameType.Matching,
            question = "¿Qué emoción muestra esta cara?", state = state,
            options = listOf("sadness" to "Tristeza", "happiness" to "Alegría", "anger" to "Enojo").map { (key, label) ->
                GameOption(key, label, key !in tried && !(helped && key == "anger"))
            },
            answer = "happiness".takeIf { state.isRevealed },
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

    override suspend fun stimulus(token: String): Outcome<ByteArray> {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        return Outcome.Success(ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            .toByteArray())
    }

    override suspend fun game(token: String, action: GameAction, expression: String?): Outcome<Unit> {
        when (action) {
            GameAction.Answer -> {
                if (!state.isPlaying) return Outcome.Failure(AppError.Server(409, null, null))
                if (expression in tried) return Outcome.Failure(AppError.Server(422, null, null))
                if (expression == "happiness") state = GameState.Matched
                else { tried += expression!!; state = GameState.Retry }
            }
            GameAction.Help -> helped = true
            GameAction.Next -> { round += 1; state = GameState.Playing; tried.clear(); helped = false }
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

    private val fake = FakeGame()
    private val viewModel = ParticipationViewModel(fake)

    private fun show() {
        compose.setContent {
            EMOtvTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val revision by viewModel.boardRevision.collectAsStateWithLifecycle()
                val image by viewModel.roundImage.collectAsStateWithLifecycle()
                ParticipationScreen(
                    state = state, board = viewModel.board, revision = revision, roundImage = image,
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

/** El pictograma está fuera de la semántica: no aparece en el árbol que lee TalkBack. */
private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistInMergedTree() = assertDoesNotExist()
