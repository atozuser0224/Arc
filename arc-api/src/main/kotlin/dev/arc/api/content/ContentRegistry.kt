package dev.arc.api.content

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

public data class ContentPublishResult(
    public val accepted: Boolean,
    public val revision: CompiledContentRevision?,
    public val diagnostics: List<ContentDiagnostic>,
)

public class ContentRegistry {
    private val currentRef = AtomicReference<CompiledContentRevision?>(null)
    private val listeners = CopyOnWriteArrayList<(CompiledContentRevision) -> Unit>()

    public val current: CompiledContentRevision?
        get() = currentRef.get()

    @Synchronized
    public fun publish(packs: List<ContentPack>): ContentPublishResult {
        val compiled = ContentCompiler.compile(packs)
        val revision = compiled.revision
            ?: return ContentPublishResult(false, currentRef.get(), compiled.diagnostics)
        currentRef.set(revision)
        listeners.forEach { listener -> runCatching { listener(revision) } }
        return ContentPublishResult(true, revision, compiled.diagnostics)
    }

    public fun subscribe(listener: (CompiledContentRevision) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }
}

public object ArcContent {
    @JvmField
    public val registry: ContentRegistry = ContentRegistry()

    @JvmStatic
    public val current: CompiledContentRevision?
        get() = registry.current
}
