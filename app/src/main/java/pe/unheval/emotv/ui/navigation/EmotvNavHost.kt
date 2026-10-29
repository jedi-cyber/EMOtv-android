package pe.unheval.emotv.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import pe.unheval.emotv.BuildConfig
import pe.unheval.emotv.domain.usecase.StartDestination
import pe.unheval.emotv.ui.common.AccountViewModel
import pe.unheval.emotv.ui.dashboard.DashboardActions
import pe.unheval.emotv.ui.dashboard.DashboardScreen
import pe.unheval.emotv.ui.dashboard.DashboardViewModel
import pe.unheval.emotv.ui.login.LoginNavigation
import pe.unheval.emotv.ui.login.LoginScreen
import pe.unheval.emotv.ui.login.LoginViewModel
import pe.unheval.emotv.ui.login.PasswordChangeRequiredScreen
import pe.unheval.emotv.ui.report.ReportActions
import pe.unheval.emotv.ui.report.ReportScreen
import pe.unheval.emotv.ui.report.ReportViewModel
import pe.unheval.emotv.ui.reviews.ReviewsScreen
import pe.unheval.emotv.ui.reviews.ReviewsViewModel
import pe.unheval.emotv.ui.sessions.SessionDetailScreen
import pe.unheval.emotv.ui.sessions.SessionDetailViewModel
import pe.unheval.emotv.ui.sessions.SessionsScreen
import pe.unheval.emotv.ui.sessions.SessionsViewModel
import pe.unheval.emotv.ui.startup.StartupScreen
import pe.unheval.emotv.ui.startup.StartupUiState
import pe.unheval.emotv.ui.startup.StartupViewModel
import pe.unheval.emotv.ui.statistics.StatisticsScreen
import pe.unheval.emotv.ui.statistics.StatisticsViewModel

@Serializable data object StartupRoute
@Serializable data object LoginRoute
@Serializable data class PasswordChangeRoute(val message: String? = null)
@Serializable data object DashboardRoute
@Serializable data object SessionsRoute
// Solo identificadores: el aplicativo nunca recibe datos de la persona evaluada.
@Serializable data class SessionDetailRoute(val sessionId: String)
@Serializable data class ReportRoute(val reportId: String)
@Serializable data object ReviewsRoute
@Serializable data object StatisticsRoute

@Composable
fun EmotvNavHost(
    startupViewModel: StartupViewModel,
    navController: NavHostController = rememberNavController(),
) {
    // Al terminar la sesión de la cuenta se vacía la pila: se descartan los ViewModel y sus datos.
    val toLogin: () -> Unit = {
        navController.navigate(LoginRoute) { popUpTo(navController.graph.id) { inclusive = true } }
    }

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
                    StartDestination.Home -> DashboardRoute
                }
                navController.navigate(target) { popUpTo<StartupRoute> { inclusive = true } }
            }
            StartupScreen(
                state = state,
                onRetry = startupViewModel::retry,
                serverUrl = BuildConfig.API_BASE_URL.takeIf { BuildConfig.DEBUG },
            )
        }

        composable<LoginRoute> {
            val viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(state.navigation) {
                when (val navigation = state.navigation) {
                    null -> return@LaunchedEffect
                    LoginNavigation.Home ->
                        navController.navigate(DashboardRoute) { popUpTo<LoginRoute> { inclusive = true } }
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

        composable<DashboardRoute> {
            val viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
            LaunchedEffect(state.loggedOut) {
                if (state.loggedOut) toLogin()
            }
            DashboardScreen(
                state = state,
                actions = DashboardActions(
                    onRefresh = viewModel::refresh,
                    onOpenSession = { id -> navController.navigate(SessionDetailRoute(id)) },
                    onOpenSessions = { navController.navigate(SessionsRoute) },
                    onOpenStatistics = { navController.navigate(StatisticsRoute) },
                    onOpenReviews = { navController.navigate(ReviewsRoute) },
                    onLogout = viewModel::logOut,
                ),
            )
        }

        composable<SessionsRoute> {
            val viewModel: SessionsViewModel = viewModel(factory = SessionsViewModel.Factory)
            AccountEffects(viewModel, toLogin)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            SessionsScreen(
                state = state,
                onRefresh = viewModel::refresh,
                onOpenSession = { id -> navController.navigate(SessionDetailRoute(id)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable<SessionDetailRoute> {
            val viewModel: SessionDetailViewModel = viewModel(factory = SessionDetailViewModel.Factory)
            AccountEffects(viewModel, toLogin)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(state.openReport) {
                val reportId = state.openReport ?: return@LaunchedEffect
                navController.navigate(ReportRoute(reportId))
                viewModel.onReportOpened()
            }
            SessionDetailScreen(
                state = state,
                onRefresh = viewModel::refresh,
                onOpenReport = { id -> navController.navigate(ReportRoute(id)) },
                onStartReport = viewModel::createReport,
                onBack = { navController.popBackStack() },
            )
        }

        composable<ReportRoute> {
            val viewModel: ReportViewModel = viewModel(factory = ReportViewModel.Factory)
            AccountEffects(viewModel, toLogin)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            ReportScreen(
                state = state,
                actions = ReportActions(
                    onRefresh = viewModel::refresh,
                    onNarrativeChange = viewModel::onNarrativeChange,
                    onSave = viewModel::save,
                    onSubmit = viewModel::submit,
                    onCommentChange = viewModel::onCommentChange,
                    onSectionChange = viewModel::onSectionChange,
                    onComment = viewModel::comment,
                    onReturn = viewModel::returnWithComments,
                    onApprove = viewModel::approve,
                    onBack = { navController.popBackStack() },
                ),
            )
        }

        composable<ReviewsRoute> {
            val viewModel: ReviewsViewModel = viewModel(factory = ReviewsViewModel.Factory)
            AccountEffects(viewModel, toLogin)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            ReviewsScreen(
                state = state,
                onRefresh = viewModel::refresh,
                onOpenReport = { id -> navController.navigate(ReportRoute(id)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable<StatisticsRoute> {
            val viewModel: StatisticsViewModel = viewModel(factory = StatisticsViewModel.Factory)
            AccountEffects(viewModel, toLogin)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            StatisticsScreen(
                state = state,
                onRefresh = viewModel::refresh,
                onChooseScope = viewModel::chooseScope,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** Actualiza al volver a la pantalla y vuelve al inicio de sesión si la cuenta se cerró. */
@Composable
private fun AccountEffects(viewModel: AccountViewModel, toLogin: () -> Unit) {
    val loggedOut by viewModel.loggedOut.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    LaunchedEffect(loggedOut) {
        if (loggedOut) toLogin()
    }
}
