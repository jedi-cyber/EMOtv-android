package pe.unheval.emotv.ui.participation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pe.unheval.emotv.R
import pe.unheval.emotv.domain.model.AppError
import pe.unheval.emotv.domain.model.DrawingStep
import pe.unheval.emotv.ui.components.Callout
import pe.unheval.emotv.ui.components.CalloutTone
import pe.unheval.emotv.ui.components.EmotvTextField
import pe.unheval.emotv.ui.components.FormScreen
import pe.unheval.emotv.ui.components.GhostButton
import pe.unheval.emotv.ui.components.PageSubtitle
import pe.unheval.emotv.ui.components.PageTitle
import pe.unheval.emotv.ui.components.PrimaryButton
import pe.unheval.emotv.ui.components.SecondaryButton
import pe.unheval.emotv.ui.components.SecureWindow
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvSpacing
import pe.unheval.emotv.ui.theme.EmotvType

/** Acciones de la pantalla de la persona; el ViewModel decide qué hace cada una. */
data class ParticipationActions(
    val onCodeChange: (String) -> Unit = {},
    val onSubmitCode: () -> Unit = {},
    val onSelectColor: (InkColor) -> Unit = {},
    val onSelectSize: (InkSize) -> Unit = {},
    val onToggleEraser: () -> Unit = {},
    val onUndo: () -> Unit = {},
    val onClear: () -> Unit = {},
    val onFinishDrawing: () -> Unit = {},
    val onKeepDrawing: () -> Unit = {},
    val onChoose: (String) -> Unit = {},
    val onStrokeStart: (Long, Float, Float) -> Boolean = { _, _, _ -> false },
    val onStrokeMove: (Long, Float, Float) -> Unit = { _, _, _ -> },
    val onStrokeEnd: (Long) -> Unit = {},
    val onStrokeCancel: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onExit: () -> Unit = {},
)

@Composable
fun ParticipationScreen(
    state: ParticipationUiState,
    board: DrawingBoard,
    revision: Int,
    actions: ParticipationActions,
) {
    // Lo que dibuja la persona no aparece en capturas ni en la vista de recientes.
    SecureWindow()
    when (state) {
        is ParticipationUiState.CodeEntry -> CodeEntry(state, actions)
        is ParticipationUiState.Active -> ActiveView(state, board, revision, actions)
        ParticipationUiState.Ended -> FormScreen {
            PageTitle(stringResource(R.string.participation_ended_title), Modifier.semantics { heading() })
            PageSubtitle(stringResource(R.string.participation_ended_body))
            PrimaryButton(stringResource(R.string.participation_back_home), actions.onExit,
                Modifier.fillMaxWidth().testTag(ParticipationTags.EXIT))
        }
    }
}

@Composable
private fun CodeEntry(state: ParticipationUiState.CodeEntry, actions: ParticipationActions) {
    FormScreen {
        PageTitle(stringResource(R.string.participation_title), Modifier.semantics { heading() })
        PageSubtitle(stringResource(R.string.participation_subtitle))
        EmotvTextField(
            value = state.code,
            onValueChange = actions.onCodeChange,
            label = stringResource(R.string.participation_code_label),
            enabled = !state.sending,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { actions.onSubmitCode() }),
            modifier = Modifier.fillMaxWidth().testTag(ParticipationTags.CODE),
        )
        state.error?.let { error ->
            Callout(
                title = stringResource(R.string.participation_code_error_title),
                body = codeErrorBody(error),
                tone = CalloutTone.Error,
                modifier = Modifier.testTag(ParticipationTags.CODE_ERROR),
            )
        }
        PrimaryButton(
            text = stringResource(R.string.participation_open),
            onClick = actions.onSubmitCode,
            enabled = state.code.isNotBlank(),
            loading = state.sending,
            modifier = Modifier.fillMaxWidth().testTag(ParticipationTags.OPEN),
        )
        GhostButton(stringResource(R.string.participation_back), actions.onExit, Modifier.fillMaxWidth())
        Text(stringResource(R.string.participation_privacy), style = EmotvType.Caption, color = EmotvColors.Slate)
    }
}

