package pe.unheval.emotv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Radios de DESIGN.md (`rounded`). */
object EmotvShapes {
    val Sm = RoundedCornerShape(6.dp)
    val Md = RoundedCornerShape(8.dp)
    val Lg = RoundedCornerShape(12.dp)
    val Full = RoundedCornerShape(percent = 50)
}

/** Espaciado de DESIGN.md (`spacing`). */
object EmotvSpacing {
    val Xxs = 4.dp
    val Xs = 8.dp
    val Sm = 12.dp
    val Md = 16.dp
    val Lg = 24.dp
    val Xl = 32.dp
    val Xxl = 48.dp
    val Section = 64.dp

    /** Zona táctil mínima del aplicativo (CLAUDE.md), mayor que los 44 px de la web. */
    val TouchTarget = 48.dp
}

val Shapes = Shapes(
    extraSmall = EmotvShapes.Sm,
    small = EmotvShapes.Md,
    medium = EmotvShapes.Md,
    large = EmotvShapes.Lg,
    extraLarge = EmotvShapes.Lg,
)
