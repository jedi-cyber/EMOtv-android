package pe.unheval.emotv.data.repository

import pe.unheval.emotv.data.remote.CommentRequestDto
import pe.unheval.emotv.data.remote.EmotvApi
import pe.unheval.emotv.data.remote.NarrativeRequestDto
import pe.unheval.emotv.domain.model.AppError
import pe.unheval.emotv.domain.model.ErrorCodes
import pe.unheval.emotv.domain.model.Outcome
import pe.unheval.emotv.domain.model.PendingReview
import pe.unheval.emotv.domain.model.Report
import pe.unheval.emotv.domain.model.ReportSection
import pe.unheval.emotv.domain.repository.AuthRepository
import pe.unheval.emotv.domain.repository.ReportRepository

class ReportRepositoryImpl(
    private val api: EmotvApi,
    private val authRepository: AuthRepository,
) : ReportRepository {

    override suspend fun forSession(sessionId: String): Outcome<Report?> =
        when (val result = authRepository.authorizedCall { api.sessionReport(sessionId).toDomain() }) {
            is Outcome.Success -> result
            // La sesión ya se cargó: un 404 aquí significa que todavía no tiene reporte.
            is Outcome.Failure ->
                if (result.error.isNotFound()) Outcome.Success(null) else result
        }

    override suspend fun create(sessionId: String): Outcome<Report> =
        authRepository.authorizedCall { api.createReport(sessionId).toDomain() }

    override suspend fun get(reportId: String): Outcome<Report> =
        authRepository.authorizedCall { api.report(reportId).toDomain() }

    override suspend fun edit(reportId: String, narrative: String): Outcome<Report> =
        authRepository.authorizedCall { api.editReport(reportId, NarrativeRequestDto(narrative)).toDomain() }

    override suspend fun submit(reportId: String): Outcome<Report> =
        authRepository.authorizedCall { api.submitReport(reportId).toDomain() }

    override suspend fun comment(reportId: String, section: ReportSection, text: String): Outcome<Report> =
        authRepository.authorizedCall {
            api.commentReport(reportId, CommentRequestDto(section.apiValue, text)).toDomain()
        }

    override suspend fun returnWithComments(reportId: String): Outcome<Report> =
        authRepository.authorizedCall { api.returnReport(reportId).toDomain() }

    override suspend fun approve(reportId: String): Outcome<Report> =
        authRepository.authorizedCall { api.approveReport(reportId).toDomain() }

    override suspend fun pendingReviews(): Outcome<List<PendingReview>> =
        authRepository.authorizedCall {
            api.reviews().mapNotNull { dto -> dto.reportId?.let { PendingReview(dto.toDomain(), it) } }
        }
}

private fun AppError.isNotFound(): Boolean =
    this is AppError.Server && httpStatus == 404 && code == ErrorCodes.NOT_FOUND
