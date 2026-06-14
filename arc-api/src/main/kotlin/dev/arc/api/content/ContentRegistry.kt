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
    private val sources = linkedMapOf<String, ContentPack>()

    public val current: CompiledContentRevision?
        get() = currentRef.get()

    @Synchronized
    public fun publish(packs: List<ContentPack>): ContentPublishResult {
        val compiled = ContentCompiler.compile(packs)
        return publishCompiled(compiled)
    }

    @Synchronized
    public fun install(owner: String, pack: ContentPack): ContentPublishResult {
        require(owner.isNotBlank()) { "Content owner must not be blank" }
        val candidate = LinkedHashMap(sources)
        candidate[owner] = pack
        val compiled = ContentCompiler.compile(candidate.values.toList())
        if (compiled.revision == null) {
            return ContentPublishResult(false, currentRef.get(), compiled.diagnostics)
        }
        sources.clear()
        sources.putAll(candidate)
        return publishCompiled(compiled)
    }

    @Synchronized
    public fun uninstall(owner: String): ContentPublishResult {
        if (owner !in sources) {
            return ContentPublishResult(true, currentRef.get(), emptyList())
        }
        val candidate = LinkedHashMap(sources)
        candidate.remove(owner)
        val compiled = ContentCompiler.compile(candidate.values.toList())
        if (compiled.revision == null) {
            return ContentPublishResult(false, currentRef.get(), compiled.diagnostics)
        }
        sources.clear()
        sources.putAll(candidate)
        return publishCompiled(compiled)
    }

    public fun subscribe(listener: (CompiledContentRevision) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }

    private fun publishCompiled(compiled: ContentCompileResult): ContentPublishResult {
        val revision = compiled.revision
            ?: return ContentPublishResult(false, currentRef.get(), compiled.diagnostics)
        currentRef.set(revision)
        listeners.forEach { listener -> runCatching { listener(revision) } }
        return ContentPublishResult(true, revision, compiled.diagnostics)
    }
}

public object ArcContent {
    @JvmField
    public val registry: ContentRegistry = ContentRegistry()

    @JvmStatic
    public val current: CompiledContentRevision?
        get() = registry.current
}
