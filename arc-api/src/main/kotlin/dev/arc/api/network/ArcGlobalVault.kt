package dev.arc.api.network

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID

/**
 * Cross-server shared inventory vault backed by Redis.
 * Uses a distributed lock to prevent item duplication across concurrent access.
 * Vault slots: determined by arc-network.yml global-vault.default-slots (9/18/27/36/45/54).
 */
object ArcGlobalVault {

    private var plugin: Plugin? = null

    fun start(p: Plugin) { plugin = p }

    private fun key(uuid: UUID, group: String?) =
        if (group != null && ArcNetworkConfig.globalVault.perGroup) "arc:vault:$group:player:$uuid"
        else "arc:vault:player:$uuid"

    private fun lockKey(uuid: UUID) = "vault:$uuid"

    // ---- API ----

    fun open(viewer: Player, owner: UUID, group: String? = currentGroup()) {
        if (!ArcNetworkConfig.globalVault.enabled) {
            viewer.sendMessage("§cGlobal Vault is disabled.")
            return
        }
        val p = plugin ?: run { viewer.sendMessage("§cVault not initialized."); return }
        val lock = ArcRelayClient.acquireLock(lockKey(owner), 30)
        if (lock == null) {
            viewer.sendMessage("§eVault is currently in use. Try again in a moment.")
            return
        }
        val slots = vaultSlots(owner, group)
        val contents = load(owner, group)
        val rows = (ArcNetworkConfig.globalVault.defaultSlots.coerceAtLeast(9) / 9).coerceIn(1, 6)
        Bukkit.getScheduler().runTask(p, Runnable {
            val title = net.kyori.adventure.text.Component.text("§6Vault §7— §e${Bukkit.getOfflinePlayer(owner).name ?: owner}")
            val menu = p.menu(title, rows) {
                for (i in 0 until rows * 9) {
                    val stored = contents.getOrNull(i)
                    item(i, stored ?: ItemStack(Material.AIR)) { event ->
                        event.isCancelled = false // allow free interaction
                    }
                }
            }
            menu.onClose { _ ->
                // Save on close, then release lock
                val inv = menu.inventory()
                val items = Array<ItemStack?>(inv.size) { inv.getItem(it) }
                Bukkit.getScheduler().runTaskAsynchronously(p, Runnable {
                    save(owner, items, group)
                    ArcRelayClient.releaseLock(lockKey(owner), lock)
                })
            }
            menu.open(viewer)
            ArcNetworkAudit.log("vault.open", mapOf("owner" to owner.toString(), "viewer" to viewer.name))
        })
    }

    fun save(owner: UUID, items: Array<ItemStack?>, group: String? = currentGroup()) {
        if (items.all { it == null || it.type == Material.AIR }) {
            ArcRelayClient.del(key(owner, group))
            return
        }
        val serialized = runCatching { serialize(items) }.getOrNull() ?: return
        ArcRelayClient.set(key(owner, group), serialized)
    }

    fun load(owner: UUID, group: String? = currentGroup()): Array<ItemStack?> {
        val data = ArcRelayClient.get(key(owner, group)) ?: return emptyArray()
        return runCatching { deserialize(data) }.getOrElse { emptyArray() }
    }

    // ---- Serialization ----

    fun serialize(items: Array<ItemStack?>): String {
        val baos = ByteArrayOutputStream()
        BukkitObjectOutputStream(baos).use { oos ->
            oos.writeInt(items.size)
            items.forEach { oos.writeObject(it) }
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    fun deserialize(data: String): Array<ItemStack?> {
        val bais = ByteArrayInputStream(Base64.getDecoder().decode(data))
        return BukkitObjectInputStream(bais).use { ois ->
            val size = ois.readInt()
            Array(size) { ois.readObject() as? ItemStack }
        }
    }

    private fun vaultSlots(owner: UUID, group: String?): Int {
        val player = Bukkit.getOfflinePlayer(owner)
        val cfg = ArcNetworkConfig.globalVault
        var slots = cfg.defaultSlots
        cfg.slotPermissions.entries.sortedByDescending { it.value }.forEach { (perm, extra) ->
            if (player.isOnline && (player as? Player)?.hasPermission(perm) == true) {
                slots = (slots + extra).coerceAtMost(cfg.maxSlots)
            }
        }
        return slots
    }

    private fun currentGroup() = ArcNetworkConfig.serverGroup.takeIf { ArcNetworkConfig.globalVault.perGroup }
}
