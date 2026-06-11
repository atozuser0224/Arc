package dev.arc.api.nms

import org.bukkit.entity.Entity

/**
 * Reflective accessor for NMS entity fields.
 * Obtained via [Entity.nms]. All access is best-effort; failures return null silently.
 */
public class NmsRef internal constructor(private val entity: Entity) {

    private val handle: Any? by lazy {
        runCatching { entity.javaClass.getMethod("getHandle").invoke(entity) }.getOrNull()
    }

    public operator fun get(field: String): NmsValue {
        return NmsValue(handle, field)
    }

    public operator fun set(field: String, value: Any) {
        val h = handle ?: return
        runCatching {
            var cls: Class<*>? = h.javaClass
            while (cls != null) {
                val f = runCatching { cls!!.getDeclaredField(field) }.getOrNull()
                if (f != null) {
                    f.isAccessible = true
                    f.set(h, value)
                    return
                }
                cls = cls.superclass
            }
        }
    }
}

/** A lazily-read NMS field value. Retrieve with [unwrap]. */
public class NmsValue internal constructor(
    @PublishedApi internal val handle: Any?,
    @PublishedApi internal val field: String,
) {
    public inline fun <reified T : Any> unwrap(): T? {
        val h = handle ?: return null
        return runCatching {
            var cls: Class<*>? = h.javaClass
            while (cls != null) {
                val f = runCatching { cls!!.getDeclaredField(field) }.getOrNull()
                if (f != null) {
                    f.isAccessible = true
                    return@runCatching f.get(h) as? T
                }
                cls = cls.superclass
            }
            null
        }.getOrNull()
    }
}

/** Access the NMS handle of this entity for reflective field reads/writes via [NmsRef]. */
public val Entity.nms: NmsRef get() = NmsRef(this)
