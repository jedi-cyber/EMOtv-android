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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pe.unheval.emotv.R
import pe.unheval.emotv.domain.model.GuidedPhase
import pe.unheval.emotv.domain.model.MissingRequirement
import pe.unheval.emotv.domain.model.Participant
import pe.unheval.emotv.domain.model.ParticipantKind
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
    fun participant(id: String) = "dashboard_participant_$id"
}

/**
 * Dashboard de quien conduce las sesiones. Muestra participantes solo por código y edad.
 * No permite crear ni editar participantes, protocolos ni cuestionarios: eso es de la web.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onSelectParticipant: (String) -> Unit,
    onStartSession: (Participant) -> Unit,
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
                    item(key = "participants_header") {
                        SectionHeader(
                            title = stringResource(R.string.dashboard_participants_title),
                            hint = stringResource(
                                if (content.participants.isEmpty()) R.string.dashboard_participants_empty
                                else R.string.dashboard_participants_hint,
                            ),
                        )
                    }
                    if (content.participants.isEmpty()) {
                        item(key = "participants_empty") {
                            SecondaryButton(
                                text = stringResource(R.string.dashboard_refresh),
                                onClick = onRefresh,
                                loading = state.isRefreshing,
                            )
                        }
                    } else {
                        item(key = "participants") {
                            Column(
                                modifier = Modifier.selectableGroup(),
                                verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Sm),
                            ) {
                                content.participants.forEach { participant ->
                                    ParticipantCard(
                                        participant = participant,
                                        selected = participant.id == state.selectedParticipantId,
                                        zone = zone,
                                        onSelect = { onSelectParticipant(participant.id) },
                                    )
                                }
                            }
                        }
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
            StartBar(availability = state.startAvailability, onStartSession = onStartSession)
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
            Text(text = session.participantCode, style = EmotvType.BodySmStrong, color = EmotvColors.Ink)
            sessionDetail(session)?.let {
                Text(text = it, style = EmotvType.Caption, color = EmotvColors.Steel)
            }
        }
        SessionStateChip(session.state)
    }
}

@Composable
private fun ParticipantCard(
    participant: Participant,
    selected: Boolean,
    zone: ZoneId,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(EmotvShapes.Lg)
            .background(EmotvColors.Canvas)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) EmotvColors.Primary else EmotvColors.Hairline,
                shape = EmotvShapes.Lg,
            )
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .testTag(DashboardTags.participant(participant.id))
            .padding(EmotvSpacing.Md),
        horizontalArrangement = Arrangement.spacedBy(EmotvSpacing.Sm),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = EmotvColors.Primary,
                unselectedColor = EmotvColors.HairlineStrong,
            ),
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xxs),
        ) {
            Text(text = participant.code, style = EmotvType.CardTitle, color = EmotvColors.Ink)
            Text(text = participantMeta(participant), style = EmotvType.BodySm, color = EmotvColors.Steel)
            val last = participant.lastSession
            if (last == null) {
                Text(
                    text = stringResource(R.string.dashboard_no_sessions),
                    style = EmotvType.BodySm,
                    color = EmotvColors.Slate,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(EmotvSpacing.Xs),
                ) {
                    Text(
                        text = stringResource(
                            R.string.dashboard_last_session,
                            last.startedAt.atZone(zone).format(DateTimeFormat),
                        ),
                        style = EmotvType.BodySm,
                        color = EmotvColors.Charcoal,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    SessionStateChip(last.state)
                }
                sessionDetail(last)?.let {
                    Text(text = it, style = EmotvType.Caption, color = EmotvColors.Steel)
                }
            }
            if (!participant.ready) {
                RequirementNotice(participant)
            }
        }
    }
}

/** Aviso visible de lo que falta. Sin región en vivo: es parte de la tarjeta, no un evento. */
@Composable
private fun RequirementNotice(participant: Participant) {
    Column(
        modifier = Modifier
            .padding(top = EmotvSpacing.Xs)
            .fillMaxWidth()
            .clip(EmotvShapes.Md)
            .background(EmotvColors.WarningSoft)
            .drawBehind {
                drawRect(EmotvColors.Warning, size = Size(3.dp.toPx(), size.height))
            }
            .padding(start = EmotvSpacing.Sm + 3.dp, end = EmotvSpacing.Sm)
            .padding(vertical = EmotvSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xxs),
    ) {
        Text(
            text = stringResource(R.string.dashboard_requirements_title),
            style = EmotvType.BodySmStrong,
            color = EmotvColors.Ink,
        )
        requirementLines(participant).forEach {
            Text(text = it, style = EmotvType.BodySm, color = EmotvColors.Charcoal)
        }
        Text(
            text = stringResource(R.string.dashboard_missing_where),
            style = EmotvType.Caption,
            color = EmotvColors.Slate,
        )
    }
}

