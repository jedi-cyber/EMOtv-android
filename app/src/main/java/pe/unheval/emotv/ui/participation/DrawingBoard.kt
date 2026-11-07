package pe.unheval.emotv.ui.participation

import androidx.annotation.StringRes
import pe.unheval.emotv.R

/**
 * Trazos del dibujo táctil, solo en memoria (en el ViewModel, que sobrevive al giro
 * de la pantalla). Nunca se escriben en disco ni en el estado guardado de la
 * actividad, no se envían al servidor y EMOtv no los interpreta. Cada punto es
 * relativo al lienzo (de 0 a 1), así el dibujo se adapta si la pantalla gira.
 * Mismas herramientas y límites que el dibujo de la web.
 */
class DrawingBoard(private val maxPoints: Int = MAX_POINTS) {

    private val done = ArrayList<Stroke>()
    private var active: Stroke? = null
    private var activePointer: Long? = null

    var tool: DrawingTool = DrawingTool.Pen
        private set
    var color: InkColor = INK_COLORS.first()
        private set
    var size: InkSize = INK_SIZES[1]
        private set

    /** Trazos terminados más el que se está dibujando. */
    val strokes: List<Stroke> get() = active?.let { done + it } ?: done

    /** Hay algo dibujado (los trazos terminados): habilita Deshacer, Limpiar y Terminé. */
    val hasDrawing: Boolean get() = done.isNotEmpty()

    val drawing: Boolean get() = active != null

    fun pointCount(): Int = done.sumOf { it.size } + (active?.size ?: 0)

    fun selectColor(ink: InkColor) {
        tool = DrawingTool.Pen
        color = ink
    }

    fun selectSize(ink: InkSize) {
        size = ink
    }

    fun toggleEraser() {
        tool = if (tool == DrawingTool.Eraser) DrawingTool.Pen else DrawingTool.Eraser
    }

    /** Empieza un trazo. Un segundo dedo mientras otro dibuja se ignora. */
    fun start(pointer: Long, x: Float, y: Float): Boolean {
        if (active != null || pointCount() >= maxPoints) return false
        active = Stroke(tool, color.argb, size.width).also { it.add(clamp(x), clamp(y)) }
        activePointer = pointer
        return true
    }

    /** Agrega un punto al trazo del mismo dedo, sin pasar el tope de puntos. */
    fun extend(pointer: Long, x: Float, y: Float): Boolean {
        val stroke = active ?: return false
        if (pointer != activePointer || pointCount() >= maxPoints) return false
        stroke.add(clamp(x), clamp(y))
        return true
    }

    fun end(pointer: Long) {
        val stroke = active ?: return
        if (pointer != activePointer) return
        done.add(stroke)
        active = null
        activePointer = null
    }

    /** El gesto se interrumpió (por ejemplo, al girar la pantalla): el trazo se conserva. */
    fun cancel() {
        active?.let { done.add(it) }
        active = null
        activePointer = null
    }

    fun undo() {
        if (active == null && done.isNotEmpty()) done.removeAt(done.lastIndex)
    }

    /** Borra todo el dibujo de la memoria. Las herramientas elegidas se conservan. */
    fun clear() {
        done.clear()
        active = null
        activePointer = null
    }

    private fun clamp(value: Float) = value.coerceIn(0f, 1f)

    companion object {
        /** Tope de puntos por dibujo: la memoria no crece sin límite (igual que la web). */
        const val MAX_POINTS = 40_000
    }
}

enum class DrawingTool { Pen, Eraser }

/** Un trazo: puntos relativos guardados en un arreglo que crece, sin objetos por punto. */
class Stroke(val tool: DrawingTool, val argb: Long, val width: Float) {
    private var data = FloatArray(64)
    var size: Int = 0
        private set

    fun add(x: Float, y: Float) {
        if (size * 2 + 2 > data.size) data = data.copyOf(data.size * 2)
        data[size * 2] = x
        data[size * 2 + 1] = y
        size += 1
    }

    fun x(index: Int): Float = data[index * 2]
    fun y(index: Int): Float = data[index * 2 + 1]
}

/** Cada color lleva su nombre: la elección no depende solo de verlo. */
data class InkColor(val key: String, @StringRes val name: Int, val argb: Long)

/** Grosor relativo al lado menor del lienzo. */
data class InkSize(val key: String, @StringRes val name: Int, val width: Float)

/** Los mismos colores y grosores que la web (web/src/drawing/strokes.ts). */
val INK_COLORS = listOf(
    InkColor("black", R.string.ink_black, 0xFF1F2328),
    InkColor("red", R.string.ink_red, 0xFFCF222E),
    InkColor("blue", R.string.ink_blue, 0xFF0969DA),
    InkColor("green", R.string.ink_green, 0xFF1A7F37),
    InkColor("yellow", R.string.ink_yellow, 0xFFD4A72C),
    InkColor("purple", R.string.ink_purple, 0xFF8250DF),
    InkColor("brown", R.string.ink_brown, 0xFF8A5A2B),
)

val INK_SIZES = listOf(
    InkSize("thin", R.string.ink_thin, 0.008f),
    InkSize("medium", R.string.ink_medium, 0.018f),
    InkSize("thick", R.string.ink_thick, 0.035f),
)

/** El borrador es más ancho que el lápiz del mismo grosor. */
const val ERASER_FACTOR = 2.5f
