package dev.arc.api.control

import dev.arc.api.Arc
import dev.arc.api.command.command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin

/**
 * Registers a built-in `/arc` admin command to inspect and control Arc at
 * runtime (feature flags + settings). Permission: `arc.admin`.
 *
 * ```
 * /arc features                       list flags and their state
 * /arc settings                       list tunables
 * /arc enable  arc:nms.blocks         flip a flag on
 * /arc disable arc:tick-dispatcher    flip a flag off
 * /arc set tickBudgetMillis 4         retune a setting
 * ```
 */
object ArcControlCommand {

    private val SETTING_KEYS = listOf(
        "tickBudgetMillis",
        "defaultTtlMillis",
        "defaultPoolSize",
        "regionBatchConcurrency",
        "dispatcherMaxPending",
        "nmsThreadPolicy",
    )

    fun register(plugin: Plugin) {
        register(plugin.name)
    }

    /** Register from a server-integrated module without requiring a plugin instance. */
    fun register(fallbackPrefix: String = "arc") {
        command("arc", fallbackPrefix) {
            description = "Inspect and control Arc features and settings"
            permission = "arc.admin"
            usage = "/arc <features|settings|nms|enable|disable|set>"

            executes { sender, args ->
                when (args.getOrNull(0)?.lowercase()) {
                    "features" -> {
                        sender.sendMessage("Arc features:")
                        Arc.features.all().sortedBy { it.id }.forEach {
                            sender.sendMessage(" - ${it.id} = ${it.isEnabled}")
                        }
                    }

                    "settings" -> {
                        sender.sendMessage("Arc settings:")
                        sender.sendMessage(" - tickBudgetMillis = ${Arc.settings.tickBudgetMillis}")
                        sender.sendMessage(" - defaultTtlMillis = ${Arc.settings.defaultTtlMillis}")
                        sender.sendMessage(" - defaultPoolSize = ${Arc.settings.defaultPoolSize}")
                        sender.sendMessage(" - regionBatchConcurrency = ${Arc.settings.regionBatchConcurrency}")
                        sender.sendMessage(" - dispatcherMaxPending = ${Arc.settings.dispatcherMaxPending}")
                        sender.sendMessage(" - nmsThreadPolicy = ${Arc.settings.nmsThreadPolicy}")
                    }

                    "nms" -> nms(sender, args.getOrNull(1))
                    "enable" -> toggle(sender, args.getOrNull(1), true)
                    "disable" -> toggle(sender, args.getOrNull(1), false)
                    "set" -> set(sender, args.getOrNull(1), args.getOrNull(2))

                    else -> sender.sendMessage(
                        "/arc <features|settings|nms [clear-cache]|enable|disable|set>"
                    )
                }
                true
            }

            completes { _, args ->
                when (args.size) {
                    1 -> listOf("features", "settings", "nms", "enable", "disable", "set")
                    2 -> when (args[0].lowercase()) {
                        "enable", "disable" -> Arc.features.all().map { it.id }
                        "set" -> SETTING_KEYS
                        "nms" -> listOf("capabilities", "clear-cache")
                        else -> emptyList()
                    }
                    3 -> if (args[0].equals("set", ignoreCase = true) &&
                        args[1].equals("nmsThreadPolicy", ignoreCase = true)
                    ) {
                        NmsThreadPolicy.entries.map { it.name.lowercase() }
                    } else {
                        emptyList()
                    }
                    else -> emptyList()
                }
            }
        }
    }

    private fun toggle(sender: CommandSender, id: String?, enabled: Boolean) {
        if (id == null) {
            sender.sendMessage("usage: /arc ${if (enabled) "enable" else "disable"} <id>")
            return
        }
        if (Arc.features.get(id) == null) {
            sender.sendMessage("unknown feature: $id")
            return
        }
        Arc.features.setEnabled(id, enabled)
        sender.sendMessage("${if (enabled) "Enabled" else "Disabled"} $id")
    }

    private fun nms(sender: CommandSender, action: String?) {
        if (!Arc.isReady) {
            sender.sendMessage("Arc NMS bridge is not installed")
            return
        }
        if (action == "clear-cache") {
            sender.sendMessage(
                if (Arc.nms.server.clearReflectionCaches()) {
                    "Arc NMS reflection caches cleared"
                } else {
                    "Arc NMS provider does not support cache clearing"
                },
            )
            return
        }
        if (action == "capabilities") {
            val capabilities = Arc.nms.server.probeCapabilities()
            sender.sendMessage("Arc NMS capabilities:")
            sender.sendMessage(" - available = ${capabilities.available.sorted().joinToString()}")
            sender.sendMessage(" - unavailable = ${capabilities.unavailable.sorted().joinToString()}")
            return
        }

        val diagnostics = Arc.nms.server.diagnostics()
        sender.sendMessage("Arc NMS diagnostics:")
        sender.sendMessage(" - implementation = ${diagnostics.implementation}")
        sender.sendMessage(
            " - cache = classes:${diagnostics.cachedClasses} methods:${diagnostics.cachedMethods} " +
                "fields:${diagnostics.cachedFields} constructors:${diagnostics.cachedConstructors}",
        )
        sender.sendMessage(
            " - handles:${diagnostics.cachedMethodHandles} hits:${diagnostics.cacheHits} " +
                "misses:${diagnostics.cacheMisses}",
        )
        sender.sendMessage(
            " - resolutionFailures:${diagnostics.resolutionFailures} " +
                "invocationFailures:${diagnostics.invocationFailures}",
        )
        sender.sendMessage(" - rejectedThreadAccesses = ${diagnostics.rejectedThreadAccesses}")
    }

    private fun set(sender: CommandSender, key: String?, rawValue: String?) {
        if (key == "nmsThreadPolicy") {
            val policy = NmsThreadPolicy.entries.firstOrNull {
                it.name.equals(rawValue, ignoreCase = true)
            }
            if (policy == null) {
                sender.sendMessage("usage: /arc set nmsThreadPolicy <strict|warn|unsafe>")
                return
            }
            Arc.settings.nmsThreadPolicy = policy
            sender.sendMessage("Set nmsThreadPolicy = $policy")
            return
        }

        val value = rawValue?.toLongOrNull()
        if (key == null || value == null || value < 0) {
            sender.sendMessage("usage: /arc set <${SETTING_KEYS.joinToString("|")}> <non-negative number>")
            return
        }
        when (key) {
            "tickBudgetMillis" -> Arc.settings.tickBudgetMillis = value
            "defaultTtlMillis" -> Arc.settings.defaultTtlMillis = value
            "defaultPoolSize", "regionBatchConcurrency", "dispatcherMaxPending" -> {
                if (value > Int.MAX_VALUE) {
                    sender.sendMessage("$key must be <= ${Int.MAX_VALUE}")
                    return
                }
                when (key) {
                    "defaultPoolSize" -> Arc.settings.defaultPoolSize = value.toInt()
                    "regionBatchConcurrency" -> Arc.settings.regionBatchConcurrency = value.toInt()
                    else -> Arc.settings.dispatcherMaxPending = value.toInt()
                }
            }
            else -> {
                sender.sendMessage("unknown setting: $key")
                return
            }
        }
        sender.sendMessage("Set $key = $value")
    }
}
