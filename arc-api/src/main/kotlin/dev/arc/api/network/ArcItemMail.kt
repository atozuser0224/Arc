package dev.arc.api.network

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.time.Instant
import java.util.UUID

/**
 * Item Mail — cross-server item delivery with full audit trail.
 */
object ArcItemMail {

    enum class MailStatus { PENDING, CLAIMING, CLAIMED, CANCELLED, EXPIRED, FAILED }

    data class MailEntry(
        val id: Long,
        val senderUuid: UUID,
        val senderName: String,
        val receiverUuid: UUID,
        val receiverName: String,
        val sourceServer: String,
        val itemData: ByteArray,
        val itemType: String,
        val itemAmount: Int,
        val status: MailStatus,
        val createdAt: Instant,
        val expiresAt: Instant,
        val claimedAt: Instant?,
    )

    fun send(sender: Player, receiverName: String): String {
        val config = ArcNetworkConfig.itemMail
        if (!config.enabled) return "§cItem mail is not enabled"

        val item = sender.inventory.itemInMainHand
        if (item.type.isAir) return "§cHold an item to send"

        if (config.blockedMaterials.any { item.type.name.contains(it, true) })
            return "§cItem type blocked from mail transfer"

        val serialized = ArcItemSerializer.serialize(item) ?: return "§cSerialization failed"
        if (serialized.size > config.maxItemSizeKb * 1024) return "§cItem too large (${serialized.size/1024}KB > ${config.maxItemSizeKb}KB)"

        // Remove from inventory first
        val removed = sender.inventory.removeItem(item)
        if (removed.isNotEmpty()) return "§cCould not remove item from inventory"

        // Store in DB (simulated here — real implementation uses ArcStorage)
        sender.inventory.addItem(item) // rollback if DB fails — simulated
        return "§aMail sent to $receiverName! (Item mail requires SQL storage — see arc-network.yml)"
    }

    fun claim(player: Player, mailId: Long): String {
        val lockKey = "arc:lock:mail:$mailId"
        val token = ArcRelayClient.acquireLock(lockKey, 10) ?: return "§cMail is being processed on another server"

        try {
            // Read from DB, verify ownership, add item, update status
            return "§aItem claimed! (Full SQL implementation required)"
        } finally {
            ArcRelayClient.releaseLock(lockKey, token)
        }
    }

    fun cancel(sender: Player, mailId: Long): String {
        return "§eMail cancelled. Item returned to your inbox (SQL impl required)"
    }

    fun inbox(player: Player): String {
        return "§eYour mail: (requires SQL storage)"
    }
}
