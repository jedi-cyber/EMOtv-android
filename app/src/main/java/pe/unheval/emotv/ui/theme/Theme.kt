package pe.unheval.emotv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// DESIGN.md no define tema oscuro y pide mantener el azul marino, el violeta y el fondo
// cálido, así que no hay esquema oscuro ni color dinámico del sistema.
private val EmotvColorScheme = lightColorScheme(
    primary = EmotvColors.Primary,
    onPrimary = EmotvColors.OnPrimary,
    primaryContainer = EmotvColors.PrimarySoft,
    onPrimaryContainer = EmotvColors.PrimaryDeep,
    inversePrimary = EmotvColors.FrameOnDark,
    secondary = EmotvColors.Navy,
    onSecondary = EmotvColors.OnDark,
    secondaryContainer = EmotvColors.NeutralSoft,
    onSecondaryContainer = EmotvColors.Charcoal,
    tertiary = EmotvColors.NavyMid,
    onTertiary = EmotvColors.OnDark,
    tertiaryContainer = EmotvColors.NeutralSoft,
    onTertiaryContainer = EmotvColors.Charcoal,
    background = EmotvColors.Surface,
    onBackground = EmotvColors.Ink,
    surface = EmotvColors.Canvas,
    onSurface = EmotvColors.Ink,
    surfaceVariant = EmotvColors.NeutralSoft,
    onSurfaceVariant = EmotvColors.Slate,
    surfaceTint = EmotvColors.Canvas,
    surfaceBright = EmotvColors.Canvas,
    surfaceDim = EmotvColors.Surface,
    surfaceContainerLowest = EmotvColors.Canvas,
    surfaceContainerLow = EmotvColors.Canvas,
    surfaceContainer = EmotvColors.Canvas,
    surfaceContainerHigh = EmotvColors.Canvas,
    surfaceContainerHighest = EmotvColors.NeutralSoft,
    inverseSurface = EmotvColors.Navy,
    inverseOnSurface = EmotvColors.OnDark,
    outline = EmotvColors.HairlineStrong,
    outlineVariant = EmotvColors.Hairline,
    error = EmotvColors.Error,
    onError = EmotvColors.OnPrimary,
    errorContainer = EmotvColors.ErrorSoft,
    onErrorContainer = EmotvColors.Error,
    scrim = EmotvColors.NavyDeep,
)

@Composable
fun EMOtvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EmotvColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
