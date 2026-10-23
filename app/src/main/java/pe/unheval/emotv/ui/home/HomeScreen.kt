package pe.unheval.emotv.ui.home

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pe.unheval.emotv.R
import pe.unheval.emotv.domain.model.AuthUser
import pe.unheval.emotv.ui.components.Callout
import pe.unheval.emotv.ui.components.CalloutTone
import pe.unheval.emotv.ui.components.FormScreen
import pe.unheval.emotv.ui.components.PageTitle
import pe.unheval.emotv.ui.components.SecondaryButton
import pe.unheval.emotv.ui.components.SecureWindow
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvType

/** Inicio provisional: aquí irá el dashboard con el resumen del día (participantes y sesiones). */
@Composable
fun HomeScreen(user: AuthUser?, isLoggingOut: Boolean, onLogout: () -> Unit) {
    SecureWindow()
    FormScreen {
        PageTitle(stringResource(R.string.home_title))
        if (user != null) {
            Text(
                text = stringResource(R.string.home_signed_in_as, user.roleName, user.email),
                style = EmotvType.BodyMd,
                color = EmotvColors.Charcoal,
            )
        }
        Callout(
            title = stringResource(R.string.home_summary_pending),
            body = null,
            tone = CalloutTone.Neutral,
        )
        SecondaryButton(
            text = stringResource(R.string.action_logout),
            onClick = onLogout,
            loading = isLoggingOut,
        )
    }
}
