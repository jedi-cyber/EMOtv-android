package pe.unheval.emotv.ui.report

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pe.unheval.emotv.data.remote.CommentDto
import pe.unheval.emotv.data.remote.CommentRequestDto
import pe.unheval.emotv.domain.model.ReportSection
import pe.unheval.emotv.domain.model.ReportStatus
import pe.unheval.emotv.domain.usecase.LogoutUseCase
import pe.unheval.emotv.testing.MainDispatcherRule
import pe.unheval.emotv.testing.TestGraph
import pe.unheval.emotv.testing.mobileError
import pe.unheval.emotv.testing.reportDto
import pe.unheval.emotv.ui.common.ScreenError

class ReportViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val graph = TestGraph()

    @Before
    fun signIn() = runTest {
        graph.authRepository.login("usuario1@example.com", "valor-de-prueba")
    }

    private fun viewModel() = ReportViewModel("reporte-1", graph.reportRepository, LogoutUseCase(graph.authRepository))

    @Test
    fun `el autor guarda el texto y lo envía a revisión`() = runTest {
        graph.api.report = { reportDto(id = it, canEdit = true, authoredByMe = true) }
        val vm = viewModel()

        vm.onNarrativeChange("Se aplicó el protocolo completo.")
        assertTrue(vm.uiState.value.hasUnsavedText)
        vm.submit()

        // Primero guarda el texto y luego lo envía.
        assertEquals(
            listOf("GET reports/reporte-1", "PUT reports/reporte-1", "POST reports/reporte-1/submit"),
            graph.api.calls,
        )
        assertEquals("Se aplicó el protocolo completo.", graph.api.narrativeRequests.single().narrative)
        val state = vm.uiState.value
        assertEquals(ReportStatus.Submitted, state.data.content?.status)
        assertNull(state.running)
    }

    @Test
    fun `enviar sin cambios no vuelve a guardar`() = runTest {
        graph.api.report = { reportDto(id = it, narrative = "Listo.", canEdit = true, authoredByMe = true) }
        val vm = viewModel()

        vm.submit()

        assertEquals(listOf("GET reports/reporte-1", "POST reports/reporte-1/submit"), graph.api.calls)
    }

    @Test
    fun `si guardar falla no se envía y se muestra el error`() = runTest {
        graph.api.report = { reportDto(id = it, canEdit = true, authoredByMe = true) }
        graph.api.editReport = { _, _ -> throw mobileError(409, "invalid_state", "El reporte ya se envió.") }
        val vm = viewModel()

        vm.onNarrativeChange("Texto")
        vm.submit()

        assertFalse("POST reports/reporte-1/submit" in graph.api.calls)
        assertEquals(ScreenError.Server("El reporte ya se envió."), vm.uiState.value.actionError)
        // El texto escrito no se pierde.
        assertEquals("Texto", vm.uiState.value.narrative)
    }

    @Test
    fun `el Docente comenta una sección y el campo se vacía`() = runTest {
        graph.api.report = { reportDto(id = it, status = "submitted", canReview = true) }
        graph.api.commentReport = { id, body ->
            reportDto(
                id = id,
                status = "submitted",
                canReview = true,
                comments = listOf(CommentDto("c-1", body.section, body.text, "2026-10-29T15:00:00+00:00")),
            )
        }
        val vm = viewModel()

        vm.onSectionChange(ReportSection.Timeline)
        vm.onCommentChange("  Revisa los tiempos.  ")
        vm.comment()

        assertEquals(CommentRequestDto("timeline", "Revisa los tiempos."), graph.api.commentRequests.single())
        val state = vm.uiState.value
        assertEquals("", state.commentText)
        assertEquals(ReportSection.Timeline, state.data.content?.comments?.single()?.section)
    }

    @Test
    fun `comentario vacío no llama al servidor`() = runTest {
        graph.api.report = { reportDto(id = it, status = "submitted", canReview = true) }
        val vm = viewModel()

        vm.onCommentChange("   ")
        vm.comment()

        assertTrue(graph.api.commentRequests.isEmpty())
    }

    @Test
    fun `el Docente devuelve o aprueba el reporte`() = runTest {
        graph.api.report = { reportDto(id = it, status = "submitted", canReview = true) }
        val vm = viewModel()

        vm.returnWithComments()
        assertEquals(ReportStatus.ChangesRequested, vm.uiState.value.data.content?.status)

        vm.approve()
        assertEquals(ReportStatus.Approved, vm.uiState.value.data.content?.status)
        assertTrue("POST reports/reporte-1/approve" in graph.api.calls)
    }

    @Test
    fun `no se recarga encima de un texto sin guardar`() = runTest {
        graph.api.report = { reportDto(id = it, canEdit = true, authoredByMe = true) }
        val vm = viewModel()

        vm.onNarrativeChange("Borrador")
        vm.refresh()

        assertEquals(1, graph.api.calls.count { it == "GET reports/reporte-1" })
        assertEquals("Borrador", vm.uiState.value.narrative)
    }
}
