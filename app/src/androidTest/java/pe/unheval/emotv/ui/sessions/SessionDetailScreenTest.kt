package pe.unheval.emotv.ui.sessions

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pe.unheval.emotv.domain.model.PracticeCounts
import pe.unheval.emotv.domain.model.PracticeTargetResult
import pe.unheval.emotv.domain.model.PracticeTotals
import pe.unheval.emotv.domain.model.SessionAudience
import pe.unheval.emotv.domain.model.SessionDetail
import pe.unheval.emotv.domain.model.SessionMetrics
import pe.unheval.emotv.domain.model.SessionPractice
import pe.unheval.emotv.domain.model.SessionState
import pe.unheval.emotv.domain.model.SessionSummary
import pe.unheval.emotv.ui.common.Loadable
import pe.unheval.emotv.ui.components.BarChartTags
import pe.unheval.emotv.ui.components.ContentTags
import pe.unheval.emotv.ui.components.PracticeTags
import pe.unheval.emotv.ui.theme.EMOtvTheme
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class SessionDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val notice = "Los modelos de expresión se entrenaron principalmente con rostros de adultos y su " +
        "precisión con niños no está validada."

    private val practice = SessionPractice(
        totals = PracticeTotals(
            applied = 2,
            achieved = 1,
            skipped = 1,
            stopped = 0,
            averageSecondsToAchieve = 4.5,
            sentence = "Logró la expresión pedida en 1 de 2 ejercicios.",
            averageSentence = "Tiempo promedio hasta lograrla: 4,5 s (solo los ejercicios logrados).",
            feedbackSentence = null,
        ),
        byTarget = listOf(
            PracticeTargetResult("happiness", PracticeCounts(applied = 1, achieved = 1, skipped = 0, stopped = 0, averageSecondsToAchieve = 4.5)),
            PracticeTargetResult("surprise", PracticeCounts(applied = 1, achieved = 0, skipped = 1, stopped = 0, averageSecondsToAchieve = null)),
        ),
        notice = notice,
    )

    private fun show(practice: SessionPractice?) {
        val detail = SessionDetail(
            session = SessionSummary(
                id = "s-1",
                code = "S-4F7K2Q",
                startedAt = Instant.parse("2026-10-30T15:00:00Z"),
                state = SessionState.Completed,
                audience = SessionAudience.Child,
                protocolName = "Alegría y sorpresa",
                mine = true,
                reportStatus = null,
            ),
            durationSeconds = 30.0,
            timeline = emptyList(),
            metrics = SessionMetrics(emptyList(), emptyList(), sentence = "", abstractNotice = null, practice = practice),
        )
        compose.setContent {
            EMOtvTheme {
                SessionDetailScreen(
                    state = SessionDetailUiState(
                        data = Loadable<SessionDetailContent>().loaded(SessionDetailContent(detail, report = null)),
                    ),
                    onRefresh = {},
                    onOpenReport = {},
                    onStartReport = {},
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun laPracticaMuestraLaFraseElDesgloseYElAviso() {
        show(practice)

        compose.onNodeWithTag(ContentTags.LIST).performScrollToNode(hasTestTag(SessionDetailTags.PRACTICE))
        compose.onNodeWithText("Logró la expresión pedida en 1 de 2 ejercicios.").assertExists()
        compose.onNodeWithText(notice).assertExists()
        compose.onNodeWithTag(BarChartTags.row(PracticeTags.target("happiness")))
            .assertContentDescriptionEquals("Felicidad, 1 de 1, Tiempo promedio hasta lograrla: 4,5 s")
        compose.onNodeWithTag(BarChartTags.row(PracticeTags.target("surprise")))
            .assertContentDescriptionEquals("Sorpresa, 0 de 1, 1 saltado")
    }

    @Test
    fun sinEjerciciosNoHaySeccionDePractica() {
        show(practice = null)

        compose.onNodeWithTag(ContentTags.LIST).performScrollToNode(hasTestTag(SessionDetailTags.TIMELINE))
        compose.onNodeWithTag(SessionDetailTags.PRACTICE).assertDoesNotExist()
    }
}
