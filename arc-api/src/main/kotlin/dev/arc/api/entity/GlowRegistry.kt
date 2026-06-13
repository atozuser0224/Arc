@file:JvmName("GlowRegistries")

package dev.arc.api.entity

import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-player entity glow override registry.
 * Makes an entity appear glowing (or not) to specific players independently of the
 * entity's actual [org.bukkit.entity.Entity.isGlowing] flag.
 *
 * Use this for team-colored highlights, stealth mechanics, or spectator-only outlines.
 *
 * ```kotlin
 * val glowRegistry = GlowRegistry()
 *
 * // Make player A see entity as glowing:
 * glowRegistry.setGlowing(entity, viewer = playerA, glowing = true)
 *
 * // Remove override (revert to entity's real state):
 * glowRegistry.clearGlowing(entity, playerA)
 *
 * // Sync all overrides when entity metadata changes:
 * glowRegistry.syncAll(entity)
 * ```
 */
class GlowRegistry {

    // entityUUID → (viewerUUID → glowing)
    private val overrides = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Boolean>>()

    /**
     * Set whether [entity] appears glowing to [viewer].
     * Sends a synthetic metadata packet immediately.
     */
    fun setGlowing(entity: org.bukkit.entity.Entity, viewer: Player, glowing: Boolean) {
        overrides
            .getOrPut(entity.uniqueId) { ConcurrentHashMap() }[viewer.uniqueId] = glowing
        sendGlowUpdate(entity, viewer, glowing)
    }

    /**
     * Remove the per-player override and restore the entity's real glow state to [viewer].
     */
    fun clearGlowing(entity: org.bukkit.entity.Entity, viewer: Player) {
        overrides[entity.uniqueId]?.remove(viewer.uniqueId)
        sendGlowUpdate(entity, viewer, entity.isGlowing)
    }

    /** Remove all overrides for [entity] and restore real glow state to all online players. */
    fun clearAll(entity: org.bukkit.entity.Entity) {
        val map = overrides.remove(entity.uniqueId) ?: return
        map.keys.forEach { viewerId ->
            org.bukkit.Bukkit.getPlayer(viewerId)?.let { viewer ->
                sendGlowUpdate(entity, viewer, entity.isGlowing)
            }
        }
    }

    /** Sync all overrides for [entity] after its metadata is updated by another system. */
    fun syncAll(entity: org.bukkit.entity.Entity) {
        val map = overrides[entity.uniqueId] ?: return
        map.forEach { (viewerId, glowing) ->
            org.bukkit.Bukkit.getPlayer(viewerId)?.let { sendGlowUpdate(entity, it, glowing) }
        }
    }

    /** Whether [viewer] has a glow override active for [entity]. */
    fun hasOverride(entity: org.bukkit.entity.Entity, viewer: Player): Boolean =
        overrides[entity.uniqueId]?.containsKey(viewer.uniqueId) == true

    /** Get the override value (or null if none). */
    fun getOverride(entity: org.bukkit.entity.Entity, viewer: Player): Boolean? =
        overrides[entity.uniqueId]?.get(viewer.uniqueId)

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun sendGlowUpdate(entity: org.bukkit.entity.Entity, viewer: Player, glowing: Boolean) {
        val nmsEntity = (entity as CraftEntity).handle
        val currentFlags: Byte = nmsEntity.entityData.get(Entity.DATA_SHARED_FLAGS_ID)
        val newFlags: Byte = if (glowing)
            (currentFlags.toInt() or 0x40).toByte()
        else
            (currentFlags.toInt() and 0x40.inv()).toByte()

        val packet = ClientboundSetEntityDataPacket(
            nmsEntity.id,
            listOf(SynchedEntityData.DataValue.create(Entity.DATA_SHARED_FLAGS_ID, newFlags))
        )
        (viewer as CraftPlayer).handle.connection.send(packet)
    }
}

/** Singleton convenience registry — use if you only need one global registry. */
val GlobalGlowRegistry = GlowRegistry()
