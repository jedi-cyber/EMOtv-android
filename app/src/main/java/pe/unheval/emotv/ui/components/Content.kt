package pe.unheval.emotv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pe.unheval.emotv.R
import pe.unheval.emotv.domain.model.ReportStatus
import pe.unheval.emotv.domain.model.SessionAudience
import pe.unheval.emotv.domain.model.SessionState
import pe.unheval.emotv.domain.model.SessionSummary
import pe.unheval.emotv.ui.common.ScreenError
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvShapes
import pe.unheval.emotv.ui.theme.EmotvSpacing
import pe.unheval.emotv.ui.theme.EmotvType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val ContentMaxWidth = 720.dp
private val SpanishPeru: Locale = Locale.forLanguageTag("es-PE")
private val DateTimeFormat = DateTimeFormatter.ofPattern("d 'de' MMMM, HH:mm", SpanishPeru)

object ContentTags {
    const val LIST = "content_list"
    const val BACK = "content_back"
    fun session(id: String) = "session_$id"
}

/**
 * Pantalla de consulta: barra con «Volver», título, deslizar para actualizar y una lista.
 * Activa [SecureWindow]: todas muestran datos de sesiones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentScreen(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    isLoading: Boolean,
    isRefreshing: Boolean,
    error: ScreenError?,
    hasContent: Boolean,
    onRefresh: () -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    SecureWindow()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EmotvColors.Surface)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        BackBar(onBack)
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxSize()
                    .testTag(ContentTags.LIST),
                contentPadding = PaddingValues(horizontal = EmotvSpacing.Md, vertical = EmotvSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Md),
            ) {
                item(key = "header") {
                    Column(verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xs)) {
                        PageTitle(title)
                        if (subtitle != null) PageSubtitle(subtitle)
                    }
                }
                if (error != null) {
                    item(key = "error") { ErrorCallout(error = error, stale = hasContent, onRetry = onRefresh) }
                }
                if (isLoading && !hasContent) {
                    item(key = "loading") { LoadingIndicator() }
                }
                if (hasContent) content()
            }
        }
        bottomBar?.invoke()
    }
}

@Composable
private fun BackBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(EmotvColors.Canvas)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRect(
                    color = EmotvColors.Hairline,
                    topLeft = Offset(0f, size.height - stroke),
                    size = Size(size.width, stroke),
                )
            }
            .padding(horizontal = EmotvSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag(ContentTags.BACK)) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.action_back),
                tint = EmotvColors.Ink,
            )
        }
    }
}

@Composable
fun LoadingIndicator() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = EmotvSpacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Sm),
    ) {
        CircularProgressIndicator(color = EmotvColors.Primary)
        Text(
            text = stringResource(R.string.common_loading),
            style = EmotvType.BodyMd,
            color = EmotvColors.Slate,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** Qué pasó y qué hacer. [stale]: se siguen mostrando los últimos datos cargados. */
@Composable
fun ErrorCallout(error: ScreenError, stale: Boolean, onRetry: (() -> Unit)?) {
    val title = when (error) {
        is ScreenError.Server -> error.message ?: stringResource(R.string.error_generic)
        ScreenError.Network -> stringResource(R.string.error_network)
        ScreenError.Unexpected -> stringResource(R.string.error_generic)
    }
    val detail = when (error) {
        is ScreenError.Server -> null
        ScreenError.Network -> stringResource(R.string.error_network_body)
        ScreenError.Unexpected -> stringResource(R.string.error_generic_body)
    }
    val staleNote = if (stale) stringResource(R.string.dashboard_error_stale) else null
    Callout(
        title = title,
        body = listOfNotNull(detail, staleNote).joinToString(" ").ifEmpty { null },
        tone = if (error == ScreenError.Network) CalloutTone.Warning else CalloutTone.Error,
    ) {
        if (onRetry != null) SecondaryButton(text = stringResource(R.string.common_refresh), onClick = onRetry)
    }
}

@Composable
fun SectionHeader(title: String, hint: String? = null) {
    Column(
        modifier = Modifier.padding(top = EmotvSpacing.Xs),
        verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xxs),
    ) {
        Text(
            text = title,
            style = EmotvType.SectionTitle,
            color = EmotvColors.Ink,
            modifier = Modifier.semantics { heading() },
        )
        if (hint != null) {
            Text(text = hint, style = EmotvType.BodySm, color = EmotvColors.Slate)
        }
    }
}

