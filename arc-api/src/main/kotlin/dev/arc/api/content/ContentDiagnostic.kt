package dev.arc.api.content

public enum class DiagnosticSeverity {
    ERROR,
    WARNING,
}

public data class ContentDiagnostic(
    public val severity: DiagnosticSeverity,
    public val message: String,
    public val packId: ContentId? = null,
    public val contentId: ContentId? = null,
    public val field: String? = null,
)
