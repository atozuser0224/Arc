package dev.arc.api.nms

import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Safe no-op [ArcNms] groups, returned by [GatedArcNms] when a feature flag
 * disables NMS access. Every method returns a harmless default.
 */
internal object NoOpEntityNms : ArcEntityNms {
    override fun saveNbt(entity: Entity): String = "{}"
    override fun loadNbt(entity: Entity, snbt: String) {}
    override fun ageTicks(entity: Entity): Int = 0
    override fun handleType(entity: Entity): String = "Unknown"
    override fun networkId(entity: Entity): Int = -1
    override fun byId(world: World, id: Int): Entity? = null
    override fun byIdInChunk(world: World, chunkX: Int, chunkZ: Int, id: Int): Entity? = null
    override fun handle(entity: Entity): Any? = null
    override fun spawnPacket(entity: Entity): Any? = null
}

internal object NoOpItemNms : ArcItemNms {
    override fun describeNms(item: ItemStack): String = "air"
    override fun nmsCopy(item: ItemStack): Any? = null
}

internal object NoOpBlockNms : ArcBlockNms {
    override fun setNoPhysics(block: Block, data: BlockData): Boolean = false
    override fun blockLight(block: Block): Int = 0
    override fun skyLight(block: Block): Int = 0
    override fun levelHandle(block: Block): Any? = null
}

internal object NoOpPlayerNms : ArcPlayerNms {
    override fun sendPacket(player: Player, packet: Any) {}
    override fun ping(player: Player): Int = -1
    override fun protocolVersion(player: Player): Int = -1
    override fun connection(player: Player): Any? = null
    override fun handle(player: Player): Any? = null
    override fun hideEntities(player: Player, vararg entityIds: Int) {}
    override fun gameProfile(player: Player): Any? = null
    override fun setProfileProperty(player: Player, name: String, value: String, signature: String?) {}
    override fun resendChunk(player: Player, chunkX: Int, chunkZ: Int): Boolean = false
}

internal object NoOpServerNms : ArcServerNms {
    override fun mspt(): Double = 0.0
    override fun tickCount(): Int = 0
    override fun tps(): DoubleArray = DoubleArray(0)
    override fun handle(): Any? = null
    override fun diagnostics(): NmsDiagnostics = NmsDiagnostics("disabled")
    override fun probeCapabilities(): NmsCapabilities =
        NmsCapabilities(emptySet(), setOf("nms-disabled"))
}