/** Botón principal fijo abajo, con una línea que explica por qué está deshabilitado. */
@Composable
private fun StartBar(availability: StartAvailability, onStartSession: (Participant) -> Unit) {
    val explanation = when (availability) {
        StartAvailability.NoParticipants -> stringResource(R.string.dashboard_start_no_participants)
        StartAvailability.NothingSelected -> stringResource(R.string.dashboard_start_select)
        is StartAvailability.Ready ->
            stringResource(R.string.dashboard_start_ready, availability.participant.code)
        is StartAvailability.Blocked -> stringResource(
            R.string.dashboard_start_blocked,
            availability.participant.code,
            requirementLines(availability.participant).joinToString(" "),
        )
    }
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
                text = explanation,
                style = EmotvType.BodySm,
                color = if (availability is StartAvailability.Blocked) EmotvColors.Warning else EmotvColors.Charcoal,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            PrimaryButton(
                text = stringResource(R.string.dashboard_start),
                onClick = {
                    if (availability is StartAvailability.Ready) onStartSession(availability.participant)
                },
                enabled = availability is StartAvailability.Ready,
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
private fun participantMeta(participant: Participant): String {
    val kind = stringResource(
        when (participant.kind) {
            ParticipantKind.Adult -> R.string.dashboard_kind_adult
            ParticipantKind.Child -> R.string.dashboard_kind_child
            ParticipantKind.Unknown -> R.string.dashboard_kind_unknown
        },
    )
    val age = participant.ageYears?.let { pluralStringResource(R.plurals.dashboard_age_years, it, it) }
        ?: stringResource(R.string.dashboard_age_missing)
    return "$kind · $age"
}

/** Protocolo y, si la sesión guiada sigue en curso, su fase. */
@Composable
private fun sessionDetail(session: SessionSummary): String? {
    val phase = session.guidedPhase
        ?.takeIf { session.state == SessionState.InProgress }
        ?.let { stringResource(phaseLabel(it)) }
    return listOfNotNull(session.protocolName, phase).joinToString(" · ").ifEmpty { null }
}

/** Textos del servidor; si no llegaron, los del aplicativo según `missing`. */
@Composable
private fun requirementLines(participant: Participant): List<String> {
    if (participant.warnings.isNotEmpty()) return participant.warnings
    val missing = participant.missing.ifEmpty { listOf(MissingRequirement.Other) }
    return missing.map { stringResource(missingLabel(it)) }
}

private fun missingLabel(requirement: MissingRequirement): Int = when (requirement) {
    MissingRequirement.Participation -> R.string.dashboard_missing_participation
    MissingRequirement.Camera -> R.string.dashboard_missing_camera
    MissingRequirement.ChildAssent -> R.string.dashboard_missing_child_assent
    MissingRequirement.Revoked -> R.string.dashboard_missing_revoked
    MissingRequirement.Age -> R.string.dashboard_missing_age
    MissingRequirement.Other -> R.string.dashboard_missing_other
}

private fun phaseLabel(phase: GuidedPhase): Int = when (phase) {
    GuidedPhase.Confirmation -> R.string.dashboard_phase_confirmation
    GuidedPhase.PreQuestionnaire -> R.string.dashboard_phase_pre_questionnaire
    GuidedPhase.BeforeAnalyzer -> R.string.dashboard_phase_before_analyzer
    GuidedPhase.Calibration -> R.string.dashboard_phase_calibration
    GuidedPhase.Stimuli -> R.string.dashboard_phase_stimuli
    GuidedPhase.PostQuestionnaire -> R.string.dashboard_phase_post_questionnaire
    GuidedPhase.Finished -> R.string.dashboard_phase_finished
    GuidedPhase.Stopped -> R.string.dashboard_phase_stopped
}

private fun formatTime(instant: Instant, zone: ZoneId): String = instant.atZone(zone).format(TimeFormatter)
