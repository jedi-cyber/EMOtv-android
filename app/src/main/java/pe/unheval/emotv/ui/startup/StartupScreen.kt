package pe.unheval.emotv.ui.startup

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import pe.unheval.emotv.R
import pe.unheval.emotv.ui.components.Callout
import pe.unheval.emotv.ui.components.CalloutTone
import pe.unheval.emotv.ui.components.EmotvLogo
import pe.unheval.emotv.ui.components.FormScreen
import pe.unheval.emotv.ui.components.PageTitle
import pe.unheval.emotv.ui.components.PrimaryButton
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvType

@Composable
fun StartupScreen(state: StartupUiState, onRetry: () -> Unit) {
    FormScreen {
        EmotvLogo(modifier = Modifier.align(Alignment.CenterHorizontally))
        PageTitle(
            text = stringResource(R.string.app_name),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        when (state) {
            StartupUiState.Loading, is StartupUiState.Ready -> {
                CircularProgressIndicator(
                    color = EmotvColors.Primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Text(
                    text = stringResource(R.string.startup_checking),
                    style = EmotvType.BodyMd,
                    color = EmotvColors.Slate,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            StartupUiState.Offline -> RetryCallout(
                title = stringResource(R.string.startup_offline_title),
                body = stringResource(R.string.startup_offline_body),
                tone = CalloutTone.Warning,
                onRetry = onRetry,
            )
            StartupUiState.ServerError -> RetryCallout(
                title = stringResource(R.string.startup_server_error_title),
                body = stringResource(R.string.startup_server_error_body),
                tone = CalloutTone.Error,
                onRetry = onRetry,
            )
            StartupUiState.LoginUnavailable -> RetryCallout(
                title = stringResource(R.string.startup_login_unavailable_title),
                body = stringResource(R.string.startup_login_unavailable_body),
                tone = CalloutTone.Warning,
                onRetry = onRetry,
            )
            is StartupUiState.UpdateRequired -> RetryCallout(
                title = stringResource(R.string.startup_update_title),
                body = stringResource(
                    R.string.startup_update_body,
                    state.currentVersion,
                    state.minVersion,
                ),
                tone = CalloutTone.Warning,
                onRetry = onRetry,
                retryLabel = stringResource(R.string.startup_check_again),
            )
        }
    }
}

@Composable
private fun RetryCallout(
    title: String,
    body: String,
    tone: CalloutTone,
    onRetry: () -> Unit,
    retryLabel: String = stringResource(R.string.action_retry),
) {
    Callout(title = title, body = body, tone = tone) {
        PrimaryButton(text = retryLabel, onClick = onRetry, modifier = Modifier.fillMaxWidth())
    }
}
