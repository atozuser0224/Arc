package dev.arc.server.nms

import dev.arc.api.nms.ArcBlockNms
import dev.arc.api.nms.ArcEntityNms
import dev.arc.api.nms.ArcItemNms
import dev.arc.api.nms.ArcNms
import dev.arc.api.nms.ArcPlayerNms
import dev.arc.api.nms.ArcServerNms
import dev.arc.api.nms.NmsCapabilities
import dev.arc.api.nms.NmsDiagnostics
import dev.arc.api.scheduling.callAsync
import dev.arc.api.scheduling.callEntity
import dev.arc.api.scheduling.callRegion
import dev.arc.api.scheduling.thenEntity
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.concurrent.CompletableFuture

/**
 * Reflection-backed [ArcNms]. Talks to the mojang-mapped runtime purely through
 * [Reflect], so `arc-server` carries no compile-time dependency on the volatile
 * internals. Every call degrades gracefully (sane default / no-op) if a name
 * can't be resolved on the running server.
 */
class ReflectiveArcNms : ArcNms {
    override val entities: ArcEntityNms = ReflectiveEntityNms
    override val items: ArcItemNms = ReflectiveItemNms
    override val blocks: ArcBlockNms = ReflectiveBlockNms
    override val players: ArcPlayerNms = ReflectivePlayerNms
    override val server: ArcServerNms = ReflectiveServerNms

    override fun handle(bukkit: Any): Any? {
        val owned = when (bukkit) {
            is Entity -> Bukkit.isOwnedByCurrentRegion(bukkit)
            is Block -> Bukkit.isOwnedByCurrentRegion(bukkit)
            else -> Bukkit.isGlobalTickThread()
        }
        NmsThreadGuard.requireOwned(owned, "handle")
        return Reflect.handleOf(bukkit)
    }
}

private object ReflectiveEntityNms : ArcEntityNms {
    private val compoundTag get() = Reflect.cls("net.minecraft.nbt.CompoundTag")
    private val tagParser get() = Reflect.cls("net.minecraft.nbt.TagParser")

    override fun handle(entity: Entity): Any? {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(entity), "entities.handle")
        return Reflect.handleOf(entity)
    }

    override fun saveNbt(entity: Entity): String {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(entity), "entities.saveNbt")
        val h = handle(entity) ?: return "{}"
        val tag = Reflect.construct(compoundTag ?: return "{}", emptyArray<Class<*>>()) ?: return "{}"
        val saved = Reflect.invoke(h, "saveWithoutId", tag) ?: Reflect.invoke(h, "save", tag) ?: tag
        return saved.toString()
    }

    override fun loadNbt(entity: Entity, snbt: String) {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(entity), "entities.loadNbt")
        val h = handle(entity) ?: return
        val parsed = Reflect.invokeStatic(tagParser ?: return, "parseTag", snbt) ?: return
        Reflect.invoke(h, "load", parsed)
    }

    override fun loadNbtAsync(
        plugin: Plugin,
        entity: Entity,
        snbt: String,
    ): CompletableFuture<Unit> {
        // SNBT parsing is CPU work and does not touch world state. Entity
        // mutation remains strictly serialized onto the entity's owner thread.
        return plugin.callAsync {
            Reflect.invokeStatic(tagParser ?: error("TagParser unavailable"), "parseTag", snbt)
                ?: error("Invalid SNBT")
        }.thenEntity(plugin, entity) { parsed ->
            val handle = handle(entity) ?: error("Entity handle unavailable")
            Reflect.invoke(handle, "load", parsed)
            Unit
        }
    }

    override fun ageTicks(entity: Entity): Int {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(entity), "entities.ageTicks")
        val h = handle(entity) ?: return 0
        val f = Reflect.field(h.javaClass, "tickCount") ?: return 0
        return Reflect.intField(f, h) ?: 0
    }

    override fun handleType(entity: Entity): String =
        handle(entity)?.javaClass?.simpleName ?: "Unknown"

    override fun networkId(entity: Entity): Int {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(entity), "entities.networkId")
        val h = handle(entity) ?: return -1
        return (Reflect.invoke(h, "getId") as? Int) ?: -1
    }

    override fun byId(world: World, id: Int): Entity? {
        NmsThreadGuard.requireOwned(Bukkit.isGlobalTickThread(), "entities.byId")
        return findById(world, id)
    }

    override fun byIdInChunk(world: World, chunkX: Int, chunkZ: Int, id: Int): Entity? {
        NmsThreadGuard.requireOwned(
            Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ),
            "entities.byIdInChunk",
        )
        val minX = chunkX shl 4
        val minZ = chunkZ shl 4
        return world.getNearbyEntities(
            org.bukkit.util.BoundingBox(
                minX.toDouble(),
                world.minHeight.toDouble(),
                minZ.toDouble(),
                (minX + 16).toDouble(),
                world.maxHeight.toDouble(),
                (minZ + 16).toDouble(),
            ),
        ).firstOrNull { entity ->
            Bukkit.isOwnedByCurrentRegion(entity) && networkId(entity) == id
        }
    }

    private fun findById(world: World, id: Int): Entity? {
        val level = Reflect.handleOf(world) ?: return null
        val m = Reflect.method(level.javaClass, "getEntity", Integer.TYPE) ?: return null
        val nms = Reflect.invoke(m, level, id) ?: return null
        return Reflect.invoke(nms, "getBukkitEntity") as? Entity
    }

    override fun spawnPacket(entity: Entity): Any? {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(entity), "entities.spawnPacket")
        val h = handle(entity) ?: return null
        val packetCls = Reflect.cls("net.minecraft.network.protocol.game.ClientboundAddEntityPacket") ?: return null
        val entityCls = Reflect.cls("net.minecraft.world.entity.Entity") ?: return null
        // ctor variants across versions: (Entity) / (Entity, int) — best effort.
        return Reflect.construct(packetCls, arrayOf<Class<*>>(entityCls), h)
            ?: Reflect.construct(packetCls, arrayOf<Class<*>>(entityCls, Integer.TYPE), h, 0)
            ?: Reflect.constructByArity(packetCls, 1, h)
    }
}

