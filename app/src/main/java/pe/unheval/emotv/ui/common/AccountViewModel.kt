package pe.unheval.emotv.ui.common

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.unheval.emotv.domain.model.AppError
import pe.unheval.emotv.domain.model.isSessionEnded
import pe.unheval.emotv.domain.usecase.LogoutUseCase

/** Errores de una pantalla de consulta. Los textos los pone la pantalla (strings.xml). */
sealed interface ScreenError {
    /** Mensaje del servidor en español; puede faltar. */
    data class Server(val message: String?) : ScreenError
    data object Network : ScreenError
    data object Unexpected : ScreenError
}

fun AppError.toScreenError(): ScreenError = when (this) {
    is AppError.Server -> ScreenError.Server(message)
    AppError.Network -> ScreenError.Network
    AppError.Unexpected -> ScreenError.Unexpected
}

/** Datos de una pantalla. Viven solo en memoria y se descartan con su ViewModel. */
data class Loadable<T>(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val content: T? = null,
    val error: ScreenError? = null,
) {
    val busy: Boolean get() = isLoading || isRefreshing

    /** Sin datos muestra el indicador de carga; con datos, el de «actualizando». */
    fun reloading(): Loadable<T> =
        if (content == null) copy(isLoading = true, error = null) else copy(isRefreshing = true)

    fun loaded(value: T): Loadable<T> = Loadable(isLoading = false, content = value)

    /** Si ya había datos, se conservan y el error avisa que pueden estar desactualizados. */
    fun failed(error: ScreenError): Loadable<T> = copy(isLoading = false, isRefreshing = false, error = error)
}

/**
 * Pantallas con datos de la cuenta. Si la sesión de la cuenta terminó (renovación
 * rechazada), cierra sesión y avisa para volver al inicio de sesión.
 */
abstract class AccountViewModel(private val logout: LogoutUseCase) : ViewModel() {
    private val ended = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = ended.asStateFlow()

    private var shown = false

    abstract fun refresh()

    /** Al volver a la pantalla (por ejemplo, desde un reporte) se actualiza; la primera vez ya cargó. */
    fun onResume() {
        if (shown) refresh() else shown = true
    }

    /** El error para mostrar, o `null` si la sesión terminó y ya se cerró. */
    protected suspend fun AppError.shownOrLogout(): ScreenError? {
        if (!isSessionEnded()) return toScreenError()
        logout()
        ended.value = true
        return null
    }
}
