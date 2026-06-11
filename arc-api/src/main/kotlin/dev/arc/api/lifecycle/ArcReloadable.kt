package dev.arc.api.lifecycle

/**
 * Opt-in contract a plugin implements to cooperate with Arc's reload manager.
 *
 * A plain Paper plugin cannot be reloaded with any guarantee — Arc can only
 * *guess* whether its resources are tracked. A plugin that implements this
 * interface tells Arc explicitly whether it is currently safe to reload and how
 * to release its resources, which lets Arc grade it [ReloadRating.SAFE].
 *
 * Implementing this changes nothing about normal Bukkit behaviour; it is only
 * consulted by `/arc plugin reload`.
 */
interface ArcReloadable {

    /** Called before a reload to decide whether to proceed. Default: safe. */
    fun canReload(): ReloadCheckResult = ReloadCheckResult.safe()

    /** Invoked just before the plugin is disabled for reload. */
    fun beforeReload() {}

    /** Invoked just after the plugin is re-enabled. */
    fun afterReload() {}

    /**
     * Release every resource Arc cannot see: custom threads, DB pools, native
     * handles, static caches. Called during disable so the classloader can be
     * collected.
     */
    fun cleanupResources() {}
}

enum class ReloadRating { SAFE, WARNING, UNSAFE, UNKNOWN }

/** Result of [ArcReloadable.canReload]. */
class ReloadCheckResult private constructor(
    val rating: ReloadRating,
    val message: String?,
) {
    companion object {
        fun safe(): ReloadCheckResult = ReloadCheckResult(ReloadRating.SAFE, null)
        fun warning(message: String): ReloadCheckResult = ReloadCheckResult(ReloadRating.WARNING, message)
        fun unsafe(message: String): ReloadCheckResult = ReloadCheckResult(ReloadRating.UNSAFE, message)
    }
}
