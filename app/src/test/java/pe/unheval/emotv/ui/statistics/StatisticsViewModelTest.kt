package pe.unheval.emotv.ui.statistics

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pe.unheval.emotv.domain.model.StatisticsScope
import pe.unheval.emotv.domain.usecase.LogoutUseCase
import pe.unheval.emotv.testing.MainDispatcherRule
import pe.unheval.emotv.testing.TestGraph
import pe.unheval.emotv.testing.sessionDto
import pe.unheval.emotv.testing.statisticsDto
import pe.unheval.emotv.ui.reviews.ReviewsViewModel

class StatisticsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val graph = TestGraph()

    @Before
    fun signIn() = runTest {
        graph.authRepository.login("usuario1@example.com", "valor-de-prueba")
    }

    private fun viewModel(isDocente: Boolean) =
        StatisticsViewModel(graph.statisticsRepository, isDocente, LogoutUseCase(graph.authRepository))

    @Test
    fun `carga las estadísticas propias, que cuentan sesiones`() = runTest {
        val statistics = checkNotNull(viewModel(isDocente = false).uiState.value.data.content)

        assertEquals(listOf("GET statistics?scope=mine"), graph.api.calls)
        assertEquals(4, statistics.total)
        assertEquals(125.0, statistics.averageDurationSeconds!!, 0.0)
        assertEquals("neutral", statistics.expressions.single().expression)
        assertEquals(1, statistics.matched)
    }

    @Test
    fun `el Usuario no cambia de alcance`() = runTest {
        val vm = viewModel(isDocente = false)

        vm.chooseScope(StatisticsScope.Group)

        assertEquals(StatisticsScope.Mine, vm.uiState.value.scope)
        assertEquals(1, graph.api.calls.size)
    }

    @Test
    fun `el Docente consulta las de su grupo`() = runTest {
        graph.api.statistics = { statisticsDto(scope = it, total = if (it == "group") 9 else 4) }
        val vm = viewModel(isDocente = true)

        vm.chooseScope(StatisticsScope.Group)

        assertEquals(listOf("GET statistics?scope=mine", "GET statistics?scope=group"), graph.api.calls)
        assertEquals(StatisticsScope.Group, vm.uiState.value.scope)
        assertEquals(9, vm.uiState.value.data.content?.total)
    }

    @Test
    fun `pendientes del Docente abren el reporte de cada sesión`() = runTest {
        graph.api.reviews = { listOf(sessionDto(id = "s-1", mine = false, reportStatus = "submitted", reportId = "r-1")) }

        val pending = ReviewsViewModel(graph.reportRepository, LogoutUseCase(graph.authRepository)).uiState.value.content!!

        assertEquals("r-1", pending.single().reportId)
        assertEquals("s-1", pending.single().session.id)
        assertTrue("GET reviews" in graph.api.calls)
    }
}
