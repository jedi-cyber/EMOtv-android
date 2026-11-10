package pe.unheval.emotv.ui.participation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingBoardTest {

    private fun DrawingBoard.line(pointer: Long = 1, vararg points: Pair<Float, Float>) {
        val (x, y) = points.first()
        start(pointer, x, y)
        points.drop(1).forEach { (px, py) -> extend(pointer, px, py) }
        end(pointer)
    }

    @Test
    fun `un solo dedo dibuja y otro toque se ignora`() {
        val board = DrawingBoard()
        assertTrue(board.start(1, 0.1f, 0.1f))
        assertFalse(board.start(2, 0.5f, 0.5f))
        assertFalse(board.extend(2, 0.6f, 0.6f))
        assertTrue(board.extend(1, 0.2f, 0.2f))
        board.end(2)
        assertTrue(board.drawing)
        board.end(1)
        assertEquals(1, board.strokes.size)
        assertEquals(2, board.strokes.single().size)
        assertTrue(board.hasDrawing)
    }

    @Test
    fun `los puntos son relativos y quedan dentro del lienzo`() {
        val board = DrawingBoard()
        board.line(1, -0.5f to 0.5f, 1.4f to 2f)
        val stroke = board.strokes.single()
        assertEquals(0f, stroke.x(0))
        assertEquals(1f, stroke.x(1))
        assertEquals(1f, stroke.y(1))
    }

    @Test
    fun `deshacer quita el ultimo trazo y limpiar borra todo`() {
        val board = DrawingBoard()
        board.line(1, 0.1f to 0.1f)
        board.line(2, 0.2f to 0.2f, 0.3f to 0.3f)
        board.undo()
        assertEquals(1, board.strokes.size)
        board.line(3, 0.4f to 0.4f)
        board.clear()
        assertTrue(board.strokes.isEmpty())
        assertFalse(board.hasDrawing)
        assertEquals(0, board.pointCount())
    }

    @Test
    fun `el borrador y el grosor quedan en el trazo y elegir un color vuelve al lapiz`() {
        val board = DrawingBoard()
        board.selectSize(INK_SIZES[2])
        board.toggleEraser()
        board.line(1, 0.5f to 0.5f)
        val erased = board.strokes.single()
        assertEquals(DrawingTool.Eraser, erased.tool)
        assertEquals(INK_SIZES[2].width, erased.width)
        board.selectColor(INK_COLORS[1])
        assertEquals(DrawingTool.Pen, board.tool)
        board.line(2, 0.1f to 0.1f)
        assertEquals(INK_COLORS[1].argb, board.strokes.last().argb)
    }

    @Test
    fun `la memoria tiene un tope de puntos`() {
        val board = DrawingBoard(maxPoints = 5)
        board.start(1, 0f, 0f)
        repeat(10) { board.extend(1, it / 10f, 0f) }
        board.end(1)
        assertEquals(5, board.pointCount())
        assertFalse(board.start(2, 0.5f, 0.5f))
    }

    @Test
    fun `un gesto interrumpido conserva el trazo`() {
        val board = DrawingBoard()
        board.start(1, 0.1f, 0.1f)
        board.extend(1, 0.2f, 0.2f)
        board.cancel()
        assertFalse(board.drawing)
        assertEquals(1, board.strokes.size)
        assertTrue(board.start(7, 0.3f, 0.3f))
    }

    @Test
    fun `los colores y grosores son los de la web`() {
        assertEquals(listOf("black", "red", "blue", "green", "yellow", "purple", "brown"), INK_COLORS.map { it.key })
        assertEquals(listOf(0.008f, 0.018f, 0.035f), INK_SIZES.map { it.width })
        assertEquals(40_000, DrawingBoard.MAX_POINTS)
    }
}
