package pe.unheval.emotv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvShapes

/**
 * `camera-frame` (DESIGN.md): cuatro marcas en L en las esquinas, que significan «aquí
 * EMOtv está observando». Solo sobre video de la cámara.
 */
@Composable
fun CameraFrame(
    modifier: Modifier = Modifier,
    frameColor: Color = EmotvColors.FrameOnDark,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(EmotvShapes.Lg)
            .background(EmotvColors.NavyDeep)
            .drawWithContent {
                drawContent()
                val arm = 16.dp.toPx()
                val stroke = 2.dp.toPx()
                val inset = 12.dp.toPx()
                val left = inset
                val top = inset
                val right = size.width - inset
                val bottom = size.height - inset
                fun line(from: Offset, to: Offset) =
                    drawLine(frameColor, from, to, strokeWidth = stroke, cap = StrokeCap.Square)
                line(Offset(left, top), Offset(left + arm, top))
                line(Offset(left, top), Offset(left, top + arm))
                line(Offset(right, top), Offset(right - arm, top))
                line(Offset(right, top), Offset(right, top + arm))
                line(Offset(left, bottom), Offset(left + arm, bottom))
                line(Offset(left, bottom), Offset(left, bottom - arm))
                line(Offset(right, bottom), Offset(right - arm, bottom))
                line(Offset(right, bottom), Offset(right, bottom - arm))
            },
        content = content,
    )
}
