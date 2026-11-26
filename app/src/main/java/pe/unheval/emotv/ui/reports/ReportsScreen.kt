package pe.unheval.emotv.ui.reports

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import pe.unheval.emotv.R
import pe.unheval.emotv.domain.model.ReportStatus
import pe.unheval.emotv.ui.components.ContentScreen
import pe.unheval.emotv.ui.components.ContentTags
import pe.unheval.emotv.ui.components.EmotvCard
import pe.unheval.emotv.ui.components.ErrorCallout
import pe.unheval.emotv.ui.components.SectionHeader
import pe.unheval.emotv.ui.components.SessionRow
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvType
import java.time.ZoneId

object ReportsTags {
    fun group(status: ReportStatus) = "reports_group_${status.name}"
}

/** Módulo «Reportes»: los reportes agrupados por estado. Tocar uno lo abre. */
@Composable
fun ReportsScreen(
    state: ReportsUiState,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val groups = state.data.content.orEmpty()
    ContentScreen(
        title = stringResource(R.string.reports_title),
        subtitle = stringResource(R.string.reports_subtitle),
        onBack = onBack,
        isLoading = state.data.isLoading,
        isRefreshing = state.data.isRefreshing,
        error = state.data.error,
        hasContent = state.data.content != null,
        onRefresh = onRefresh,
    ) {
        state.actionError?.let { error ->
            item(key = "action_error") { ErrorCallout(error = error, stale = false, onRetry = null) }
        }
        if (groups.isEmpty()) {
            item(key = "empty") {
                Text(stringResource(R.string.reports_empty), style = EmotvType.BodyMd, color = EmotvColors.Slate)
            }
            return@ContentScreen
        }
        groups.forEach { group ->
            item(key = "header_${group.status.name}") {
                SectionHeader(
                    title = stringResource(groupTitle(group.status), group.sessions.size),
                    hint = groupHint(group.status)?.let { stringResource(it) },
                )
            }
            item(key = "group_${group.status.name}") {
                EmotvCard(modifier = Modifier.testTag(ReportsTags.group(group.status))) {
                    Column {
                        group.sessions.forEachIndexed { index, session ->
                            if (index > 0) HorizontalDivider(color = EmotvColors.Hairline)
                            SessionRow(
                                session = session,
                                zone = zone,
                                onClick = { onOpen(session.id) },
                                modifier = Modifier.testTag(ContentTags.session(session.id)),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun groupTitle(status: ReportStatus): Int = when (status) {
    ReportStatus.ChangesRequested -> R.string.reports_group_changes_requested
    ReportStatus.Draft -> R.string.reports_group_draft
    ReportStatus.Submitted -> R.string.reports_group_submitted
    ReportStatus.Approved -> R.string.reports_group_approved
    ReportStatus.Unknown -> R.string.reports_group_unknown
}

private fun groupHint(status: ReportStatus): Int? = when (status) {
    ReportStatus.ChangesRequested -> R.string.reports_hint_changes_requested
    ReportStatus.Draft -> R.string.reports_hint_draft
    ReportStatus.Submitted -> R.string.reports_hint_submitted
    else -> null
}
