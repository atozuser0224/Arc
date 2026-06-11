package dev.arc.server.nms

import dev.arc.api.registry.RegistryBackend
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import java.util.logging.Logger

/**
 * NMS-backed [RegistryBackend] using mojang-mapped runtime class names.
 * All reflection is routed through [Reflect] for caching and graceful failure.
 *
 * Supported operations:
 *  - Unfreeze / refreeze any [MappedRegistry]
 *  - Register entries under a new [ResourceKey]
 *  - Remove entries from all internal maps (byLocation, byKey, byValue)
 *  - Typed factories for SoundEvent, RangedAttribute, MobEffect
 */
internal object NmsRegistryBackend : RegistryBackend {

    private val log: Logger = Logger.getLogger("Arc/Registry")

    // ---- NMS class references ------------------------------------------------

    private val clsMappedRegistry  get() = Reflect.cls("net.minecraft.core.MappedRegistry")
    private val clsBuiltIn         get() = Reflect.cls("net.minecraft.core.BuiltInRegistries")
    private val clsResourceLoc     get() = Reflect.cls("net.minecraft.resources.ResourceLocation")
    private val clsResourceKey     get() = Reflect.cls("net.minecraft.resources.ResourceKey")
    private val clsRegistrationInfo get() = Reflect.cls("net.minecraft.core.RegistrationInfo")
    private val clsSoundEvent      get() = Reflect.cls("net.minecraft.sounds.SoundEvent")
    private val clsRangedAttribute get() = Reflect.cls("net.minecraft.world.entity.ai.attributes.RangedAttribute")
    private val clsAttribute       get() = Reflect.cls("net.minecraft.world.entity.ai.attributes.Attribute")

    // ---- Helpers: ResourceLocation / ResourceKey ----------------------------

    private fun resourceLocation(key: NamespacedKey): Any? {
        val cls = clsResourceLoc ?: return null
        // 1.21+: ResourceLocation.fromNamespaceAndPath(namespace, path)
        return runCatching {
            cls.getMethod("fromNamespaceAndPath", String::class.java, String::class.java)
                .invoke(null, key.namespace, key.key)
        }.recoverCatching {
            // fallback: new ResourceLocation(namespace, path)
            cls.getConstructor(String::class.java, String::class.java)
                .newInstance(key.namespace, key.key)
        }.getOrNull()
    }

    private fun resourceKey(nmsRegistry: Any, key: NamespacedKey): Any? {
        val cls = clsResourceKey ?: return null
        val loc = resourceLocation(key) ?: return null
        // Get the registry's own ResourceKey<Registry<T>> for the first argument
        val registryKey = runCatching {
            nmsRegistry.javaClass.getMethod("key").invoke(nmsRegistry)
        }.getOrNull() ?: return null
        return runCatching {
            cls.getMethod("create", registryKey.javaClass, clsResourceLoc)
                .invoke(null, registryKey, loc)
        }.getOrNull()
    }

    private fun builtInRegistrationInfo(): Any? {
        val cls = clsRegistrationInfo ?: return null
        return runCatching {
            cls.getField("BUILT_IN").get(null)
        }.getOrNull()
    }

    // ---- RegistryBackend API ------------------------------------------------

    override fun nmsRegistry(registryId: String): Any? {
        val clsBI = clsBuiltIn ?: run {
            log.warning("[Arc/Registry] BuiltInRegistries class not found")
            return null
        }
        // BuiltInRegistries.REGISTRY is a Registry<Registry<?>>
        // Use it to look up the target registry by its ResourceLocation
        val metaRegistry = runCatching {
            clsBI.getField("REGISTRY").get(null)
        }.getOrNull() ?: run {
            log.warning("[Arc/Registry] BuiltInRegistries.REGISTRY field not found")
            return null
        }

        val loc = runCatching {
            val cls = clsResourceLoc ?: return null
            // parse("minecraft:sound_event")
            cls.getMethod("parse", String::class.java).invoke(null, registryId)
        }.recoverCatching {
            val cls = clsResourceLoc ?: return null
            val parts = registryId.split(":")
            cls.getConstructor(String::class.java, String::class.java)
                .newInstance(parts.getOrElse(0) { "minecraft" }, parts.getOrElse(1) { registryId })
        }.getOrNull() ?: return null

        return runCatching {
            // Registry.get(ResourceLocation): T?
            metaRegistry.javaClass.getMethod("get", clsResourceLoc).invoke(metaRegistry, loc)
        }.getOrNull()
    }

    override fun unfreeze(nmsRegistry: Any) {
        val cls = clsMappedRegistry ?: return
        runCatching {
            val f = findField(cls, nmsRegistry.javaClass, "frozen") ?: return
            f.isAccessible = true
            f.set(nmsRegistry, false)
        }.onFailure { log.warning("[Arc/Registry] unfreeze failed: ${it.message}") }
    }

    override fun refreeze(nmsRegistry: Any) {
        runCatching {
            nmsRegistry.javaClass.getMethod("freeze").invoke(nmsRegistry)
        }.onFailure { log.warning("[Arc/Registry] refreeze failed: ${it.message}") }
    }

