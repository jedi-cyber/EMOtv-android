package pe.unheval.emotv.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import pe.unheval.emotv.R
import pe.unheval.emotv.domain.model.PracticeCounts
import pe.unheval.emotv.domain.model.PracticeOutcome
import pe.unheval.emotv.domain.model.PracticeTargetResult
import pe.unheval.emotv.domain.model.PracticeTotals
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvType
import java.text.NumberFormat
import java.util.Locale

/** Claves de las barras de práctica, para [BarChartTags.row]. */
object PracticeTags {
    fun target(expression: String) = "practice_target_$expression"
    fun protocol(index: Int) = "practice_protocol_$index"
}

/** Frases del servidor tal cual: el aplicativo no valora los ejercicios. */
@Composable
fun PracticeSentences(totals: PracticeTotals) {
    if (totals.sentence.isNotEmpty()) Text(totals.sentence, style = EmotvType.BodyMd, color = EmotvColors.Ink)
    listOfNotNull(totals.averageSentence, totals.feedbackSentence).forEach {
        Text(it, style = EmotvType.BodySm, color = EmotvColors.Charcoal)
    }
}

/**
 * Por expresión pedida: en cuántos de los ejercicios que llegaron a su final se logró. Barras de un
 * solo color; la expresión se distingue por su nombre.
 */
@Composable
fun PracticeTargetChart(rows: List<PracticeTargetResult>, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) return
    Text(stringResource(R.string.practice_by_target), style = EmotvType.BodySmStrong, color = EmotvColors.Ink)
    Text(stringResource(R.string.practice_chart_hint), style = EmotvType.Caption, color = EmotvColors.Slate)
    BarChart(
        rows = rows.map { row ->
            practiceBar(
                key = PracticeTags.target(row.targetExpression),
                label = expressionLabel(row.targetExpression),
                counts = row.counts,
                prefix = null,
            )
        },
        max = 1.0,
        modifier = modifier,
    )
}

/** Una barra con la fracción lograda; los saltados, detenidos y el tiempo promedio van en el detalle. */
@Composable
fun practiceBar(key: String, label: String, counts: PracticeCounts, prefix: String?): BarChartRow {
    val details = buildList {
        prefix?.let(::add)
        if (counts.skipped > 0) add(pluralStringResource(R.plurals.practice_skipped, counts.skipped, counts.skipped))
        if (counts.stopped > 0) add(pluralStringResource(R.plurals.practice_stopped, counts.stopped, counts.stopped))
        counts.averageSecondsToAchieve?.let { add(stringResource(R.string.practice_average, formatSecondsDecimal(it))) }
    }
    return BarChartRow(
        key = key,
        label = label,
        value = if (counts.applied > 0) counts.achieved.toDouble() / counts.applied else 0.0,
        valueText = stringResource(R.string.practice_of_applied, counts.achieved, counts.applied),
        detail = details.joinToString(" · ").ifEmpty { null },
    )
}

/** Texto de un ejercicio en la línea de tiempo: la expresión pedida y lo que se registró. */
@Composable
fun practiceOutcomeText(target: String, outcome: PracticeOutcome?, secondsToAchieve: Double?): String {
    val result = when (outcome) {
        PracticeOutcome.Achieved -> secondsToAchieve
            ?.let { stringResource(R.string.practice_outcome_achieved_in, formatSecondsDecimal(it)) }
            ?: stringResource(R.string.practice_outcome_achieved)
        PracticeOutcome.TimedOut -> stringResource(R.string.practice_outcome_timed_out)
        PracticeOutcome.Skipped -> stringResource(R.string.practice_outcome_skipped)
        PracticeOutcome.Stopped -> stringResource(R.string.practice_outcome_stopped)
        PracticeOutcome.Unknown, null -> stringResource(R.string.practice_outcome_none)
    }
    return stringResource(R.string.timeline_practice, expressionLabel(target), result)
}

private val SecondsFormat = Locale.forLanguageTag("es-PE")

fun formatSecondsDecimal(seconds: Double): String =
    NumberFormat.getNumberInstance(SecondsFormat).apply { maximumFractionDigits = 1 }.format(seconds)