@Composable
private fun codeErrorBody(error: AppError): String = when (error) {
    is AppError.Server -> error.message ?: stringResource(R.string.participation_code_invalid)
    AppError.Network -> stringResource(R.string.participation_offline)
    AppError.Unexpected -> stringResource(R.string.participation_unexpected)
}

@Composable
private fun ActiveView(
    state: ParticipationUiState.Active,
    board: DrawingBoard,
    revision: Int,
    actions: ParticipationActions,
) {
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val drawing = state.view.drawing
    // Salir con un dibujo en pantalla lo borra: se pregunta antes.
    BackHandler { confirmLeave = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EmotvColors.Surface)
            .safeDrawingPadding()
            .padding(EmotvSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Sm),
    ) {
        val activity = state.view.activity
        Text(
            text = activity?.name ?: stringResource(R.string.participation_activity_default),
            style = EmotvType.PageTitleCompact,
            modifier = Modifier.semantics { heading() }.testTag(ParticipationTags.ACTIVITY),
        )
        if (activity?.number != null && activity.total != null) {
            Text(stringResource(R.string.participation_step, activity.number, activity.total),
                style = EmotvType.Caption, color = EmotvColors.Slate)
        }
        if (state.reconnecting) {
            Callout(stringResource(R.string.participation_reconnecting), null, CalloutTone.Warning)
        }
        if (state.stopFailed) {
            Callout(stringResource(R.string.participation_stop_failed), null, CalloutTone.Error)
        }

        if (drawing != null) {
            DrawingStepView(drawing, state, board, revision, actions, onClear = { confirmClear = true },
                modifier = Modifier.weight(1f))
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(
                    text = state.view.message ?: stringResource(R.string.participation_follow_conductor),
                    style = EmotvType.Subtitle,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        GhostButton(stringResource(R.string.participation_stop), { confirmStop = true },
            Modifier.fillMaxWidth().testTag(ParticipationTags.STOP))
    }

    if (confirmClear) {
        ConfirmDialog(R.string.drawing_clear_title, R.string.drawing_clear_body, R.string.drawing_clear,
            onConfirm = { confirmClear = false; actions.onClear() }, onDismiss = { confirmClear = false })
    }
    if (confirmStop) {
        ConfirmDialog(R.string.participation_stop_title, R.string.participation_stop_body, R.string.participation_stop,
            onConfirm = { confirmStop = false; actions.onStop() }, onDismiss = { confirmStop = false })
    }
    if (confirmLeave) {
        ConfirmDialog(R.string.participation_leave_title,
            if (board.hasDrawing) R.string.participation_leave_drawing_body else R.string.participation_leave_body,
            R.string.participation_leave,
            onConfirm = { confirmLeave = false; actions.onExit() }, onDismiss = { confirmLeave = false })
    }
}

@Composable
private fun DrawingStepView(
    step: DrawingStep,
    state: ParticipationUiState.Active,
    board: DrawingBoard,
    revision: Int,
    actions: ParticipationActions,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Sm)) {
        when (state.phase) {
            DrawingPhase.Drawing, DrawingPhase.Choosing -> {
                val drawingNow = state.phase == DrawingPhase.Drawing
                Text(if (drawingNow) step.instruction else step.question, style = EmotvType.Subtitle,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                DrawingCanvas(
                    board = board,
                    revision = revision,
                    enabled = drawingNow,
                    description = stringResource(
                        if (board.hasDrawing) R.string.drawing_canvas_with_drawing else R.string.drawing_canvas_empty),
                    onStart = actions.onStrokeStart,
                    onMove = actions.onStrokeMove,
                    onEnd = actions.onStrokeEnd,
                    onCancel = actions.onStrokeCancel,
                    modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 160.dp)
                        .testTag(ParticipationTags.CANVAS),
                )
                if (drawingNow) {
                    DrawingTools(board, revision, actions, onClear)
                    PrimaryButton(stringResource(R.string.drawing_finish), actions.onFinishDrawing,
                        Modifier.fillMaxWidth().testTag(ParticipationTags.FINISH), enabled = board.hasDrawing)
                    Text(stringResource(R.string.drawing_privacy), style = EmotvType.Caption, color = EmotvColors.Slate)
                } else {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(EmotvSpacing.Xs),
                    ) {
                        step.options.forEach { option ->
                            SecondaryButton(option.label, { actions.onChoose(option.key) },
                                Modifier.testTag(ParticipationTags.option(option.key)),
                                enabled = !state.sending)
                        }
                    }
                    if (state.sendFailed) {
                        Callout(stringResource(R.string.drawing_send_failed), null, CalloutTone.Error)
                    }
                    GhostButton(stringResource(R.string.drawing_keep_drawing), actions.onKeepDrawing,
                        Modifier.fillMaxWidth(), enabled = !state.sending)
                }
            }
            DrawingPhase.Done -> {
                // Describe lo que eligió la persona; no se compara con el dibujo, que ya se borró.
                val message = step.message
                    ?: if (step.finished && step.chosen == null) stringResource(R.string.drawing_time_up) else null
                Text(
                    text = message ?: stringResource(R.string.drawing_received),
                    style = EmotvType.Subtitle,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag(ParticipationTags.DONE),
                )
                Text(stringResource(R.string.participation_follow_conductor), style = EmotvType.Caption,
                    color = EmotvColors.Slate)
            }
        }
    }
}

