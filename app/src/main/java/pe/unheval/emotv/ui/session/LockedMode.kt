package pe.unheval.emotv.ui.session

import android.app.Activity
import android.app.ActivityManager
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

/**
 * Fija la pantalla (screen pinning), oculta las barras del sistema y la mantiene encendida
 * mientras la sesión está en composición. Al salir, deshace todo. En un cambio de
 * configuración (por ejemplo, al girar la tablet) se conserva el bloqueo.
 */
@Composable
fun LockedModeEffect() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.pinScreen()
        onDispose {
            if (!activity.isChangingConfigurations) {
                activity.unpinScreen()
                controller.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}

/** Si la pantalla sigue fijada; se revisa cada pocos segundos. */
@Composable
fun rememberScreenPinned(): State<Boolean> {
    val activity = LocalActivity.current
    return produceState(initialValue = true, activity) {
        while (activity != null) {
            delay(PINNED_CHECK_MS)
            value = activity.isScreenPinned()
        }
    }
}

/**
 * Sin el aplicativo como propietario del dispositivo, Android pide confirmar la fijación;
 * si no se confirma, la sesión sigue y la pantalla avisa con un botón para reintentarlo.
 */
fun Activity.pinScreen() {
    if (!isScreenPinned()) runCatching { startLockTask() }
}

private fun Activity.unpinScreen() {
    if (isScreenPinned()) runCatching { stopLockTask() }
}

private fun Activity.isScreenPinned(): Boolean =
    getSystemService(ActivityManager::class.java)?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

private const val PINNED_CHECK_MS = 2_000L
