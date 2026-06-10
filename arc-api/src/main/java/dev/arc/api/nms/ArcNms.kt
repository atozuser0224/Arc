package dev.arc.api.nms

/**
 * Aggregates every capability that can only be implemented against the server
 * internals (NMS) and is not surfaced by the Bukkit/Paper API.
 *
 * Grouped by domain; reach the implementation via [dev.arc.api.Arc.nms]:
 *
 * ```
 * Arc.nms.entities.saveNbt(entity)
 * Arc.nms.blocks.setNoPhysics(block, data)
 * Arc.nms.players.sendPacket(player, packet)
 * Arc.nms.server.tps()
 * val rawLevel = Arc.nms.handle(world)   // escape hatch for anything not modelled
 * ```
 */
interface ArcNms {

    val entities: ArcEntityNms
    val items: ArcItemNms
    val blocks: ArcBlockNms
    val players: ArcPlayerNms
    val server: ArcServerNms

    /**
     * Generic escape hatch — the raw NMS / CraftBukkit handle behind any Bukkit
     * object (Entity, World, Block, ItemStack, Server, …) via its `getHandle()`,
     * or null if it exposes none. Guarantees that **anything** reachable through
     * NMS is reachable through this API, even if not modelled by a typed method.
     */
    fun handle(bukkit: Any): Any?
}
