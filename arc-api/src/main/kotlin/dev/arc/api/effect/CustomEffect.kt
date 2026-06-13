@file:JvmName("CustomEffects")

package dev.arc.api.effect

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.logging.Level
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Convert a positive duration to server ticks, rounding partial ticks up. */
public fun Duration.toEffectTicks(): Int {
    require(isFinite() && isPositive()) { "effect duration must be positive and finite" }
    val tickNanos = 50_000_000L
    val nanos = inWholeNanoseconds
    val ticks = nanos / tickNanos + if (nanos % tickNanos == 0L) 0 else 1
    require(ticks <= Int.MAX_VALUE) { "effect duration is too large" }
    return ticks.toInt()
}

/** Immutable custom effect definition registered to one plugin registry. */
public class CustomEffect internal constructor(
    public val key: NamespacedKey,
    public val visualEffect: PotionEffectType?,
    public val defaultDurationTicks: Int,
    public val defaultAmplifier: Int,
    public val reapplyPolicy: EffectReapplyPolicy,
    public val tickInterval: Int,
    internal val applyCallback: ((Player, ActiveCustomEffect<NamespacedKey>) -> Unit)?,
    internal val tickCallback: ((Player, ActiveCustomEffect<NamespacedKey>) -> Unit)?,
    internal val expireCallback: ((Player, ActiveCustomEffect<NamespacedKey>) -> Unit)?,
    internal val removeCallback: ((
        Player,
        ActiveCustomEffect<NamespacedKey>,
        EffectRemovalReason,
    ) -> Unit)?,
) {
    /** Compatibility name for code written against the prototype API. */
    public val id: String get() = key.toString()

    override fun equals(other: Any?): Boolean = other is CustomEffect && key == other.key
    override fun hashCode(): Int = key.hashCode()
    override fun toString(): String = "CustomEffect($key)"
}

/** Kotlin DSL builder for an immutable [CustomEffect]. */
@DslMarker
public annotation class CustomEffectDsl

/** Kotlin DSL builder for an immutable [CustomEffect]. */
@CustomEffectDsl
public class CustomEffectBuilder internal constructor() {
    public var visualEffect: PotionEffectType? = null
    public var defaultDuration: Duration = 10.seconds
    public var defaultAmplifier: Int = 0
    public var reapplyPolicy: EffectReapplyPolicy = EffectReapplyPolicy.KEEP_STRONGER
    public var tickInterval: Int = 1

    private var applyCallback: ((Player, ActiveCustomEffect<NamespacedKey>) -> Unit)? = null
    private var tickCallback: ((Player, ActiveCustomEffect<NamespacedKey>) -> Unit)? = null
    private var expireCallback: ((Player, ActiveCustomEffect<NamespacedKey>) -> Unit)? = null
    private var removeCallback: ((
        Player,
        ActiveCustomEffect<NamespacedKey>,
        EffectRemovalReason,
    ) -> Unit)? = null

    public fun visual(type: PotionEffectType?) {
        visualEffect = type
    }

    public fun onApply(block: (Player) -> Unit) {
        applyCallback = { player, _ -> block(player) }
    }

    @JvmName("onApplyWithInstance")
    public fun onApply(block: (Player, ActiveCustomEffect<NamespacedKey>) -> Unit) {
        applyCallback = block
    }

    /** Prototype-compatible callback receiving remaining ticks. */
    public fun onTick(block: (Player, Int) -> Unit) {
        tickCallback = { player, instance -> block(player, instance.remainingTicks) }
    }

    /** Configure a state-aware callback and its tick interval. */
    public fun onTick(
        interval: Int,
        block: (Player, ActiveCustomEffect<NamespacedKey>) -> Unit,
    ) {
        require(interval > 0) { "tick interval must be positive" }
        tickInterval = interval
        tickCallback = block
    }

    /** Configure a state-aware callback using a duration interval. */
    public fun onTick(
        interval: Duration,
        block: (Player, ActiveCustomEffect<NamespacedKey>) -> Unit,
    ): Unit = onTick(interval.toEffectTicks(), block)

    public fun onExpire(block: (Player) -> Unit) {
        expireCallback = { player, _ -> block(player) }
    }

    @JvmName("onExpireWithInstance")
    public fun onExpire(block: (Player, ActiveCustomEffect<NamespacedKey>) -> Unit) {
        expireCallback = block
    }

    public fun onRemove(
        block: (Player, ActiveCustomEffect<NamespacedKey>, EffectRemovalReason) -> Unit,
    ) {
        removeCallback = block
    }

    internal fun build(key: NamespacedKey): CustomEffect {
        val durationTicks = defaultDuration.toEffectTicks()
        require(defaultAmplifier >= 0) { "default amplifier must not be negative" }
        require(tickInterval > 0) { "tick interval must be positive" }
        return CustomEffect(
            key = key,
            visualEffect = visualEffect,
            defaultDurationTicks = durationTicks,
            defaultAmplifier = defaultAmplifier,
            reapplyPolicy = reapplyPolicy,
            tickInterval = tickInterval,
            applyCallback = applyCallback,
            tickCallback = tickCallback,
            expireCallback = expireCallback,
            removeCallback = removeCallback,
        )
    }
}

