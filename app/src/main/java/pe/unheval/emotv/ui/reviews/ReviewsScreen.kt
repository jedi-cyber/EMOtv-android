package pe.unheval.emotv.ui.reviews

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import pe.unheval.emotv.R
import pe.unheval.emotv.domain.model.PendingReview
import pe.unheval.emotv.ui.common.Loadable
import pe.unheval.emotv.ui.components.ContentScreen
import pe.unheval.emotv.ui.components.ContentTags
import pe.unheval.emotv.ui.components.EmotvCard
import pe.unheval.emotv.ui.components.SessionRow
import pe.unheval.emotv.ui.theme.EmotvColors
import pe.unheval.emotv.ui.theme.EmotvType
import java.time.ZoneId

/** Pendientes del Docente. Tocar uno abre su reporte para revisarlo. */
@Composable
fun ReviewsScreen(
    state: Loadable<List<PendingReview>>,
    onRefresh: () -> Unit,
    onOpenReport: (String) -> Unit,
    onBack: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val pending = state.content.orEmpty()
    ContentScreen(
        title = stringResource(R.string.reviews_title),
        subtitle = stringResource(R.string.reviews_subtitle),
        onBack = onBack,
        isLoading = state.isLoading,
        isRefreshing = state.isRefreshing,
        error = state.error,
        hasContent = state.content != null,
        onRefresh = onRefresh,
    ) {
        if (pending.isEmpty()) {
            item(key = "empty") {
                Text(stringResource(R.string.reviews_empty), style = EmotvType.BodyMd, color = EmotvColors.Slate)
            }
            return@ContentScreen
        }
        item(key = "pending") {
            EmotvCard {
                Column {
                    pending.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(color = EmotvColors.Hairline)
                        SessionRow(
                            session = item.session,
                            zone = zone,
                            onClick = { onOpenReport(item.reportId) },
                            modifier = Modifier.testTag(ContentTags.session(item.session.id)),
                        )
                    }
                }
            }
        }
    }
}
