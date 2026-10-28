package pe.unheval.emotv.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pe.unheval.emotv.R
import pe.unheval.emotv.domain.model.GuidedPhase
import pe.unheval.emotv.domain.model.SessionAudience
import pe.unheval.emotv.domain.model.SessionState
import pe.unheval.emotv.domain.model.SessionSummary
import pe.unheval.emotv.ui.components.Callout
import pe.unheval.emotv.ui.components.CalloutTone
import pe.unheval.emotv.ui.components.ChipTone
import pe.unheval.emotv.ui.components.PageSubtitle
import pe.unheval.emotv.ui.components.PageTitle
import pe.unheval.emotv.ui.components.PrimaryButton
import pe.unheval.emotv.ui.components.SecondaryButton
import pe.unheval.emotv.ui.components.SecureWindow
import pe.unheval.emotv.ui.components.StatusChip
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvShapes
import pe.unheval.emotv.ui.theme.EmotvSpacing
import pe.unheval.emotv.ui.theme.EmotvType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ContentMaxWidth = 720.dp
private val SpanishPeru: Locale = Locale.forLanguageTag("es-PE")
private val DayFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SpanishPeru)
private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm", SpanishPeru)
private val DateTimeFormat = DateTimeFormatter.ofPattern("d 'de' MMMM, HH:mm", SpanishPeru)

object DashboardTags {
    const val LIST = "dashboard_list"
    const val START = "dashboard_start"
    const val ACCOUNT_MENU = "dashboard_account_menu"
    const val LOGOUT = "dashboard_logout"
    fun recentSession(id: String) = "dashboard_recent_$id"
}

