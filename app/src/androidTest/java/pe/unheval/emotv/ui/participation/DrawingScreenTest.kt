package pe.unheval.emotv.ui.participation

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pe.unheval.emotv.domain.model.ActivityKind
import pe.unheval.emotv.domain.model.DrawingStep
import pe.unheval.emotv.domain.model.EmotionOption
import pe.unheval.emotv.domain.model.GameAction
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.ParticipantActivity
import pe.unheval.emotv.domain.model.ParticipantView
import pe.unheval.emotv.domain.repository.ParticipationRepository
import pe.unheval.emotv.ui.theme.EMOtvTheme

/** Repositorio falso: ninguna prueba llama al servidor real. */
private class FakeParticipation : ParticipationRepository {
    var finished = false
    val answers = mutableListOf<String>()

    private fun view() = ParticipantView(
        ended = false, message = null,
        activity = ParticipantActivity(ActivityKind.Drawing, "Dibuja una cara", 1, 1),
        drawing = DrawingStep(
            instruction = "Dibuja una cara que muestre una emoción.",
            question = "¿Qué emoción quisiste dibujar?",
            options = listOf(EmotionOption("happiness", "Alegría"), EmotionOption("sadness", "Tristeza")),
            round = "0-1", finished = finished,
            chosen = if (finished) "happiness" else null,
            message = if (finished) "Dibujaste alegría." else null,
        ),
    )

    override suspend fun claim(code: String): Outcome<String> = Outcome.Success("enlace-de-prueba")
    override suspend fun view(token: String): Outcome<ParticipantView> = Outcome.Success(view())
    override suspend fun answer(token: String, expression: String): Outcome<Unit> {
        answers += expression
        finished = true
        return Outcome.Success(Unit)
    }
    override suspend fun stop(token: String): Outcome<Unit> = Outcome.Success(Unit)
    override suspend fun game(token: String, action: GameAction, expression: String?): Outcome<Unit> =
        Outcome.Success(Unit)
    override suspend fun stimulus(token: String): Outcome<ByteArray> = Outcome.Success(ByteArray(0))
}

@RunWith(AndroidJUnit4::class)
class DrawingScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val fake = FakeParticipation()
    private val viewModel = ParticipationViewModel(fake)

    private fun show() {
        compose.setContent {
            EMOtvTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val revision by viewModel.boardRevision.collectAsStateWithLifecycle()
                ParticipationScreen(
                    state = state, board = viewModel.board, revision = revision,
                    actions = ParticipationActions(
                        onCodeChange = viewModel::onCodeChange, onSubmitCode = viewModel::submitCode,
                        onSelectColor = viewModel::selectColor, onSelectSize = viewModel::selectSize,
                        onToggleEraser = viewModel::toggleEraser, onUndo = viewModel::undo, onClear = viewModel::clear,
                        onFinishDrawing = viewModel::finishDrawing, onKeepDrawing = viewModel::keepDrawing,
                        onChoose = viewModel::choose, onStrokeStart = viewModel::strokeStart,
                        onStrokeMove = viewModel::strokeMove, onStrokeEnd = viewModel::strokeEnd,
                        onStrokeCancel = viewModel::strokeCancel,
                    ),
                )
            }
        }
    }

    @Test
    fun seVinculaDibujaYSoloEnviaLaEmocion() {
        show()
        compose.onNodeWithTag(ParticipationTags.CODE).performTextInput("k7mp2qxr")
        compose.onNodeWithTag(ParticipationTags.OPEN).performClick()
        compose.onNodeWithText("Dibuja una cara").assertIsDisplayed()
        compose.onNodeWithText("Paso 1 de 1").assertIsDisplayed()
        compose.onNodeWithTag(ParticipationTags.FINISH).assertIsNotEnabled()
        compose.onNodeWithTag(ParticipationTags.UNDO).assertIsNotEnabled()

        compose.onNodeWithTag(ParticipationTags.color("red")).performClick().assertIsSelected()
        compose.onNodeWithTag(ParticipationTags.size("thick")).performClick().assertIsSelected()
        compose.onNodeWithTag(ParticipationTags.CANVAS).performTouchInput {
            swipe(Offset(width * 0.2f, height * 0.3f), Offset(width * 0.8f, height * 0.7f), durationMillis = 300)
        }
        compose.waitForIdle()
        assertTrue(viewModel.board.hasDrawing)
        compose.onNodeWithTag(ParticipationTags.UNDO).assertIsEnabled()
        compose.onNodeWithTag(ParticipationTags.FINISH).assertIsEnabled().performClick()

        compose.onNodeWithText("¿Qué emoción quisiste dibujar?").assertIsDisplayed()
        compose.onNodeWithTag(ParticipationTags.option("happiness")).performClick()
        compose.waitForIdle()
        assertEquals(listOf("happiness"), fake.answers)
        compose.onNodeWithTag(ParticipationTags.DONE).assertIsDisplayed()
        compose.onNodeWithText("Dibujaste alegría.").assertIsDisplayed()
        assertTrue(viewModel.board.strokes.isEmpty())
    }

    @Test
    fun limpiarPideConfirmacionYElBorradorSeActiva() {
        show()
        compose.onNodeWithTag(ParticipationTags.CODE).performTextInput("K7MP2QXR")
        compose.onNodeWithTag(ParticipationTags.OPEN).performClick()
        compose.onNodeWithTag(ParticipationTags.CANVAS).performTouchInput {
            swipe(Offset(width * 0.3f, height * 0.3f), Offset(width * 0.6f, height * 0.6f), durationMillis = 200)
        }
        compose.onNodeWithTag(ParticipationTags.ERASER).performClick().assertIsSelected()
        compose.onNodeWithTag(ParticipationTags.CLEAR).performClick()
        compose.onNodeWithText("Se borrará todo el dibujo. ¿Quieres limpiarlo?").assertIsDisplayed()
        // «Limpiar» aparece dos veces (herramienta y confirmación): se comprueba el título del diálogo.
        compose.onNodeWithText("Limpiar el lienzo").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        assertTrue(viewModel.board.hasDrawing)
    }
}