private object ReflectiveItemNms : ArcItemNms {
    private val craftItemStack get() = Reflect.cls("org.bukkit.craftbukkit.inventory.CraftItemStack")

    override fun nmsCopy(item: ItemStack): Any? {
        NmsThreadGuard.requireOwned(Bukkit.isGlobalTickThread(), "items.nmsCopy")
        val cls = craftItemStack ?: return null
        val m = Reflect.method(cls, "asNMSCopy", ItemStack::class.java) ?: return null
        return Reflect.invoke(m, null, item)
    }

    override fun describeNms(item: ItemStack): String = nmsCopy(item)?.toString() ?: "air"
}

private object ReflectiveBlockNms : ArcBlockNms {
    private val blockPosCls get() = Reflect.cls("net.minecraft.core.BlockPos")
    private val lightLayerCls get() = Reflect.cls("net.minecraft.world.level.LightLayer")

    override fun levelHandle(block: Block): Any? {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(block), "blocks.levelHandle")
        return Reflect.handleOf(block.world)
    }

    private fun blockPos(block: Block): Any? {
        val type = blockPosCls ?: return null
        return Reflect.construct(
            type,
            arrayOf<Class<*>>(Integer.TYPE, Integer.TYPE, Integer.TYPE),
            block.x, block.y, block.z,
        )
    }

    override fun setNoPhysics(block: Block, data: BlockData): Boolean {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(block), "blocks.setNoPhysics")
        val level = levelHandle(block) ?: return false
        val pos = blockPos(block) ?: return false
        // CraftBlockData#getState() -> net.minecraft.world.level.block.state.BlockState
        val state = Reflect.invoke(data, "getState") ?: return false
        // Level#setBlock(BlockPos, BlockState, int flags); flag excludes UPDATE_NEIGHBORS(1)
        // 2 = UPDATE_CLIENTS, 16 = UPDATE_KNOWN_SHAPE (no neighbour physics)
        return Reflect.invoke(level, "setBlock", pos, state, 2 or 16) as? Boolean ?: false
    }

    override fun blockLight(block: Block): Int = brightness(block, "BLOCK")
    override fun skyLight(block: Block): Int = brightness(block, "SKY")

    private fun brightness(block: Block, layer: String): Int {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(block), "blocks.brightness")
        val level = levelHandle(block) ?: return 0
        val pos = blockPos(block) ?: return 0
        val layerCls = lightLayerCls ?: return 0
        val layerVal = Reflect.invokeStatic(layerCls, "valueOf", layer) ?: return 0
        val m = Reflect.method(level.javaClass, "getBrightness", layerCls, blockPosCls!!) ?: return 0
        return Reflect.invoke(m, level, layerVal, pos) as? Int ?: 0
    }
}

