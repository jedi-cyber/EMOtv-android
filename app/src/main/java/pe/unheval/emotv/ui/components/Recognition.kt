package pe.unheval.emotv.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import pe.unheval.emotv.R
import pe.unheval.emotv.domain.model.RecognitionOutcome
import pe.unheval.emotv.domain.model.RecognitionTargetResult
import pe.unheval.emotv.domain.model.RecognitionTotals
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvType

/** Claves de las barras de reconocimiento, para [BarChartTags.row]. */
object RecognitionTags {
    fun target(expression: String) = "recognition_target_$expression"
}

/** Frase del servidor tal cual y, aparte, los pasos sin respuesta, que sí cuentan en el total. */
@Composable
fun RecognitionSentences(totals: RecognitionTotals) {
    if (totals.sentence.isNotEmpty()) Text(totals.sentence, style = EmotvType.BodyMd, color = EmotvColors.Ink)
    if (totals.timedOut > 0) {
        Text(
            pluralStringResource(R.plurals.recognition_timed_out, totals.timedOut, totals.timedOut),
            style = EmotvType.BodySm,
            color = EmotvColors.Charcoal,
        )
    }
    totals.averageSecondsToAnswer?.let {
        Text(stringResource(R.string.recognition_average, formatSecondsDecimal(it)), style = EmotvType.BodySm, color = EmotvColors.Charcoal)
    }
}

/**
 * Por emoción de la imagen: en cuántos pasos se eligió esa emoción y qué se eligió en su lugar.
 * Barras de un solo color; la emoción se distingue por su nombre.
 */
@Composable
fun RecognitionTargetChart(rows: List<RecognitionTargetResult>, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) return
    Text(stringResource(R.string.recognition_by_target), style = EmotvType.BodySmStrong, color = EmotvColors.Ink)
    Text(stringResource(R.string.recognition_chart_hint), style = EmotvType.Caption, color = EmotvColors.Slate)
    BarChart(
        rows = rows.map { row ->
            val details = buildList {
                if (row.otherChoices.isNotEmpty()) {
                    // map es inline: expressionLabel se llama dentro del contexto composable.
                    val choices = row.otherChoices.map { "${expressionLabel(it.expression)} (${it.count})" }.joinToString(", ")
                    add(stringResource(R.string.recognition_other_choices, choices))
                }
                if (row.skipped > 0) add(pluralStringResource(R.plurals.practice_skipped, row.skipped, row.skipped))
                if (row.stopped > 0) add(pluralStringResource(R.plurals.practice_stopped, row.stopped, row.stopped))
            }
            BarChartRow(
                key = RecognitionTags.target(row.targetExpression),
                label = expressionLabel(row.targetExpression),
                value = if (row.applied > 0) row.matched.toDouble() / row.applied else 0.0,
                valueText = stringResource(R.string.practice_of_applied, row.matched, row.applied),
                detail = details.joinToString(" · ").ifEmpty { null },
            )
        },
        max = 1.0,
        modifier = modifier,
    )
}

/** Texto de un paso «¿Qué emoción es?» en la línea de tiempo: la imagen y lo que se eligió. */
@Composable
fun recognitionOutcomeText(
    title: String,
    target: String,
    outcome: RecognitionOutcome?,
    chosen: String?,
    secondsToAnswer: Double?,
): String {
    val result = when (outcome) {
        RecognitionOutcome.Answered -> when {
            chosen == null -> stringResource(R.string.recognition_outcome_answered)
            secondsToAnswer != null ->
                stringResource(R.string.recognition_outcome_chose_in, expressionLabel(chosen), formatSecondsDecimal(secondsToAnswer))
            else -> stringResource(R.string.recognition_outcome_chose, expressionLabel(chosen))
        }
        RecognitionOutcome.TimedOut -> stringResource(R.string.recognition_outcome_timed_out)
        RecognitionOutcome.Skipped -> stringResource(R.string.practice_outcome_skipped)
        RecognitionOutcome.Stopped -> stringResource(R.string.practice_outcome_stopped)
        RecognitionOutcome.Unknown, null -> stringResource(R.string.practice_outcome_none)
    }
    val image = title.ifBlank { stringResource(R.string.recognition_image_of, expressionLabel(target)) }
    return stringResource(R.string.timeline_recognition, image, expressionLabel(target), result)
}
