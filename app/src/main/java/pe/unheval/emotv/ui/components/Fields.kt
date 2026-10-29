package pe.unheval.emotv.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvShapes
import pe.unheval.emotv.ui.theme.EmotvType

/** `text-input` con etiqueta visible y error anunciado por los lectores de pantalla. */
@Composable
fun EmotvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    /** Varias líneas para textos largos, como el del reporte. */
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (error != null) error(error) },
        enabled = enabled,
        label = { Text(label, style = EmotvType.BodySm) },
        textStyle = EmotvType.BodyMd,
        isError = error != null,
        supportingText = error?.let { { Text(it, style = EmotvType.Caption) } },
        singleLine = minLines == 1,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        shape = EmotvShapes.Md,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = EmotvColors.Canvas,
            unfocusedContainerColor = EmotvColors.Canvas,
            disabledContainerColor = EmotvColors.NeutralSoft,
            errorContainerColor = EmotvColors.Canvas,
            focusedBorderColor = EmotvColors.FocusRing,
            unfocusedBorderColor = EmotvColors.HairlineStrong,
            errorBorderColor = EmotvColors.Error,
            focusedLabelColor = EmotvColors.PrimaryDeep,
            unfocusedLabelColor = EmotvColors.Slate,
            errorLabelColor = EmotvColors.Error,
            errorSupportingTextColor = EmotvColors.Error,
            cursorColor = EmotvColors.Primary,
            focusedTextColor = EmotvColors.Ink,
            unfocusedTextColor = EmotvColors.Ink,
        ),
    )
}
