package pe.unheval.emotv.ui.pin

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import pe.unheval.emotv.R
import pe.unheval.emotv.ui.components.EmotvTextField
import pe.unheval.emotv.ui.components.FormScreen
import pe.unheval.emotv.ui.components.PageSubtitle
import pe.unheval.emotv.ui.components.PageTitle
import pe.unheval.emotv.ui.components.PrimaryButton
import pe.unheval.emotv.ui.components.SecureWindow

@Composable
fun PinSetupScreen(
    state: PinSetupUiState,
    onPinChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    SecureWindow()
    val focusManager = LocalFocusManager.current

    FormScreen {
        PageTitle(stringResource(R.string.pin_title))
        PageSubtitle(stringResource(R.string.pin_subtitle))

        EmotvTextField(
            value = state.pin,
            onValueChange = onPinChange,
            label = stringResource(R.string.pin_label),
            error = state.pinError?.let { stringResource(R.string.pin_error_length) },
            enabled = !state.isSaving,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Next,
            ),
            visualTransformation = PasswordVisualTransformation(),
        )
        EmotvTextField(
            value = state.confirmation,
            onValueChange = onConfirmationChange,
            label = stringResource(R.string.pin_confirm_label),
            error = state.confirmationError?.let { stringResource(R.string.pin_error_mismatch) },
            enabled = !state.isSaving,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onSubmit()
            }),
            visualTransformation = PasswordVisualTransformation(),
        )
        PrimaryButton(
            text = stringResource(R.string.pin_submit),
            onClick = {
                focusManager.clearFocus()
                onSubmit()
            },
            loading = state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
