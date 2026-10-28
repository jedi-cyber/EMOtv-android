package pe.unheval.emotv.ui.session

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.unheval.emotv.R
import pe.unheval.emotv.domain.model.Answer
import pe.unheval.emotv.domain.model.Calibration
import pe.unheval.emotv.domain.model.CalibrationState
import pe.unheval.emotv.domain.model.CaptureIssue
import pe.unheval.emotv.domain.model.GuidedPhase
import pe.unheval.emotv.domain.model.Question
import pe.unheval.emotv.domain.model.QuestionKind
import pe.unheval.emotv.domain.model.Questionnaire
import pe.unheval.emotv.domain.model.Stimulus
import pe.unheval.emotv.ui.components.Callout
import pe.unheval.emotv.ui.components.CalloutTone
import pe.unheval.emotv.ui.components.CheckboxRow
import pe.unheval.emotv.ui.components.EmotvTextField
import pe.unheval.emotv.ui.components.PrimaryButton
import pe.unheval.emotv.ui.components.RadioRow
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvShapes
import pe.unheval.emotv.ui.theme.EmotvSpacing
import pe.unheval.emotv.ui.theme.EmotvType

private val ContentMaxWidth = 720.dp

/** Lo que ve la persona evaluada en cada fase. No muestra nada que no venga del servidor. */
@Composable
fun SessionPhaseContent(
    state: LockedSessionUiState,
    onAnswer: (String, Answer?) -> Unit,
    onSubmitQuestionnaire: () -> Unit,
    onStartCalibration: () -> Unit,
    onHandBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = state.step
    when (step?.phase) {
        GuidedPhase.PreQuestionnaire, GuidedPhase.PostQuestionnaire -> {
            val questionnaire = step.questionnaire
            if (questionnaire == null) {
                Waiting(modifier)
            } else {
                QuestionnaireContent(
                    questionnaire = questionnaire,
                    answers = state.answers,
                    canSubmit = state.canSubmitQuestionnaire,
                    isSubmitting = state.isSubmitting,
                    error = state.submitError,
                    onAnswer = onAnswer,
                    onSubmit = onSubmitQuestionnaire,
                    modifier = modifier,
                )
            }
        }
        GuidedPhase.Calibration -> CalibrationContent(
            calibration = step.calibration,
            captureIssues = state.captureIssues,
            isStarting = state.isSubmitting,
            onStart = onStartCalibration,
            modifier = modifier,
        )
        GuidedPhase.BeforeAnalyzer, GuidedPhase.Stimuli -> StimulusContent(
            stimulus = step.stimulus,
            image = state.stimulusImage,
            stepCount = step.stepCount,
            captureIssues = if (step.phase == GuidedPhase.Stimuli) state.captureIssues else emptyList(),
            modifier = modifier,
        )
        GuidedPhase.Live -> LiveContent(captureIssues = state.captureIssues, modifier = modifier)
        GuidedPhase.Finished -> EndContent(
            title = stringResource(R.string.session_finished_title),
            body = stringResource(R.string.session_finished_body),
            code = state.code,
            onHandBack = onHandBack,
            modifier = modifier,
        )
        GuidedPhase.Stopped -> EndContent(
            title = stringResource(R.string.session_stopped_title),
            body = stringResource(R.string.session_stopped_body),
            code = state.code,
            onHandBack = onHandBack,
            modifier = modifier,
        )
        null -> Waiting(modifier)
    }
}

@Composable
private fun CenteredColumn(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = EmotvSpacing.Lg, vertical = EmotvSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Md),
            content = content,
        )
    }
}

@Composable
private fun Waiting(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Md),
        ) {
            CircularProgressIndicator(color = EmotvColors.Primary)
            Text(stringResource(R.string.session_loading), style = EmotvType.BodyMd, color = EmotvColors.Slate)
        }
    }
}

@Composable
private fun Heading(text: String, color: Color = EmotvColors.Ink) {
    Text(
        text = text,
        style = EmotvType.PageTitleCompact,
        color = color,
        modifier = Modifier.semantics { heading() },
    )
}

