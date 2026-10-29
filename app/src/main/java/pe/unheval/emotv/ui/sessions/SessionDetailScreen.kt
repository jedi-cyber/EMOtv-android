package pe.unheval.emotv.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pe.unheval.emotv.R
import pe.unheval.emotv.domain.model.AbstractStimulusMetric
import pe.unheval.emotv.domain.model.EmotionalStimulusMetric
import pe.unheval.emotv.domain.model.MarkerKind
import pe.unheval.emotv.domain.model.QuestionnaireMoment
import pe.unheval.emotv.domain.model.SessionMetrics
import pe.unheval.emotv.domain.model.TimelineEvent
import pe.unheval.emotv.ui.components.Callout
import pe.unheval.emotv.ui.components.CalloutTone
import pe.unheval.emotv.ui.components.ContentScreen
import pe.unheval.emotv.ui.components.EmotvCard
import pe.unheval.emotv.ui.components.ErrorCallout
import pe.unheval.emotv.ui.components.PrimaryButton
import pe.unheval.emotv.ui.components.ReportStatusChip
import pe.unheval.emotv.ui.components.SessionStateChip
import pe.unheval.emotv.ui.components.expressionLabel
import pe.unheval.emotv.ui.components.formatDateTime
import pe.unheval.emotv.ui.components.sessionCode
import pe.unheval.emotv.ui.components.sessionDetail
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvSpacing
import pe.unheval.emotv.ui.theme.EmotvType
import java.time.ZoneId
import kotlin.math.roundToInt

object SessionDetailTags {
    const val OPEN_REPORT = "detail_open_report"
    const val START_REPORT = "detail_start_report"
    const val TIMELINE = "detail_timeline"
}

/**
 * Detalle de una sesión: las mismas métricas y línea de tiempo de la web. Las expresiones se
 * muestran como estimadas, por su nombre y sin colores ni valoraciones.
 */
@Composable
fun SessionDetailScreen(
    state: SessionDetailUiState,
    onRefresh: () -> Unit,
    onOpenReport: (String) -> Unit,
    onStartReport: () -> Unit,
    onBack: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val content = state.data.content
    val session = content?.detail?.session
    ContentScreen(
        title = session?.let { stringResource(R.string.detail_title, sessionCode(it)) }
            ?: stringResource(R.string.sessions_title),
        subtitle = session?.let {
            stringResource(R.string.session_started_at, formatDateTime(it.startedAt, zone)) + "\n" + sessionDetail(it)
        },
        onBack = onBack,
        isLoading = state.data.isLoading,
        isRefreshing = state.data.isRefreshing,
        error = state.data.error,
        hasContent = content != null,
        onRefresh = onRefresh,
    ) {
        if (content == null || session == null) return@ContentScreen
        item(key = "state") { SessionStateChip(session.state) }
        item(key = "report") {
            EmotvCard {
                CardTitle(stringResource(R.string.detail_report_title))
                val report = content.report
                when {
                    report != null -> {
                        ReportStatusChip(report.status)
                        PrimaryButton(
                            text = stringResource(R.string.detail_report_open),
                            onClick = { onOpenReport(report.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(SessionDetailTags.OPEN_REPORT),
                        )
                    }
                    session.mine && session.state.finished -> {
                        BodyText(stringResource(R.string.detail_report_none))
                        PrimaryButton(
                            text = stringResource(R.string.detail_report_start),
                            onClick = onStartReport,
                            loading = state.isCreatingReport,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(SessionDetailTags.START_REPORT),
                        )
                    }
                    session.mine -> BodyText(stringResource(R.string.detail_report_not_finished))
                    else -> BodyText(stringResource(R.string.detail_report_none_other))
                }
                state.actionError?.let { ErrorCallout(error = it, stale = false, onRetry = null) }
            }
        }
        item(key = "metrics") { MetricsCard(content.detail.metrics) }
        item(key = "notice") {
            Callout(title = stringResource(R.string.estimates_notice), body = null, tone = CalloutTone.Neutral)
        }
        item(key = "timeline") { TimelineCard(content.detail.timeline) }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text = text,
        style = EmotvType.SectionTitle,
        color = EmotvColors.Ink,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun BodyText(text: String, color: Color = EmotvColors.Charcoal) {
    Text(text = text, style = EmotvType.BodyMd, color = color)
}

@Composable
private fun MetricsCard(metrics: SessionMetrics) {
    EmotvCard {
        CardTitle(stringResource(R.string.detail_metrics_title))
        if (metrics.sentence.isNotEmpty()) BodyText(metrics.sentence, EmotvColors.Ink)
        if (metrics.emotional.isNotEmpty()) {
            Text(stringResource(R.string.detail_metrics_emotional), style = EmotvType.BodySmStrong, color = EmotvColors.Ink)
            Column {
                metrics.emotional.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = EmotvColors.Hairline)
                    EmotionalRow(item)
                }
            }
        }
        if (metrics.abstract.isNotEmpty()) {
            Text(stringResource(R.string.detail_metrics_abstract), style = EmotvType.BodySmStrong, color = EmotvColors.Ink)
            Column {
                metrics.abstract.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = EmotvColors.Hairline)
                    AbstractRow(item)
                }
            }
        }
        metrics.abstractNotice?.let {
            Callout(title = it, body = null, tone = CalloutTone.Neutral)
        }
    }
}

