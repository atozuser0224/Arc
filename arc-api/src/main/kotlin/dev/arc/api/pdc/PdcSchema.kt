@file:JvmName("PdcSchemas")

package dev.arc.api.pdc

import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataHolder
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.Locale

private val PDC_NAMESPACE = Regex("[a-z0-9._-]+")
private val PDC_KEY = Regex("[a-z0-9/._-]+")

/** Immutable typed field definition in a [PdcSchema]. */
public class PdcField<P : Any, C : Any> internal constructor(
    public val key: NamespacedKey,
    public val type: PersistentDataType<P, C>,
    private val defaultFactory: (() -> C)?,
    private val validationMessage: String,
    private val validator: (C) -> Boolean,
) {
    public val hasDefault: Boolean get() = defaultFactory != null

    public fun defaultValue(): C {
        val value = checkNotNull(defaultFactory) { "PDC field $key has no default value" }.invoke()
        return validate(value)
    }

    public fun validate(value: C): C {
        require(validator(value)) { "$validationMessage: $value" }
        return value
    }

    override fun toString(): String = "PdcField($key)"
}

/** Configures default and validation behavior for one field. */
@PdcSchemaDsl
public class PdcFieldBuilder<C : Any> internal constructor() {
    private var defaultFactory: (() -> C)? = null
    private var validationMessage: String = "Invalid PDC value"
    private var validator: (C) -> Boolean = { true }

    public fun default(value: C) {
        defaultFactory = { value }
    }

    public fun default(factory: () -> C) {
        defaultFactory = factory
    }

    public fun validate(message: String, predicate: (C) -> Boolean) {
        validationMessage = message
        validator = predicate
    }

    internal fun <P : Any> build(
        key: NamespacedKey,
        type: PersistentDataType<P, C>,
    ): PdcField<P, C> = PdcField(key, type, defaultFactory, validationMessage, validator)
}

/** Immutable registry of reusable PDC fields. */
public class PdcSchema internal constructor(
    public val namespace: String,
    fields: Map<String, PdcField<*, *>>,
) {
    private val fieldsByName: Map<String, PdcField<*, *>> = fields.toMap()

    public val size: Int get() = fieldsByName.size
    public val keys: Set<NamespacedKey> get() = fieldsByName.values.mapTo(linkedSetOf()) { it.key }

    public operator fun get(name: String): PdcField<*, *> =
        find(name) ?: error("Unknown PDC field: $name")

    public fun find(name: String): PdcField<*, *>? {
        val simpleName = if (':' in name) {
            val key = NamespacedKey.fromString(name) ?: return null
            if (key.namespace != namespace) return null
            key.value()
        } else {
            name
        }
        return fieldsByName[simpleName]
    }

    public fun fields(): List<PdcField<*, *>> = fieldsByName.values.toList()
}

@DslMarker
public annotation class PdcSchemaDsl

/** Definition scope for [PdcSchema]. */
@PdcSchemaDsl
public class PdcSchemaBuilder internal constructor(private val namespace: String) {
    private val fields = LinkedHashMap<String, PdcField<*, *>>()

    public fun byte(
        name: String,
        block: PdcFieldBuilder<Byte>.() -> Unit = {},
    ): PdcField<Byte, Byte> = field(name, PersistentDataType.BYTE, block)

    public fun short(
        name: String,
        block: PdcFieldBuilder<Short>.() -> Unit = {},
    ): PdcField<Short, Short> = field(name, PersistentDataType.SHORT, block)

    public fun int(
        name: String,
        block: PdcFieldBuilder<Int>.() -> Unit = {},
    ): PdcField<Int, Int> = field(name, PersistentDataType.INTEGER, block)

    public fun long(
        name: String,
        block: PdcFieldBuilder<Long>.() -> Unit = {},
    ): PdcField<Long, Long> = field(name, PersistentDataType.LONG, block)

    public fun float(
        name: String,
        block: PdcFieldBuilder<Float>.() -> Unit = {},
    ): PdcField<Float, Float> = field(name, PersistentDataType.FLOAT, block)

    public fun double(
        name: String,
        block: PdcFieldBuilder<Double>.() -> Unit = {},
    ): PdcField<Double, Double> = field(name, PersistentDataType.DOUBLE, block)

    public fun string(
        name: String,
        block: PdcFieldBuilder<String>.() -> Unit = {},
    ): PdcField<String, String> = field(name, PersistentDataType.STRING, block)

    public fun boolean(
        name: String,
        block: PdcFieldBuilder<Boolean>.() -> Unit = {},
    ): PdcField<Byte, Boolean> = field(name, PersistentDataType.BOOLEAN, block)

