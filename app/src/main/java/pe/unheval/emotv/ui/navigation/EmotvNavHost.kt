package pe.unheval.emotv.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import pe.unheval.emotv.domain.usecase.StartDestination
import pe.unheval.emotv.ui.dashboard.DashboardScreen
import pe.unheval.emotv.ui.dashboard.DashboardViewModel
import pe.unheval.emotv.ui.login.LoginNavigation
import pe.unheval.emotv.ui.login.LoginScreen
import pe.unheval.emotv.ui.login.LoginViewModel
import pe.unheval.emotv.ui.login.PasswordChangeRequiredScreen
import pe.unheval.emotv.ui.pin.PinSetupScreen
import pe.unheval.emotv.ui.pin.PinSetupViewModel
import pe.unheval.emotv.ui.session.LockedSessionActions
import pe.unheval.emotv.ui.session.LockedSessionScreen
import pe.unheval.emotv.ui.session.LockedSessionViewModel
import pe.unheval.emotv.ui.session.SessionSetupScreen
import pe.unheval.emotv.ui.session.SessionSetupViewModel
import pe.unheval.emotv.ui.startup.StartupScreen
import pe.unheval.emotv.ui.startup.StartupUiState
import pe.unheval.emotv.ui.startup.StartupViewModel

@Serializable data object StartupRoute
@Serializable data object LoginRoute
@Serializable data class PasswordChangeRoute(val message: String? = null)
@Serializable data object PinSetupRoute
@Serializable data object DashboardRoute
// Solo el código: el nombre del participante nunca llega al aplicativo.
@Serializable data class SessionSetupRoute(val participantId: String, val participantCode: String)
@Serializable data class LockedSessionRoute(val sessionId: String, val participantKind: String)

// El dashboard vuelve a pedir el resumen al regresar de una sesión.
private const val REFRESH_DASHBOARD = "refresh_dashboard"

@Composable
fun EmotvNavHost(
    startupViewModel: StartupViewModel,
    navController: NavHostController = rememberNavController(),
) {
    // Sin transiciones animadas entre pantallas (DESIGN.md: no animar entradas de página).
    NavHost(
        navController = navController,
        startDestination = StartupRoute,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable<StartupRoute> {
            val state by startupViewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(state) {
                val ready = state as? StartupUiState.Ready ?: return@LaunchedEffect
                val target: Any = when (ready.destination) {
                    StartDestination.Login -> LoginRoute
                    StartDestination.PinSetup -> PinSetupRoute
                    StartDestination.Home -> DashboardRoute
                }
                navController.navigate(target) { popUpTo<StartupRoute> { inclusive = true } }
            }
            StartupScreen(state = state, onRetry = startupViewModel::retry)
        }

        composable<LoginRoute> {
            val viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(state.navigation) {
                when (val navigation = state.navigation) {
                    null -> return@LaunchedEffect
                    LoginNavigation.PinSetup ->
                        navController.navigate(PinSetupRoute) { popUpTo<LoginRoute> { inclusive = true } }
                    is LoginNavigation.PasswordChangeRequired ->
                        navController.navigate(PasswordChangeRoute(navigation.message))
                }
                viewModel.onNavigated()
            }
            LoginScreen(
                state = state,
                onEmailChange = viewModel::onEmailChange,
                onPasswordChange = viewModel::onPasswordChange,
                onSubmit = viewModel::submit,
            )
        }

        composable<PasswordChangeRoute> { entry ->
            val route = entry.toRoute<PasswordChangeRoute>()
            PasswordChangeRequiredScreen(
                serverMessage = route.message,
                onBackToLogin = { navController.popBackStack() },
            )
        }

        composable<PinSetupRoute> {
            val viewModel: PinSetupViewModel = viewModel(factory = PinSetupViewModel.Factory)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(state.saved) {
                if (state.saved) {
                    navController.navigate(DashboardRoute) { popUpTo<PinSetupRoute> { inclusive = true } }
                }
            }
            PinSetupScreen(
                state = state,
                onPinChange = viewModel::onPinChange,
                onConfirmationChange = viewModel::onConfirmationChange,
                onSubmit = viewModel::submit,
            )
        }

        composable<DashboardRoute> { entry ->
            val viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val refreshRequested by entry.savedStateHandle
                .getStateFlow(REFRESH_DASHBOARD, false)
                .collectAsStateWithLifecycle()
            LaunchedEffect(refreshRequested) {
                if (refreshRequested) {
                    entry.savedStateHandle[REFRESH_DASHBOARD] = false
                    viewModel.refresh()
                }
            }
            LaunchedEffect(state.loggedOut) {
                if (state.loggedOut) {
                    // Al sacar el dashboard de la pila se descarta su ViewModel y sus datos.
                    navController.navigate(LoginRoute) { popUpTo<DashboardRoute> { inclusive = true } }
                }
            }
            DashboardScreen(
                state = state,
                onRefresh = viewModel::refresh,
                onSelectParticipant = viewModel::selectParticipant,
                onStartSession = { participant ->
                    navController.navigate(SessionSetupRoute(participant.id, participant.code))
                },
                onLogout = viewModel::logOut,
            )
        }

        composable<SessionSetupRoute> {
            val viewModel: SessionSetupViewModel = viewModel(factory = SessionSetupViewModel.Factory)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(state.locked) {
                val locked = state.locked ?: return@LaunchedEffect
                navController.navigate(LockedSessionRoute(locked.sessionId, locked.participantKind.apiValue)) {
                    popUpTo<SessionSetupRoute> { inclusive = true }
                }
                viewModel.onNavigated()
            }
            LaunchedEffect(state.closed) {
                if (state.closed) navController.popBackStack()
            }
            SessionSetupScreen(
                state = state,
                onRetryProtocols = viewModel::retryProtocols,
                onSelectProtocol = viewModel::selectProtocol,
                onContinue = viewModel::startSession,
                onCheckedChange = viewModel::setChecked,
                onCameraReadyChange = viewModel::onCameraReady,
                onLightingCheckedChange = viewModel::setLightingChecked,
                onHandOver = viewModel::handOver,
                onCancel = viewModel::cancel,
            )
        }

        composable<LockedSessionRoute> {
            val viewModel: LockedSessionViewModel = viewModel(factory = LockedSessionViewModel.Factory)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(state.exited) {
                if (state.exited) {
                    // Al salir de la pila se libera la cámara y se quita el bloqueo.
                    navController.previousBackStackEntry?.savedStateHandle?.set(REFRESH_DASHBOARD, true)
                    navController.popBackStack()
                }
            }
            LockedSessionScreen(
                state = state,
                actions = LockedSessionActions(
                    onAnswer = viewModel::answer,
                    onSubmitQuestionnaire = viewModel::submitQuestionnaire,
                    onStartCalibration = viewModel::startCalibration,
                    onRequestStop = viewModel::requestStop,
                    onRequestExit = viewModel::requestExit,
                    onDismissExit = viewModel::dismissExitRequest,
                    onRetryConnection = viewModel::retryConnection,
                    onPinChange = viewModel::onPinChange,
                    onSubmitPin = viewModel::submitPin,
                    onStopReasonChange = viewModel::setStopReason,
                    onResume = viewModel::resume,
                    onEnd = viewModel::endSession,
                ),
                onAttachCamera = viewModel::attachFrameSource,
                onDetachCamera = viewModel::detachFrameSource,
            )
        }
    }
}
