package dev.arc.api.nms

import dev.arc.api.Arc
import dev.arc.api.control.ArcFeatures

/**
 * Wraps a real [ArcNms] and routes each group through its feature flag, falling
 * back to a no-op group when the master [ArcFeatures.NMS] flag or the group's own
 * flag is off. The check is live on every access, so toggling takes effect
 * immediately — no re-install required.
 *
 * `arc-server` registers `GatedArcNms(ReflectiveArcNms())` so plugins can switch
 * any NMS capability on or off through [Arc.features].
 */
class GatedArcNms(private val delegate: ArcNms) : ArcNms {

    private fun on(groupId: String): Boolean =
        Arc.features.isEnabled(ArcFeatures.NMS) && Arc.features.isEnabled(groupId)

    override val entities: ArcEntityNms
        get() = if (on(ArcFeatures.NMS_ENTITIES)) delegate.entities else NoOpEntityNms

    override val items: ArcItemNms
        get() = if (on(ArcFeatures.NMS_ITEMS)) delegate.items else NoOpItemNms

    override val blocks: ArcBlockNms
        get() = if (on(ArcFeatures.NMS_BLOCKS)) delegate.blocks else NoOpBlockNms

    override val players: ArcPlayerNms
        get() = if (on(ArcFeatures.NMS_PLAYERS)) delegate.players else NoOpPlayerNms

    override val server: ArcServerNms
        get() = if (on(ArcFeatures.NMS_SERVER)) delegate.server else NoOpServerNms

    override fun handle(bukkit: Any): Any? =
        if (Arc.features.isEnabled(ArcFeatures.NMS)) delegate.handle(bukkit) else null
}
