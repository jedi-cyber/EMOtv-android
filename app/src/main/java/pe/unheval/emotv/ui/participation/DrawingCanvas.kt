package pe.unheval.emotv.ui.participation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as StrokeStyle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvShapes
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Lienzo táctil. Un solo dedo dibuja: otro toque mientras tanto (la palma, otro
 * dedo) se ignora. Los puntos se pasan relativos al lienzo, así el dibujo se
 * redibuja igual al girar la pantalla. [revision] solo obliga a redibujar.
 */
@Composable
fun DrawingCanvas(
    board: DrawingBoard,
    revision: Int,
    enabled: Boolean,
    description: String,
    onStart: (Long, Float, Float) -> Boolean,
    onMove: (Long, Float, Float) -> Unit,
    onEnd: (Long) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .border(width = 1.dp, color = EmotvColors.HairlineStrong, shape = EmotvShapes.Md)
            .background(EmotvColors.Canvas, EmotvShapes.Md)
            // Capa propia: el borrador quita la tinta y deja ver el fondo blanco.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .semantics { contentDescription = description }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val pointer = down.id.value
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    if (!onStart(pointer, down.position.x / width, down.position.y / height)) return@awaitEachGesture
                    down.consume()
                    var finished = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.historical.forEach { onMove(pointer, it.position.x / width, it.position.y / height) }
                            onMove(pointer, change.position.x / width, change.position.y / height)
                            change.consume()
                            if (!change.pressed) break
                        }
                        onEnd(pointer)
                        finished = true
                    } finally {
                        // El gesto se interrumpió (giro, otra ventana): el trazo se conserva.
                        if (!finished) onCancel()
                    }
                }
            },
    ) {
        if (revision < 0) return@Canvas  // solo se lee para volver a dibujar
        board.strokes.forEach { drawStroke(it) }
    }
}

private fun DrawScope.drawStroke(stroke: Stroke) {
    if (stroke.size == 0) return
    val eraser = stroke.tool == DrawingTool.Eraser
    val width = (stroke.width * min(size.width, size.height) * (if (eraser) ERASER_FACTOR else 1f)).coerceAtLeast(1f)
    val color = if (eraser) Color.Black else Color(stroke.argb)
    val blend = if (eraser) BlendMode.Clear else BlendMode.SrcOver
    if (stroke.size == 1) {
        // Un toque sin arrastrar deja un punto.
        drawCircle(color, radius = width / 2, center = Offset(stroke.x(0) * size.width, stroke.y(0) * size.height),
            blendMode = blend)
        return
    }
    val path = Path().apply {
        moveTo(stroke.x(0) * size.width, stroke.y(0) * size.height)
        for (index in 1 until stroke.size) lineTo(stroke.x(index) * size.width, stroke.y(index) * size.height)
    }
    drawPath(path, color, style = StrokeStyle(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
        blendMode = blend)
}
