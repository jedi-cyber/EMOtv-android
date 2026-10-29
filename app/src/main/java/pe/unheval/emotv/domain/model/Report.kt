package pe.unheval.emotv.domain.model

import java.time.Instant

/**
 * Reporte de una sesión. Lo redacta quien la condujo y lo revisa su Docente; el servidor
 * decide qué puede hacer cada cuenta ([canEdit], [canReview]) y el aplicativo solo lo refleja.
 */
data class Report(
    val id: String,
    val version: Int,
    val status: ReportStatus,
    /** `false` si una versión más reciente lo reemplazó. */
    val current: Boolean,
    val narrative: String,
    val sessionCode: String?,
    val estimatesNotice: String,
    val abstractNotice: String?,
    val authoredByMe: Boolean,
    /** `false` si lo redactó un Docente: al cerrarlo queda aprobado. */
    val requiresReview: Boolean,
    val canEdit: Boolean,
    val canReview: Boolean,
    val comments: List<ReportComment>,
)

data class ReportComment(
    val id: String,
    val section: ReportSection,
    val text: String,
    val createdAt: Instant,
)

enum class ReportStatus(val apiValue: String) {
    Draft("draft"),
    Submitted("submitted"),
    ChangesRequested("changes_requested"),
    Approved("approved"),
    Unknown(""),
    ;

    companion object {
        fun fromApi(value: String) = entries.firstOrNull { it.apiValue == value } ?: Unknown
    }
}

/** Parte del reporte a la que se refiere un comentario. */
enum class ReportSection(val apiValue: String) {
    General("general"),
    Narrative("narrative"),
    Timeline("timeline"),
    Metrics("metrics"),
    Questionnaires("questionnaires"),
    ;

    companion object {
        fun fromApi(value: String) = entries.firstOrNull { it.apiValue == value } ?: General
    }
}

/** Reporte de una sesión del grupo que espera la revisión del Docente. */
data class PendingReview(val session: SessionSummary, val reportId: String)