/**
 * Plugin-scoped custom effect registry and tick manager.
 *
 * Mutations must happen on the primary server thread. Call [close] from plugin
 * shutdown to cancel scheduling and remove managed visuals.
 */
public class CustomEffectRegistry(public val plugin: Plugin) : AutoCloseable {
    private val definitions = LinkedHashMap<NamespacedKey, CustomEffect>()
    private val engine = CustomEffectEngine<UUID, NamespacedKey>()
    private val task: BukkitTask =
        plugin.server.scheduler.runTaskTimer(plugin, Runnable(::tick), 0L, 1L)
    private var closed: Boolean = false

    public val size: Int get() = definitions.size
    public val isClosed: Boolean get() = closed
    public val keys: Set<NamespacedKey> get() = definitions.keys.toSet()

    public fun effects(block: CustomEffectRegistry.() -> Unit) {
        ensureMutable()
        block()
    }

    public fun effect(id: String, block: CustomEffectBuilder.() -> Unit): CustomEffect =
        effect(key(id), block)

    public fun effect(
        key: NamespacedKey,
        block: CustomEffectBuilder.() -> Unit,
    ): CustomEffect {
        ensureMutable()
        require(key !in definitions) { "Custom effect already registered: $key" }
        return CustomEffectBuilder().apply(block).build(key).also {
            definitions[key] = it
        }
    }

    /** Compatibility alias retained from the prototype API. */
    public fun define(id: String, configure: CustomEffectBuilder.() -> Unit): CustomEffect =
        effect(id, configure)

    public operator fun get(id: String): CustomEffect =
        find(id) ?: error("Unknown custom effect: ${key(id)}")

    public operator fun get(key: NamespacedKey): CustomEffect =
        definitions[key] ?: error("Unknown custom effect: $key")

    public fun find(id: String): CustomEffect? = definitions[key(id)]
    public fun find(key: NamespacedKey): CustomEffect? = definitions[key]

    public fun definitions(): List<CustomEffect> = definitions.values.toList()

    public fun unregister(effect: CustomEffect): Boolean = unregister(effect.key)

    public fun unregister(key: NamespacedKey): Boolean {
        ensureMutable()
        val definition = definitions.remove(key) ?: return false
        for (entry in engine.clearEffect(key, EffectRemovalReason.UNREGISTERED)) {
            plugin.server.getPlayer(entry.subject)?.let { player ->
                finishRemoval(player, definition, entry.removal)
            }
        }
        return true
    }

    public fun apply(
        player: Player,
        effect: CustomEffect,
        durationTicks: Int = effect.defaultDurationTicks,
        amplifier: Int = effect.defaultAmplifier,
        policy: EffectReapplyPolicy = effect.reapplyPolicy,
    ): EffectApplication<NamespacedKey> {
        ensureMutable()
        require(definitions[effect.key] === effect) {
            "Effect ${effect.key} is not registered in this registry"
        }
        val application = engine.apply(
            subject = player.uniqueId,
            effectKey = effect.key,
            durationTicks = durationTicks,
            amplifier = amplifier,
            tickInterval = effect.tickInterval,
            policy = policy,
        )
        if (application.result == EffectApplyResult.IGNORED) return application

        if (application.result == EffectApplyResult.REPLACED) {
            application.previous?.let { previous ->
                invokeRemoval(player, effect, previous, EffectRemovalReason.REPLACED)
            }
        }
        reconcileVisual(player, effect.visualEffect)
        invokeSafely(effect, player, "apply") {
            effect.applyCallback?.invoke(player, application.current)
        }
        return application
    }

    public fun apply(
        player: Player,
        effect: CustomEffect,
        duration: Duration,
        amplifier: Int = effect.defaultAmplifier,
        policy: EffectReapplyPolicy = effect.reapplyPolicy,
    ): EffectApplication<NamespacedKey> =
        apply(player, effect, duration.toEffectTicks(), amplifier, policy)

    public fun remove(player: Player, effect: CustomEffect): Boolean {
        ensureMutable()
        val removal = engine.remove(
            player.uniqueId,
            effect.key,
            EffectRemovalReason.REMOVED,
        ) ?: return false
        finishRemoval(player, effect, removal)
        return true
    }

    public fun isActive(player: Player, effect: CustomEffect): Boolean =
        engine.get(player.uniqueId, effect.key) != null

    public fun active(player: Player, effect: CustomEffect): ActiveCustomEffect<NamespacedKey>? =
        engine.get(player.uniqueId, effect.key)

    public fun active(player: Player): Map<NamespacedKey, ActiveCustomEffect<NamespacedKey>> =
        engine.snapshot(player.uniqueId)

    public fun remainingTicks(player: Player, effect: CustomEffect): Int =
        active(player, effect)?.remainingTicks ?: 0

    public fun clearAll(player: Player): Int {
        ensureMutable()
        val removals = engine.clearSubject(player.uniqueId, EffectRemovalReason.CLEARED)
        for (removal in removals) {
            definitions[removal.effect.effectKey]?.let { finishRemoval(player, it, removal) }
        }
        return removals.size
    }

