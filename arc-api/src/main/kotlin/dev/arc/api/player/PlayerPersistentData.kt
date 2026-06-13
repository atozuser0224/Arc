@file:JvmName("PlayerPersistentDataKt")

package dev.arc.api.player

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin

/** Immutable typed handle for one PDC-backed player field. */
public class PlayerStore<P : Any, C : Any> internal constructor(
    public val key: NamespacedKey,
    public val type: PersistentDataType<P, C>,
    internal val defaultProvider: (() -> C)?,
    internal val validator: ((C) -> Boolean)?,
    internal val validationMessage: String,
    internal val legacyKeys: List<NamespacedKey>,
) {
    internal fun validate(value: C): C {
        require(validator?.invoke(value) != false) {
            "Invalid value for player field $key: $validationMessage"
        }
        return value
    }

    override fun equals(other: Any?): Boolean =
        other is PlayerStore<*, *> && key == other.key && type == other.type

    override fun hashCode(): Int = 31 * key.hashCode() + type.hashCode()
    override fun toString(): String = "PlayerStore($key)"
}

/** Policy builder for a [PlayerStore]. */
@PlayerDataDsl
public class PlayerStoreBuilder<P : Any, C : Any> internal constructor(
    private val plugin: Plugin,
) {
    private var defaultProvider: (() -> C)? = null
    private var validator: ((C) -> Boolean)? = null
    private var validationMessage: String = "validation failed"
    private val legacyKeys = ArrayList<NamespacedKey>()

    public fun default(provider: () -> C) {
        defaultProvider = provider
    }

    public fun validate(
        reason: String = "validation failed",
        predicate: (C) -> Boolean,
    ) {
        require(reason.isNotBlank()) { "validation reason cannot be blank" }
        validationMessage = reason
        validator = predicate
    }

    public fun migrateFrom(keyName: String) {
        val key = NamespacedKey(plugin, keyName)
        require(key !in legacyKeys) { "Legacy key already registered: $key" }
        legacyKeys += key
    }

    internal fun build(
        key: NamespacedKey,
        type: PersistentDataType<P, C>,
    ): PlayerStore<P, C> = PlayerStore(
        key = key,
        type = type,
        defaultProvider = defaultProvider,
        validator = validator,
        validationMessage = validationMessage,
        legacyKeys = legacyKeys.toList(),
    )
}

@DslMarker
public annotation class PlayerDataDsl

/** Create a basic typed player field. */
public fun <P : Any, C : Any> Plugin.playerStore(
    keyName: String,
    type: PersistentDataType<P, C>,
): PlayerStore<P, C> = playerStore(keyName, type) {}

/** Create a typed player field with defaults, validation, or migrations. */
public fun <P : Any, C : Any> Plugin.playerStore(
    keyName: String,
    type: PersistentDataType<P, C>,
    block: PlayerStoreBuilder<P, C>.() -> Unit,
): PlayerStore<P, C> {
    val key = NamespacedKey(this, keyName)
    return PlayerStoreBuilder<P, C>(this).apply(block).build(key, type)
}

/** Read the current value, lazily migrating a legacy key when necessary. */
public operator fun <P : Any, C : Any> Player.get(store: PlayerStore<P, C>): C? {
    val container = persistentDataContainer
    container.get(store.key, store.type)?.let { return it }
    for (legacyKey in store.legacyKeys) {
        val legacy = container.get(legacyKey, store.type) ?: continue
        val validated = store.validate(legacy)
        container.set(store.key, store.type, validated)
        container.remove(legacyKey)
        return validated
    }
    return null
}

/** Validate and persist a value. */
public operator fun <P : Any, C : Any> Player.set(
    store: PlayerStore<P, C>,
    value: C,
) {
    persistentDataContainer.set(store.key, store.type, store.validate(value))
}

/** Delete the current key. Legacy keys are intentionally left untouched. */
public operator fun <P : Any, C : Any> Player.minusAssign(store: PlayerStore<P, C>) {
    persistentDataContainer.remove(store.key)
}

/** Check whether the current or a configured legacy key exists. */
public operator fun <P : Any, C : Any> Player.contains(store: PlayerStore<P, C>): Boolean {
    val container = persistentDataContainer
    return container.has(store.key, store.type) ||
        store.legacyKeys.any { container.has(it, store.type) }
}