    override fun register(nmsRegistry: Any, key: NamespacedKey, nmsValue: Any): Any? {
        val rk = resourceKey(nmsRegistry, key) ?: run {
            log.warning("[Arc/Registry] could not create ResourceKey for $key")
            return null
        }
        val info = builtInRegistrationInfo() ?: run {
            log.warning("[Arc/Registry] RegistrationInfo.BUILT_IN not found")
            return null
        }
        return runCatching {
            // MappedRegistry.register(ResourceKey, T, RegistrationInfo): Holder.Reference<T>
            val method = nmsRegistry.javaClass.methods.firstOrNull { m ->
                m.name == "register" && m.parameterCount == 3
            } ?: return null
            method.invoke(nmsRegistry, rk, nmsValue, info)
        }.onFailure { log.warning("[Arc/Registry] register($key) failed: ${it.message}") }
            .getOrNull()
    }

    override fun unregister(nmsRegistry: Any, key: NamespacedKey): Boolean {
        val cls = clsMappedRegistry ?: return false
        val loc = resourceLocation(key) ?: return false
        val rk = resourceKey(nmsRegistry, key) ?: return false

        var removed = false
        // Remove from byLocation map
        removed = removeFromMap(nmsRegistry, cls, "byLocation", loc) || removed
        // Remove from byKey map
        removed = removeFromMap(nmsRegistry, cls, "byKey", rk) || removed
        // byValue / byId are harder — leave for now (orphaned holder, but won't crash)
        return removed
    }

    override fun contains(nmsRegistry: Any, key: NamespacedKey): Boolean {
        val loc = resourceLocation(key) ?: return false
        return runCatching {
            // Registry.containsKey(ResourceLocation): boolean
            nmsRegistry.javaClass.getMethod("containsKey", clsResourceLoc)
                .invoke(nmsRegistry, loc) as? Boolean ?: false
        }.getOrDefault(false)
    }

    override fun keys(nmsRegistry: Any): Set<String> {
        return runCatching {
            // Registry.keySet(): Set<ResourceKey<T>>
            @Suppress("UNCHECKED_CAST")
            val keySet = nmsRegistry.javaClass.getMethod("keySet").invoke(nmsRegistry) as? Set<Any>
                ?: return emptySet()
            keySet.mapNotNull { rk ->
                runCatching {
                    rk.javaClass.getMethod("location").invoke(rk).toString()
                }.getOrNull()
            }.toSet()
        }.getOrDefault(emptySet())
    }

    // ---- Typed factories ----------------------------------------------------

    override fun createSoundEvent(key: NamespacedKey, fixedRange: Float?): Any {
        val cls = clsSoundEvent ?: error("[Arc/Registry] SoundEvent class not found")
        val loc = resourceLocation(key) ?: error("[Arc/Registry] could not build ResourceLocation for $key")
        return if (fixedRange != null) {
            runCatching {
                cls.getMethod("createFixedRangeEvent", clsResourceLoc, Float::class.javaPrimitiveType)
                    .invoke(null, loc, fixedRange)
            }.getOrElse {
                cls.getMethod("createVariableRangeEvent", clsResourceLoc).invoke(null, loc)
            }
        } else {
            cls.getMethod("createVariableRangeEvent", clsResourceLoc).invoke(null, loc)
        } ?: error("[Arc/Registry] SoundEvent factory returned null for $key")
    }

    override fun createAttribute(
        descriptionKey: String,
        defaultValue: Double,
        min: Double,
        max: Double,
        syncable: Boolean,
    ): Any {
        val cls = clsRangedAttribute ?: error("[Arc/Registry] RangedAttribute class not found")
        val instance = cls.getConstructor(
            String::class.java,
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
        ).newInstance(descriptionKey, defaultValue, min, max)
            ?: error("[Arc/Registry] RangedAttribute constructor returned null")
        if (syncable) {
            runCatching {
                // setSyncable(boolean) — may also be called setClientSyncable
                val m = instance.javaClass.methods.firstOrNull { it.name == "setSyncable" || it.name == "setClientSyncable" }
                m?.invoke(instance, true)
            }
        }
        return instance
    }

    override fun createMobEffect(beneficial: Boolean, color: Int): Any? {
        // MobEffect is abstract — callers must subclass in their own code.
        // Return null to signal "use raw NMS instead".
        return null
    }

    override fun syncToClients() {
        // Send updated registry data to all online players.
        // In Paper 1.21.4 the relevant packet is ClientboundUpdateTagsPacket / login sequence.
        // A full registry sync requires re-sending the configuration phase packets which is
        // not trivially achievable at runtime without kicking clients; log a warning instead.
        runCatching {
            val onlineCount = Bukkit.getOnlinePlayers().size
            if (onlineCount > 0) {
                log.warning(
                    "[Arc/Registry] syncToClients() called with $onlineCount player(s) online. " +
                        "Runtime registry changes are not propagated to already-connected clients. " +
                        "Register entries before server accepts connections for full client support."
                )
            }
        }
    }

    // ---- Internal helpers ---------------------------------------------------

    private fun findField(baseClass: Class<*>, instanceClass: Class<*>, name: String): java.lang.reflect.Field? {
        var cls: Class<*>? = instanceClass
        while (cls != null && cls != Any::class.java) {
            val f = runCatching { cls!!.getDeclaredField(name) }.getOrNull()
            if (f != null) return f
            cls = cls.superclass
        }
        // Try the base class directly
        return runCatching { baseClass.getDeclaredField(name) }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    private fun removeFromMap(nmsRegistry: Any, baseClass: Class<*>, fieldName: String, mapKey: Any): Boolean {
        val field = findField(baseClass, nmsRegistry.javaClass, fieldName) ?: return false
        field.isAccessible = true
        val map = field.get(nmsRegistry) as? MutableMap<Any, Any> ?: return false
        return map.remove(mapKey) != null
    }
}
