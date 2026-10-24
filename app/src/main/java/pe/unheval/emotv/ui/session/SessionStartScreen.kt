package pe.unheval.emotv.ui.session

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
import pe.unheval.emotv.ui.components.SecureWindow

/**
 * Destino provisional del botón «Iniciar sesión con un participante». Aquí irán la
 * elección del protocolo (`GET /mobile/protocols`) y el modo sesión bloqueado.
 */
@Composable
fun SessionStartScreen(participantCode: String, onBack: () -> Unit) {
    SecureWindow()
    FormScreen {
        PageTitle(stringResource(R.string.session_start_title))
        PageSubtitle(stringResource(R.string.session_start_subtitle, participantCode))
        Callout(
            title = stringResource(R.string.session_start_pending),
            body = stringResource(R.string.session_start_pending_body),
            tone = CalloutTone.Neutral,
        )
        PrimaryButton(
            text = stringResource(R.string.session_start_back),
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
