package pe.unheval.emotv.ui.statistics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import pe.unheval.emotv.R
import pe.unheval.emotv.domain.model.SessionStatistics
import pe.unheval.emotv.domain.model.StatisticsPractice
import pe.unheval.emotv.domain.model.StatisticsScope
import pe.unheval.emotv.ui.components.BarChart
import pe.unheval.emotv.ui.components.BarChartRow
import pe.unheval.emotv.ui.components.Callout
import pe.unheval.emotv.ui.components.CalloutTone
import pe.unheval.emotv.ui.components.ContentScreen
import pe.unheval.emotv.ui.components.EmotvCard
import pe.unheval.emotv.ui.components.PracticeSentences
import pe.unheval.emotv.ui.components.PracticeTags
import pe.unheval.emotv.ui.components.PracticeTargetChart
import pe.unheval.emotv.ui.components.RadioRow
import pe.unheval.emotv.ui.components.expressionLabel
import pe.unheval.emotv.ui.components.practiceBar
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvSpacing
import pe.unheval.emotv.ui.theme.EmotvType
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

object StatisticsTags {
    const val SCOPE_MINE = "statistics_scope_mine"
    const val SCOPE_GROUP = "statistics_scope_group"
    const val TOTAL = "statistics_total"
    const val SESSIONS_CHART = "statistics_sessions_chart"
    const val EXPRESSIONS_CHART = "statistics_expressions_chart"
    const val PRACTICE = "statistics_practice"
}

/**
 * Estadísticas agregadas. Cuentan sesiones, no personas, y no se desglosan por usuario. Las
 * expresiones se nombran como estimadas y se muestran en barras de un solo color, sin valoraciones.
 */
