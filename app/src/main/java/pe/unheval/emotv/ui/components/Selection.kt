package pe.unheval.emotv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvSpacing
import pe.unheval.emotv.ui.theme.EmotvType

/** `checkbox`: todo el texto es parte de la etiqueta y toda la fila responde al toque. */
@Composable
fun CheckboxRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textColor: Color = EmotvColors.Ink,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = EmotvSpacing.TouchTarget)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.spacedBy(EmotvSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = EmotvColors.Primary,
                uncheckedColor = EmotvColors.HairlineStrong,
                checkmarkColor = EmotvColors.OnPrimary,
            ),
        )
        Text(text = text, style = EmotvType.BodyMd, color = if (enabled) textColor else EmotvColors.Steel)
    }
}

/** Opción única con la fila completa como zona táctil. */
@Composable
fun RadioRow(
    text: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textColor: Color = EmotvColors.Ink,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = EmotvSpacing.TouchTarget)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        horizontalArrangement = Arrangement.spacedBy(EmotvSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(
                selectedColor = EmotvColors.Primary,
                unselectedColor = EmotvColors.HairlineStrong,
            ),
        )
        Text(text = text, style = EmotvType.BodyMd, color = if (enabled) textColor else EmotvColors.Steel)
    }
}