/** Read a value or initialize the definition's configured default. */
public fun <P : Any, C : Any> Player.getOrDefault(store: PlayerStore<P, C>): C {
    this[store]?.let { return it }
    val provider = store.defaultProvider
        ?: error("Player field ${store.key} has no configured default")
    return provider().also { this[store] = it }
}

/** Read a value or initialize it from [default]. */
public fun <P : Any, C : Any> Player.getOrPut(
    store: PlayerStore<P, C>,
    default: () -> C,
): C {
    this[store]?.let { return it }
    return default().also { this[store] = it }
}

/** Read, transform, validate, and persist a value. */
public fun <P : Any, C : Any> Player.mutate(
    store: PlayerStore<P, C>,
    fallback: C,
    transform: (C) -> C,
): C {
    val next = transform(this[store] ?: fallback)
    this[store] = next
    return next
}

/** Increment an integer field, initializing it to zero when absent. */
@JvmOverloads
public fun <P : Any> Player.increment(
    store: PlayerStore<P, Int>,
    amount: Int = 1,
): Int = mutate(store, 0) { Math.addExact(it, amount) }

/** A named collection of player field definitions. */
public class PlayerDataSchema internal constructor(
    private val fields: Map<String, PlayerStore<*, *>>,
) {
    public val size: Int get() = fields.size
    public val names: Set<String> get() = fields.keys

    public operator fun get(name: String): PlayerStore<*, *> =
        fields[name] ?: error("Unknown player data field: $name")

    public fun definitions(): List<PlayerStore<*, *>> = fields.values.toList()
}

/** Kotlin DSL scope for declaring multiple player fields. */
@PlayerDataDsl
public class PlayerDataSchemaBuilder internal constructor(
    private val plugin: Plugin,
) {
    private val fields = LinkedHashMap<String, PlayerStore<*, *>>()

    public fun int(
        name: String,
        block: PlayerStoreBuilder<Int, Int>.() -> Unit = {},
    ): PlayerStore<Int, Int> = register(
        name,
        plugin.playerStore(name, PersistentDataType.INTEGER, block),
    )

    public fun long(
        name: String,
        block: PlayerStoreBuilder<Long, Long>.() -> Unit = {},
    ): PlayerStore<Long, Long> = register(
        name,
        plugin.playerStore(name, PersistentDataType.LONG, block),
    )

    public fun double(
        name: String,
        block: PlayerStoreBuilder<Double, Double>.() -> Unit = {},
    ): PlayerStore<Double, Double> = register(
        name,
        plugin.playerStore(name, PersistentDataType.DOUBLE, block),
    )

    public fun string(
        name: String,
        block: PlayerStoreBuilder<String, String>.() -> Unit = {},
    ): PlayerStore<String, String> = register(
        name,
        plugin.playerStore(name, PersistentDataType.STRING, block),
    )

    public fun boolean(
        name: String,
        block: PlayerStoreBuilder<Byte, Boolean>.() -> Unit = {},
    ): PlayerStore<Byte, Boolean> = register(
        name,
        plugin.playerStore(name, PersistentDataType.BOOLEAN, block),
    )

    public fun byteArray(
        name: String,
        block: PlayerStoreBuilder<ByteArray, ByteArray>.() -> Unit = {},
    ): PlayerStore<ByteArray, ByteArray> = register(
        name,
        plugin.playerStore(name, PersistentDataType.BYTE_ARRAY, block),
    )

    private fun <P : Any, C : Any> register(
        name: String,
        store: PlayerStore<P, C>,
    ): PlayerStore<P, C> {
        require(name !in fields) { "Player data field already defined: $name" }
        fields[name] = store
        return store
    }

    internal fun build(): PlayerDataSchema = PlayerDataSchema(fields.toMap())
}

/** Declare a plugin-scoped schema of typed player PDC fields. */
public fun Plugin.playerDataSchema(
    block: PlayerDataSchemaBuilder.() -> Unit,
): PlayerDataSchema = PlayerDataSchemaBuilder(this).apply(block).build()