private object ReflectivePlayerNms : ArcPlayerNms {

    override fun handle(player: Player): Any? {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(player), "players.handle")
        return Reflect.handleOf(player)
    }

    override fun connection(player: Player): Any? {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(player), "players.connection")
        val h = handle(player) ?: return null
        // ServerPlayer#connection (ServerGamePacketListenerImpl)
        val field = Reflect.field(h.javaClass, "connection") ?: return null
        return Reflect.fieldValue(field, h)
    }

    override fun sendPacket(player: Player, packet: Any) {
        val conn = connection(player) ?: return
        Reflect.invoke(conn, "send", packet)
    }

    override fun ping(player: Player): Int {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(player), "players.ping")
        val h = handle(player) ?: return -1
        val f = Reflect.field(h.javaClass, "latency") ?: return -1
        return Reflect.intField(f, h) ?: -1
    }

    override fun protocolVersion(player: Player): Int {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(player), "players.protocolVersion")
        val conn = connection(player) ?: return -1
        return (Reflect.invoke(conn, "protocolVersion") as? Int) ?: -1
    }

    override fun hideEntities(player: Player, vararg entityIds: Int) {
        val cls = Reflect.cls("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket") ?: return
        val packet = Reflect.construct(cls, arrayOf<Class<*>>(IntArray::class.java), entityIds) ?: return
        sendPacket(player, packet)
    }

    override fun gameProfile(player: Player): Any? {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(player), "players.gameProfile")
        val h = handle(player) ?: return null
        val field = Reflect.field(h.javaClass, "gameProfile")
        return Reflect.invoke(h, "getGameProfile") ?: field?.let { Reflect.fieldValue(it, h) }
    }

    override fun setProfileProperty(player: Player, name: String, value: String, signature: String?) {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(player), "players.setProfileProperty")
        val profile = gameProfile(player) ?: return
        val props = Reflect.invoke(profile, "getProperties") ?: return
        val propertyCls = Reflect.cls("com.mojang.authlib.properties.Property") ?: return
        val property = if (signature != null) {
            Reflect.construct(
                propertyCls,
                arrayOf<Class<*>>(String::class.java, String::class.java, String::class.java),
                name, value, signature,
            )
        } else {
            Reflect.construct(
                propertyCls,
                arrayOf<Class<*>>(String::class.java, String::class.java),
                name, value,
            )
        } ?: return
        Reflect.invoke(props, "put", name, property)
    }

    override fun resendChunk(player: Player, chunkX: Int, chunkZ: Int): Boolean {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(player), "players.resendChunk.player")
        NmsThreadGuard.requireOwned(
            Bukkit.isOwnedByCurrentRegion(player.world, chunkX, chunkZ),
            "players.resendChunk.chunk",
        )
        val packet = buildChunkPacket(player.world, chunkX, chunkZ) ?: return false
        sendPacket(player, packet)
        return true
    }

    override fun resendChunkAsync(
        plugin: Plugin,
        player: Player,
        chunkX: Int,
        chunkZ: Int,
    ): CompletableFuture<Boolean> {
        return plugin.callEntity(player) { player.world }.thenCompose { world ->
            val location = Location(
                world,
                (chunkX shl 4).toDouble(),
                world.minHeight.toDouble(),
                (chunkZ shl 4).toDouble(),
            )
            plugin.callRegion(location) {
                buildChunkPacket(world, chunkX, chunkZ)
            }.thenCompose { packet ->
                if (packet == null) {
                    CompletableFuture.completedFuture(false)
                } else {
                    plugin.callEntity(player) {
                        sendPacket(player, packet)
                        true
                    }
                }
            }
        }.toCompletableFuture()
    }

    private fun buildChunkPacket(world: World, chunkX: Int, chunkZ: Int): Any? {
        val level = Reflect.handleOf(world) ?: return null
        val chunk = Reflect.invoke(level, "getChunk", chunkX, chunkZ) ?: return null
        val lightEngine = Reflect.invoke(level, "getLightEngine") ?: return null
        val packetCls = Reflect.cls("net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket")
            ?: return null
        return Reflect.constructByArity(packetCls, 4, chunk, lightEngine, null, null)
            ?: Reflect.constructByArity(packetCls, 5, chunk, lightEngine, null, null, true)
    }
}