    public fun byteArray(
        name: String,
        block: PdcFieldBuilder<ByteArray>.() -> Unit = {},
    ): PdcField<ByteArray, ByteArray> = field(name, PersistentDataType.BYTE_ARRAY, block)

    public fun intArray(
        name: String,
        block: PdcFieldBuilder<IntArray>.() -> Unit = {},
    ): PdcField<IntArray, IntArray> = field(name, PersistentDataType.INTEGER_ARRAY, block)

    public fun longArray(
        name: String,
        block: PdcFieldBuilder<LongArray>.() -> Unit = {},
    ): PdcField<LongArray, LongArray> = field(name, PersistentDataType.LONG_ARRAY, block)

    public fun <P : Any, C : Any> custom(
        name: String,
        type: PersistentDataType<P, C>,
        block: PdcFieldBuilder<C>.() -> Unit = {},
    ): PdcField<P, C> = field(name, type, block)

    internal fun build(): PdcSchema = PdcSchema(namespace, fields)

    private fun <P : Any, C : Any> field(
        name: String,
        type: PersistentDataType<P, C>,
        block: PdcFieldBuilder<C>.() -> Unit,
    ): PdcField<P, C> {
        require(name.matches(PDC_KEY) && name.split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "Invalid PDC field name: $name"
        }
        require(name !in fields) { "Duplicate PDC field: $namespace:$name" }
        val field = PdcFieldBuilder<C>().apply(block)
            .build(NamespacedKey(namespace, name), type)
        fields[name] = field
        return field
    }
}

/** Build a schema for an explicit namespace. */
public fun pdcSchema(
    namespace: String,
    block: PdcSchemaBuilder.() -> Unit,
): PdcSchema {
    require(namespace.matches(PDC_NAMESPACE)) { "Invalid PDC namespace: $namespace" }
    return PdcSchemaBuilder(namespace).apply(block).build()
}

/** Build a schema using this plugin's normalized namespace. */
public fun Plugin.pdcSchema(block: PdcSchemaBuilder.() -> Unit): PdcSchema =
    pdcSchema(name.lowercase(Locale.ROOT), block)

/** Read a field, returning null when it is absent. */
public operator fun <P : Any, C : Any> PersistentDataHolder.get(field: PdcField<P, C>): C? =
    persistentDataContainer.get(field.key, field.type)

/** Validate and write a field. */
public operator fun <P : Any, C : Any> PersistentDataHolder.set(
    field: PdcField<P, C>,
    value: C,
) {
    persistentDataContainer.set(field.key, field.type, field.validate(value))
}

/** Remove a field. */
public operator fun <P : Any, C : Any> PersistentDataHolder.minusAssign(field: PdcField<P, C>) {
    persistentDataContainer.remove(field.key)
}

/** Check whether a field exists with its declared primitive type. */
public operator fun <P : Any, C : Any> PersistentDataHolder.contains(field: PdcField<P, C>): Boolean =
    persistentDataContainer.has(field.key, field.type)

/** Read a field or evaluate its declared default. */
public fun <P : Any, C : Any> PersistentDataHolder.getOrDefault(field: PdcField<P, C>): C =
    this[field] ?: field.defaultValue()

/** Read a field or create, validate, and persist a value. */
public fun <P : Any, C : Any> PersistentDataHolder.getOrPut(
    field: PdcField<P, C>,
    default: () -> C,
): C {
    this[field]?.let { return it }
    return field.validate(default()).also { this[field] = it }
}

/** Transform a present value or the field's declared default and persist it. */
public fun <P : Any, C : Any> PersistentDataHolder.mutate(
    field: PdcField<P, C>,
    transform: (C) -> C,
): C {
    val next = field.validate(transform(getOrDefault(field)))
    this[field] = next
    return next
}

/** Transform a present value or an explicit fallback and persist it. */
public fun <P : Any, C : Any> PersistentDataHolder.mutate(
    field: PdcField<P, C>,
    fallback: C,
    transform: (C) -> C,
): C {
    val next = field.validate(transform(this[field] ?: fallback))
    this[field] = next
    return next
}

/** Increment an integer field with overflow checking, initializing it to zero when absent. */
@JvmOverloads
public fun PersistentDataHolder.increment(
    field: PdcField<Int, Int>,
    amount: Int = 1,
): Int = mutate(field, 0) { Math.addExact(it, amount) }

/** Increment a long field with overflow checking, initializing it to zero when absent. */
@JvmOverloads
public fun PersistentDataHolder.increment(
    field: PdcField<Long, Long>,
    amount: Long = 1L,
): Long = mutate(field, 0L) { Math.addExact(it, amount) }
