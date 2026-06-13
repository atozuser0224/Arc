@file:JvmName("VirtualEntities")

package dev.arc.api.entity

import io.papermc.paper.adventure.PaperAdventure
import net.kyori.adventure.text.Component
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType as NmsEntityType
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.phys.Vec3
import org.bukkit.Location
import org.bukkit.craftbukkit.entity.CraftEntityType
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import java.util.Optional
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * A client-only fake entity visible only in the client renderer.
 * No server-side physics, AI, or collision — purely visual.
 * Useful for holograms, path visualisers, custom markers, and trails.
 *
 * ```kotlin
 * val marker = VirtualEntity(EntityType.ARMOR_STAND, player.location.add(0.0, 1.5, 0.0))
 * marker.customName = Component.text("Hello, world!")
 * marker.isCustomNameVisible = true
 * marker.hasNoGravity = true
 * marker.show(player)
 *
 * // Move later:
 * marker.teleport(newLocation, player)
 *
 * // Clean up:
 * marker.hide(player)
 * ```
 */
class VirtualEntity(
    type: EntityType,
    var location: Location,
) {
    /** Unique fake entity ID — negative to avoid collision with real server entity IDs. */
    val entityId: Int = ID_COUNTER.decrementAndGet()

    val uuid: UUID = UUID.randomUUID()

    private val nmsType: NmsEntityType<*> = CraftEntityType.bukkitToMinecraft(type)

    /** Display name shown above the entity (requires [isCustomNameVisible] = true). */
    var customName: Component? = null

    /** Whether the custom name tag is always visible. */
    var isCustomNameVisible: Boolean = false

    /** Whether this entity is invisible (model hidden, name still renders). */
    var isInvisible: Boolean = false

    /** Whether this entity renders with a glowing outline. */
    var hasGlowing: Boolean = false

    /** Whether this entity has no gravity (stays in place vertically). */
    var hasNoGravity: Boolean = true

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Spawn this entity on [players]' clients. */
    fun show(vararg players: Player) {
        val spawnPacket = ClientboundAddEntityPacket(
            entityId, uuid,
            location.x, location.y, location.z,
            location.pitch, location.yaw,
            nmsType, 0, Vec3.ZERO, 0.0
        )
        players.forEach { player ->
            val conn = (player as CraftPlayer).handle.connection
            conn.send(spawnPacket)
            sendMetadata(player)
        }
    }

    /** Remove this entity from [players]' clients. */
    fun hide(vararg players: Player) {
        val packet = ClientboundRemoveEntitiesPacket(entityId)
        players.forEach { (it as CraftPlayer).handle.connection.send(packet) }
    }

    /** Teleport this entity to [loc] for [players]. Updates [location]. */
    fun teleport(loc: Location, vararg players: Player) {
        location = loc
        val packet = ClientboundTeleportEntityPacket.teleport(
            entityId,
            PositionMoveRotation(Vec3(loc.x, loc.y, loc.z), Vec3.ZERO, loc.yaw, loc.pitch),
            emptySet(),
            false
        )
        players.forEach { (it as CraftPlayer).handle.connection.send(packet) }
    }

    /** Push updated metadata (name, visibility flags) to [players]. */
    fun updateMetadata(vararg players: Player) {
        players.forEach { sendMetadata(it) }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun sendMetadata(player: Player) {
        val items = buildList {
            // Index 0: shared flags (invisible, glowing)
            var flags: Byte = 0
            if (isInvisible) flags = (flags.toInt() or 0x20).toByte()
            if (hasGlowing)  flags = (flags.toInt() or 0x40).toByte()
            add(SynchedEntityData.DataValue.create(Entity.DATA_SHARED_FLAGS_ID, flags))

            // Index 5: no gravity
            add(SynchedEntityData.DataValue.create(Entity.DATA_NO_GRAVITY, hasNoGravity))

            // Index 2: custom name (Optional<NMS Component>)
            val nmsName: Optional<net.minecraft.network.chat.Component> =
                Optional.ofNullable(customName?.let { PaperAdventure.SERIALIZER.serialize(it) })
            add(SynchedEntityData.DataValue.create(Entity.DATA_CUSTOM_NAME, nmsName))

            // Index 3: custom name visible
            add(SynchedEntityData.DataValue.create(Entity.DATA_CUSTOM_NAME_VISIBLE, isCustomNameVisible))
        }
        (player as CraftPlayer).handle.connection.send(ClientboundSetEntityDataPacket(entityId, items))
    }

    companion object {
        private val ID_COUNTER = AtomicInteger(-1)
    }
}
