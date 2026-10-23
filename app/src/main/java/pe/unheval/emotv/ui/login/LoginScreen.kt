package pe.unheval.emotv.ui.login

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import pe.unheval.emotv.R
import pe.unheval.emotv.ui.components.Callout
import pe.unheval.emotv.ui.components.CalloutTone
import pe.unheval.emotv.ui.components.EmotvLogo
import pe.unheval.emotv.ui.components.EmotvTextField
import pe.unheval.emotv.ui.components.FormScreen
import pe.unheval.emotv.ui.components.GhostButton
import pe.unheval.emotv.ui.components.PageSubtitle
import pe.unheval.emotv.ui.components.PageTitle
import pe.unheval.emotv.ui.components.PrimaryButton

@Composable
fun LoginScreen(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    FormScreen {
        EmotvLogo()
        PageTitle(stringResource(R.string.login_title))
        PageSubtitle(stringResource(R.string.login_subtitle))

        EmotvTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = stringResource(R.string.login_email),
            error = state.emailError?.let { emailErrorText(it) },
            enabled = !state.isLoading,
            modifier = Modifier
                .testTag(LoginTags.EMAIL)
                .semantics { contentType = ContentType.EmailAddress },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                autoCorrectEnabled = false,
            ),
        )

        val toggleDescription = stringResource(
            if (passwordVisible) R.string.login_hide_password_description
            else R.string.login_show_password_description,
        )
        EmotvTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.login_password),
            error = state.passwordError?.let { stringResource(R.string.login_password_required) },
            enabled = !state.isLoading,
            modifier = Modifier
                .testTag(LoginTags.PASSWORD)
                .semantics { contentType = ContentType.Password },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onSubmit()
            }),
            visualTransformation =
                if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                GhostButton(
                    text = stringResource(
                        if (passwordVisible) R.string.login_hide_password else R.string.login_show_password,
                    ),
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.semantics { contentDescription = toggleDescription },
                )
            },
        )

        state.error?.let { error ->
            Callout(
                title = loginErrorTitle(error),
                body = loginErrorBody(error),
                tone = CalloutTone.Error,
                modifier = Modifier.testTag(LoginTags.ERROR),
            )
        }

        PrimaryButton(
            text = stringResource(R.string.login_submit),
            onClick = {
                focusManager.clearFocus()
                onSubmit()
            },
            loading = state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LoginTags.SUBMIT),
        )
    }
}

object LoginTags {
    const val EMAIL = "login_email"
    const val PASSWORD = "login_password"
    const val SUBMIT = "login_submit"
    const val ERROR = "login_error"
}

@Composable
private fun emailErrorText(error: FieldError): String = when (error) {
    FieldError.Required -> stringResource(R.string.login_email_required)
    FieldError.InvalidEmail -> stringResource(R.string.login_email_invalid)
}

@Composable
private fun loginErrorTitle(error: LoginError): String = when (error) {
    is LoginError.Server -> error.message ?: stringResource(R.string.login_error_generic)
    is LoginError.TooManyAttempts -> error.message ?: stringResource(R.string.login_error_too_many)
    LoginError.Network -> stringResource(R.string.login_error_network)
    LoginError.Unexpected -> stringResource(R.string.login_error_generic)
}

@Composable
private fun loginErrorBody(error: LoginError): String? = when (error) {
    is LoginError.Server -> null
    is LoginError.TooManyAttempts -> error.retryAfterSeconds?.let { seconds ->
        val minutes = ((seconds + 59) / 60).toInt().coerceAtLeast(1)
        pluralStringResource(R.plurals.login_retry_after_minutes, minutes, minutes)
    }
    LoginError.Network -> stringResource(R.string.login_error_network_body)
    LoginError.Unexpected -> stringResource(R.string.login_error_generic_body)
}