/** `card`: fondo blanco, borde fino, sin sombra. */
@Composable
fun EmotvCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(EmotvShapes.Lg)
            .background(EmotvColors.Canvas)
            .border(1.dp, EmotvColors.Hairline, EmotvShapes.Lg)
            .padding(EmotvSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Md),
        content = content,
    )
}

/** Una sesión: solo su código, cuándo fue, con quién, el protocolo y el estado del reporte. */
@Composable
fun SessionRow(
    session: SessionSummary,
    zone: ZoneId,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = EmotvSpacing.Xs)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EmotvSpacing.Sm),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xxs),
        ) {
            Text(text = sessionCode(session), style = EmotvType.CardTitle, color = EmotvColors.Ink)
            Text(
                text = formatDateTime(session.startedAt, zone),
                style = EmotvType.BodySm,
                color = EmotvColors.Charcoal,
            )
            Text(text = sessionDetail(session), style = EmotvType.Caption, color = EmotvColors.Steel)
            Text(text = reportStatusLabel(session.reportStatus), style = EmotvType.Caption, color = EmotvColors.Slate)
        }
        SessionStateChip(session.state)
    }
}

@Composable
fun SessionStateChip(state: SessionState) {
    val (label, tone) = when (state) {
        SessionState.InProgress -> R.string.dashboard_state_in_progress to ChipTone.InProgress
        SessionState.Completed -> R.string.dashboard_state_completed to ChipTone.Success
        // Una sesión cancelada no es un error: chip neutro (DESIGN.md).
        SessionState.Cancelled -> R.string.dashboard_state_cancelled to ChipTone.Neutral
        SessionState.Created -> R.string.dashboard_state_created to ChipTone.Neutral
        SessionState.Recognized -> R.string.dashboard_state_recognized to ChipTone.Neutral
        SessionState.Unknown -> R.string.dashboard_state_unknown to ChipTone.Neutral
    }
    StatusChip(text = stringResource(label), tone = tone)
}

@Composable
fun ReportStatusChip(status: ReportStatus) {
    val tone = when (status) {
        ReportStatus.Approved -> ChipTone.Success
        ReportStatus.Submitted -> ChipTone.InProgress
        // Con observaciones es un paso normal de la revisión, no un error.
        ReportStatus.ChangesRequested -> ChipTone.Warning
        ReportStatus.Draft, ReportStatus.Unknown -> ChipTone.Neutral
    }
    StatusChip(text = reportStatusLabel(status), tone = tone)
}

@Composable
fun reportStatusLabel(status: ReportStatus?): String = stringResource(
    when (status) {
        null -> R.string.report_status_none
        ReportStatus.Draft -> R.string.report_status_draft
        ReportStatus.Submitted -> R.string.report_status_submitted
        ReportStatus.ChangesRequested -> R.string.report_status_changes_requested
        ReportStatus.Approved -> R.string.report_status_approved
        ReportStatus.Unknown -> R.string.report_status_unknown
    },
)

@Composable
fun sessionCode(session: SessionSummary): String =
    session.code ?: stringResource(R.string.dashboard_session_no_code)

/** Con quién fue, qué protocolo se aplicó (o ninguno) y, del Docente, si es de su grupo. */
@Composable
fun sessionDetail(session: SessionSummary): String {
    val audience = audienceLabel(session.audience)?.let { stringResource(it) }
    val protocol = session.protocolName ?: stringResource(R.string.dashboard_no_protocol)
    val group = if (session.mine) null else stringResource(R.string.session_from_group)
    return listOfNotNull(audience, protocol, group).joinToString(" · ")
}

private fun audienceLabel(audience: SessionAudience): Int? = when (audience) {
    SessionAudience.Own -> R.string.audience_own
    SessionAudience.Adult -> R.string.audience_adult
    SessionAudience.Child -> R.string.audience_child
    SessionAudience.Unknown -> null
}

/** Nombre en español de una expresión estimada; una clave desconocida se muestra tal cual. */
@Composable
fun expressionLabel(key: String): String = when (key) {
    "neutral" -> stringResource(R.string.expression_neutral)
    "happiness" -> stringResource(R.string.expression_happiness)
    "surprise" -> stringResource(R.string.expression_surprise)
    "sadness" -> stringResource(R.string.expression_sadness)
    "anger" -> stringResource(R.string.expression_anger)
    "disgust" -> stringResource(R.string.expression_disgust)
    "fear" -> stringResource(R.string.expression_fear)
    "contempt" -> stringResource(R.string.expression_contempt)
    else -> key
}

fun formatDateTime(instant: Instant, zone: ZoneId): String = instant.atZone(zone).format(DateTimeFormat)
