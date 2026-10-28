package pe.unheval.emotv.ui.components

import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import pe.unheval.emotv.BuildConfig
import java.util.WeakHashMap

/**
 * Activa `FLAG_SECURE` mientras la pantalla está en composición: impide capturas de
 * pantalla, grabaciones y la vista previa en la lista de aplicaciones recientes.
 * Usar en toda pantalla que muestre sesiones o el PIN.
 *
 * Solo en la versión final. La versión de depuración permite capturas para documentar el
 * proyecto; no se usa con personas evaluadas reales.
 *
 * Lleva la cuenta por ventana para que, durante la transición entre dos pantallas
 * protegidas, la salida de una no quite la protección de la otra.
 */
@Composable
fun SecureWindow() {
    if (BuildConfig.DEBUG) return
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window) {
        SecureFlag.acquire(window)
        onDispose { SecureFlag.release(window) }
    }
}

private object SecureFlag {
    private val holders = WeakHashMap<Window, Int>()

    fun acquire(window: Window) {
        val count = holders[window] ?: 0
        if (count == 0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        holders[window] = count + 1
    }

    fun release(window: Window) {
        val count = (holders[window] ?: 1) - 1
        if (count <= 0) {
            holders.remove(window)
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            holders[window] = count
        }
    }
}
