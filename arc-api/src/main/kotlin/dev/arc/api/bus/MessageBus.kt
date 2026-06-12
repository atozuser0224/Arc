@file:JvmName("MessageBus")

package dev.arc.api.bus

import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Type-safe, decoupled inter-plugin message bus.
 *
 * Publishers and subscribers reference only a [Topic] — they never need to depend
 * on each other or share an interface.  Messages are dispatched synchronously on the
 * calling thread; for async delivery wrap the publish call in a coroutine or scheduler.
 *
 * ```kotlin
 * // Define a topic (share the object between plugins via a shared-api jar or companion)
 * val ECONOMY_TRANSACTION = Topic.of<TransactionEvent>("economy.transaction")
 *
 * // Producer plugin
 * ArcMessageBus.publish(ECONOMY_TRANSACTION, TransactionEvent(player, 100))
 *
 * // Consumer plugin (auto-unsubscribed when plugin disables)
 * ArcMessageBus.subscribe(plugin, ECONOMY_TRANSACTION) { event ->
 *     player.sendMessage("You received ${event.amount} coins")
 * }
 * ```
 */
object ArcMessageBus {

    @PublishedApi
    internal val subscribers = ConcurrentHashMap<String, CopyOnWriteArrayList<Subscription<*>>>()

    /**
     * Subscribe to [topic].  The [handler] is called on every [publish] for this topic.
     * All subscriptions for [plugin] are removed automatically when the plugin is disabled.
     */
    fun <T : Any> subscribe(plugin: Plugin, topic: Topic<T>, handler: (T) -> Unit): Subscription<T> {
        val sub = Subscription(plugin, topic, handler)
        subscribers.getOrPut(topic.key) { CopyOnWriteArrayList() }.add(sub)
        return sub
    }

    /** Remove a specific subscription. */
    fun unsubscribe(subscription: Subscription<*>) {
        subscribers[subscription.topic.key]?.remove(subscription)
    }

    /** Remove all subscriptions registered by [plugin]. */
    fun unsubscribeAll(plugin: Plugin) {
        subscribers.values.forEach { list -> list.removeIf { it.plugin == plugin } }
    }

    /** Publish [message] to all subscribers of [topic]. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> publish(topic: Topic<T>, message: T) {
        subscribers[topic.key]?.forEach { sub ->
            (sub as Subscription<T>).handler(message)
        }
    }
}

/** A named channel for messages of type [T]. Create with [Topic.of]. */
class Topic<T : Any> private constructor(val key: String) {
    companion object {
        fun <T : Any> of(key: String): Topic<T> = Topic(key)
    }
    override fun toString() = "Topic($key)"
}

/** An active subscription — hold a reference to call [cancel]. */
class Subscription<T : Any> internal constructor(
    val plugin: Plugin,
    val topic: Topic<T>,
    internal val handler: (T) -> Unit,
) {
    fun cancel() = ArcMessageBus.unsubscribe(this)
}

/** Publish to [topic] without importing [ArcMessageBus] directly. */
fun <T : Any> Topic<T>.publish(message: T) = ArcMessageBus.publish(this, message)

/** Subscribe to this topic from [plugin]. */
fun <T : Any> Topic<T>.subscribe(plugin: Plugin, handler: (T) -> Unit): Subscription<T> =
    ArcMessageBus.subscribe(plugin, this, handler)