/**
 * Dashboard de quien conduce las sesiones. Cada sesión se identifica solo por su código.
 * No permite crear ni editar protocolos ni cuestionarios: eso es de la web.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onStartSession: () -> Unit,
    onLogout: () -> Unit,
) {
    SecureWindow()
    val zone = remember { ZoneId.systemDefault() }
    val content = state.content

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EmotvColors.Surface)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        TopBar(
            roleName = content?.roleName,
            email = content?.email,
            isLoggingOut = state.isLoggingOut,
            onLogout = onLogout,
        )
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
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
                    .testTag(DashboardTags.LIST),
                contentPadding = PaddingValues(horizontal = EmotvSpacing.Md, vertical = EmotvSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Md),
            ) {
                item(key = "header") {
                    Column(verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xs)) {
                        PageTitle(stringResource(R.string.dashboard_greeting))
                        if (content != null) {
                            PageSubtitle(
                                stringResource(R.string.dashboard_subtitle, content.date.format(DayFormatter)),
                            )
                        }
                    }
                }

                state.error?.let { error ->
                    item(key = "error") {
                        ErrorCallout(error = error, stale = content != null, onRefresh = onRefresh)
                    }
                }

                if (state.isLoading && content == null) {
                    item(key = "loading") { LoadingIndicator() }
                }

                if (content != null) {
                    item(key = "today") { TodayCard(content, zone) }
                    item(key = "recent_header") {
                        SectionHeader(
                            title = stringResource(R.string.dashboard_recent_title),
                            hint = stringResource(
                                if (content.recentSessions.isEmpty()) R.string.dashboard_recent_empty
                                else R.string.dashboard_recent_hint,
                            ),
                        )
                    }
                    if (content.recentSessions.isNotEmpty()) {
                        item(key = "recent") { RecentSessionsCard(content.recentSessions, zone) }
                    }
                    item(key = "web") {
                        Callout(
                            title = stringResource(R.string.dashboard_web_title),
                            body = stringResource(R.string.dashboard_web_body),
                            tone = CalloutTone.Neutral,
                        )
                    }
                }
            }
        }
        if (content != null) {
            StartBar(onStartSession = onStartSession)
        }
    }
}

/** Barra superior fija: sección actual y menú de la cuenta con «Cerrar sesión». */
@Composable
private fun TopBar(
    roleName: String?,
    email: String?,
    isLoggingOut: Boolean,
    onLogout: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val accountLabel = roleName ?: stringResource(R.string.dashboard_account)
    val menuDescription = stringResource(R.string.dashboard_account_menu, accountLabel)
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
            .padding(start = EmotvSpacing.Md, end = EmotvSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.dashboard_section),
            style = EmotvType.BodySmStrong,
            color = EmotvColors.Ink,
            modifier = Modifier.weight(1f),
        )
        Box {
            TextButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .heightIn(min = EmotvSpacing.TouchTarget)
                    .testTag(DashboardTags.ACCOUNT_MENU)
                    .semantics { contentDescription = menuDescription },
                shape = EmotvShapes.Md,
            ) {
                Text(text = accountLabel, style = EmotvType.Button, color = EmotvColors.Ink)
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    tint = EmotvColors.Slate,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = EmotvColors.Canvas,
            ) {
                if (email != null) {
                    Text(
                        text = stringResource(R.string.dashboard_signed_in_as, email),
                        style = EmotvType.Caption,
                        color = EmotvColors.Slate,
                        modifier = Modifier.padding(horizontal = EmotvSpacing.Md, vertical = EmotvSpacing.Xs),
                    )
                    HorizontalDivider(color = EmotvColors.Hairline)
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_logout), style = EmotvType.BodyMd) },
                    onClick = {
                        expanded = false
                        onLogout()
                    },
                    enabled = !isLoggingOut,
                    modifier = Modifier
                        .heightIn(min = EmotvSpacing.TouchTarget)
                        .testTag(DashboardTags.LOGOUT),
                )
            }
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = EmotvSpacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Sm),
    ) {
        CircularProgressIndicator(color = EmotvColors.Primary)
        Text(
            text = stringResource(R.string.dashboard_loading),
            style = EmotvType.BodyMd,
            color = EmotvColors.Slate,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun ErrorCallout(error: DashboardError, stale: Boolean, onRefresh: () -> Unit) {
    val title = when (error) {
        is DashboardError.Server -> error.message ?: stringResource(R.string.dashboard_error_generic)
        DashboardError.Network -> stringResource(R.string.dashboard_error_network)
        DashboardError.Unexpected -> stringResource(R.string.dashboard_error_generic)
    }
    val detail = when (error) {
        is DashboardError.Server -> null
        DashboardError.Network -> stringResource(R.string.dashboard_error_network_body)
        DashboardError.Unexpected -> stringResource(R.string.dashboard_error_generic_body)
    }
    val staleNote = if (stale) stringResource(R.string.dashboard_error_stale) else null
    Callout(
        title = title,
        body = listOfNotNull(detail, staleNote).joinToString(" ").ifEmpty { null },
        tone = if (error == DashboardError.Network) CalloutTone.Warning else CalloutTone.Error,
    ) {
        SecondaryButton(text = stringResource(R.string.dashboard_refresh), onClick = onRefresh)
    }
}

@Composable
private fun SectionHeader(title: String, hint: String?) {
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
private fun EmotvCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
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

@Composable
private fun TodayCard(content: DashboardContent, zone: ZoneId) {
    EmotvCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.dashboard_today_title),
                style = EmotvType.SectionTitle,
                color = EmotvColors.Ink,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            Text(
                text = pluralStringResource(
                    R.plurals.dashboard_today_count,
                    content.todaySessions.size,
                    content.todaySessions.size,
                ),
                style = EmotvType.ReadoutMd,
                color = EmotvColors.Ink,
            )
        }
        if (content.todaySessions.isEmpty()) {
            Text(
                text = stringResource(R.string.dashboard_today_empty),
                style = EmotvType.BodyMd,
                color = EmotvColors.Slate,
            )
        } else {
            ActivityChart(activity = content.activity)
            Column {
                content.todaySessions.forEachIndexed { index, session ->
                    if (index > 0) HorizontalDivider(color = EmotvColors.Hairline)
                    TodaySessionRow(session, zone)
                }
            }
        }
    }
}