@Composable
private fun DrawingTools(board: DrawingBoard, revision: Int, actions: ParticipationActions, onClear: () -> Unit) {
    // revision: se vuelve a componer al cambiar de herramienta o de trazo.
    if (revision < 0) return
    val eraser = board.tool == DrawingTool.Eraser
    Column(verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xxs)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(EmotvSpacing.Xs),
        ) {
            INK_COLORS.forEach { ink ->
                ToolChip(
                    label = stringResource(ink.name),
                    selected = !eraser && board.color == ink,
                    onClick = { actions.onSelectColor(ink) },
                    swatch = Color(ink.argb),
                    tag = ParticipationTags.color(ink.key),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(EmotvSpacing.Xs),
        ) {
            INK_SIZES.forEach { size ->
                ToolChip(stringResource(size.name), board.size == size, { actions.onSelectSize(size) },
                    tag = ParticipationTags.size(size.key))
            }
            ToolChip(stringResource(R.string.drawing_eraser), eraser, actions.onToggleEraser,
                tag = ParticipationTags.ERASER)
            SecondaryButton(stringResource(R.string.drawing_undo), actions.onUndo,
                Modifier.testTag(ParticipationTags.UNDO), enabled = board.hasDrawing)
            SecondaryButton(stringResource(R.string.drawing_clear), onClear,
                Modifier.testTag(ParticipationTags.CLEAR), enabled = board.hasDrawing)
        }
    }
}

@Composable
private fun ToolChip(label: String, selected: Boolean, onClick: () -> Unit, swatch: Color? = null, tag: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = swatch?.let { color ->
            { Box(Modifier.size(18.dp).background(color, CircleShape)) }
        },
        modifier = Modifier.heightIn(min = EmotvSpacing.TouchTarget).testTag(tag),
    )
}

@Composable
private fun ConfirmDialog(title: Int, body: Int, confirm: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

object ParticipationTags {
    const val CODE = "participation_code"
    const val CODE_ERROR = "participation_code_error"
    const val OPEN = "participation_open"
    const val ACTIVITY = "participation_activity"
    const val CANVAS = "drawing_canvas"
    const val FINISH = "drawing_finish"
    const val UNDO = "drawing_undo"
    const val CLEAR = "drawing_clear"
    const val ERASER = "drawing_eraser"
    const val DONE = "drawing_done"
    const val STOP = "participation_stop"
    const val EXIT = "participation_exit"
    fun color(key: String) = "drawing_color_$key"
    fun size(key: String) = "drawing_size_$key"
    fun option(key: String) = "drawing_option_$key"
}