@Composable
fun StatisticsScreen(
    state: StatisticsUiState,
    onRefresh: () -> Unit,
    onChooseScope: (StatisticsScope) -> Unit,
    onBack: () -> Unit,
) {
    val data = state.data
    ContentScreen(
        title = stringResource(R.string.statistics_title),
        subtitle = data.content?.notice,
        onBack = onBack,
        isLoading = data.isLoading,
        isRefreshing = data.isRefreshing,
        error = data.error,
        // El selector de alcance se ve aunque los datos estén cargando.
        hasContent = true,
        onRefresh = onRefresh,
    ) {
        if (state.canChooseScope) {
            item(key = "scope") {
                Column {
                    RadioRow(
                        text = stringResource(R.string.statistics_scope_mine),
                        selected = state.scope == StatisticsScope.Mine,
                        onSelect = { onChooseScope(StatisticsScope.Mine) },
                        modifier = Modifier.testTag(StatisticsTags.SCOPE_MINE),
                    )
                    RadioRow(
                        text = stringResource(R.string.statistics_scope_group),
                        selected = state.scope == StatisticsScope.Group,
                        onSelect = { onChooseScope(StatisticsScope.Group) },
                        modifier = Modifier.testTag(StatisticsTags.SCOPE_GROUP),
                    )
                }
            }
        }
        val statistics = data.content
        if (statistics == null) {
            if (data.isLoading) item(key = "loading") { pe.unheval.emotv.ui.components.LoadingIndicator() }
            return@ContentScreen
        }
        statistics.fewSessionsNotice?.let { notice ->
            item(key = "few") { Callout(title = notice, body = null, tone = CalloutTone.Neutral) }
        }
        item(key = "sessions") { SessionsCard(statistics) }
        item(key = "expressions") { ExpressionsCard(statistics) }
        item(key = "coincidence") { CoincidenceCard(statistics) }
        // Aparte de la coincidencia con estímulos: en la práctica se pide la expresión.
        statistics.practice?.let { practice -> item(key = "practice") { PracticeCard(practice) } }
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
private fun SessionsCard(statistics: SessionStatistics) {
    EmotvCard {
        CardTitle(stringResource(R.string.statistics_sessions_title))
        Column {
            Figure(stringResource(R.string.statistics_total), statistics.total.toString(), Modifier.testTag(StatisticsTags.TOTAL))
            HorizontalDivider(color = EmotvColors.Hairline)
            val average = statistics.averageDurationSeconds?.roundToInt()?.let {
                stringResource(R.string.statistics_duration, it / 60, it % 60)
            } ?: stringResource(R.string.statistics_no_value)
            Figure(stringResource(R.string.statistics_average), average)
        }
        if (statistics.total > 0) {
            BarChart(
                rows = listOf(
                    BarChartRow(
                        key = "completed",
                        label = stringResource(R.string.statistics_completed),
                        value = statistics.completed.toDouble(),
                        valueText = stringResource(R.string.statistics_of_total, statistics.completed, statistics.total),
                    ),
                    BarChartRow(
                        key = "interrupted",
                        label = stringResource(R.string.statistics_interrupted),
                        value = statistics.interrupted.toDouble(),
                        valueText = stringResource(R.string.statistics_of_total, statistics.interrupted, statistics.total),
                    ),
                ),
                max = statistics.total.toDouble(),
                modifier = Modifier.testTag(StatisticsTags.SESSIONS_CHART),
            )
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = EmotvSpacing.Xs)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = EmotvType.BodyMd, color = EmotvColors.Charcoal, modifier = Modifier.weight(1f))
        Text(value, style = EmotvType.ReadoutMd, color = EmotvColors.Ink)
    }
}

@Composable
private fun ExpressionsCard(statistics: SessionStatistics) {
    val percent = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-PE")).apply { maximumFractionDigits = 1 }
    EmotvCard {
        CardTitle(stringResource(R.string.statistics_expressions_title))
        Text(
            text = stringResource(R.string.statistics_expressions_hint, statistics.countedCaptures, statistics.excludedCaptures),
            style = EmotvType.BodySm,
            color = EmotvColors.Slate,
        )
        if (statistics.countedCaptures > 0) {
            BarChart(
                rows = statistics.expressions.sortedByDescending { it.percent }.map { row ->
                    BarChartRow(
                        key = row.expression,
                        label = expressionLabel(row.expression),
                        value = row.percent,
                        valueText = stringResource(R.string.statistics_percent, percent.format(row.percent)),
                        detail = pluralStringResource(R.plurals.statistics_captures_in_sessions, row.captures, row.captures) +
                            " " + pluralStringResource(R.plurals.statistics_in_sessions, row.sessions, row.sessions),
                    )
                },
                max = 100.0,
                modifier = Modifier.testTag(StatisticsTags.EXPRESSIONS_CHART),
            )
        }
    }
}

@Composable
private fun CoincidenceCard(statistics: SessionStatistics) {
    EmotvCard {
        CardTitle(stringResource(R.string.statistics_coincidence_title))
        if (statistics.compared > 0) {
            BarChart(
                rows = listOf(
                    BarChartRow(
                        key = "matched",
                        label = stringResource(R.string.statistics_matched),
                        value = statistics.matched.toDouble(),
                        valueText = stringResource(R.string.statistics_of_total, statistics.matched, statistics.compared),
                    ),
                ),
                max = statistics.compared.toDouble(),
            )
        }
        Text(
            text = if (statistics.compared == 0) {
                stringResource(R.string.statistics_coincidence_none)
            } else {
                stringResource(R.string.statistics_coincidence_body, statistics.matched, statistics.compared)
            },
            style = EmotvType.BodyMd,
            color = EmotvColors.Ink,
        )
    }
}

@Composable
private fun PracticeCard(practice: StatisticsPractice) {
    EmotvCard(modifier = Modifier.testTag(StatisticsTags.PRACTICE)) {
        CardTitle(stringResource(R.string.practice_title))
        Text(
            text = pluralStringResource(R.plurals.statistics_practice_sessions, practice.sessions, practice.sessions),
            style = EmotvType.BodySm,
            color = EmotvColors.Slate,
        )
        PracticeSentences(practice.totals)
        PracticeTargetChart(practice.byTarget)
        if (practice.byProtocol.isNotEmpty()) {
            Text(stringResource(R.string.practice_by_protocol), style = EmotvType.BodySmStrong, color = EmotvColors.Ink)
            BarChart(
                rows = practice.byProtocol.mapIndexed { index, row ->
                    practiceBar(
                        key = PracticeTags.protocol(index),
                        label = row.name,
                        counts = row.counts,
                        prefix = pluralStringResource(R.plurals.statistics_in_sessions, row.sessions, row.sessions),
                    )
                },
                max = 1.0,
            )
        }
        if (practice.notice.isNotEmpty()) Callout(title = practice.notice, body = null, tone = CalloutTone.Neutral)
    }
}
