package pe.unheval.emotv.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pe.unheval.emotv.R
import pe.unheval.emotv.domain.model.SessionSummary
import pe.unheval.emotv.ui.common.ScreenError
import pe.unheval.emotv.ui.components.Callout
import pe.unheval.emotv.ui.components.CalloutTone
import pe.unheval.emotv.ui.components.ContentMaxWidth
import pe.unheval.emotv.ui.components.EmotvCard
import pe.unheval.emotv.ui.components.ErrorCallout
import pe.unheval.emotv.ui.components.LoadingIndicator
import pe.unheval.emotv.ui.components.PageSubtitle
import pe.unheval.emotv.ui.components.PageTitle
import pe.unheval.emotv.ui.components.PrimaryButton
import pe.unheval.emotv.ui.components.SecondaryButton
import pe.unheval.emotv.ui.components.SecureWindow
import pe.unheval.emotv.ui.components.SectionHeader
import pe.unheval.emotv.ui.components.SessionRow
import pe.unheval.emotv.ui.components.SessionStateChip
import pe.unheval.emotv.ui.components.sessionCode
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvShapes
import pe.unheval.emotv.ui.theme.EmotvSpacing
import pe.unheval.emotv.ui.theme.EmotvType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SpanishPeru: Locale = Locale.forLanguageTag("es-PE")
private val DayFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SpanishPeru)
private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm", SpanishPeru)

object DashboardTags {
    const val LIST = "dashboard_list"
    const val SESSIONS = "dashboard_sessions"
    const val STATISTICS = "dashboard_statistics"
    const val REVIEWS = "dashboard_reviews"
    const val ACCOUNT_MENU = "dashboard_account_menu"
    const val LOGOUT = "dashboard_logout"
    fun recentSession(id: String) = "dashboard_recent_$id"
}

/** A dónde lleva cada acción del inicio. */
data class DashboardActions(
    val onRefresh: () -> Unit,
    val onOpenSession: (String) -> Unit,
    val onOpenSessions: () -> Unit,
    val onOpenStatistics: () -> Unit,
    val onOpenReviews: () -> Unit,
    val onLogout: () -> Unit,
)

/**
 * Inicio del Usuario o del Docente: sesiones de hoy y recientes, accesos a todas las
 * sesiones y a las estadísticas y, del Docente, los reportes por revisar. Las sesiones se
 * conducen en la web: el aplicativo no usa la cámara.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(state: DashboardUiState, actions: DashboardActions) {
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
            onLogout = actions.onLogout,
        )
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = actions.onRefresh,
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
                        ErrorCallout(error = error.toScreenError(), stale = content != null, onRetry = actions.onRefresh)
                    }
                }

                if (state.isLoading && content == null) {
                    item(key = "loading") { LoadingIndicator() }
                }

                if (content != null) {
                    if (content.isDocente && content.pendingReviews > 0) {
                        item(key = "pending") {
                            Callout(
                                title = pluralStringResource(
                                    R.plurals.dashboard_pending_count,
                                    content.pendingReviews,
                                    content.pendingReviews,
                                ),
                                body = stringResource(R.string.dashboard_pending_body),
                                tone = CalloutTone.Neutral,
                            ) {
                                PrimaryButton(
                                    text = stringResource(R.string.dashboard_pending_action),
                                    onClick = actions.onOpenReviews,
                                    modifier = Modifier.testTag(DashboardTags.REVIEWS),
                                )
                            }
                        }
                    }
                    item(key = "today") { TodayCard(content, zone, actions.onOpenSession) }
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
                        item(key = "recent") { RecentSessionsCard(content.recentSessions, zone, actions.onOpenSession) }
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
            NavigationBar(actions)
        }
    }
}

private fun DashboardError.toScreenError(): ScreenError = when (this) {
    is DashboardError.Server -> ScreenError.Server(message)
    DashboardError.Network -> ScreenError.Network
    DashboardError.Unexpected -> ScreenError.Unexpected
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
private fun TodayCard(content: DashboardContent, zone: ZoneId, onOpenSession: (String) -> Unit) {
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
                    TodaySessionRow(session, zone, onClick = { onOpenSession(session.id) })
                }
            }
        }
    }
}

@Composable
private fun TodaySessionRow(session: SessionSummary, zone: ZoneId, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = EmotvShapes.Md,
        contentPadding = PaddingValues(vertical = EmotvSpacing.Xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EmotvSpacing.Sm),
        ) {
            Text(
                text = formatTime(session.startedAt, zone),
                style = EmotvType.ReadoutMd,
                color = EmotvColors.Ink,
            )
            Text(
                text = sessionCode(session),
                style = EmotvType.BodySmStrong,
                color = EmotvColors.Ink,
                modifier = Modifier.weight(1f),
            )
            SessionStateChip(session.state)
        }
    }
}

@Composable
private fun RecentSessionsCard(sessions: List<SessionSummary>, zone: ZoneId, onOpenSession: (String) -> Unit) {
    EmotvCard {
        Column {
            sessions.forEachIndexed { index, session ->
                if (index > 0) HorizontalDivider(color = EmotvColors.Hairline)
                SessionRow(
                    session = session,
                    zone = zone,
                    onClick = { onOpenSession(session.id) },
                    modifier = Modifier.testTag(DashboardTags.recentSession(session.id)),
                )
            }
        }
    }
}

/** Barra fija abajo: todas las sesiones y las estadísticas. */
@Composable
private fun NavigationBar(actions: DashboardActions) {
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
        Row(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EmotvSpacing.Sm),
        ) {
            SecondaryButton(
                text = stringResource(R.string.dashboard_nav_sessions),
                onClick = actions.onOpenSessions,
                modifier = Modifier
                    .weight(1f)
                    .testTag(DashboardTags.SESSIONS),
            )
            SecondaryButton(
                text = stringResource(R.string.dashboard_nav_statistics),
                onClick = actions.onOpenStatistics,
                modifier = Modifier
                    .weight(1f)
                    .testTag(DashboardTags.STATISTICS),
            )
        }
    }
}

private fun formatTime(instant: Instant, zone: ZoneId): String = instant.atZone(zone).format(TimeFormatter)
