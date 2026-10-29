package pe.unheval.emotv.ui.statistics

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pe.unheval.emotv.domain.model.ExpressionShare
import pe.unheval.emotv.domain.model.SessionStatistics
import pe.unheval.emotv.domain.model.StatisticsScope
import pe.unheval.emotv.ui.common.Loadable
import pe.unheval.emotv.ui.components.BarChartTags
import pe.unheval.emotv.ui.components.ContentTags
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

    private fun show() {
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
}
