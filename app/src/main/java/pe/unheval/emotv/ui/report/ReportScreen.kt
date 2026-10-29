package pe.unheval.emotv.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import pe.unheval.emotv.R
import pe.unheval.emotv.domain.model.Report
import pe.unheval.emotv.domain.model.ReportComment
import pe.unheval.emotv.domain.model.ReportSection
import pe.unheval.emotv.domain.model.ReportStatus
import pe.unheval.emotv.ui.components.Callout
import pe.unheval.emotv.ui.components.CalloutTone
import pe.unheval.emotv.ui.components.ContentScreen
import pe.unheval.emotv.ui.components.EmotvCard
import pe.unheval.emotv.ui.components.EmotvTextField
import pe.unheval.emotv.ui.components.ErrorCallout
import pe.unheval.emotv.ui.components.PrimaryButton
import pe.unheval.emotv.ui.components.RadioRow
import pe.unheval.emotv.ui.components.ReportStatusChip
import pe.unheval.emotv.ui.components.SecondaryButton
import pe.unheval.emotv.ui.components.formatDateTime
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvSpacing
import pe.unheval.emotv.ui.theme.EmotvType
import java.time.ZoneId

object ReportTags {
    const val NARRATIVE = "report_narrative"
    const val SAVE = "report_save"
    const val SUBMIT = "report_submit"
    const val COMMENT = "report_comment"
    const val SEND_COMMENT = "report_send_comment"
    const val RETURN = "report_return"
    const val APPROVE = "report_approve"
}

/** Lo que la pantalla del reporte puede pedir. */
data class ReportActions(
    val onRefresh: () -> Unit,
    val onNarrativeChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onSubmit: () -> Unit,
    val onCommentChange: (String) -> Unit,
    val onSectionChange: (ReportSection) -> Unit,
    val onComment: () -> Unit,
    val onReturn: () -> Unit,
    val onApprove: () -> Unit,
    val onBack: () -> Unit,
)

/**
 * Reporte: estado, texto y comentarios. El autor lo redacta y lo envía; el Docente asignado
 * lo comenta, lo devuelve o lo aprueba. Los botones dependen de lo que dice el servidor.
 */
@Composable
fun ReportScreen(state: ReportUiState, actions: ReportActions) {
    val zone = remember { ZoneId.systemDefault() }
    val report = state.data.content
    ContentScreen(
        title = stringResource(R.string.report_title, report?.sessionCode.orEmpty()),
        subtitle = report?.let { stringResource(R.string.report_version, it.version) },
        onBack = actions.onBack,
        isLoading = state.data.isLoading,
        isRefreshing = state.data.isRefreshing,
        error = state.data.error,
        hasContent = report != null,
        onRefresh = actions.onRefresh,
    ) {
        if (report == null) return@ContentScreen
        item(key = "status") {
            Column(verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xs)) {
                ReportStatusChip(report.status)
                statusHint(report)?.let { Text(it, style = EmotvType.BodySm, color = EmotvColors.Charcoal) }
            }
        }
        if (!report.current) {
            item(key = "not_current") {
                Callout(title = stringResource(R.string.report_not_current), body = null, tone = CalloutTone.Neutral)
            }
        }
        state.actionError?.let { error ->
            item(key = "action_error") { ErrorCallout(error = error, stale = false, onRetry = null) }
        }
        item(key = "narrative") { NarrativeCard(report, state, actions) }
        item(key = "comments") { CommentsCard(report.comments, zone) }
        if (report.canReview) {
            item(key = "review") { ReviewCard(state, actions) }
        }
        item(key = "notice") {
            Callout(
                title = report.estimatesNotice.ifEmpty { stringResource(R.string.estimates_notice) },
                body = report.abstractNotice,
                tone = CalloutTone.Neutral,
            )
        }
    }
}

