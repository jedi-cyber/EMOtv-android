package pe.unheval.emotv.ui.participation

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import pe.unheval.emotv.R
import pe.unheval.emotv.domain.model.GameAction
import pe.unheval.emotv.domain.model.GameOption
import pe.unheval.emotv.domain.model.GameState
import pe.unheval.emotv.domain.model.GameType
import pe.unheval.emotv.domain.model.MatchingGame
import pe.unheval.emotv.ui.components.Callout
import pe.unheval.emotv.ui.components.CalloutTone
import pe.unheval.emotv.ui.components.PrimaryButton
import pe.unheval.emotv.ui.components.SecondaryButton
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvSpacing
import pe.unheval.emotv.ui.theme.EmotvType

/** Los mismos pictogramas que la web (`web/src/stimuli/labels.ts`). */
internal val EMOTION_PICTOGRAMS = mapOf(
    "happiness" to "😊", "sadness" to "😢", "anger" to "😠", "fear" to "😨",
    "surprise" to "😮", "disgust" to "🤢", "neutral" to "😐", "contempt" to "😒",
)

/**
 * «Empareja la emoción», como en la web: la imagen de la ronda y un botón por
 * emoción, sin arrastrar. Si la persona elige otra, se le invita a mirar otra vez;
 * tras dos, se le muestra cuál es con su explicación. «Ayuda» quita una opción.
 * Sin puntaje: solo «Ronda 2 de 3». Lo que pasó lo dice el servidor en su estado.
 */
@Composable
internal fun MatchingGameView(
    game: MatchingGame,
    image: RoundImage?,
    pictograms: Boolean,
    sending: Boolean,
    failed: Boolean,
    onAction: (GameAction, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (game.type != GameType.Matching) {
        // «Busca la misma emoción» todavía no se juega en Android: quien conduce registra en la web.
        Column(modifier) {
            Text(stringResource(R.string.participation_follow_conductor), style = EmotvType.Subtitle,
                modifier = Modifier.testTag(ParticipationTags.GAME_ELSEWHERE))
        }
        return
    }
    val playing = game.state.isPlaying
    val revealed = game.state.isRevealed
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Sm),
    ) {
        Text(stringResource(R.string.game_round, game.number, game.total), style = EmotvType.Caption,
            color = EmotvColors.Slate, modifier = Modifier.testTag(ParticipationTags.GAME_ROUND))
        val bitmap = rememberRoundBitmap(image)
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.game_image),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).testTag(ParticipationTags.GAME_IMAGE),
            )
        }
        Text(game.question, style = EmotvType.Subtitle, modifier = Modifier.semantics { heading() })
        game.options.forEach { option ->
            GameOptionButton(
                option = option,
                isAnswer = revealed && game.answer == option.key,
                pictogram = EMOTION_PICTOGRAMS[option.key].takeIf { pictograms },
                enabled = playing && option.available && !sending,
                onClick = { onAction(GameAction.Answer, option.key) },
            )
        }
        // Lo que pasó en la ronda: TalkBack lo lee al cambiar.
        Column(
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(ParticipationTags.GAME_FEEDBACK),
            verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Xs),
        ) {
            game.message?.let { Text(it, style = EmotvType.Subtitle) }
            game.explanation?.let { Text(it, style = EmotvType.BodyMd) }
        }
        if (failed) {
            Callout(stringResource(R.string.game_send_failed), null, CalloutTone.Error)
        }
        if (revealed) {
            PrimaryButton(
                stringResource(if (game.lastRound) R.string.game_finish else R.string.game_next),
                { onAction(GameAction.Next, null) },
                Modifier.fillMaxWidth().testTag(ParticipationTags.GAME_NEXT),
                enabled = !sending,
            )
        }
        if (game.canHelp) {
            SecondaryButton(stringResource(R.string.game_help), { onAction(GameAction.Help, null) },
                Modifier.fillMaxWidth().testTag(ParticipationTags.GAME_HELP), enabled = !sending)
        }
        if (game.state == GameState.Finished || game.state == GameState.Ended) {
            Text(stringResource(R.string.participation_follow_conductor), style = EmotvType.Caption,
                color = EmotvColors.Slate)
        }
    }
}

/** Un botón por emoción. TalkBack lee el nombre y su estado; el pictograma y la marca no se leen. */
@Composable
private fun GameOptionButton(
    option: GameOption,
    isAnswer: Boolean,
    pictogram: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val state = when {
        isAnswer -> stringResource(R.string.game_option_answer)
        !option.available -> stringResource(R.string.game_option_unavailable)
        else -> null
    }
    val modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = EmotvSpacing.TouchTarget)
        .semantics { if (state != null) stateDescription = state }
        .testTag(ParticipationTags.gameOption(option.key))
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EmotvSpacing.Xs)) {
            pictogram?.let { Text(it, modifier = Modifier.clearAndSetSemantics { }) }
            Text(option.label)
            // Visible para no depender del color.
            if (isAnswer) Text(stringResource(R.string.game_is_this), modifier = Modifier.clearAndSetSemantics { })
        }
    }
    if (isAnswer) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) { content() }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { content() }
    }
}

/** Decodifica la imagen en memoria, reducida a un tamaño de pantalla. */
@Composable
private fun rememberRoundBitmap(image: RoundImage?): ImageBitmap? = remember(image) {
    val bytes = image?.bytes ?: return@remember null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= MAX_IMAGE_SIDE || bounds.outHeight / (sample * 2) >= MAX_IMAGE_SIDE) {
        sample *= 2
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?.asImageBitmap()
}

private const val MAX_IMAGE_SIDE = 1024
