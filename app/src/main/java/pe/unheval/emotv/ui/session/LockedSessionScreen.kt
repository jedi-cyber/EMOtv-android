package pe.unheval.emotv.ui.session

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import pe.unheval.emotv.R
import pe.unheval.emotv.domain.model.Answer
import pe.unheval.emotv.domain.model.GuidedPhase
import pe.unheval.emotv.domain.model.ParticipantKind
import pe.unheval.emotv.domain.model.StopReason
import pe.unheval.emotv.domain.repository.FrameSource
import pe.unheval.emotv.ui.components.Callout
import pe.unheval.emotv.ui.components.CalloutTone
import pe.unheval.emotv.ui.components.EmotvTextField
import pe.unheval.emotv.ui.components.GhostButton
import pe.unheval.emotv.ui.components.PrimaryButton
import pe.unheval.emotv.ui.components.RadioRow
import pe.unheval.emotv.ui.components.SecondaryButton
import pe.unheval.emotv.ui.components.SecureWindow
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvShapes
import pe.unheval.emotv.ui.theme.EmotvSpacing
import pe.unheval.emotv.ui.theme.EmotvType

object LockedSessionTags {
    const val STOP = "session_stop"
    const val PIN = "session_pin"
    const val UNLOCK = "session_unlock"
    const val RESUME = "session_resume"
    const val END = "session_end"
}

/** Callbacks de la pantalla bloqueada, agrupados para no repetir la firma en cada nivel. */
class LockedSessionActions(
    val onAnswer: (String, Answer?) -> Unit = { _, _ -> },
    val onSubmitQuestionnaire: () -> Unit = {},
    val onStartCalibration: () -> Unit = {},
    val onRequestStop: () -> Unit = {},
    val onRequestExit: () -> Unit = {},
    val onDismissExit: () -> Unit = {},
    val onRetryConnection: () -> Unit = {},
    val onPinChange: (String) -> Unit = {},
    val onSubmitPin: () -> Unit = {},
    val onStopReasonChange: (StopReason) -> Unit = {},
    val onResume: () -> Unit = {},
    val onEnd: () -> Unit = {},
)

/**
 * Modo sesión: pantalla fijada, sin barras del sistema y con `FLAG_SECURE`. El participante
 * ve solo el cuestionario, los estímulos y «Quiero parar»; la cámara captura sin vista previa.
 */
@Composable
fun LockedSessionScreen(
    state: LockedSessionUiState,
    actions: LockedSessionActions,
    onAttachCamera: (FrameSource) -> Unit,
    onDetachCamera: () -> Unit,
) {
    SecureWindow()
    LockedModeEffect()
    if (!state.exited) SessionCameraEffect(onAttach = onAttachCamera, onDetach = onDetachCamera)
    // El gesto atrás no sale: pide el PIN.
    BackHandler(onBack = actions.onRequestExit)

    LockedSessionContent(state, actions)
}

/** Contenido sin efectos del sistema, para las pruebas de interfaz. */
@Composable
fun LockedSessionContent(state: LockedSessionUiState, actions: LockedSessionActions) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EmotvColors.Surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            PinningNotice()
            SessionPhaseContent(
                state = state,
                onAnswer = actions.onAnswer,
                onSubmitQuestionnaire = actions.onSubmitQuestionnaire,
                onStartCalibration = actions.onStartCalibration,
                onHandBack = actions.onRequestExit,
                modifier = Modifier
                    .weight(1f)
                    .imePadding(),
            )
            if (!state.isTerminal) StopButton(onClick = actions.onRequestStop)
        }
        if (state.pause != null) ConductorPanel(state, actions)
    }
}

/** Botón grande, siempre visible mientras la sesión sigue. */
@Composable
private fun StopButton(onClick: () -> Unit) {
    val description = stringResource(R.string.session_stop_description)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(EmotvColors.Surface)
            .padding(horizontal = EmotvSpacing.Lg, vertical = EmotvSpacing.Md),
        contentAlignment = Alignment.Center,
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .testTag(LockedSessionTags.STOP)
                .semantics { contentDescription = description },
            shape = EmotvShapes.Lg,
            border = BorderStroke(2.dp, EmotvColors.HairlineStrong),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = EmotvColors.Canvas,
                contentColor = EmotvColors.Ink,
            ),
        ) {
            Text(stringResource(R.string.session_stop), style = EmotvType.SectionTitle)
        }
    }
}

/** Aviso si Android no fijó la pantalla (la persona puede no haber confirmado el diálogo). */
@Composable
private fun PinningNotice() {
    val activity = LocalActivity.current ?: return
    val pinned by rememberScreenPinned()
    if (pinned) return
    Box(Modifier.padding(EmotvSpacing.Md)) {
        Callout(
            title = stringResource(R.string.session_not_pinned),
            body = stringResource(R.string.session_not_pinned_body),
            tone = CalloutTone.Warning,
        ) {
            SecondaryButton(text = stringResource(R.string.session_pin_screen), onClick = { activity.pinScreen() })
        }
    }
}

// --- Panel de quien conduce ---------------------------------------------------------------

