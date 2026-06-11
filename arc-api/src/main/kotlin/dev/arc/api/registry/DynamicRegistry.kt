@file:JvmName("DynamicRegistries")

package dev.arc.api.registry

import org.bukkit.NamespacedKey

/**
 * SPI implemented by arc-server's NMS backend.
 * Plugin code never calls this directly — use [ArcRegistries] instead.
 */
public interface RegistryBackend {
    /**
     * Return the NMS `MappedRegistry` (or compatible) object for [registryId]
     * (e.g. `"minecraft:sound_event"`), or null if not found.
     */
    fun nmsRegistry(registryId: String): Any?

    /** Set the registry's `frozen` flag to false so entries can be added. */
    fun unfreeze(nmsRegistry: Any)

    /** Re-freeze the registry after editing. */
    fun refreeze(nmsRegistry: Any)

    /**
     * Register [nmsValue] under [key] in [nmsRegistry].
     * Returns the NMS `Holder.Reference` for the new entry.
     */
    fun register(nmsRegistry: Any, key: NamespacedKey, nmsValue: Any): Any?

    /**
     * Remove an entry from [nmsRegistry] by [key].
     * Returns true if the entry was present and removed.
     */
    fun unregister(nmsRegistry: Any, key: NamespacedKey): Boolean

    /** Whether [key] exists in [nmsRegistry]. */
    fun contains(nmsRegistry: Any, key: NamespacedKey): Boolean

    /** All registered keys in [nmsRegistry] as `namespace:path` strings. */
    fun keys(nmsRegistry: Any): Set<String>

    // ---- Typed NMS object factories ----------------------------------------

    /** Create an NMS `SoundEvent`. [fixedRange] null = variable range. */
    fun createSoundEvent(key: NamespacedKey, fixedRange: Float?): Any

    /** Create an NMS `RangedAttribute`. */
    fun createAttribute(
        descriptionKey: String,
        defaultValue: Double,
        min: Double,
        max: Double,
        syncable: Boolean,
    ): Any

    /** Create an NMS `MobEffect` (potion effect) via the provided subclass builder. */
    fun createMobEffect(beneficial: Boolean, color: Int): Any?

    /** Sync registries to connected clients after bulk edits (if applicable). */
    fun syncToClients()
}

// ---------------------------------------------------------------------------
// Global access point
// ---------------------------------------------------------------------------

/**
 * Entry point for all dynamic (runtime) registry operations.
 * The backend is wired in by `ArcBootstrap.install()`.
 *
 * ```kotlin
 * ArcRegistries.edit("minecraft:sound_event") {
 *     register(NamespacedKey("myplugin", "my_sound")) {
 *         createSoundEvent(it, fixedRange = null)
 *     }
 * }
 * ```
 */
public object ArcRegistries {
    @Volatile public var backend: RegistryBackend? = null
        @JvmStatic set

    public val isAvailable: Boolean get() = backend != null

    private fun requireBackend(): RegistryBackend =
        backend ?: error("ArcRegistries backend not installed — call ArcBootstrap.install() first")

    /**
     * Open a registry by its Minecraft ID for bulk editing.
     * The registry is automatically unfrozen before [block] and re-frozen after.
     *
     * @param registryId  e.g. `"minecraft:sound_event"`, `"minecraft:attribute"`
     */
    public fun edit(registryId: String, block: RegistryEditScope.() -> Unit) {
        val backend = requireBackend()
        val nms = backend.nmsRegistry(registryId)
            ?: error("Registry not found: $registryId")
        backend.unfreeze(nms)
        try {
            RegistryEditScope(registryId, nms, backend).block()
        } finally {
            backend.refreeze(nms)
        }
    }

