package dev.arc.api.content.runtime

import java.util.concurrent.atomic.AtomicReference

public enum class ContentCapability {
    ARC_SYNC,
    CREATIVE_SYNC,
    ITEM_FALLBACK,
    BLOCK_FALLBACK,
    PALETTE_PERSISTENCE,
}

public interface ContentRuntimeBackend {
    public val capabilities: Set<ContentCapability>
}

public object ArcContentRuntime {
    private val backendRef = AtomicReference<ContentRuntimeBackend?>(null)

    @JvmStatic
    public fun installBackend(backend: ContentRuntimeBackend?) {
        backendRef.set(backend)
    }

    @JvmStatic
    public val isAvailable: Boolean
        get() = backendRef.get() != null

    @JvmStatic
    public val capabilities: Set<ContentCapability>
        get() = backendRef.get()?.capabilities.orEmpty()

    @JvmStatic
    public fun supports(capability: ContentCapability): Boolean =
        capability in capabilities
}
