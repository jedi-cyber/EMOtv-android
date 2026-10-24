package pe.unheval.emotv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvShapes
import pe.unheval.emotv.ui.theme.EmotvSpacing
import pe.unheval.emotv.ui.theme.EmotvType

/** Estados del sistema (nunca expresiones ni emociones). */
enum class CalloutTone(val accent: Color, val background: Color) {
    Error(EmotvColors.Error, EmotvColors.ErrorSoft),
    Warning(EmotvColors.Warning, EmotvColors.WarningSoft),
    Success(EmotvColors.Success, EmotvColors.SuccessSoft),
    Neutral(EmotvColors.Slate, EmotvColors.NeutralSoft),
}

/**
 * `callout`: dice qué pasó y qué hacer; la acción va como botón dentro del aviso.
 * Se anuncia a los lectores de pantalla al aparecer.
 */
@Composable
fun Callout(
    title: String,
    body: String?,
    tone: CalloutTone,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(EmotvShapes.Md)
            .background(tone.background)
            .drawBehind {
                drawRect(tone.accent, topLeft = Offset.Zero, size = Size(3.dp.toPx(), size.height))
            }
            .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite }
            .padding(start = EmotvSpacing.Md + 3.dp, end = EmotvSpacing.Md)
            .padding(vertical = EmotvSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xs),
    ) {
        Text(text = title, style = EmotvType.BodySmStrong, color = EmotvColors.Ink)
        if (body != null) {
            Text(text = body, style = EmotvType.BodySm, color = EmotvColors.Charcoal)
        }
        actions()
    }
}
