package pe.unheval.emotv.ui.session

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import pe.unheval.emotv.R
import pe.unheval.emotv.domain.model.ConfirmationItem
import pe.unheval.emotv.domain.model.SessionProtocol
import pe.unheval.emotv.ui.components.CameraFrame
import pe.unheval.emotv.ui.components.Callout
import pe.unheval.emotv.ui.components.CalloutTone
import pe.unheval.emotv.ui.components.CheckboxRow
import pe.unheval.emotv.ui.components.ChipTone
import pe.unheval.emotv.ui.components.FormScreen
import pe.unheval.emotv.ui.components.GhostButton
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

object SessionSetupTags {
    const val CONTINUE = "setup_continue"
    const val HAND_OVER = "setup_hand_over"
    const val ALLOW_CAMERA = "setup_allow_camera"
}

@Composable
fun SessionSetupScreen(
    state: SessionSetupUiState,
    onRetryProtocols: () -> Unit,
    onSelectProtocol: (String) -> Unit,
    onContinue: () -> Unit,
    onCheckedChange: (ConfirmationItem, Boolean) -> Unit,
    onCameraReadyChange: (Boolean) -> Unit,
    onLightingCheckedChange: (Boolean) -> Unit,
    onHandOver: () -> Unit,
    onCancel: () -> Unit,
) {
    SecureWindow()
    BackHandler(onBack = onCancel)

    FormScreen {
        PageTitle(stringResource(R.string.setup_title))
        PageSubtitle(stringResource(R.string.setup_subtitle, state.participantCode))

        if (state.started == null) {
            ProtocolStep(state, onRetryProtocols, onSelectProtocol, onContinue, onCancel)
        } else {
            ConfirmationStep(
                state = state,
                onCheckedChange = onCheckedChange,
                onCameraReadyChange = onCameraReadyChange,
                onLightingCheckedChange = onLightingCheckedChange,
                onHandOver = onHandOver,
                onCancel = onCancel,
            )
        }
    }
}

@Composable
private fun ProtocolStep(
    state: SessionSetupUiState,
    onRetry: () -> Unit,
    onSelect: (String) -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    SectionTitle(stringResource(R.string.setup_protocol_title))
    when {
        state.isLoadingProtocols -> Row(
            horizontalArrangement = Arrangement.spacedBy(EmotvSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = EmotvColors.Primary, strokeWidth = 2.dp)
            Text(stringResource(R.string.setup_protocol_loading), style = EmotvType.BodyMd, color = EmotvColors.Slate)
        }
        state.protocols.isEmpty() && state.error == null -> Callout(
            title = stringResource(R.string.setup_protocol_empty),
            body = stringResource(R.string.setup_protocol_empty_body),
            tone = CalloutTone.Neutral,
        )
        else -> Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xs),
        ) {
            state.protocols.forEach { protocol ->
                ProtocolRow(
                    protocol = protocol,
                    selected = protocol.id == state.selectedProtocolId,
                    enabled = !state.isStarting,
                    onSelect = { onSelect(protocol.id) },
                )
            }
        }
    }

    state.error?.let { ErrorCallout(it, onRetry = if (state.protocols.isEmpty()) onRetry else null) }

    PrimaryButton(
        text = stringResource(R.string.setup_continue),
        onClick = onContinue,
        enabled = state.selectedProtocol != null,
        loading = state.isStarting,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SessionSetupTags.CONTINUE),
    )
    GhostButton(text = stringResource(R.string.setup_back), onClick = onCancel)
}

@Composable
private fun ProtocolRow(protocol: SessionProtocol, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val details = buildList {
        add(stringResource(R.string.setup_protocol_version, protocol.version))
        add(pluralStringResource(R.plurals.setup_protocol_steps, protocol.stepCount, protocol.stepCount))
        if (protocol.hasPreQuestionnaire) add(stringResource(R.string.setup_protocol_pre))
        if (protocol.hasPostQuestionnaire) add(stringResource(R.string.setup_protocol_post))
    }.joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = EmotvSpacing.TouchTarget)
            .clip(EmotvShapes.Lg)
            .background(EmotvColors.Canvas)
            .border(
                BorderStroke(if (selected) 2.dp else 1.dp, if (selected) EmotvColors.Primary else EmotvColors.Hairline),
                EmotvShapes.Lg,
            )
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(EmotvSpacing.Md),
        horizontalArrangement = Arrangement.spacedBy(EmotvSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(
                selectedColor = EmotvColors.Primary,
                unselectedColor = EmotvColors.HairlineStrong,
            ),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xxs)) {
            Text(protocol.name, style = EmotvType.CardTitle, color = EmotvColors.Ink)
            Text(details, style = EmotvType.BodySm, color = EmotvColors.Steel)
            if (protocol.forChildren) {
                StatusChip(stringResource(R.string.setup_protocol_for_children), ChipTone.Neutral)
            }
        }
    }
}