    override fun close() {
        ensurePrimaryThread()
        if (closed) return
        closed = true
        task.cancel()
        for ((uuid, effects) in engine.snapshotAll()) {
            val player = plugin.server.getPlayer(uuid)
            val removals = engine.clearSubject(uuid, EffectRemovalReason.SHUTDOWN)
            if (player != null) {
                for (removal in removals) {
                    definitions[removal.effect.effectKey]?.let {
                        finishRemoval(player, it, removal)
                    }
                }
            }
        }
        definitions.clear()
    }

    private fun tick() {
        if (closed) return
        val offline = engine.snapshotAll().keys.filter { plugin.server.getPlayer(it) == null }
        offline.forEach { engine.clearSubject(it, EffectRemovalReason.CLEARED) }

        val transitions = engine.tick()
        for (due in transitions.due) {
            val player = plugin.server.getPlayer(due.subject) ?: continue
            val definition = definitions[due.effect.effectKey] ?: continue
            invokeSafely(definition, player, "tick") {
                definition.tickCallback?.invoke(player, due.effect)
            }
        }
        for (expired in transitions.expired) {
            val player = plugin.server.getPlayer(expired.subject) ?: continue
            val definition = definitions[expired.removal.effect.effectKey] ?: continue
            finishRemoval(player, definition, expired.removal)
        }
    }

    private fun finishRemoval(
        player: Player,
        definition: CustomEffect,
        removal: EffectRemoval<NamespacedKey>,
    ) {
        reconcileVisual(player, definition.visualEffect)
        invokeRemoval(player, definition, removal.effect, removal.reason)
    }

    private fun invokeRemoval(
        player: Player,
        definition: CustomEffect,
        instance: ActiveCustomEffect<NamespacedKey>,
        reason: EffectRemovalReason,
    ) {
        invokeSafely(definition, player, "remove") {
            definition.removeCallback?.invoke(player, instance, reason)
        }
        invokeSafely(definition, player, "expire") {
            definition.expireCallback?.invoke(player, instance)
        }
    }

    @Suppress("DEPRECATION")
    private fun reconcileVisual(player: Player, type: PotionEffectType?) {
        if (type == null) return
        val strongest = engine.snapshot(player.uniqueId).values
            .filter { definitions[it.effectKey]?.visualEffect == type }
            .maxWithOrNull(
                compareBy<ActiveCustomEffect<NamespacedKey>> { it.amplifier }
                    .thenBy { it.remainingTicks },
            )
        if (strongest == null) {
            player.removePotionEffect(type)
        } else {
            player.addPotionEffect(
                PotionEffect(
                    type,
                    strongest.remainingTicks,
                    strongest.amplifier,
                    true,
                    true,
                    true,
                ),
                true,
            )
        }
    }

    private fun invokeSafely(
        effect: CustomEffect,
        player: Player,
        phase: String,
        callback: () -> Unit,
    ) {
        runCatching(callback).onFailure {
            plugin.logger.log(
                Level.SEVERE,
                "Custom effect ${effect.key} $phase callback failed for ${player.uniqueId}",
                it,
            )
        }
    }

    private fun key(id: String): NamespacedKey =
        if (':' in id) {
            requireNotNull(NamespacedKey.fromString(id)) { "Invalid namespaced effect key: $id" }
        } else {
            NamespacedKey(plugin, id)
        }

    private fun ensureMutable() {
        ensurePrimaryThread()
        check(!closed) { "Custom effect registry is closed" }
    }

    private fun ensurePrimaryThread() {
        check(Bukkit.isPrimaryThread()) {
            "Custom effect registry mutations must run on the primary server thread"
        }
    }
}

/** Create and configure a plugin-scoped custom effect registry. */
public fun Plugin.customEffects(
    block: CustomEffectRegistry.() -> Unit,
): CustomEffectRegistry = CustomEffectRegistry(this).apply(block)

/** Create an empty plugin-scoped custom effect registry. */
public fun Plugin.customEffectRegistry(): CustomEffectRegistry = CustomEffectRegistry(this)

public fun Player.applyCustomEffect(
    effect: CustomEffect,
    durationTicks: Int,
    amplifier: Int = effect.defaultAmplifier,
    registry: CustomEffectRegistry,
): EffectApplication<NamespacedKey> =
    registry.apply(this, effect, durationTicks, amplifier)

public fun Player.applyCustomEffect(
    effect: CustomEffect,
    duration: Duration,
    amplifier: Int = effect.defaultAmplifier,
    registry: CustomEffectRegistry,
): EffectApplication<NamespacedKey> =
    registry.apply(this, effect, duration, amplifier)

public fun Player.removeCustomEffect(
    effect: CustomEffect,
    registry: CustomEffectRegistry,
): Boolean = registry.remove(this, effect)

public fun Player.hasCustomEffect(
    effect: CustomEffect,
    registry: CustomEffectRegistry,
): Boolean = registry.isActive(this, effect)
