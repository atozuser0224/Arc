package dev.arc.server.content

import dev.arc.api.content.runtime.ContentCapability
import dev.arc.api.content.runtime.ContentRuntimeBackend

public object ArcContentBackend : ContentRuntimeBackend {
    override val capabilities: Set<ContentCapability> =
        setOf(
            ContentCapability.ARC_SYNC,
            ContentCapability.CREATIVE_SYNC,
            ContentCapability.ITEM_FALLBACK,
            ContentCapability.BLOCK_FALLBACK,
            ContentCapability.PALETTE_PERSISTENCE,
        )
}
