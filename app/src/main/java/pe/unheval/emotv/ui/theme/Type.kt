package pe.unheval.emotv.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import pe.unheval.emotv.R

/** Atkinson Hyperlegible Next (OFL), incluida en res/font. Sin descargas. */
val AtkinsonHyperlegible = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_semibold, FontWeight.SemiBold),
    Font(R.font.atkinson_bold, FontWeight.Bold),
)

// Los tamaños van en sp para respetar el tamaño de letra del sistema.
private fun style(
    size: Int,
    weight: FontWeight,
    lineHeight: Float,
    letterSpacing: TextUnit = 0.sp,
    fontFeatures: String? = null,
) = TextStyle(
    fontFamily = AtkinsonHyperlegible,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = letterSpacing,
    fontFeatureSettings = fontFeatures,
)

/** Estilos de DESIGN.md con su nombre original. */
object EmotvType {
    val PageTitle = style(36, FontWeight.Bold, 1.15f, letterSpacing = (-0.5).sp)
    val PageTitleCompact = style(28, FontWeight.Bold, 1.15f, letterSpacing = (-0.5).sp)
    val SectionTitle = style(22, FontWeight.Bold, 1.3f)
    val CardTitle = style(18, FontWeight.Bold, 1.35f)
    val Subtitle = style(17, FontWeight.Normal, 1.55f)
    val BodyMd = style(16, FontWeight.Normal, 1.6f)
    val BodySm = style(14, FontWeight.Normal, 1.5f)
    val BodySmStrong = style(14, FontWeight.SemiBold, 1.5f)
    val Caption = style(13, FontWeight.Normal, 1.4f)
    val TableHeader = style(13, FontWeight.SemiBold, 1.4f)
    val Button = style(15, FontWeight.SemiBold, 1.2f)
    val ReadoutLg = style(40, FontWeight.Bold, 1.0f, fontFeatures = "tnum")
    val ReadoutMd = style(20, FontWeight.SemiBold, 1.2f, fontFeatures = "tnum")
}

val Typography = Typography(
    displaySmall = EmotvType.PageTitle,
    headlineLarge = EmotvType.PageTitle,
    headlineMedium = EmotvType.PageTitleCompact,
    headlineSmall = EmotvType.SectionTitle,
    titleLarge = EmotvType.SectionTitle,
    titleMedium = EmotvType.CardTitle,
    titleSmall = EmotvType.BodySmStrong,
    bodyLarge = EmotvType.BodyMd,
    bodyMedium = EmotvType.BodySm,
    bodySmall = EmotvType.Caption,
    labelLarge = EmotvType.Button,
    labelMedium = EmotvType.TableHeader,
    labelSmall = EmotvType.Caption,
)
