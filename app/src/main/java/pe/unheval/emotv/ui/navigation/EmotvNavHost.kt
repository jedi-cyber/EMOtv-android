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
import pe.unheval.emotv.ui.home.HomeScreen
import pe.unheval.emotv.ui.home.HomeViewModel
import pe.unheval.emotv.ui.login.LoginNavigation
import pe.unheval.emotv.ui.login.LoginScreen
import pe.unheval.emotv.ui.login.LoginViewModel
import pe.unheval.emotv.ui.login.PasswordChangeRequiredScreen
import pe.unheval.emotv.ui.pin.PinSetupScreen
import pe.unheval.emotv.ui.pin.PinSetupViewModel
import pe.unheval.emotv.ui.startup.StartupScreen
import pe.unheval.emotv.ui.startup.StartupUiState
import pe.unheval.emotv.ui.startup.StartupViewModel

@Serializable data object StartupRoute
@Serializable data object LoginRoute
@Serializable data class PasswordChangeRoute(val message: String? = null)
@Serializable data object PinSetupRoute
@Serializable data object HomeRoute

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
                    StartDestination.Home -> HomeRoute
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
                    navController.navigate(HomeRoute) { popUpTo<PinSetupRoute> { inclusive = true } }
                }
            }
            PinSetupScreen(
                state = state,
                onPinChange = viewModel::onPinChange,
                onConfirmationChange = viewModel::onConfirmationChange,
                onSubmit = viewModel::submit,
            )
        }

        composable<HomeRoute> {
            val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
            val user by viewModel.user.collectAsStateWithLifecycle()
            val loggingOut by viewModel.isLoggingOut.collectAsStateWithLifecycle()
            val loggedOut by viewModel.isLoggedOut.collectAsStateWithLifecycle()
            LaunchedEffect(loggedOut) {
                if (loggedOut) {
                    navController.navigate(LoginRoute) { popUpTo<HomeRoute> { inclusive = true } }
                }
            }
            HomeScreen(user = user, isLoggingOut = loggingOut, onLogout = viewModel::logOut)
        }
    }
}