private object ReflectiveServerNms : ArcServerNms {
    override fun handle(): Any? {
        NmsThreadGuard.requireOwned(Bukkit.isGlobalTickThread(), "server.handle")
        return Reflect.invoke(Bukkit.getServer(), "getServer")
    }

    override fun mspt(): Double {
        NmsThreadGuard.requireOwned(Bukkit.isGlobalTickThread(), "server.mspt")
        val server = Bukkit.getServer()
        val m = Reflect.method(server.javaClass, "getAverageTickTime") ?: return 0.0
        return (Reflect.invoke(m, server) as? Number)?.toDouble() ?: 0.0
    }

    override fun tickCount(): Int {
        NmsThreadGuard.requireOwned(Bukkit.isGlobalTickThread(), "server.tickCount")
        val ms = handle() ?: return 0
        val f = Reflect.field(ms.javaClass, "tickCount") ?: return 0
        return Reflect.intField(f, ms) ?: 0
    }

    override fun tps(): DoubleArray {
        NmsThreadGuard.requireOwned(Bukkit.isGlobalTickThread(), "server.tps")
        val server = Bukkit.getServer()
        val m = Reflect.method(server.javaClass, "getTPS") ?: return DoubleArray(0)
        return (Reflect.invoke(m, server) as? DoubleArray) ?: DoubleArray(0)
    }

    override fun diagnostics(): NmsDiagnostics {
        val stats = Reflect.stats()
        return NmsDiagnostics(
            implementation = ReflectiveArcNms::class.java.name,
            cachedClasses = stats.classes,
            cachedMethods = stats.methods,
            cachedFields = stats.fields,
            cachedConstructors = stats.constructors,
            cachedMethodHandles = stats.methodHandles,
            cacheHits = stats.hits,
            cacheMisses = stats.misses,
            resolutionFailures = stats.resolutionFailures,
            invocationFailures = stats.invocationFailures,
            rejectedThreadAccesses = NmsThreadGuard.rejectedCount(),
        )
    }

    override fun probeCapabilities(): NmsCapabilities {
        val available = linkedSetOf<String>()
        val unavailable = linkedSetOf<String>()

        fun probe(name: String, check: () -> Boolean) {
            if (runCatching(check).getOrDefault(false)) available += name else unavailable += name
        }

        probe("entities.nbt") {
            Reflect.cls("net.minecraft.nbt.CompoundTag") != null &&
                Reflect.cls("net.minecraft.nbt.TagParser") != null
        }
        probe("entities.spawn-packet") {
            Reflect.cls("net.minecraft.network.protocol.game.ClientboundAddEntityPacket") != null &&
                Reflect.cls("net.minecraft.world.entity.Entity") != null
        }
        probe("items.craft-copy") {
            val type = Reflect.cls("org.bukkit.craftbukkit.inventory.CraftItemStack") ?: return@probe false
            Reflect.method(type, "asNMSCopy", ItemStack::class.java) != null
        }
        probe("blocks.position") {
            Reflect.cls("net.minecraft.core.BlockPos") != null
        }
        probe("blocks.lighting") {
            Reflect.cls("net.minecraft.world.level.LightLayer") != null
        }
        probe("players.remove-entities") {
            Reflect.cls("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket") != null
        }
        probe("players.profile-property") {
            Reflect.cls("com.mojang.authlib.properties.Property") != null
        }
        probe("players.chunk-resend") {
            Reflect.cls("net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket") != null
        }
        probe("server.mspt") {
            Reflect.method(Bukkit.getServer().javaClass, "getAverageTickTime") != null
        }
        probe("server.tps") {
            Reflect.method(Bukkit.getServer().javaClass, "getTPS") != null
        }
        probe("server.tick-count") {
            val raw = Reflect.invoke(Bukkit.getServer(), "getServer") ?: return@probe false
            Reflect.field(raw.javaClass, "tickCount") != null
        }

        return NmsCapabilities(available, unavailable)
    }

    override fun clearReflectionCaches(): Boolean {
        Reflect.clearCaches()
        NmsThreadGuard.reset()
        return true
    }
}
