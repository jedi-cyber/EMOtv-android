package pe.unheval.emotv.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import pe.unheval.emotv.R
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvSpacing
import pe.unheval.emotv.ui.theme.EmotvType

// Jornada mínima que muestra el gráfico, para que pocas sesiones no ocupen todo el ancho.
private const val DAY_START = 8
private const val DAY_END = 18

/**
 * Tendencia de las sesiones de hoy por hora, dibujada con Canvas. Es lo único que el
 * resumen permite graficar: la API no devuelve resultados ni historial. Las barras usan
 * un solo color porque representan cantidad de sesiones, no expresiones.
 */
@Composable
fun ActivityChart(activity: HourlyActivity, modifier: Modifier = Modifier) {
    val first = activity.firstHour ?: return
    val last = activity.lastHour ?: return
    val busiest = activity.busiestHour ?: return
    val start = minOf(first, DAY_START)
    val end = maxOf(last, DAY_END)
    val counts = activity.counts.subList(start, end + 1)
    val max = counts.max().coerceAtLeast(1)

    val description = stringResource(
        R.string.dashboard_chart_description,
        activity.total.toString(),
        hourLabel(first),
        hourLabel(last),
        hourLabel(busiest),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xs),
    ) {
        Text(
            text = stringResource(R.string.dashboard_chart_title),
            style = EmotvType.BodySmStrong,
            color = EmotvColors.Charcoal,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
        ) {
            val baseline = size.height - 1.dp.toPx()
            val slot = size.width / counts.size
            val barWidth = slot * 0.6f
            counts.forEachIndexed { index, count ->
                if (count == 0) return@forEachIndexed
                val barHeight = baseline * count / max
                drawRoundRect(
                    color = EmotvColors.Primary,
                    topLeft = Offset(index * slot + (slot - barWidth) / 2, baseline - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            drawLine(
                color = EmotvColors.HairlineStrong,
                start = Offset(0f, baseline),
                end = Offset(size.width, baseline),
                strokeWidth = 1.dp.toPx(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = hourLabel(start), style = EmotvType.Caption, color = EmotvColors.Steel)
            Text(text = hourLabel(end), style = EmotvType.Caption, color = EmotvColors.Steel)
        }
    }
}

@Composable
private fun hourLabel(hour: Int): String = stringResource(R.string.dashboard_hour, hour)
