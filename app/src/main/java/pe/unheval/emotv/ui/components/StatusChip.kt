package pe.unheval.emotv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvShapes
import pe.unheval.emotv.ui.theme.EmotvSpacing
import pe.unheval.emotv.ui.theme.EmotvType

/** Colores de `status-chip` (DESIGN.md). Solo estados del sistema, nunca expresiones. */
enum class ChipTone(val content: Color, val background: Color) {
    Success(EmotvColors.Success, EmotvColors.SuccessSoft),
    InProgress(EmotvColors.PrimaryDeep, EmotvColors.PrimarySoft),
    Neutral(EmotvColors.Slate, EmotvColors.NeutralSoft),
    Warning(EmotvColors.Warning, EmotvColors.WarningSoft),
    Error(EmotvColors.Error, EmotvColors.ErrorSoft),
}

/** `status-chip`: punto de 6 dp más texto, para que el estado no dependa solo del color. */
@Composable
fun StatusChip(text: String, tone: ChipTone, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(EmotvShapes.Full)
            .background(tone.background)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(EmotvSpacing.Xs - 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(EmotvShapes.Full)
                .background(tone.content),
        )
        Text(text = text, style = EmotvType.Caption, color = tone.content)
    }
}
