package dev.arc.api.control

import dev.arc.api.Arc
import dev.arc.api.command.command
import dev.arc.api.ops.ArcOps
import dev.arc.api.ops.ArcOpsCommand
import dev.arc.api.ops.audit.AuditLog
import dev.arc.api.ops.plugin.ConfirmManager
import org.bukkit.Bukkit
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
            description("Inspect and control Arc features and settings")
            permission("arc.admin")
            usage("/arc <help|version|features|settings|nms|enable|disable|set|reload|save|" +
                "doctor|lagspike|plugin-cost|config|chunks|entity|profiler|pregen|memory|ping|status|" +
                "plugin|command|permission|world|logs|paste|safe-mode|maintenance>")

            execute { ctx ->
                val sender = ctx.sender
                val args = ctx.args
                when (args.getOrNull(0)?.lowercase()) {
                    "help"    -> showHelp(sender)
                    "version" -> showVersion(sender)
                    "restart" -> doRestart(sender, args)
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
                    "confirm" -> {
                        val token = args.getOrNull(1)
                        if (token == null) {
                            sender.sendMessage("usage: /arc confirm <token>")
                        } else if (!ConfirmManager.confirm(sender, token)) {
                            sender.sendMessage("[Arc] unknown or expired token: $token")
                        }
                    }
                    "reload" -> {
                        runCatching { Arc.config.reload() }.fold(
                            onSuccess = { sender.sendMessage("Arc config reloaded.") },
                            onFailure = { sender.sendMessage("Reload failed: ${it.message}") },
                        )
                        if (ArcOps.installed) {
                            runCatching { ArcOps.reload() }.fold(
                                onSuccess = { sender.sendMessage("Arc ops config reloaded.") },
                                onFailure = { sender.sendMessage("Ops reload failed: ${it.message}") },
                            )
                        }
                    }
                    "save" -> {
                        runCatching { Arc.config.save() }.fold(
                            onSuccess = { sender.sendMessage("Arc config saved.") },
                            onFailure = { sender.sendMessage("Save failed: ${it.message}") },
                        )
                    }
                    "nms"     -> nms(sender, args.getOrNull(1))
                    "enable"  -> toggle(sender, args.getOrNull(1), true)
                    "disable" -> toggle(sender, args.getOrNull(1), false)
                    "set"     -> set(sender, args.getOrNull(1), args.getOrNull(2))
                    else      -> if (!ArcOpsCommand.dispatch(sender, args.toTypedArray())) {
                        sender.sendMessage("/arc help — see all commands")
                    }
                }
            }

            complete { ctx ->
                val args = ctx.args
                when (args.size) {
                    1 -> listOf("help", "version", "restart", "confirm", "features", "settings",
                        "nms", "enable", "disable", "set", "reload", "save") + ArcOpsCommand.roots
                    2 -> when (args[0].lowercase()) {
                        "enable", "disable" -> Arc.features.all().map { it.id }
                        "set"               -> SETTING_KEYS
                        "nms"               -> listOf("capabilities", "clear-cache")
                        else                -> ArcOpsCommand.complete(args.toTypedArray())
                    }
                    3 -> if (args[0].equals("set", ignoreCase = true) &&
                        args[1].equals("nmsThreadPolicy", ignoreCase = true)
                    ) {
                        NmsThreadPolicy.entries.map { it.name.lowercase() }
                    } else {
                        ArcOpsCommand.complete(args.toTypedArray())
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

    // ---- version / help / restart ----

    private fun showVersion(sender: CommandSender) {
        val pkg = Arc::class.java.`package`
        val arcVer = pkg?.implementationVersion ?: "dev"
        val mcVer = Bukkit.getMinecraftVersion()
        val bukkitVer = Bukkit.getBukkitVersion()
        sender.sendMessage("§eArc §f$arcVer §7(Minecraft $mcVer, $bukkitVer)")
        sender.sendMessage("§7Arc Bucket — Paper/Purpur compatible server")
    }

    private fun showHelp(sender: CommandSender) {
        sender.sendMessage("§e===== Arc Commands =====")
        sender.sendMessage("§e/arc help §7- This help")
        sender.sendMessage("§e/arc version §7- Show Arc version")
        sender.sendMessage("§e/arc features §7- List feature flags")
        sender.sendMessage("§e/arc settings §7- List tunables")
        sender.sendMessage("§e/arc reload §7- Reload Arc config")
        sender.sendMessage("")
        sender.sendMessage("§6--- Diagnostics ---")
        sender.sendMessage("§e/arc doctor §7- Server health report")
        sender.sendMessage("§e/arc lagspike [list|last] §7- Lag spike history")
        sender.sendMessage("§e/arc plugin-cost [top|<plugin>] §7- Plugin cost profiler")
        sender.sendMessage("§e/arc profiler [start|stop|report] §7- Main thread profiler")
        sender.sendMessage("§e/arc memory §7- Heap usage")
        sender.sendMessage("§e/arc ping §7- Player ping")
        sender.sendMessage("§e/arc issue-bundle §7- Gather diagnostic zip")
        sender.sendMessage("")
        sender.sendMessage("§6--- Plugin Management ---")
        sender.sendMessage("§e/arc sandbox check <jar> §7- Analyze JAR before loading")
        sender.sendMessage("§e/arc plugin list|info|check|enable|disable|reload|restart|dependents|reload-chain|leaks")
        sender.sendMessage("§e/arc plugin rollback <p> [--list|--restore] §7- Restore previous version")
        sender.sendMessage("§e/arc plugins report|outdated §7- Safety report & compatibility")
        sender.sendMessage("§e/arc reload-policy list|set §7- Per-plugin reload policy")
        sender.sendMessage("§e/arc plugin-overrides [list|exclude|include] §7- Feature excludes")
        sender.sendMessage("")
        sender.sendMessage("§6--- Server Tools ---")
        sender.sendMessage("§e/arc config get|set|reset|reload|diff|search §7- Config management")
        sender.sendMessage("§e/arc config-history [list|diff|search] §7- Who changed what")
        sender.sendMessage("§e/arc command search <kw> §7- Find commands")
        sender.sendMessage("§e/arc permission search <kw>|player §7- Permission inspection")
        sender.sendMessage("§e/arc world report [world] §7- World status")
        sender.sendMessage("§e/arc chunks [report|tickets] §7- Chunk diagnostics")
        sender.sendMessage("§e/arc logs [summary|errors|plugin] §7- Log inspection")
        sender.sendMessage("§e/arc paste [doctor|config|logs|plugin] §7- Upload to paste service")
        sender.sendMessage("")
        sender.sendMessage("§6--- Security & Maintenance ---")
        sender.sendMessage("§e/arc security-audit §7- Basic security checks")
        sender.sendMessage("§e/arc proxy-check §7- Validate proxy setup")
        sender.sendMessage("§e/arc audit [last|since|search] §7- Who ran dangerous commands")
        sender.sendMessage("§e/arc safe-mode [enable|disable] §7- Safe mode toggle")
        sender.sendMessage("§e/arc maintenance [on|off|allow|status] §7- Maintenance control")
        sender.sendMessage("§e/arc startup-profile [detail <p>] §7- Boot time analysis")
        sender.sendMessage("§e/arc restart §7- Restart the server")
        sender.sendMessage("")
        sender.sendMessage("§7All commands require §farc.admin §7permission")
    }

    private fun doRestart(sender: CommandSender, args: List<String>) {
        val token = ConfirmManager.stage(sender, "restart the server") {
            AuditLog.log(sender, "restart", "server restart")
            sender.sendMessage("[Arc] §cServer restarting...")
            Bukkit.spigot().restart()
        }
        sender.sendMessage("§eServer restart will kick all players and reload.")
        sender.sendMessage("§eType §f/arc confirm $token §eto proceed (expires in 30s)")
    }

    // ---- settings set helper ----

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
