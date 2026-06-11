package dev.arc.server.nms

import dev.arc.api.Arc
import dev.arc.api.npc.Npc
import dev.arc.api.npc.NpcBackend
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Packet-level NPC backend using mojang-mapped NMS classes via [Reflect].
 *
 * Spawns a fake humanoid entity via `ClientboundAddEntityPacket` and keeps it
 * visible by sending the required tab-list and spawn packets per viewer.
 */
object NmsNpcBackend : NpcBackend {

    private fun send(player: Player, packet: Any) = Arc.nms.players.sendPacket(player, packet)

    private fun mkPacket(className: String, vararg args: Any?): Any? {
        val cls = Reflect.cls(className) ?: return null
        return Reflect.constructByArity(cls, args.size, *args)
    }

    // Stores UUIDs so we can remove from tab list on despawn
    private val profileUuids = java.util.concurrent.ConcurrentHashMap<Int, UUID>()

    override fun spawn(npc: Npc, viewer: Player) {
        val loc = npc.location
        val uuid = UUID.randomUUID()
        profileUuids[npc.id] = uuid

        // 1. Tab-list ADD_PLAYER (required before the entity spawn for player-type entities)
        addTabListEntry(npc, viewer, uuid)

        // 2. Spawn entity packet
        val playerTypeCls = Reflect.cls("net.minecraft.world.entity.EntityType")
        val playerType = playerTypeCls?.let { cls -> Reflect.field(cls, "PLAYER")?.let { Reflect.fieldValue(it, null) } }

        val yawByte   = angleToByte(loc.yaw)
        val pitchByte = angleToByte(loc.pitch)

        val spawnPacket = if (playerType != null) {
            mkPacket(
                "net.minecraft.network.protocol.game.ClientboundAddEntityPacket",
                npc.id, uuid, loc.x, loc.y, loc.z, pitchByte, yawByte, playerType, 0, 0.0, 0.0, 0.0, yawByte,
            )
        } else null

        if (spawnPacket != null) send(viewer, spawnPacket)
    }

    override fun despawn(npc: Npc, viewer: Player) {
        val removePacket = mkPacket(
            "net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket",
            intArrayOf(npc.id),
        ) ?: mkPacket(
            "net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket",
            listOf(npc.id),
        ) ?: return
        send(viewer, removePacket)

        // Remove tab-list entry
        profileUuids[npc.id]?.let { uuid ->
            val removeCls = Reflect.cls("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket")
            removeCls?.let { cls ->
                val packet = Reflect.constructByArity(cls, 1, listOf(uuid))
                if (packet != null) send(viewer, packet)
            }
        }
    }

    override fun teleport(npc: Npc, location: Location) {
        val yawByte   = angleToByte(location.yaw)
        val pitchByte = angleToByte(location.pitch)
        val packet = mkPacket(
            "net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket",
            npc.id, location.x, location.y, location.z, yawByte, pitchByte, true,
        ) ?: return
        location.world.players.forEach { send(it, packet) }
    }

    override fun lookAt(npc: Npc, target: Location) {
        val npcLoc = npc.location
        val dx = target.x - npcLoc.x
        val dy = target.y - npcLoc.y
        val dz = target.z - npcLoc.z
        val yaw   = (-Math.toDegrees(Math.atan2(dx, dz))).toFloat()
        val pitch = (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))).toFloat()
        val yawByte   = angleToByte(yaw)
        val pitchByte = angleToByte(pitch)

        val rotPacket = mkPacket(
            "net.minecraft.network.protocol.game.ClientboundMoveEntityPacket\$Rot",
            npc.id.toShort(), yawByte, pitchByte, false,
        ) ?: return
        val headPacket = mkPacket(
            "net.minecraft.network.protocol.game.ClientboundRotateHeadPacket",
            npc.id, yawByte,
        )

        npcLoc.world.players.forEach {
            send(it, rotPacket)
            headPacket?.let { p -> send(it, p) }
        }
    }

    override fun setSkin(npc: Npc, texture: String, signature: String) {
        // Skin is baked into the GameProfile at spawn time; re-spawn needed to update.
    }

    override fun setName(npc: Npc, name: String) {
        // Display name via metadata — sent at spawn for simplicity.
    }

    // ---------------------------------------------------------------------------

    private fun addTabListEntry(npc: Npc, viewer: Player, uuid: UUID) {
        val actionCls = Reflect.cls(
            "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket\$Action"
        ) ?: return
        @Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        val addAction = runCatching {
            (actionCls as Class<out Enum<*>>).enumConstants?.find { it.name == "ADD_PLAYER" }
        }.getOrNull() ?: return

        val enumSetCls = Reflect.cls("java.util.EnumSet") ?: return
        val enumSet = Reflect.invokeStatic(enumSetCls, "of", addAction) ?: return

        val profileCls = Reflect.cls("com.mojang.authlib.GameProfile") ?: return
        val displayName = npc.displayName.take(16).ifEmpty { "ArcNPC" }
        val profile = Reflect.constructByArity(profileCls, 2, uuid, displayName)

        // Apply skin texture if provided
        if (profile != null) {
            npc.skin?.let { skin ->
                val propCls = Reflect.cls("com.mojang.authlib.properties.Property") ?: return@let
                val prop = Reflect.constructByArity(propCls, 3, "textures", skin.texture, skin.signature) ?: return@let
                val propMap = Reflect.invoke(profile, "getProperties") ?: return@let
                Reflect.invoke(propMap, "put", "textures", prop)
            }
        }

        // ClientboundPlayerInfoUpdatePacket(EnumSet<Action>, GameProfile, ...)
        val packetCls = Reflect.cls("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket") ?: return
        val packet = Reflect.constructByArity(packetCls, 2, enumSet, profile ?: return) ?: return
        send(viewer, packet)
    }

    private fun angleToByte(angle: Float): Byte = (angle * 256f / 360f).toInt().toByte()
}