    /** Shorthand for known registry IDs. */
    public fun soundEvents(block: RegistryEditScope.() -> Unit) = edit("minecraft:sound_event", block)
    public fun attributes(block: RegistryEditScope.() -> Unit) = edit("minecraft:attribute", block)
    public fun mobEffects(block: RegistryEditScope.() -> Unit) = edit("minecraft:mob_effect", block)
    public fun enchantments(block: RegistryEditScope.() -> Unit) = edit("minecraft:enchantment", block)
    public fun entityTypes(block: RegistryEditScope.() -> Unit) = edit("minecraft:entity_type", block)
    public fun items(block: RegistryEditScope.() -> Unit) = edit("minecraft:item", block)
    public fun blocks(block: RegistryEditScope.() -> Unit) = edit("minecraft:block", block)
    public fun potions(block: RegistryEditScope.() -> Unit) = edit("minecraft:potion", block)
    public fun particles(block: RegistryEditScope.() -> Unit) = edit("minecraft:particle_type", block)
    public fun damageTypes(block: RegistryEditScope.() -> Unit) = edit("minecraft:damage_type", block)

    /** Sync all client-bound registries to connected players after edits. */
    public fun syncToClients(): Unit = requireBackend().syncToClients()
}

// ---------------------------------------------------------------------------
// Edit scope DSL
// ---------------------------------------------------------------------------


/**
 * Scope available inside [ArcRegistries.edit] blocks.
 * All mutations are applied to the *same* (already-unfrozen) registry instance.
 */
@RegistryDsl
public class RegistryEditScope(
    public val registryId: String,
    @PublishedApi internal val nmsRegistry: Any,
    @PublishedApi internal val backend: RegistryBackend,
) {
    /**
     * Register [nmsValue] under [key].
     * Use [createSoundEvent], [createAttribute], etc. to build the NMS object,
     * or supply a raw NMS instance for advanced cases.
     */
    public fun register(key: NamespacedKey, nmsValue: Any): Any? =
        backend.register(nmsRegistry, key, nmsValue)

    /** Remove the entry for [key]. Returns true if it existed. */
    public fun unregister(key: NamespacedKey): Boolean =
        backend.unregister(nmsRegistry, key)

    /** True if [key] is already registered. */
    public fun has(key: NamespacedKey): Boolean =
        backend.contains(nmsRegistry, key)

    /** All currently registered keys in this registry. */
    public val keys: Set<String> get() = backend.keys(nmsRegistry)

    // ---- Typed factories (delegate to backend) ----

    /** Build an NMS `SoundEvent`. Pass [fixedRange] for a fixed-attenuation sound. */
    public fun createSoundEvent(key: NamespacedKey, fixedRange: Float? = null): Any =
        backend.createSoundEvent(key, fixedRange)

    /** Build an NMS `RangedAttribute`. */
    public fun createAttribute(
        descriptionKey: String,
        defaultValue: Double = 0.0,
        min: Double = 0.0,
        max: Double = Double.MAX_VALUE,
        syncable: Boolean = false,
    ): Any = backend.createAttribute(descriptionKey, defaultValue, min, max, syncable)

    // ---- Convenience one-liners ----

    /**
     * Register a new sound event. Returns the NMS holder.
     * ```kotlin
     * ArcRegistries.soundEvents {
     *     soundEvent(NamespacedKey("myplugin", "sword_swing"))
     * }
     * ```
     */
    public fun soundEvent(key: NamespacedKey, fixedRange: Float? = null): Any? =
        register(key, createSoundEvent(key, fixedRange))

    /**
     * Register a new ranged attribute.
     * ```kotlin
     * ArcRegistries.attributes {
     *     attribute(NamespacedKey("myplugin", "max_mana"), "myplugin.attribute.max_mana",
     *         defaultValue = 100.0, min = 0.0, max = 10_000.0, syncable = true)
     * }
     * ```
     */
    public fun attribute(
        key: NamespacedKey,
        descriptionKey: String,
        defaultValue: Double = 0.0,
        min: Double = 0.0,
        max: Double = Double.MAX_VALUE,
        syncable: Boolean = false,
    ): Any? = register(key, createAttribute(descriptionKey, defaultValue, min, max, syncable))
}
