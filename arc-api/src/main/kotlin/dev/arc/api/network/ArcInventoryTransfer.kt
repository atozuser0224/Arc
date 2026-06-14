package dev.arc.api.network

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * Saves and restores player inventory when transferring between servers.
 * Stores serialized inventory in Redis with a 5-minute TTL.
 * Only active when arc-network.inventory-transfer.enabled: true.
 */
object ArcInventoryTransfer {

    private fun key(player: Player) = "arc:inventory:transfer:${player.uniqueId}"

    /** Call before sending player to another server. */
    fun save(player: Player) {
        if (!ArcNetworkConfig.inventoryTransfer.enabled) return
        if (!ArcRelayClient.connected) return
        runCatching {
            val data = serialize(player)
            ArcRelayClient.setex(key(player), 300, data)
        }
    }

    /** Call on PlayerJoin — restores inventory if transfer data exists. */
    fun restore(player: Player): Boolean {
        if (!ArcNetworkConfig.inventoryTransfer.enabled) return false
        if (!ArcRelayClient.connected) return false
        val data = ArcRelayClient.get(key(player)) ?: return false
        ArcRelayClient.del(key(player))
        return runCatching {
            deserialize(player, data)
            true
        }.getOrElse { false }
    }

    private fun serialize(player: Player): String {
        val baos = ByteArrayOutputStream()
        BukkitObjectOutputStream(baos).use { oos ->
            val inv = player.inventory
            oos.writeObject(inv.contents)
            oos.writeObject(inv.armorContents)
            oos.writeObject(inv.extraContents)
            oos.writeDouble(player.exp.toDouble())
            oos.writeInt(player.level)
            oos.writeDouble(player.health)
            oos.writeInt(player.foodLevel)
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    @Suppress("UNCHECKED_CAST")
    private fun deserialize(player: Player, data: String) {
        val bais = ByteArrayInputStream(Base64.getDecoder().decode(data))
        BukkitObjectInputStream(bais).use { ois ->
            player.inventory.contents = ois.readObject() as Array<ItemStack?>
            player.inventory.armorContents = ois.readObject() as Array<ItemStack?>
            player.inventory.extraContents = ois.readObject() as Array<ItemStack?>
            player.exp = ois.readDouble().toFloat()
            player.level = ois.readInt()
            runCatching { player.health = ois.readDouble().coerceIn(0.0, player.maxHealth) }
            runCatching { player.foodLevel = ois.readInt() }
        }
    }
}

/** Restores inventory on join if transfer data is present. */
class ArcInventoryTransferListener : Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onJoin(event: PlayerJoinEvent) {
        if (ArcInventoryTransfer.restore(event.player)) {
            event.player.sendMessage("§aYour inventory has been transferred.")
        }
    }
}