@Composable
private fun TodaySessionRow(session: SessionSummary, zone: ZoneId) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(vertical = EmotvSpacing.Xs)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EmotvSpacing.Sm),
    ) {
        Text(
            text = formatTime(session.startedAt, zone),
            style = EmotvType.ReadoutMd,
            color = EmotvColors.Ink,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = sessionCode(session), style = EmotvType.BodySmStrong, color = EmotvColors.Ink)
            sessionDetail(session)?.let {
                Text(text = it, style = EmotvType.Caption, color = EmotvColors.Steel)
            }
        }
        SessionStateChip(session.state)
    }
}

@Composable
private fun RecentSessionsCard(sessions: List<SessionSummary>, zone: ZoneId) {
    EmotvCard {
        Column {
            sessions.forEachIndexed { index, session ->
                if (index > 0) HorizontalDivider(color = EmotvColors.Hairline)
                RecentSessionRow(session, zone)
            }
        }
    }
}

@Composable
private fun RecentSessionRow(session: SessionSummary, zone: ZoneId) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(vertical = EmotvSpacing.Xs)
            .testTag(DashboardTags.recentSession(session.id))
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
                text = session.startedAt.atZone(zone).format(DateTimeFormat),
                style = EmotvType.BodySm,
                color = EmotvColors.Charcoal,
            )
            Text(text = recentDetail(session), style = EmotvType.Caption, color = EmotvColors.Steel)
        }
        SessionStateChip(session.state)
    }
}

/** Barra fija abajo: el protocolo (o ninguno) se elige en el siguiente paso. */
@Composable
private fun StartBar(onStartSession: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(EmotvColors.Canvas)
            .drawBehind {
                drawRect(EmotvColors.Hairline, size = Size(size.width, 1.dp.toPx()))
            }
            .padding(horizontal = EmotvSpacing.Md, vertical = EmotvSpacing.Sm),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xs),
        ) {
            Text(
                text = stringResource(R.string.dashboard_start_hint),
                style = EmotvType.BodySm,
                color = EmotvColors.Charcoal,
            )
            PrimaryButton(
                text = stringResource(R.string.dashboard_start),
                onClick = onStartSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DashboardTags.START),
            )
        }
    }
}

@Composable
private fun SessionStateChip(state: SessionState) {
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
private fun sessionCode(session: SessionSummary): String =
    session.code ?: stringResource(R.string.dashboard_session_no_code)

/** Con quién fue y qué protocolo se aplicó (o ninguno). */
@Composable
private fun recentDetail(session: SessionSummary): String {
    val audience = audienceLabel(session.audience)?.let { stringResource(it) }
    val protocol = session.protocolName ?: stringResource(R.string.dashboard_no_protocol)
    return listOfNotNull(audience, protocol).joinToString(" · ")
}

private fun audienceLabel(audience: SessionAudience): Int? = when (audience) {
    SessionAudience.Own -> R.string.audience_own
    SessionAudience.Adult -> R.string.audience_adult
    SessionAudience.Child -> R.string.audience_child
    SessionAudience.Unknown -> null
}

/** Protocolo y, si la sesión sigue en curso, su fase. */
@Composable
private fun sessionDetail(session: SessionSummary): String? {
    val phase = session.guidedPhase
        ?.takeIf { session.state == SessionState.InProgress }
        ?.let { stringResource(phaseLabel(it)) }
    return listOfNotNull(session.protocolName, phase).joinToString(" · ").ifEmpty { null }
}

private fun phaseLabel(phase: GuidedPhase): Int = when (phase) {
    GuidedPhase.PreQuestionnaire -> R.string.dashboard_phase_pre_questionnaire
    GuidedPhase.BeforeAnalyzer -> R.string.dashboard_phase_before_analyzer
    GuidedPhase.Calibration -> R.string.dashboard_phase_calibration
    GuidedPhase.Stimuli -> R.string.dashboard_phase_stimuli
    GuidedPhase.PostQuestionnaire -> R.string.dashboard_phase_post_questionnaire
    GuidedPhase.Live -> R.string.dashboard_phase_live
    GuidedPhase.Finished -> R.string.dashboard_phase_finished
    GuidedPhase.Stopped -> R.string.dashboard_phase_stopped
}

private fun formatTime(instant: Instant, zone: ZoneId): String = instant.atZone(zone).format(TimeFormatter)
