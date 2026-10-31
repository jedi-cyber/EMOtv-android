package pe.unheval.emotv.ui.statistics

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pe.unheval.emotv.domain.model.ExpressionShare
import pe.unheval.emotv.domain.model.PracticeCounts
import pe.unheval.emotv.domain.model.PracticeProtocolResult
import pe.unheval.emotv.domain.model.PracticeTargetResult
import pe.unheval.emotv.domain.model.PracticeTotals
import pe.unheval.emotv.domain.model.SessionStatistics
import pe.unheval.emotv.domain.model.StatisticsPractice
import pe.unheval.emotv.domain.model.StatisticsScope
import pe.unheval.emotv.ui.common.Loadable
import pe.unheval.emotv.ui.components.BarChartTags
import pe.unheval.emotv.ui.components.ContentTags
import pe.unheval.emotv.ui.components.PracticeTags
import pe.unheval.emotv.ui.theme.EMOtvTheme

@RunWith(AndroidJUnit4::class)
class StatisticsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val statistics = SessionStatistics(
        scope = StatisticsScope.Mine,
        notice = "Cuentan sesiones, no personas.",
        fewSessionsNotice = null,
        total = 6,
        completed = 4,
        interrupted = 2,
        averageDurationSeconds = 750.0,
        countedCaptures = 4,
        excludedCaptures = 1,
        expressions = listOf(
            ExpressionShare("neutral", captures = 1, percent = 25.0, sessions = 1),
            ExpressionShare("happiness", captures = 3, percent = 75.0, sessions = 2),
        ),
        compared = 4,
        matched = 3,
    )

    private val notice = "Los modelos de expresión se entrenaron principalmente con rostros de adultos."

    private val practice = StatisticsPractice(
        sessions = 2,
        totals = PracticeTotals(
            applied = 3,
            achieved = 2,
            skipped = 1,
            stopped = 0,
            averageSecondsToAchieve = 3.0,
            sentence = "Logró la expresión pedida en 2 de 3 ejercicios.",
            averageSentence = null,
            feedbackSentence = null,
        ),
        byProtocol = listOf(
            PracticeProtocolResult("Alegría y sorpresa", sessions = 2, counts = PracticeCounts(3, 2, 1, 0, 3.0)),
        ),
        byTarget = listOf(PracticeTargetResult("happiness", PracticeCounts(2, 2, 0, 0, 3.0))),
        notice = notice,
    )

    private fun show(statistics: SessionStatistics = this.statistics) {
        compose.setContent {
            EMOtvTheme {
                StatisticsScreen(
                    state = StatisticsUiState(data = Loadable<SessionStatistics>().loaded(statistics)),
                    onRefresh = {},
                    onChooseScope = {},
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun lasBarrasSeLeenConSuEtiquetaYSuValor() {
        show()

        compose.onNodeWithTag(ContentTags.LIST).performScrollToNode(hasTestTag(BarChartTags.row("completed")))
        compose.onNodeWithTag(BarChartTags.row("completed")).assertContentDescriptionEquals("Completas, 4 de 6")
        compose.onNodeWithTag(ContentTags.LIST).performScrollToNode(hasTestTag(BarChartTags.row("happiness")))
        compose.onNodeWithTag(BarChartTags.row("happiness"))
            .assertContentDescriptionEquals("Felicidad, 75 %, 3 capturas en 2 sesiones")
    }

    @Test
    fun laPracticaSeMuestraAparteDeLaCoincidencia() {
        show(statistics.copy(practice = practice))

        compose.onNodeWithTag(ContentTags.LIST).performScrollToNode(hasTestTag(StatisticsTags.PRACTICE))
        compose.onNodeWithText("2 sesiones con ejercicios de práctica.").assertExists()
        compose.onNodeWithText("Logró la expresión pedida en 2 de 3 ejercicios.").assertExists()
        compose.onNodeWithTag(BarChartTags.row(PracticeTags.target("happiness")))
            .assertContentDescriptionEquals("Felicidad, 2 de 2, Tiempo promedio hasta lograrla: 3 s")
        compose.onNodeWithTag(ContentTags.LIST).performScrollToNode(hasTestTag(BarChartTags.row(PracticeTags.protocol(0))))
        compose.onNodeWithTag(BarChartTags.row(PracticeTags.protocol(0)))
            .assertContentDescriptionEquals("Alegría y sorpresa, 2 de 3, 2 sesiones · 1 saltado · Tiempo promedio hasta lograrla: 3 s")
        compose.onNodeWithTag(ContentTags.LIST).performScrollToNode(hasText(notice))
        compose.onNodeWithText(notice).assertExists()
    }

    @Test
    fun sinPracticaNoHaySeccion() {
        show()

        compose.onNodeWithTag(ContentTags.LIST).performScrollToNode(hasTestTag(BarChartTags.row("matched")))
        compose.onNodeWithTag(StatisticsTags.PRACTICE).assertDoesNotExist()
    }
}