@Composable
private fun ConfirmationStep(
    state: SessionSetupUiState,
    onCheckedChange: (ConfirmationItem, Boolean) -> Unit,
    onCameraReadyChange: (Boolean) -> Unit,
    onLightingCheckedChange: (Boolean) -> Unit,
    onHandOver: () -> Unit,
    onCancel: () -> Unit,
) {
    val started = state.started ?: return
    val busy = state.isConfirming || state.isCancelling
    state.selectedProtocol?.let {
        Text(stringResource(R.string.setup_selected_protocol, it.name), style = EmotvType.BodyMd, color = EmotvColors.Charcoal)
    }

    SectionTitle(stringResource(R.string.setup_confirmation_title))
    started.confirmation.reminder?.let {
        Callout(title = stringResource(R.string.setup_reminder_title), body = it, tone = CalloutTone.Neutral)
    }
    Column {
        started.confirmation.required.forEach { item ->
            CheckboxRow(
                text = stringResource(item.label()),
                checked = item in state.checked,
                onCheckedChange = { onCheckedChange(item, it) },
                enabled = !busy,
            )
        }
    }

    SectionTitle(stringResource(R.string.setup_camera_title))
    CameraCheck(
        cameraReady = state.cameraReady,
        lightingChecked = state.lightingChecked,
        enabled = !busy,
        onCameraReadyChange = onCameraReadyChange,
        onLightingCheckedChange = onLightingCheckedChange,
    )

    state.error?.let { ErrorCallout(it, onRetry = null) }

    val missing = when {
        state.missingConfirmations.isNotEmpty() -> R.string.setup_missing_confirmations
        !state.cameraReady -> R.string.setup_missing_camera
        !state.lightingChecked -> R.string.setup_missing_lighting
        else -> R.string.setup_lock_notice
    }
    Text(stringResource(missing), style = EmotvType.BodySm, color = EmotvColors.Slate)
    PrimaryButton(
        text = stringResource(R.string.setup_hand_over),
        onClick = onHandOver,
        enabled = state.canHandOver,
        loading = state.isConfirming,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SessionSetupTags.HAND_OVER),
    )
    SecondaryButton(
        text = stringResource(R.string.setup_cancel),
        onClick = onCancel,
        enabled = !state.isConfirming,
        loading = state.isCancelling,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Permiso de cámara y vista previa para revisar la luz. Nada de la vista previa se guarda. */
@Composable
private fun CameraCheck(
    cameraReady: Boolean,
    lightingChecked: Boolean,
    enabled: Boolean,
    onCameraReadyChange: (Boolean) -> Unit,
    onLightingCheckedChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(hasPermission()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var openFailed by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    // Al volver de los ajustes del sistema. El permiso se pide solo al tocar el botón,
    // después de explicar para qué se usa la cámara.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasPermission() }

    Text(stringResource(R.string.setup_camera_body), style = EmotvType.BodyMd, color = EmotvColors.Charcoal)
    LaunchedEffect(granted) {
        if (!granted) onCameraReadyChange(false)
    }
    if (!granted) {
        CameraPermissionRequest(
            denied = asked,
            onAllow = { launcher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
        )
        return
    }

    val previewDescription = stringResource(R.string.setup_camera_preview_description)
    CameraFrame(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .semantics { contentDescription = previewDescription },
    ) {
        CameraPreview(
            onReadyChange = { ready ->
                openFailed = !ready && granted
                onCameraReadyChange(ready)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
    if (openFailed && !cameraReady) {
        Callout(
            title = stringResource(R.string.setup_camera_unavailable),
            body = stringResource(R.string.setup_camera_unavailable_body),
            tone = CalloutTone.Warning,
        )
    }
    CheckboxRow(
        text = stringResource(R.string.setup_lighting_checked),
        checked = lightingChecked,
        onCheckedChange = onLightingCheckedChange,
        enabled = enabled && cameraReady,
    )
}

/**
 * Explica para qué se usa la cámara antes de pedir el permiso. Si se negó, ofrece pedirlo
 * de nuevo o abrir los ajustes del sistema.
 */
@Composable
fun CameraPermissionRequest(denied: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Callout(
        title = stringResource(if (denied) R.string.setup_camera_permission else R.string.setup_camera_why_title),
        body = stringResource(if (denied) R.string.setup_camera_permission_body else R.string.setup_camera_why_body),
        tone = if (denied) CalloutTone.Warning else CalloutTone.Neutral,
    ) {
        SecondaryButton(
            text = stringResource(R.string.setup_camera_allow),
            onClick = onAllow,
            modifier = Modifier.testTag(SessionSetupTags.ALLOW_CAMERA),
        )
        if (denied) GhostButton(text = stringResource(R.string.setup_camera_settings), onClick = onOpenSettings)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = EmotvType.SectionTitle,
        color = EmotvColors.Ink,
        modifier = Modifier
            .padding(top = EmotvSpacing.Xs)
            .semantics { heading() },
    )
}

@Composable
fun ErrorCallout(error: SessionError, onRetry: (() -> Unit)?) {
    val (title, body) = when (error) {
        is SessionError.Server ->
            (error.message ?: stringResource(R.string.session_error_generic)) to null
        SessionError.Network ->
            stringResource(R.string.session_error_network) to stringResource(R.string.session_error_network_body)
        SessionError.AccountSessionEnded ->
            stringResource(R.string.session_error_account_ended) to stringResource(R.string.session_error_account_ended_body)
        SessionError.Unexpected ->
            stringResource(R.string.session_error_generic) to stringResource(R.string.session_error_generic_body)
    }
    Callout(title = title, body = body, tone = CalloutTone.Error) {
        if (onRetry != null) SecondaryButton(text = stringResource(R.string.action_retry), onClick = onRetry)
    }
}

private fun ConfirmationItem.label() = when (this) {
    ConfirmationItem.CommitmentReminder -> R.string.setup_confirm_commitment
    ConfirmationItem.AuthorizationConfirmed -> R.string.setup_confirm_authorization
    ConfirmationItem.GuardianPresent -> R.string.setup_confirm_guardian
    ConfirmationItem.ChildAssent -> R.string.setup_confirm_assent
}
