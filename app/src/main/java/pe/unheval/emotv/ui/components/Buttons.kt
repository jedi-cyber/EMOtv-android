package pe.unheval.emotv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvShapes
import pe.unheval.emotv.ui.theme.EmotvSpacing
import pe.unheval.emotv.ui.theme.EmotvType

private val ButtonPadding = PaddingValues(horizontal = EmotvSpacing.Lg, vertical = EmotvSpacing.Xs)

/** `button-primary`: uno por vista. Al cargar mantiene el texto y suma un indicador. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = EmotvSpacing.TouchTarget),
        enabled = enabled && !loading,
        shape = EmotvShapes.Md,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (pressed) EmotvColors.PrimaryPressed else EmotvColors.Primary,
            contentColor = EmotvColors.OnPrimary,
            disabledContainerColor = if (loading) EmotvColors.Primary else EmotvColors.NeutralSoft,
            disabledContentColor = if (loading) EmotvColors.OnPrimary else EmotvColors.Steel,
        ),
        contentPadding = ButtonPadding,
        interactionSource = interactionSource,
    ) {
        ButtonContent(text, loading, EmotvColors.OnPrimary)
    }
}

/** `button-secondary`. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = EmotvSpacing.TouchTarget),
        enabled = enabled && !loading,
        shape = EmotvShapes.Md,
        border = BorderStroke(1.dp, EmotvColors.HairlineStrong),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = EmotvColors.Canvas,
            contentColor = EmotvColors.Ink,
            disabledContainerColor = EmotvColors.NeutralSoft,
            disabledContentColor = EmotvColors.Steel,
        ),
        contentPadding = ButtonPadding,
    ) {
        ButtonContent(text, loading, EmotvColors.Ink)
    }
}

/** `button-ghost`. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = EmotvSpacing.TouchTarget),
        enabled = enabled,
        shape = EmotvShapes.Md,
        colors = ButtonDefaults.textButtonColors(
            contentColor = EmotvColors.PrimaryDeep,
            disabledContentColor = EmotvColors.Steel,
        ),
        contentPadding = PaddingValues(horizontal = EmotvSpacing.Sm),
    ) {
        Text(text = text, style = EmotvType.Button)
    }
}

@Composable
private fun RowScope.ButtonContent(text: String, loading: Boolean, indicatorColor: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(EmotvSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = indicatorColor,
                strokeWidth = 2.dp,
            )
        }
        Text(text = text, style = EmotvType.Button)
    }
}
