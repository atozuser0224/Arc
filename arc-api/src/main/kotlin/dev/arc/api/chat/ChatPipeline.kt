@file:JvmName("ChatPipelines")

package dev.arc.api.chat

import dev.arc.api.event.listen
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap

private val mm = MiniMessage.miniMessage()

// ---------------------------------------------------------------------------

data class ChatContext(
    val player: Player,
    val rawMessage: String,
    var formattedMessage: Component,
    val raw: AsyncChatEvent,
) {
    fun cancel() { raw.isCancelled = true }
}

class ChannelBuilder(val name: String) {
    internal var permission: String? = null
    internal var formatProvider: ((ChatContext) -> Component)? = null
    internal val filters = mutableListOf<(ChatContext) -> Boolean>()

    /** Only players with [perm] can speak in (and see) this channel. */
    fun permission(perm: String) { permission = perm }

    /** Format the outgoing message. Return the [Component] to broadcast. */
    fun format(block: (ChatContext) -> String) {
        formatProvider = { ctx -> mm.deserialize(block(ctx)) }
    }

    fun formatComponent(block: (ChatContext) -> Component) {
        formatProvider = block
    }

    /** Return false to cancel the message silently. */
    fun filter(block: (ChatContext) -> Boolean) { filters += block }
}

class ChatPipelineBuilder {
    internal val channels = mutableListOf<ChannelBuilder>()
    internal val globalFilters = mutableListOf<(ChatContext) -> Boolean>()
    internal var defaultFormat: ((ChatContext) -> Component)? = null

    /** Register a named channel. */
    fun channel(name: String, block: ChannelBuilder.() -> Unit) {
        channels += ChannelBuilder(name).apply(block)
    }

    /** Applied to every message regardless of channel. Cancel by returning false. */
    fun globalFilter(block: (ChatContext) -> Boolean) { globalFilters += block }

    /**
     * Default format for messages that don't match any channel.
     * If not set, Minecraft default format is used.
     */
    fun defaultFormat(block: (ChatContext) -> String) {
        defaultFormat = { ctx -> mm.deserialize(block(ctx)) }
    }
}

// ---------------------------------------------------------------------------

/**
 * Install a chat processing pipeline that routes messages through channels and filters.
 *
 * ```kotlin
 * plugin.chatPipeline {
 *     channel("staff") {
 *         permission("myplugin.staff")
 *         format { ctx -> "<gray>[STAFF] <white>${ctx.player.name}: ${ctx.rawMessage}" }
 *     }
 *
 *     globalFilter { ctx ->
 *         if (isSpamming(ctx.player)) { ctx.player.msg("§cSlow down!"); false } else true
 *     }
 *
 *     defaultFormat { ctx -> "<gray>${ctx.player.name}: <white>${ctx.rawMessage}" }
 * }
 * ```
 */
public fun Plugin.chatPipeline(block: ChatPipelineBuilder.() -> Unit) {
    val builder = ChatPipelineBuilder().apply(block)
    val cooldowns = ConcurrentHashMap<String, Long>()

    listen<AsyncChatEvent> { event ->
        val player = event.player
        val rawMsg = mm.serialize(event.message())
        val ctx = ChatContext(player, rawMsg, event.message(), event)

        // global filters
        for (filter in builder.globalFilters) {
            if (!filter(ctx)) { event.isCancelled = true; return@listen }
        }

        // find matching channel
        val channel = builder.channels.firstOrNull { ch ->
            ch.permission == null || player.hasPermission(ch.permission!!)
        }

        if (channel != null) {
            // channel filters
            for (filter in channel.filters) {
                if (!filter(ctx)) { event.isCancelled = true; return@listen }
            }
            val formatted = channel.formatProvider?.invoke(ctx) ?: event.message()
            event.renderer { _, _, _, _ -> formatted }
            // restrict viewers to players with channel permission
            channel.permission?.let { perm ->
                event.viewers().removeIf { viewer ->
                    viewer is Player && !viewer.hasPermission(perm)
                }
            }
        } else {
            // default format
            builder.defaultFormat?.let { fmt ->
                val formatted = fmt(ctx)
                event.renderer { _, _, _, _ -> formatted }
            }
        }
    }
}
