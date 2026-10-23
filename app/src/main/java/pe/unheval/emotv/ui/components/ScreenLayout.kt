package pe.unheval.emotv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pe.unheval.emotv.R
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvShapes
import pe.unheval.emotv.ui.theme.EmotvSpacing
import pe.unheval.emotv.ui.theme.EmotvType

/**
 * Columna centrada de ancho limitado sobre el fondo cálido. Se desplaza para que el
 * contenido siga accesible con letra grande o con el teclado abierto.
 */
@Composable
fun FormScreen(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EmotvColors.Surface)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(horizontal = EmotvSpacing.Md, vertical = EmotvSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(EmotvSpacing.Md),
            content = content,
        )
    }
}

@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = EmotvType.PageTitleCompact,
        color = EmotvColors.Ink,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
fun PageSubtitle(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = EmotvType.Subtitle, color = EmotvColors.Slate, modifier = modifier)
}

/** Logo de EMOtv sobre azul marino. */
@Composable
fun EmotvLogo(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(EmotvShapes.Lg)
            .background(EmotvColors.Navy),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_emotv_logo),
            contentDescription = stringResource(R.string.logo_description),
            modifier = Modifier.size(size),
        )
    }
}
