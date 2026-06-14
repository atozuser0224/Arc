package dev.arc.api.content.pack

import dev.arc.api.content.CompiledContentRevision
import dev.arc.api.content.ContentDiagnostic
import dev.arc.api.content.ContentRegistry
import dev.arc.api.content.DiagnosticSeverity
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory

public data class ContentDirectoryPublishResult(
    public val accepted: Boolean,
    public val revision: CompiledContentRevision?,
    public val diagnostics: List<ContentDiagnostic>,
)

public class ContentPackDirectory(
    private val loader: FileContentPackLoader = FileContentPackLoader(),
) {
    public fun publish(root: Path, registry: ContentRegistry): ContentDirectoryPublishResult {
        root.createDirectories()
        val loaded = Files.list(root).use { paths ->
            paths.filter { it.isDirectory() }.sorted().map(loader::load).toList()
        }
        val diagnostics = loaded.flatMap { it.diagnostics }
        if (loaded.any { it.pack == null } || diagnostics.any { it.severity == DiagnosticSeverity.ERROR }) {
            return ContentDirectoryPublishResult(false, registry.current, diagnostics)
        }
        val published = registry.publish(loaded.mapNotNull { it.pack })
        return ContentDirectoryPublishResult(
            accepted = published.accepted,
            revision = published.revision,
            diagnostics = diagnostics + published.diagnostics,
        )
    }
}