@Composable
private fun ConductorPanel(state: LockedSessionUiState, actions: LockedSessionActions) {
    val pause = state.pause ?: return
    val focusManager = LocalFocusManager.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EmotvColors.Navy)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(horizontal = EmotvSpacing.Lg, vertical = EmotvSpacing.Xxl),
            verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Md),
        ) {
            val (title, body) = pause.texts(state)
            Text(
                text = title,
                style = EmotvType.PageTitleCompact,
                color = EmotvColors.OnDark,
                modifier = Modifier.semantics { heading() },
            )
            Text(body, style = EmotvType.Subtitle, color = EmotvColors.OnDarkMuted)

            if (pause == SessionPause.Offline) {
                SecondaryButton(
                    text = stringResource(R.string.pause_offline_retry),
                    onClick = actions.onRetryConnection,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (!state.unlocked) {
                PinEntry(state, actions, onDone = { focusManager.clearFocus() })
                if (pause == SessionPause.ExitRequest && !state.isTerminal) {
                    GhostButton(
                        text = stringResource(R.string.pause_back_to_session),
                        onClick = actions.onDismissExit,
                    )
                }
            } else {
                UnlockedActions(state, actions)
            }
        }
    }
}

@Composable
private fun PinEntry(state: LockedSessionUiState, actions: LockedSessionActions, onDone: () -> Unit) {
    val lockedOut = state.pinError as? PinCheckError.LockedOut
    val error = when (val pinError = state.pinError) {
        PinCheckError.Wrong -> stringResource(R.string.pause_pin_wrong)
        is PinCheckError.LockedOut ->
            pluralStringResource(R.plurals.pause_pin_locked, pinError.seconds, pinError.seconds)
        null -> null
    }
    EmotvTextField(
        value = state.pin,
        onValueChange = actions.onPinChange,
        label = stringResource(R.string.pause_pin_label),
        modifier = Modifier.testTag(LockedSessionTags.PIN),
        error = error,
        enabled = lockedOut == null && !state.isCheckingPin,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            onDone()
            actions.onSubmitPin()
        }),
        visualTransformation = PasswordVisualTransformation(),
    )
    PrimaryButton(
        text = stringResource(R.string.pause_unlock),
        onClick = {
            onDone()
            actions.onSubmitPin()
        },
        enabled = lockedOut == null && state.pin.isNotEmpty(),
        loading = state.isCheckingPin,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LockedSessionTags.UNLOCK),
    )
}

@Composable
private fun UnlockedActions(state: LockedSessionUiState, actions: LockedSessionActions) {
    val sessionActive = !state.isTerminal && state.pause !is SessionPause.Failed
    if (state.canResume) {
        PrimaryButton(
            text = stringResource(R.string.pause_resume),
            onClick = actions.onResume,
            enabled = !state.isEnding,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LockedSessionTags.RESUME),
        )
    }
    if (sessionActive) {
        Text(
            text = stringResource(R.string.pause_reason_title),
            style = EmotvType.CardTitle,
            color = EmotvColors.OnDark,
            modifier = Modifier.padding(top = EmotvSpacing.Xs),
        )
        Column(Modifier.selectableGroup()) {
            val reasons = buildList {
                add(StopReason.Participant)
                if (state.participantKind == ParticipantKind.Child) add(StopReason.Guardian)
                add(StopReason.Conductor)
            }
            reasons.forEach { reason ->
                RadioRow(
                    text = stringResource(reason.label()),
                    selected = state.stopReason == reason,
                    onSelect = { actions.onStopReasonChange(reason) },
                    enabled = !state.isEnding,
                    textColor = EmotvColors.OnDark,
                )
            }
        }
        SecondaryButton(
            text = stringResource(R.string.pause_end),
            onClick = actions.onEnd,
            loading = state.isEnding,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LockedSessionTags.END),
        )
    } else {
        PrimaryButton(
            text = stringResource(R.string.pause_go_home),
            onClick = actions.onEnd,
            loading = state.isEnding,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LockedSessionTags.END),
        )
    }
    Text(stringResource(R.string.pause_report_notice), style = EmotvType.BodySm, color = EmotvColors.OnDarkMuted)
}

@Composable
private fun SessionPause.texts(state: LockedSessionUiState): Pair<String, String> = when (this) {
    SessionPause.ParticipantRequest ->
        stringResource(R.string.pause_participant_title) to stringResource(R.string.pause_participant_body)
    SessionPause.ExitRequest -> when (state.step?.phase) {
        GuidedPhase.Finished ->
            stringResource(R.string.pause_finished_title) to stringResource(R.string.pause_ended_body)
        GuidedPhase.Stopped ->
            stringResource(R.string.pause_stopped_title) to stringResource(R.string.pause_ended_body)
        else -> stringResource(R.string.pause_exit_title) to stringResource(R.string.pause_exit_body)
    }
    SessionPause.Offline ->
        stringResource(R.string.pause_offline_title) to stringResource(R.string.pause_offline_body)
    is SessionPause.Failed -> {
        val body = when (val error = error) {
            SessionError.AccountSessionEnded -> stringResource(R.string.pause_failed_account_body)
            is SessionError.Server -> listOfNotNull(error.message, stringResource(R.string.pause_failed_body)).joinToString(" ")
            else -> stringResource(R.string.pause_failed_body)
        }
        stringResource(R.string.pause_failed_title) to body
    }
}

private fun StopReason.label() = when (this) {
    StopReason.Participant -> R.string.pause_reason_participant
    StopReason.Guardian -> R.string.pause_reason_guardian
    StopReason.Conductor -> R.string.pause_reason_conductor
}