// --- Cuestionario -------------------------------------------------------------------------

@Composable
private fun QuestionnaireContent(
    questionnaire: Questionnaire,
    answers: Map<String, Answer>,
    canSubmit: Boolean,
    isSubmitting: Boolean,
    error: SessionError?,
    onAnswer: (String, Answer?) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredColumn(modifier) {
        Heading(questionnaire.title)
        questionnaire.instructions?.let {
            Text(it, style = EmotvType.Subtitle, color = EmotvColors.Slate)
        }
        if (questionnaire.answered) {
            Text(stringResource(R.string.questionnaire_answered), style = EmotvType.BodyMd, color = EmotvColors.Charcoal)
            PrimaryButton(
                text = stringResource(R.string.questionnaire_continue),
                onClick = onSubmit,
                enabled = canSubmit,
                loading = isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
            return@CenteredColumn
        }
        questionnaire.questions.forEach { question ->
            QuestionCard(question, answers[question.id], enabled = !isSubmitting) { onAnswer(question.id, it) }
        }
        error?.let { ErrorCallout(it, onRetry = null) }
        PrimaryButton(
            text = stringResource(R.string.questionnaire_submit),
            onClick = onSubmit,
            enabled = canSubmit,
            loading = isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun QuestionCard(question: Question, answer: Answer?, enabled: Boolean, onAnswer: (Answer?) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(EmotvShapes.Lg)
            .background(EmotvColors.Canvas)
            .border(BorderStroke(1.dp, EmotvColors.Hairline), EmotvShapes.Lg)
            .padding(EmotvSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Sm),
    ) {
        Text(question.text, style = EmotvType.CardTitle, color = EmotvColors.Ink)
        if (question.required) {
            Text(stringResource(R.string.questionnaire_required), style = EmotvType.Caption, color = EmotvColors.Steel)
        }
        when (question.kind) {
            QuestionKind.Faces -> FacesInput(
                selected = (answer as? Answer.Number)?.value,
                enabled = enabled,
                onSelect = { onAnswer(Answer.Number(it)) },
            )
            QuestionKind.Scale -> ScaleInput(
                points = question.scalePoints ?: QuestionKind.FACES_POINTS,
                minLabel = question.minLabel,
                maxLabel = question.maxLabel,
                selected = (answer as? Answer.Number)?.value,
                enabled = enabled,
                onSelect = { onAnswer(Answer.Number(it)) },
            )
            QuestionKind.SingleChoice -> Column(Modifier.selectableGroup()) {
                val selected = (answer as? Answer.Choice)?.option
                question.options.forEach { option ->
                    RadioRow(
                        text = option,
                        selected = option == selected,
                        enabled = enabled,
                        onSelect = { onAnswer(Answer.Choice(option)) },
                    )
                }
            }
            QuestionKind.MultipleChoice -> Column {
                val selected = (answer as? Answer.Choices)?.options.orEmpty()
                question.options.forEach { option ->
                    CheckboxRow(
                        text = option,
                        checked = option in selected,
                        enabled = enabled,
                        onCheckedChange = { checked ->
                            // Se conserva el orden de las opciones del cuestionario.
                            val next = if (checked) {
                                question.options.filter { it in selected || it == option }
                            } else {
                                selected - option
                            }
                            onAnswer(if (next.isEmpty()) null else Answer.Choices(next))
                        },
                    )
                }
            }
            QuestionKind.ShortText -> EmotvTextField(
                value = (answer as? Answer.Text)?.text.orEmpty(),
                onValueChange = { onAnswer(if (it.isEmpty()) null else Answer.Text(it)) },
                label = stringResource(R.string.questionnaire_text_label),
                enabled = enabled,
            )
            QuestionKind.Unknown ->
                Text(stringResource(R.string.questionnaire_unsupported), style = EmotvType.BodySm, color = EmotvColors.Steel)
        }
    }
}

/**
 * Escala de cinco caras, todas del mismo color: la selección se marca con el acento, nunca
 * con un color por emoción.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FacesInput(selected: Int?, enabled: Boolean, onSelect: (Int) -> Unit) {
    val points = QuestionKind.FACES_POINTS
    FlowRow(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(EmotvSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Sm),
    ) {
        (1..points).forEach { value ->
            val description = stringResource(R.string.questionnaire_face, value, points)
            OptionBox(
                selected = value == selected,
                enabled = enabled,
                onSelect = { onSelect(value) },
                modifier = Modifier.semantics { contentDescription = description },
            ) {
                Face(value = value, points = points, modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun Face(value: Int, points: Int, modifier: Modifier = Modifier) {
    // De -1 (boca hacia abajo) a 1 (boca hacia arriba).
    val curve = if (points <= 1) 0f else (value - 1) / (points - 1).toFloat() * 2f - 1f
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        val radius = size.minDimension / 2 - stroke
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(EmotvColors.Ink, radius, center, style = Stroke(stroke))
        val eyeY = center.y - radius * 0.25f
        val eyeDx = radius * 0.35f
        drawCircle(EmotvColors.Ink, stroke * 1.2f, Offset(center.x - eyeDx, eyeY))
        drawCircle(EmotvColors.Ink, stroke * 1.2f, Offset(center.x + eyeDx, eyeY))
        val mouthY = center.y + radius * 0.35f
        val mouthDx = radius * 0.45f
        val mouth = Path().apply {
            moveTo(center.x - mouthDx, mouthY)
            quadraticTo(center.x, mouthY + radius * 0.3f * curve, center.x + mouthDx, mouthY)
        }
        drawPath(mouth, EmotvColors.Ink, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScaleInput(
    points: Int,
    minLabel: String?,
    maxLabel: String?,
    selected: Int?,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xs)) {
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(EmotvSpacing.Xs),
            verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xs),
        ) {
            (1..points.coerceAtLeast(1)).forEach { value ->
                val description = stringResource(R.string.questionnaire_scale_point, value, points)
                OptionBox(
                    selected = value == selected,
                    enabled = enabled,
                    onSelect = { onSelect(value) },
                    modifier = Modifier.semantics { contentDescription = description },
                ) {
                    Text(value.toString(), style = EmotvType.ReadoutMd, color = EmotvColors.Ink)
                }
            }
        }
        if (minLabel != null || maxLabel != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(minLabel.orEmpty(), style = EmotvType.BodySm, color = EmotvColors.Slate)
                Text(maxLabel.orEmpty(), style = EmotvType.BodySm, color = EmotvColors.Slate, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun OptionBox(
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(EmotvShapes.Md)
            .background(if (selected) EmotvColors.PrimarySoft else EmotvColors.Canvas)
            .border(
                BorderStroke(if (selected) 2.dp else 1.dp, if (selected) EmotvColors.Primary else EmotvColors.HairlineStrong),
                EmotvShapes.Md,
            )
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// --- Calibración --------------------------------------------------------------------------

@Composable
private fun CalibrationContent(
    calibration: Calibration?,
    captureIssues: List<CaptureIssue>,
    isStarting: Boolean,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredColumn(modifier) {
        Heading(stringResource(R.string.calibration_title))
        when (calibration?.state ?: CalibrationState.None) {
            CalibrationState.None -> {
                Text(stringResource(R.string.calibration_body), style = EmotvType.Subtitle, color = EmotvColors.Charcoal)
                calibration?.notice?.let { Callout(title = it, body = null, tone = CalloutTone.Neutral) }
                PrimaryButton(
                    text = stringResource(R.string.calibration_start),
                    onClick = onStart,
                    loading = isStarting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            CalibrationState.Calibrating -> {
                Text(stringResource(R.string.calibration_running), style = EmotvType.Subtitle, color = EmotvColors.Charcoal)
                val progress = calibration?.progress ?: 0f
                val progressLabel = stringResource(R.string.calibration_progress)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .semantics {
                            contentDescription = progressLabel
                            progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                        },
                    color = EmotvColors.Primary,
                    trackColor = EmotvColors.NeutralSoft,
                    drawStopIndicator = {},
                )
                CaptureHint(captureIssues, color = EmotvColors.Charcoal)
            }
            CalibrationState.Calibrated ->
                Text(stringResource(R.string.calibration_done), style = EmotvType.Subtitle, color = EmotvColors.Charcoal)
        }
    }
}

// --- En vivo (sin protocolo) --------------------------------------------------------------

/** La cámara sigue tomando cuadros; no se muestra ningún resultado. Termina quien conduce. */
@Composable
private fun LiveContent(captureIssues: List<CaptureIssue>, modifier: Modifier = Modifier) {
    CenteredColumn(modifier) {
        Heading(stringResource(R.string.live_title))
        Text(stringResource(R.string.live_body), style = EmotvType.Subtitle, color = EmotvColors.Charcoal)
        CaptureHint(captureIssues, color = EmotvColors.Charcoal)
    }
}

/** Indicación neutral para acomodarse frente a la cámara. Nunca menciona la expresión. */
@Composable
private fun CaptureHint(issues: List<CaptureIssue>, color: Color) {
    val text = issues.firstNotNullOfOrNull { it.hint() } ?: return
    Text(
        text = stringResource(text),
        style = EmotvType.BodyMd,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

private fun CaptureIssue.hint(): Int? = when (this) {
    CaptureIssue.NoFace -> R.string.capture_no_face
    CaptureIssue.OffCenter -> R.string.capture_off_center
    CaptureIssue.TooSmall -> R.string.capture_too_small
    CaptureIssue.TooDark -> R.string.capture_too_dark
    CaptureIssue.TooBright -> R.string.capture_too_bright
    CaptureIssue.Tilted -> R.string.capture_tilted
    CaptureIssue.NoFrame -> R.string.capture_no_frame
    CaptureIssue.Other -> null
}

// --- Estímulos ----------------------------------------------------------------------------

/** El estímulo sobre fondo oscuro, sin título: solo la imagen y, si es abstracta, su aviso. */
@Composable
private fun StimulusContent(
    stimulus: Stimulus?,
    image: StimulusImage?,
    stepCount: Int?,
    captureIssues: List<CaptureIssue>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EmotvColors.NavyDeep)
            .padding(EmotvSpacing.Lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Md),
    ) {
        if (stimulus != null && stepCount != null && stepCount > 0) {
            Text(
                text = stringResource(R.string.stimulus_step, stimulus.stepIndex + 1, stepCount),
                style = EmotvType.BodySmStrong,
                color = EmotvColors.OnDarkMuted,
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val bitmap = image?.let { rememberDecoded(it) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.stimulus_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (stimulus?.imageUrl != null) {
                val loading = stringResource(R.string.stimulus_loading)
                CircularProgressIndicator(
                    color = EmotvColors.FrameOnDark,
                    modifier = Modifier.semantics { contentDescription = loading },
                )
            }
        }
        stimulus?.abstractNotice?.let {
            Text(
                text = it,
                style = EmotvType.Subtitle,
                color = EmotvColors.OnDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = ContentMaxWidth),
            )
        }
        CaptureHint(captureIssues, color = EmotvColors.OnDarkMuted)
    }
}

/** Decodifica la imagen en memoria fuera del hilo principal. */
@Composable
private fun rememberDecoded(image: StimulusImage): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, image.key) {
        value = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)?.asImageBitmap()
        }
    }
    return bitmap
}

// --- Fin ----------------------------------------------------------------------------------

@Composable
private fun EndContent(
    title: String,
    body: String,
    code: String,
    onHandBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredColumn(modifier) {
        Heading(title)
        Text(body, style = EmotvType.Subtitle, color = EmotvColors.Charcoal)
        Text(stringResource(R.string.session_code, code), style = EmotvType.CardTitle, color = EmotvColors.Ink)
        PrimaryButton(
            text = stringResource(R.string.session_hand_back),
            onClick = onHandBack,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
