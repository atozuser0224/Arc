package dev.arc.api.plugin

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Optional base class for plugins that use arc-api.
 *
 * Provides typed property delegates for cross-plugin service dependencies,
 * eliminating null checks and unchecked casts at the call site.
 *
 * Services are resolved lazily on first property access and cached. Plugins that
 * register via Bukkit's [org.bukkit.plugin.ServicesManager] (Vault/Economy,
 * LuckPerms, etc.) work out of the box.
 *
 * ```kotlin
 * class MyPlugin : ArcPlugin() {
 *     // Fails to enable if Economy service is absent (Vault not loaded)
 *     val economy: Economy by service()
 *
 *     // Null if PlaceholderAPI is not loaded — no NPE risk
 *     val papi: PlaceholderAPI? by optionalService()
 *
 *     override fun onEnable() {
 *         economy.depositPlayer(player, 100.0)   // typed, no cast
 *         papi?.registerExpansion(myExpansion)   // null-safe
 *     }
 * }
 * ```
 *
 * Extending this class is optional. All arc-api DSL functions (listen, command,
 * arcContent, etc.) work equally on plain [JavaPlugin] subclasses.
 */
abstract class ArcPlugin : JavaPlugin() {

    /**
     * Hard service dependency. Resolves via [org.bukkit.plugin.ServicesManager] on
     * first access. Throws [IllegalStateException] if the service is not registered,
     * which surfaces as an enable-time error rather than a silent NPE later.
     */
    protected inline fun <reified T : Any> service(): ReadOnlyProperty<ArcPlugin, T> =
        RequiredServiceDelegate(T::class.java)

    /**
     * Soft service dependency. Returns `null` if the service is absent instead of
     * throwing. Use when the feature depending on this service should degrade
     * gracefully rather than prevent the plugin from loading.
     */
    protected inline fun <reified T : Any> optionalService(): ReadOnlyProperty<ArcPlugin, T?> =
        OptionalServiceDelegate(T::class.java)
}

// ── Delegates ────────────────────────────────────────────────────────────────

@PublishedApi
internal class RequiredServiceDelegate<T : Any>(
    private val type: Class<T>,
) : ReadOnlyProperty<ArcPlugin, T> {

    @Volatile private var cache: T? = null

    override fun getValue(thisRef: ArcPlugin, property: KProperty<*>): T =
        cache ?: Bukkit.getServicesManager().load(type)
            ?.also { cache = it }
            ?: error(
                "Required service ${type.simpleName} is not registered. " +
                "Ensure the plugin providing it is listed in depend[] and is loaded."
            )
}

@PublishedApi
internal class OptionalServiceDelegate<T : Any>(
    private val type: Class<T>,
) : ReadOnlyProperty<ArcPlugin, T?> {

    @Volatile private var resolved = false
    @Volatile private var cache: T? = null

    override fun getValue(thisRef: ArcPlugin, property: KProperty<*>): T? {
        if (!resolved) {
            cache = Bukkit.getServicesManager().load(type)
            resolved = true
        }
        return cache
    }
}
