package dev.arc.api.ops.plugin

import org.bukkit.command.CommandSender
import java.util.concurrent.ConcurrentHashMap

/**
 * Token-based confirmation for dangerous operations.
 *
 * A command that is risky (plugin reload, reload-chain apply, …) does not act
 * immediately: it [stage]s the action under a short random token and tells the
 * sender to type `/arc confirm <token>`. [confirm] runs the staged action if the
 * token is still valid (matching sender + not expired).
 *
 * Pure in-memory, per-sender, with a TTL — nothing here touches world state, so
 * it is always safe to have active.
 */
object ConfirmManager {

    private const val TTL_MILLIS = 30_000L

    private data class Pending(
        val senderId: String,
        val description: String,
        val action: () -> Unit,
        val createdAt: Long,
    )

    private val pending = ConcurrentHashMap<String, Pending>()

    private fun senderId(sender: CommandSender): String = sender.name

    /** Stage [action] and return the token the sender must confirm with. */
    fun stage(sender: CommandSender, description: String, action: () -> Unit): String {
        purgeExpired()
        val token = Integer.toHexString((Math.random() * 0xFFFFFF).toInt()).uppercase().padStart(4, '0').takeLast(4)
        pending[token] = Pending(senderId(sender), description, action, System.currentTimeMillis())
        return token
    }

    /** Returns true if a staged action was found, valid, and executed. */
    fun confirm(sender: CommandSender, token: String): Boolean {
        purgeExpired()
        val p = pending[token.uppercase()] ?: return false
        if (p.senderId != senderId(sender)) return false
        pending.remove(token.uppercase())
        p.action()
        return true
    }

    fun describe(token: String): String? = pending[token.uppercase()]?.description

    private fun purgeExpired() {
        val now = System.currentTimeMillis()
        pending.entries.removeIf { now - it.value.createdAt > TTL_MILLIS }
    }
}