@Composable
private fun statusHint(report: Report): String? = when {
    report.status == ReportStatus.ChangesRequested && report.authoredByMe -> stringResource(R.string.report_changes_hint)
    report.canEdit && report.requiresReview -> stringResource(R.string.report_draft_hint)
    report.canEdit -> stringResource(R.string.report_draft_hint_docente)
    report.status == ReportStatus.Submitted && !report.canReview -> stringResource(R.string.report_submitted_hint)
    report.status == ReportStatus.Approved -> stringResource(R.string.report_approved_hint)
    else -> null
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text = text,
        style = EmotvType.SectionTitle,
        color = EmotvColors.Ink,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun NarrativeCard(report: Report, state: ReportUiState, actions: ReportActions) {
    EmotvCard {
        CardTitle(stringResource(R.string.report_narrative_title))
        if (report.canEdit) {
            EmotvTextField(
                value = state.narrative,
                onValueChange = actions.onNarrativeChange,
                label = stringResource(R.string.report_narrative_label),
                enabled = state.running == null,
                minLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.testTag(ReportTags.NARRATIVE),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(EmotvSpacing.Sm)) {
                SecondaryButton(
                    text = stringResource(R.string.report_save),
                    onClick = actions.onSave,
                    enabled = state.hasUnsavedText && state.running == null,
                    loading = state.running == ReportAction.Save,
                    modifier = Modifier.testTag(ReportTags.SAVE),
                )
                PrimaryButton(
                    text = stringResource(if (report.requiresReview) R.string.report_submit else R.string.report_close),
                    onClick = actions.onSubmit,
                    enabled = state.narrative.isNotBlank() && state.running == null,
                    loading = state.running == ReportAction.Submit,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(ReportTags.SUBMIT),
                )
            }
        } else {
            Text(
                text = report.narrative.ifEmpty { stringResource(R.string.report_narrative_empty) },
                style = EmotvType.BodyMd,
                color = if (report.narrative.isEmpty()) EmotvColors.Slate else EmotvColors.Ink,
            )
        }
    }
}

@Composable
private fun CommentsCard(comments: List<ReportComment>, zone: ZoneId) {
    EmotvCard {
        CardTitle(stringResource(R.string.report_comments_title))
        if (comments.isEmpty()) {
            Text(stringResource(R.string.report_comments_empty), style = EmotvType.BodyMd, color = EmotvColors.Slate)
            return@EmotvCard
        }
        Column {
            comments.forEachIndexed { index, comment ->
                if (index > 0) HorizontalDivider(color = EmotvColors.Hairline)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = EmotvSpacing.Xs)
                        .semantics(mergeDescendants = true) {},
                    verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xxs),
                ) {
                    Text(
                        text = sectionLabel(comment.section) + " · " + formatDateTime(comment.createdAt, zone),
                        style = EmotvType.Caption,
                        color = EmotvColors.Slate,
                    )
                    Text(comment.text, style = EmotvType.BodyMd, color = EmotvColors.Ink)
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(state: ReportUiState, actions: ReportActions) {
    EmotvCard {
        CardTitle(stringResource(R.string.report_review_title))
        Text(stringResource(R.string.report_review_hint), style = EmotvType.BodySm, color = EmotvColors.Charcoal)
        Text(stringResource(R.string.report_section_label), style = EmotvType.BodySmStrong, color = EmotvColors.Ink)
        Column {
            ReportSection.entries.forEach { section ->
                RadioRow(
                    text = sectionLabel(section),
                    selected = state.commentSection == section,
                    onSelect = { actions.onSectionChange(section) },
                    enabled = state.running == null,
                )
            }
        }
        EmotvTextField(
            value = state.commentText,
            onValueChange = actions.onCommentChange,
            label = stringResource(R.string.report_comment_label),
            enabled = state.running == null,
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.testTag(ReportTags.COMMENT),
        )
        SecondaryButton(
            text = stringResource(R.string.report_comment_send),
            onClick = actions.onComment,
            enabled = state.commentText.isNotBlank() && state.running == null,
            loading = state.running == ReportAction.Comment,
            modifier = Modifier.testTag(ReportTags.SEND_COMMENT),
        )
        HorizontalDivider(color = EmotvColors.Hairline)
        SecondaryButton(
            text = stringResource(R.string.report_return),
            onClick = actions.onReturn,
            enabled = state.running == null,
            loading = state.running == ReportAction.Return,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ReportTags.RETURN),
        )
        PrimaryButton(
            text = stringResource(R.string.report_approve),
            onClick = actions.onApprove,
            enabled = state.running == null,
            loading = state.running == ReportAction.Approve,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ReportTags.APPROVE),
        )
    }
}

@Composable
private fun sectionLabel(section: ReportSection): String = stringResource(
    when (section) {
        ReportSection.General -> R.string.report_section_general
        ReportSection.Narrative -> R.string.report_section_narrative
        ReportSection.Timeline -> R.string.report_section_timeline
        ReportSection.Metrics -> R.string.report_section_metrics
        ReportSection.Questionnaires -> R.string.report_section_questionnaires
    },
)
