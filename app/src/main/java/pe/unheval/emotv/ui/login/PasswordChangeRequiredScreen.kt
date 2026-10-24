package pe.unheval.emotv.ui.login

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pe.unheval.emotv.R
import pe.unheval.emotv.ui.components.Callout
import pe.unheval.emotv.ui.components.CalloutTone
import pe.unheval.emotv.ui.components.FormScreen
import pe.unheval.emotv.ui.components.PageSubtitle
import pe.unheval.emotv.ui.components.PageTitle
import pe.unheval.emotv.ui.components.PrimaryButton

/**
 * `403 password_change_required`: la API móvil no tiene un endpoint para cambiar la
 * contraseña (docs/mobile-api.md indica cambiarla en la web), así que se explica cómo
 * hacerlo y se vuelve al inicio de sesión.
 */
@Composable
fun PasswordChangeRequiredScreen(serverMessage: String?, onBackToLogin: () -> Unit) {
    FormScreen {
        PageTitle(stringResource(R.string.password_change_title))
        PageSubtitle(stringResource(R.string.password_change_subtitle))
        Callout(
            title = serverMessage ?: stringResource(R.string.password_change_default_message),
            body = stringResource(R.string.password_change_body),
            tone = CalloutTone.Warning,
        )
        PrimaryButton(
            text = stringResource(R.string.password_change_back),
            onClick = onBackToLogin,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
