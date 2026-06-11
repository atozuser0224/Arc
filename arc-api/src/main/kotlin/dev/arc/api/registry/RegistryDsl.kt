@file:JvmName("ArcRegistryDsl")

package dev.arc.api.registry

import org.bukkit.NamespacedKey
import org.bukkit.plugin.Plugin

@DslMarker public annotation class RegistryDsl

// ============================================================
//  Top-level entry point
// ============================================================

/**
 * Register all custom registry content for this plugin in one cohesive block.
 *
 * ```kotlin
 * override fun onEnable() {
 *     arcRegistries {
 *         sounds {
 *             +"spell_cast"
 *             +"spell_fail"
 *             "boss_appear" { fixedRange = 128f }
 *         }
 *         attributes {
 *             "max_mana" {
 *                 default = 100.0
 *                 range   = 0.0..10_000.0
 *                 syncable = true
 *             }
 *         }
 *     }
 * }
 * ```
 */
public fun Plugin.arcRegistries(block: ArcRegistryRootScope.() -> Unit) {
    ArcRegistryRootScope(this).block()
}

// ============================================================
//  Root scope
// ============================================================

@RegistryDsl
public class ArcRegistryRootScope(public val plugin: Plugin) {

    /** Register sound events. */
    public fun sounds(block: SoundsScope.() -> Unit) {
        val p = plugin; ArcRegistries.soundEvents { SoundsScope(p, this).block() }
    }

    /** Register ranged attributes. */
    public fun attributes(block: AttributesScope.() -> Unit) {
        val p = plugin; ArcRegistries.attributes { AttributesScope(p, this).block() }
    }

    /** Register mob effects (potion effects). */
    public fun mobEffects(block: MobEffectsScope.() -> Unit) {
        val p = plugin; ArcRegistries.mobEffects { MobEffectsScope(p, this).block() }
    }

    /** Register enchantments (raw NMS; use with care on 1.21+ data-driven enchants). */
    public fun enchantments(block: RawRegistryScope.() -> Unit) {
        val p = plugin; ArcRegistries.enchantments { RawRegistryScope(p, this).block() }
    }

    /** Open any registry by its Minecraft ID for advanced / raw registration. */
    public fun registry(id: String, block: RawRegistryScope.() -> Unit) {
        val p = plugin; ArcRegistries.edit(id) { RawRegistryScope(p, this).block() }
    }
}

// ============================================================
//  Sounds scope
// ============================================================

@RegistryDsl
public class SoundsScope(
    private val plugin: Plugin,
    private val edit: RegistryEditScope,
) {
    /**
     * Register a variable-range sound with operator syntax.
     * ```kotlin
     * +"spell_cast"
     * ```
     */
    public operator fun String.unaryPlus(): NamespacedKey {
        val p = plugin
        return NamespacedKey(p, this).also { edit.soundEvent(it) }
    }

    /**
     * Register a sound with additional config.
     * ```kotlin
     * "boss_appear" { fixedRange = 128f }
     * ```
     */
    public operator fun String.invoke(block: SoundBuilder.() -> Unit): NamespacedKey {
        val p = plugin
        val key = NamespacedKey(p, this)
        val builder = SoundBuilder().apply(block)
        edit.soundEvent(key, builder.fixedRange)
        return key
    }
}

@RegistryDsl
public class SoundBuilder {
    /** Set to a positive value to use fixed attenuation instead of distance-falloff. */
    public var fixedRange: Float? = null
}

// ============================================================
//  Attributes scope
// ============================================================

@RegistryDsl
public class AttributesScope(
    private val plugin: Plugin,
    private val edit: RegistryEditScope,
) {
    /**
     * Register a ranged attribute with a builder block.
     * ```kotlin
     * "max_mana" {
     *     description = "myplugin.attribute.max_mana"
     *     default  = 100.0
     *     range    = 0.0..10_000.0
     *     syncable = true
     * }
     * ```
     */
    public operator fun String.invoke(block: AttributeBuilder.() -> Unit): NamespacedKey {
        val p = plugin
        val key = NamespacedKey(p, this)
        val b = AttributeBuilder("${p.name.lowercase()}.attribute.$this").apply(block)
        edit.attribute(key, b.description, b.default, b.range.start, b.range.endInclusive, b.syncable)
        return key
    }
}

@RegistryDsl
public class AttributeBuilder(defaultDescription: String) {
    /** Translation / description key shown in tooltips. Defaults to `<plugin>.attribute.<name>`. */
    public var description: String = defaultDescription
    /** Value used when the attribute is not modified by any modifier. */
    public var default: Double = 0.0
    /** Allowed value range (inclusive on both ends). */
    public var range: ClosedFloatingPointRange<Double> = 0.0..Double.MAX_VALUE
    /** Whether the value is synced to the client (required for display in inventory). */
    public var syncable: Boolean = false
}

// ============================================================
//  Mob effects scope
// ============================================================

@RegistryDsl
public class MobEffectsScope(
    private val plugin: Plugin,
    private val edit: RegistryEditScope,
) {
    /**
     * Register a mob effect using a raw NMS instance.
     * Mob effects are abstract in NMS — create a subclass in your server code
     * and pass the instance here.
     *
     * ```kotlin
     * "mana_regen" with myNmsMobEffect
     * ```
     */
    public infix fun String.with(nmsEffect: Any): NamespacedKey {
        val p = plugin
        val key = NamespacedKey(p, this)
        edit.register(key, nmsEffect)
        return key
    }
}

// ============================================================
//  Raw / advanced scope  (escape hatch for other registries)
// ============================================================

@RegistryDsl
public class RawRegistryScope(
    private val plugin: Plugin,
    private val edit: RegistryEditScope,
) {
    /** Register a raw NMS object under `<plugin>:<name>`. */
    public infix fun String.with(nmsValue: Any): NamespacedKey {
        val p = plugin
        val key = NamespacedKey(p, this)
        edit.register(key, nmsValue)
        return key
    }

    /** Remove an entry by simple name. Returns true if it existed. */
    public fun remove(name: String): Boolean =
        edit.unregister(NamespacedKey(plugin, name))

    /** Check if `<plugin>:<name>` is registered. */
    public fun has(name: String): Boolean =
        edit.has(NamespacedKey(plugin, name))

    /** All keys currently in this registry. */
    public val allKeys: Set<String> get() = edit.keys

    /** Build a SoundEvent NMS object (useful when inside a raw registry block). */
    public fun soundEvent(name: String, fixedRange: Float? = null): Any =
        edit.createSoundEvent(NamespacedKey(plugin, name), fixedRange)

    /** Build a RangedAttribute NMS object. */
    public fun attribute(
        name: String,
        description: String = "${plugin.name.lowercase()}.attribute.$name",
        default: Double = 0.0,
        range: ClosedFloatingPointRange<Double> = 0.0..Double.MAX_VALUE,
        syncable: Boolean = false,
    ): Any = edit.createAttribute(description, default, range.start, range.endInclusive, syncable)
}
