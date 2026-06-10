@file:JvmName("ChatInput")

package dev.arc.api.conversation

import io.papermc.paper.event.player.AsyncChatEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import kotlin.coroutines.resume

/**
 * Coroutine "conversation" input: suspend until this player types their next chat message, then resume with
 * it (the message is swallowed, not broadcast). Far cleaner than a stateful chat-listener state machine.
 *
 * ```kotlin
 * plugin.launch {
 *     player.sendMessage("Enter a name:")
 *     val name = player.awaitChatText(plugin)
 *     player.sendMessage("Hi, $name")
 * }
 * ```
 */
public suspend fun Player.awaitChat(plugin: Plugin): Component = suspendCancellableCoroutine { continuation ->
    val target = this
    val listener = object : Listener {
        @EventHandler
        fun onChat(event: AsyncChatEvent) {
            if (event.player == target) {
                event.isCancelled = true
                HandlerList.unregisterAll(this)
                if (continuation.isActive) continuation.resume(event.message())
            }
        }
    }
    plugin.server.pluginManager.registerEvents(listener, plugin)
    continuation.invokeOnCancellation { HandlerList.unregisterAll(listener) }
}

/** Like [awaitChat] but yields the plain-text content. */
public suspend fun Player.awaitChatText(plugin: Plugin): String =
    PlainTextComponentSerializer.plainText().serialize(awaitChat(plugin))
