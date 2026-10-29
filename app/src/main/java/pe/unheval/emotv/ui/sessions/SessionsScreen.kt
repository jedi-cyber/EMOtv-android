package pe.unheval.emotv.ui.sessions

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import pe.unheval.emotv.R
import pe.unheval.emotv.domain.model.SessionSummary
import pe.unheval.emotv.ui.common.Loadable
import pe.unheval.emotv.ui.components.ContentScreen
import pe.unheval.emotv.ui.components.ContentTags
import pe.unheval.emotv.ui.components.EmotvCard
import pe.unheval.emotv.ui.components.SessionRow
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvType
import java.time.ZoneId

/** Lista de sesiones por código. Tocar una abre su detalle. */
@Composable
fun SessionsScreen(
    state: Loadable<List<SessionSummary>>,
    onRefresh: () -> Unit,
    onOpenSession: (String) -> Unit,
    onBack: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val sessions = state.content.orEmpty()
    ContentScreen(
        title = stringResource(R.string.sessions_title),
        subtitle = stringResource(R.string.sessions_subtitle),
        onBack = onBack,
        isLoading = state.isLoading,
        isRefreshing = state.isRefreshing,
        error = state.error,
        hasContent = state.content != null,
        onRefresh = onRefresh,
    ) {
        if (sessions.isEmpty()) {
            item(key = "empty") {
                Text(stringResource(R.string.sessions_empty), style = EmotvType.BodyMd, color = EmotvColors.Slate)
            }
        } else {
            item(key = "sessions") { SessionList(sessions, zone, onOpenSession) }
        }
    }
}

@Composable
fun SessionList(sessions: List<SessionSummary>, zone: ZoneId, onOpenSession: (String) -> Unit) {
    EmotvCard {
        Column {
            sessions.forEachIndexed { index, session ->
                if (index > 0) HorizontalDivider(color = EmotvColors.Hairline)
                SessionRow(
                    session = session,
                    zone = zone,
                    onClick = { onOpenSession(session.id) },
                    modifier = Modifier.testTag(ContentTags.session(session.id)),
                )
            }
        }
    }
}
