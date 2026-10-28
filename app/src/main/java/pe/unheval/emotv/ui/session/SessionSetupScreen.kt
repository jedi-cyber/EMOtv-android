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
import pe.unheval.emotv.domain.model.SessionAudience
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
    const val NO_PROTOCOL = "setup_no_protocol"
    const val CONFIRM = "setup_confirm"
    const val HAND_OVER = "setup_hand_over"
    const val ALLOW_CAMERA = "setup_allow_camera"
    fun protocol(id: String) = "setup_protocol_$id"
    fun audience(audience: SessionAudience) = "setup_audience_${audience.apiValue}"
}

/** Acciones de la preparación, agrupadas para no alargar la firma de la pantalla. */
data class SessionSetupActions(
    val onRetry: () -> Unit = {},
    val onChoose: (SetupChoice) -> Unit = {},
    val onChooseAudience: (SessionAudience) -> Unit = {},
    val onConfirmedChange: (Boolean) -> Unit = {},
    val onCameraReadyChange: (Boolean) -> Unit = {},
    val onLightingCheckedChange: (Boolean) -> Unit = {},
    val onHandOver: () -> Unit = {},
    val onCancel: () -> Unit = {},
)

/**
 * Una sola pantalla: qué aplicar (un protocolo o ninguno), con quién es la sesión si no hay
 * protocolo, la cámara y la única confirmación. La sesión se crea al entregar la tablet.
 */
@Composable
fun SessionSetupScreen(state: SessionSetupUiState, actions: SessionSetupActions) {
    SecureWindow()
    BackHandler(onBack = actions.onCancel)

    FormScreen {
        PageTitle(stringResource(R.string.setup_title))
        PageSubtitle(stringResource(R.string.setup_subtitle))

        PlanSection(state, actions)

        if (state.choice == SetupChoice.NoProtocol && !state.isLoadingOptions) {
            AudienceSection(state, actions.onChooseAudience)
        }

        SectionTitle(stringResource(R.string.setup_camera_title))
        CameraCheck(
            cameraReady = state.cameraReady,
            lightingChecked = state.lightingChecked,
            enabled = !state.isStarting,
            onCameraReadyChange = actions.onCameraReadyChange,
            onLightingCheckedChange = actions.onLightingCheckedChange,
        )

        state.confirmationText?.let { text ->
            SectionTitle(stringResource(R.string.setup_confirmation_title))
            CheckboxRow(
                text = text,
                checked = state.confirmed,
                onCheckedChange = actions.onConfirmedChange,
                enabled = !state.isStarting,
                modifier = Modifier.testTag(SessionSetupTags.CONFIRM),
            )
        }

        state.error?.let { ErrorCallout(it, onRetry = if (state.protocols.isEmpty() && state.audiences.isEmpty()) actions.onRetry else null) }

        val missing = when {
            state.plan == null -> R.string.setup_missing_plan
            !state.cameraReady -> R.string.setup_missing_camera
            !state.lightingChecked -> R.string.setup_missing_lighting
            !state.confirmed -> R.string.setup_missing_confirmation
            else -> R.string.setup_lock_notice
        }
        Text(stringResource(missing), style = EmotvType.BodySm, color = EmotvColors.Slate)
        PrimaryButton(
            text = stringResource(R.string.setup_hand_over),
            onClick = actions.onHandOver,
            enabled = state.canHandOver,
            loading = state.isStarting,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SessionSetupTags.HAND_OVER),
        )
        GhostButton(text = stringResource(R.string.setup_back), onClick = actions.onCancel)
    }
}

@Composable
private fun PlanSection(state: SessionSetupUiState, actions: SessionSetupActions) {
    SectionTitle(stringResource(R.string.setup_protocol_title))
    if (state.isLoadingOptions) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(EmotvSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = EmotvColors.Primary, strokeWidth = 2.dp)
            Text(stringResource(R.string.setup_protocol_loading), style = EmotvType.BodyMd, color = EmotvColors.Slate)
        }
        return
    }
    if (state.protocols.isEmpty() && state.error == null) {
        Text(stringResource(R.string.setup_protocol_empty_body), style = EmotvType.BodySm, color = EmotvColors.Slate)
    }
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xs),
    ) {
        state.protocols.forEach { protocol ->
            OptionRow(
                title = protocol.name,
                details = protocolDetails(protocol),
                selected = state.choice == SetupChoice.Protocol(protocol.id),
                enabled = !state.isStarting,
                onSelect = { actions.onChoose(SetupChoice.Protocol(protocol.id)) },
                modifier = Modifier.testTag(SessionSetupTags.protocol(protocol.id)),
            )
        }
        OptionRow(
            title = stringResource(R.string.setup_no_protocol),
            details = stringResource(R.string.setup_no_protocol_body),
            selected = state.choice == SetupChoice.NoProtocol,
            enabled = !state.isStarting,
            onSelect = { actions.onChoose(SetupChoice.NoProtocol) },
            modifier = Modifier.testTag(SessionSetupTags.NO_PROTOCOL),
        )
    }
}

@Composable
private fun AudienceSection(state: SessionSetupUiState, onChoose: (SessionAudience) -> Unit) {
    SectionTitle(stringResource(R.string.setup_audience_title))
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xs),
    ) {
        state.audiences.forEach { option ->
            OptionRow(
                title = stringResource(audienceLabel(option.audience)),
                details = null,
                selected = state.audience == option.audience,
                enabled = !state.isStarting,
                onSelect = { onChoose(option.audience) },
                modifier = Modifier.testTag(SessionSetupTags.audience(option.audience)),
            )
        }
    }
}

@Composable
private fun protocolDetails(protocol: SessionProtocol): String = buildList {
    add(stringResource(audienceLabel(protocol.audience)))
    add(pluralStringResource(R.plurals.setup_protocol_steps, protocol.stepCount, protocol.stepCount))
    if (protocol.hasPreQuestionnaire) add(stringResource(R.string.setup_protocol_pre))
    if (protocol.hasPostQuestionnaire) add(stringResource(R.string.setup_protocol_post))
    add(stringResource(R.string.setup_protocol_version, protocol.version))
}.joinToString(" · ")

private fun audienceLabel(audience: SessionAudience): Int = when (audience) {
    SessionAudience.Own -> R.string.audience_own
    SessionAudience.Adult -> R.string.audience_adult
    SessionAudience.Child -> R.string.audience_child
    SessionAudience.Unknown -> R.string.audience_unknown
}

@Composable
private fun OptionRow(
    title: String,
    details: String?,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
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
            Text(title, style = EmotvType.CardTitle, color = EmotvColors.Ink)
            if (details != null) Text(details, style = EmotvType.BodySm, color = EmotvColors.Steel)
        }
    }
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
