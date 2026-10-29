package pe.unheval.emotv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvSpacing
import pe.unheval.emotv.ui.theme.EmotvType

/** Una barra del gráfico. [detail] se muestra debajo de la etiqueta y se lee con el lector de pantalla. */
data class BarChartRow(
    val key: String,
    val label: String,
    val value: Double,
    val valueText: String,
    val detail: String? = null,
)

object BarChartTags {
    fun row(key: String) = "bar_chart_row_$key"
}

/**
 * Gráfico de barras horizontales de una sola serie. Todas las barras tienen el mismo color:
 * las expresiones no se colorean ni se valoran; la identidad la da la etiqueta.
 * [max] es el valor que llena la barra entera; si falta, el mayor de las filas.
 */
@Composable
fun BarChart(rows: List<BarChartRow>, modifier: Modifier = Modifier, max: Double? = null) {
    val top = max ?: rows.maxOfOrNull { it.value } ?: 0.0
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xs)) {
        rows.forEach { row ->
            val fraction = if (top > 0) (row.value / top).coerceIn(0.0, 1.0).toFloat() else 0f
            val description = listOfNotNull(row.label, row.valueText, row.detail).joinToString(", ")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(BarChartTags.row(row.key))
                    .clearAndSetSemantics { contentDescription = description },
                verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xxs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.label, style = EmotvType.BodySmStrong, color = EmotvColors.Charcoal, modifier = Modifier.weight(1f))
                    Text(
                        text = row.valueText,
                        style = EmotvType.BodySmStrong,
                        color = EmotvColors.Ink,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .widthIn(min = 56.dp)
                            .padding(start = EmotvSpacing.Sm),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(EmotvColors.NeutralSoft, RoundedCornerShape(4.dp)),
                ) {
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .widthIn(min = 4.dp)
                                .background(EmotvColors.Primary, RoundedCornerShape(4.dp)),
                        )
                    }
                }
                row.detail?.let { Text(it, style = EmotvType.Caption, color = EmotvColors.Slate) }
            }
        }
    }
}
