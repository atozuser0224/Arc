package dev.arc.api.effect

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.function.Consumer
import kotlin.time.Duration.Companion.seconds

/**
 * Java-friendly bridge for Arc custom effects.
 *
 * Kotlin callers should use [customEffects] and [Plugin.customEffectRegistry] directly.
 *
 * ```java
 * // Create registry and register an effect
 * CustomEffectRegistry effects = ArcCustomEffects.createRegistry(plugin, registry -> {
 *     ArcCustomEffects.registerEffect(registry, "mana_surge", effect -> {
 *         effect.setVisualEffect(PotionEffectType.REGENERATION);
 *         ArcCustomEffects.setDefaultDurationSeconds(effect, 15L);
 *         effect.setReapplyPolicy(EffectReapplyPolicy.KEEP_STRONGER);
 *         effect.onApply(player -> player.sendMessage("Mana surge begins!"));
 *         effect.onTick(20, (player, state) -> {
 *             player.sendMessage("Mana tick: " + state.getRemainingTicks() + " left");
 *         });
 *         effect.onRemove((player, state, reason) ->
 *             player.sendMessage("Mana surge ended: " + reason)
 *         );
 *     });
 * });
 *
 * // Apply with duration in seconds
 * ArcCustomEffects.applySeconds(effects, player, effects.get("mana_surge"), 20L, 1);
 *
 * // In onDisable:
 * effects.close();
 * ```
 */
public object ArcCustomEffects {

    /**
     * Create a plugin-scoped [CustomEffectRegistry] and immediately configure it.
     *
     * @param plugin owning plugin (used for scheduler and lifecycle)
     * @param block  receives the fresh registry to register effects
     */
    @JvmStatic
    public fun createRegistry(plugin: Plugin, block: Consumer<CustomEffectRegistry>): CustomEffectRegistry =
        plugin.customEffects { block.accept(this) }

    /**
     * Create an empty plugin-scoped [CustomEffectRegistry].
     * Use [registerEffect] to populate it.
     */
    @JvmStatic
    public fun createRegistry(plugin: Plugin): CustomEffectRegistry =
        plugin.customEffectRegistry()

    /**
     * Register a custom effect in [registry] using a [Consumer]-based builder.
     *
     * @param registry target registry
     * @param id       effect id (simple name, or `"namespace:name"` for cross-plugin effects)
     * @param block    receives a [CustomEffectBuilder]; configure visual, duration, callbacks, etc.
     */
    @JvmStatic
    public fun registerEffect(
        registry: CustomEffectRegistry,
        id: String,
        block: Consumer<CustomEffectBuilder>,
    ): CustomEffect = registry.effect(id) { block.accept(this) }

    /**
     * Set [CustomEffectBuilder.defaultDuration] in whole seconds.
     * Avoids the need to import [kotlin.time.Duration] from Java.
     *
     * ```java
     * ArcCustomEffects.setDefaultDurationSeconds(builder, 15L);
     * // equivalent to: builder.defaultDuration = 15.seconds  (Kotlin)
     * ```
     */
    @JvmStatic
    public fun setDefaultDurationSeconds(builder: CustomEffectBuilder, seconds: Long) {
        builder.defaultDuration = seconds.seconds
    }

    /**
     * Apply [effect] to [player] for [durationSeconds] whole seconds.
     * Avoids the need to import [kotlin.time.Duration] from Java.
     *
     * @param registry  the registry the effect was registered in
     * @param player    target player
     * @param effect    [CustomEffect] to apply (must be in [registry])
     * @param durationSeconds duration in whole seconds (must be ≥ 1)
     * @param amplifier effect amplifier level (0-based; default is [CustomEffect.defaultAmplifier])
     */
    @JvmStatic
    @JvmOverloads
    public fun applySeconds(
        registry: CustomEffectRegistry,
        player: Player,
        effect: CustomEffect,
        durationSeconds: Long,
        amplifier: Int = effect.defaultAmplifier,
    ): EffectApplication<NamespacedKey> =
        registry.apply(player, effect, durationSeconds.seconds, amplifier)
}

// ── Java-friendly overloads on CustomEffectBuilder ─────────────────────────

/** Java: `builder.onApply(player -> player.sendMessage("Applied!"));` */
public fun CustomEffectBuilder.onApply(consumer: Consumer<Player>): Unit =
    onApply { consumer.accept(it) }

/**
 * Java-friendly onTick with tick-interval and state object.
 * ```java
 * builder.onTick(20, (player, state) -> player.sendMessage("tick"));
 * ```
 */
public fun CustomEffectBuilder.onTick(
    interval: Int,
    consumer: java.util.function.BiConsumer<Player, ActiveCustomEffect<NamespacedKey>>,
): Unit = onTick(interval) { player, state -> consumer.accept(player, state) }

/** Java: `builder.onRemove((player, state, reason) -> log(reason));` */
public fun CustomEffectBuilder.onRemove(
    consumer: TriConsumer<Player, ActiveCustomEffect<NamespacedKey>, EffectRemovalReason>,
): Unit = onRemove { player, state, reason -> consumer.accept(player, state, reason) }

/** Three-argument consumer for Java lambdas (no equivalent in java.util.function). */
public fun interface TriConsumer<A, B, C> {
    public fun accept(a: A, b: B, c: C)
}