@Composable
private fun EmotionalRow(item: EmotionalStimulusMetric) {
    Column(
        modifier = Modifier.padding(vertical = EmotvSpacing.Xs),
        verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xxs),
    ) {
        Text(item.title, style = EmotvType.BodySmStrong, color = EmotvColors.Ink)
        item.expectedExpression?.let {
            Text(stringResource(R.string.detail_metrics_expected, expressionLabel(it)), style = EmotvType.BodySm, color = EmotvColors.Charcoal)
        }
        val outcome = when {
            item.skipped -> R.string.detail_metrics_skipped
            !item.counted -> R.string.detail_metrics_not_counted
            item.matchesExpected == true -> R.string.detail_metrics_matched
            else -> R.string.detail_metrics_not_matched
        }
        Text(stringResource(outcome), style = EmotvType.Caption, color = EmotvColors.Slate)
    }
}

@Composable
private fun AbstractRow(item: AbstractStimulusMetric) {
    Column(
        modifier = Modifier.padding(vertical = EmotvSpacing.Xs),
        verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xxs),
    ) {
        Text(item.title, style = EmotvType.BodySmStrong, color = EmotvColors.Ink)
        val said = when {
            item.skipped -> stringResource(R.string.detail_metrics_skipped)
            item.said != null -> stringResource(R.string.detail_metrics_said, item.said)
            else -> stringResource(R.string.detail_metrics_said_none)
        }
        Text(said, style = EmotvType.BodySm, color = EmotvColors.Charcoal)
    }
}

@Composable
private fun TimelineCard(events: List<TimelineEvent>) {
    EmotvCard(modifier = Modifier.testTag(SessionDetailTags.TIMELINE)) {
        CardTitle(stringResource(R.string.detail_timeline_title))
        if (events.isEmpty()) {
            BodyText(stringResource(R.string.detail_timeline_empty), EmotvColors.Slate)
            return@EmotvCard
        }
        Column {
            events.forEachIndexed { index, event ->
                if (index > 0) HorizontalDivider(color = EmotvColors.Hairline)
                TimelineRow(event)
            }
        }
    }
}

@Composable
private fun TimelineRow(event: TimelineEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = EmotvSpacing.Xs)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(EmotvSpacing.Sm),
    ) {
        Text(
            text = event.time?.let { formatSeconds(it) }.orEmpty(),
            style = EmotvType.ReadoutMd,
            color = EmotvColors.Ink,
            modifier = Modifier.widthIn(min = 56.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xxs),
        ) {
            Text(timelineText(event), style = EmotvType.BodySm, color = EmotvColors.Ink)
            if (event is TimelineEvent.Questionnaire) {
                event.answers.forEach { answer ->
                    Text(
                        text = stringResource(
                            R.string.timeline_answer,
                            answer.question,
                            answer.answer ?: stringResource(R.string.timeline_no_answer),
                        ),
                        style = EmotvType.Caption,
                        color = EmotvColors.Charcoal,
                    )
                }
            }
        }
    }
}

@Composable
private fun timelineText(event: TimelineEvent): String = when (event) {
    is TimelineEvent.Questionnaire -> stringResource(
        if (event.moment == QuestionnaireMoment.Pre) R.string.timeline_questionnaire_pre
        else R.string.timeline_questionnaire_post,
        event.title,
    )
    is TimelineEvent.Stimulus -> stringResource(
        if (event.skipped) R.string.timeline_stimulus_skipped else R.string.timeline_stimulus,
        event.title,
    )
    is TimelineEvent.Capture -> when {
        event.expression == null -> stringResource(R.string.timeline_capture_no_face)
        !event.goodQuality -> stringResource(R.string.timeline_capture_flagged)
        else -> stringResource(R.string.timeline_capture, expressionLabel(event.expression))
    }
    is TimelineEvent.Said -> stringResource(R.string.timeline_said, event.text)
    is TimelineEvent.Marker -> when (event.kind) {
        MarkerKind.Distracted -> stringResource(R.string.timeline_marker_distracted)
        MarkerKind.Laughed -> stringResource(R.string.timeline_marker_laughed)
        MarkerKind.AskedToStop -> stringResource(R.string.timeline_marker_asked_to_stop)
        MarkerKind.Cried -> stringResource(R.string.timeline_marker_cried)
        MarkerKind.Note -> stringResource(R.string.timeline_marker_note, event.note.orEmpty())
        MarkerKind.Unknown -> stringResource(R.string.timeline_marker_other)
    }
}

@Composable
private fun formatSeconds(seconds: Double): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    return stringResource(R.string.detail_timeline_time, total / 60, total % 60)
}
