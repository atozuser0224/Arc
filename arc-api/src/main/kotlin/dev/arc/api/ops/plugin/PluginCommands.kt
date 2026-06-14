package dev.arc.api.ops.plugin

import dev.arc.api.lifecycle.ReloadRating
import dev.arc.api.ops.audit.AuditLog
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin

/**
 * `/arc plugin *` subcommands wired into [ArcOpsCommand].
 * Uses existing [PluginLifecycle], [ReloadSafetyAnalyzer], [DependencyGraph],
 * [PluginInspector], and [ConfirmManager].
 */
object PluginCommands {
    private const val FORCE_PERMISSION = "arc.command.plugin.reload.force"

    fun roots() = listOf("plugin", "plugins")

    fun complete(args: Array<out String>): List<String> {
        val sub = args.getOrNull(1)?.lowercase() ?: return roots()
        return when {
            args.size == 2 -> listOf("list","info","check","enable","disable","reload",
                "restart","dependents","reload-chain","leaks","report","outdated","rollback","confirm","marketplace")
            args.size == 3 && sub in listOf("info","check","enable","disable","reload",
                "restart","dependents","reload-chain","leaks") ->
                Bukkit.getPluginManager().plugins.map { it.name }
            args.size == 3 && sub == "rollback" -> PluginRollbackManager.complete(args)
            args.size == 3 && sub == "marketplace" -> PluginMarketplace.complete(args)
            args.size == 3 && sub in listOf("reload-chain") ->
                Bukkit.getPluginManager().plugins.map { it.name }
            args.size >= 4 && sub == "reload-chain" ->
                listOf("--dry-run", "--apply", "--force").filterNot(args::contains)
            args.size == 4 && sub in listOf("reload", "restart", "reload-chain") ->
                listOf("--force")
            else -> emptyList()
        }
    }

    fun dispatch(sender: CommandSender, args: Array<out String>): Boolean {
        val sub = args.getOrNull(1)?.lowercase() ?: return false
        if (sub == "confirm" && args.size >= 2) {
            val token = args.getOrNull(2) ?: return false.also { sender.sendMessage("/arc confirm <code>") }
            return handleConfirm(sender, token)
        }
        if (sub == "rollback") return PluginRollbackManager.dispatch(sender, args)
        if (sub == "marketplace") return PluginMarketplace.dispatch(sender, args)
        return when (sub) {
            "list" -> handleList(sender)
            "info" -> handleInfo(sender, args.getOrNull(2))
            "check" -> handleCheck(sender, args.getOrNull(2))
            "enable" -> handleEnable(sender, args.getOrNull(2))
            "disable" -> handleDisable(sender, args.getOrNull(2))
            "reload" -> handleReload(sender, args.getOrNull(2), args.any { it == "--force" })
            "restart" -> handleRestart(sender, args.getOrNull(2), args.any { it == "--force" })
            "dependents" -> handleDependents(sender, args.getOrNull(2))
            "reload-chain" -> handleReloadChain(sender, args.getOrNull(2), args)
            "leaks" -> handleLeaks(sender, args.getOrNull(2))
            "report" -> handlePluginsReport(sender)
            "outdated" -> handleOutdated(sender)
            else -> false
        }
    }

    // ---- list ----
    private fun handleList(sender: CommandSender): Boolean {
        val plugins = Bukkit.getPluginManager().plugins
        sender.sendMessage("[Arc] ${plugins.size} plugin(s):")
        plugins.sortedBy { it.name }.forEach { p ->
            val state = if (p.isEnabled) "§aENABLED" else "§cDISABLED"
            val api = p.description.apiVersion ?: "legacy"
            sender.sendMessage("  §e${p.name} §7v${p.description.version} $state §7api=$api")
        }
        return true
    }

    // ---- info ----
    private fun handleInfo(sender: CommandSender, name: String?): Boolean {
        val plugin = resolvePlugin(sender, name) ?: return true
        PluginInspector.report(plugin).render().lineSequence().forEach { sender.sendMessage(it) }
        return true
    }

    // ---- check ----
    private fun handleCheck(sender: CommandSender, name: String?): Boolean {
        val plugin = resolvePlugin(sender, name) ?: return true
        val assessment = ReloadSafetyAnalyzer.assess(plugin)
        sender.sendMessage(assessment.render())
        if (assessment.rating == ReloadRating.UNSAFE) {
            sender.sendMessage("§c§lUse --force to reload anyway (requires arc.command.plugin.reload.force)")
        }
        return true
    }

    // ---- enable ----
    private fun handleEnable(sender: CommandSender, name: String?): Boolean {
        val plugin = resolvePlugin(sender, name) ?: return true
        if (plugin.isEnabled) { sender.sendMessage("[Arc] ${plugin.name} is already enabled"); return true }
        val result = PluginLifecycle.enable(plugin)
        sender.sendMessage(result.render())
        return true
    }

    // ---- disable ----
    private fun handleDisable(sender: CommandSender, name: String?): Boolean {
        val plugin = resolvePlugin(sender, name) ?: return true
        if (!plugin.isEnabled) { sender.sendMessage("[Arc] ${plugin.name} is already disabled"); return true }
        val dependents = DependencyGraph.directDependents(plugin.name).filter { it.isEnabled }
        if (dependents.isNotEmpty()) {
            sender.sendMessage("[Arc] §e${plugin.name} has ${dependents.size} enabled dependent(s): ${dependents.joinToString { it.name }}")
            val token = ConfirmManager.stage(sender, "disable ${plugin.name} (dependents will lose their dependency)") {
                AuditLog.log(sender, "plugin disable", "${plugin.name} (with dependents)")
                val result = PluginLifecycle.disable(plugin)
                sender.sendMessage(result.render())
            }
            sender.sendMessage("§eType /arc plugin confirm $token to proceed")
            return true
        }
        AuditLog.log(sender, "plugin disable", plugin.name)
        val result = PluginLifecycle.disable(plugin)
        sender.sendMessage(result.render())
        return true
    }

    // ---- reload ----
    private fun handleReload(sender: CommandSender, name: String?, force: Boolean): Boolean {
        val plugin = resolvePlugin(sender, name) ?: return true
        if (force && !sender.hasPermission(FORCE_PERMISSION)) {
            sender.sendMessage("[Arc] §cMissing permission: $FORCE_PERMISSION")
            return true
        }
        val assessment = ReloadSafetyAnalyzer.assess(plugin)

        if (assessment.rating == ReloadRating.UNSAFE && !force) {
            sender.sendMessage(assessment.render())
            sender.sendMessage("§c§lUNSAFE: use --force if you understand the risks")
            return true
        }

        val description = "reload ${plugin.name} (rating=${assessment.rating})"
        val token = ConfirmManager.stage(sender, description) {
            AuditLog.log(sender, "plugin reload", "${plugin.name} rating=${assessment.rating}")
            val result = PluginLifecycle.reload(plugin)
            sender.sendMessage(result.render())
        }
        sender.sendMessage("[Arc] Reload safety: §e${assessment.rating}")
        assessment.blockers.forEach { sender.sendMessage("  §c- $it") }
        assessment.warnings.forEach { sender.sendMessage("  §e- $it") }
        sender.sendMessage("§eType /arc plugin confirm $token to proceed")
        return true
    }

    // ---- restart ----
    private fun handleRestart(sender: CommandSender, name: String?, force: Boolean): Boolean {
        val plugin = resolvePlugin(sender, name) ?: return true
        if (force && !sender.hasPermission(FORCE_PERMISSION)) {
            sender.sendMessage("[Arc] §cMissing permission: $FORCE_PERMISSION")
            return true
        }
        val assessment = ReloadSafetyAnalyzer.assess(plugin)
        if (assessment.rating == ReloadRating.UNSAFE && !force) {
            sender.sendMessage(assessment.render())
            sender.sendMessage("§c§lUNSAFE: use --force if you understand the risks")
            return true
        }
        val description = "restart ${plugin.name} (rating=${assessment.rating})"

        val token = ConfirmManager.stage(sender, description) {
            AuditLog.log(sender, "plugin restart", "${plugin.name}")
            val result = PluginLifecycle.restart(plugin)
            sender.sendMessage(result.render())
        }
        sender.sendMessage("[Arc] Restart safety: §e${assessment.rating}")
        sender.sendMessage("§eType /arc plugin confirm $token to proceed")
        return true
    }

    // ---- dependents ----
    private fun handleDependents(sender: CommandSender, name: String?): Boolean {
        val plugin = resolvePlugin(sender, name) ?: return true
        val plan = DependencyGraph.chainPlan(plugin)
        sender.sendMessage(plan.render())
        return true
    }

    // ---- reload-chain ----
    private fun handleReloadChain(sender: CommandSender, name: String?, args: Array<out String>): Boolean {
        val plugin = resolvePlugin(sender, name) ?: return true
        val plan = DependencyGraph.chainPlan(plugin)
        val dryRun = args.any { it == "--dry-run" }
        val force = args.any { it == "--force" }
        val apply = args.any { it == "--apply" }
        if (force && !sender.hasPermission(FORCE_PERMISSION)) {
            sender.sendMessage("[Arc] §cMissing permission: $FORCE_PERMISSION")
            return true
        }

        if (dryRun || !apply) {
            sender.sendMessage(plan.render())
            // Show safety per plugin in chain
            for (n in plan.disableOrder) {
                val p = Bukkit.getPluginManager().getPlugin(n) ?: continue
                val a = ReloadSafetyAnalyzer.assess(p)
                sender.sendMessage("  $n: §e${a.rating}")
            }
            if (!apply) sender.sendMessage("§eAdd --apply to execute, --dry-run to only show this")
            return true
        }

        // Check for UNSAFE
        val unsafe = plan.disableOrder.mapNotNull { Bukkit.getPluginManager().getPlugin(it) }
            .filter { ReloadSafetyAnalyzer.assess(it).rating == ReloadRating.UNSAFE }
        if (unsafe.isNotEmpty() && !force) {
            sender.sendMessage("[Arc] §cUNSAFE plugins in chain: ${unsafe.joinToString { it.name }}. Use --force.")
            return true
        }

        val token = ConfirmManager.stage(sender, "reload-chain ${plan.disableOrder.joinToString(" → ")}") {
            AuditLog.log(sender, "reload-chain", plan.disableOrder.joinToString(" → "))
            val results = PluginLifecycle.reloadChain(plan)
            results.forEach { sender.sendMessage(it.render()) }
        }
        sender.sendMessage(plan.render())
        sender.sendMessage("§eType /arc plugin confirm $token to execute the chain")
        return true
    }

    // ---- leaks ----
    private fun handleLeaks(sender: CommandSender, name: String?): Boolean {
        val plugin = resolvePlugin(sender, name) ?: return true
        val threads = PluginInspector.threadsOwnedBy(plugin)
        sender.sendMessage("[Arc] Leak report for §e${plugin.name} §7(state=${if(plugin.isEnabled) "ENABLED" else "DISABLED"}):")

        if (threads.isEmpty()) {
            sender.sendMessage("  §aNo classloader-owned threads found")
        } else {
            sender.sendMessage("  §c${threads.size} thread(s) reference plugin classloader:")
            threads.forEach { sender.sendMessage("    §c- ${it.name} daemon=${it.daemon} state=${it.state}") }
        }

        val counts = PluginInspector.counts(plugin)
        if (!plugin.isEnabled) {
            if (counts.pendingTasks > 0) sender.sendMessage("  §c${counts.pendingTasks} pending task(s) still registered")
            if (counts.activeWorkers > 0) sender.sendMessage("  §c${counts.activeWorkers} active async worker(s)")
            if (counts.listeners > 0) sender.sendMessage("  §e${counts.listeners} listener(s) still registered")
        } else {
            sender.sendMessage("  §7tasks=${counts.pendingTasks} workers=${counts.activeWorkers} listeners=${counts.listeners}")
        }
        sender.sendMessage("  §7commands=${counts.commands} permissions=${counts.permissions} channels=${counts.incomingChannels}/${counts.outgoingChannels} services=${counts.services}")
        return true
    }

    // ---- plugins report ----
    private fun handlePluginsReport(sender: CommandSender): Boolean {
        val plugins = Bukkit.getPluginManager().plugins.sortedBy { it.name }
        sender.sendMessage("[Arc] Plugin Report (${plugins.size} total):")
        for (p in plugins) {
            val a = ReloadSafetyAnalyzer.assess(p)
            val tag = when (a.rating) {
                ReloadRating.SAFE -> "§aSAFE"
                ReloadRating.WARNING -> "§eWARN"
                ReloadRating.UNSAFE -> "§cUNSAFE"
                ReloadRating.UNKNOWN -> "§7???"
            }
            sender.sendMessage("  §e${p.name} §7v${p.description.version} ${if(p.isEnabled) "§aON" else "§cOFF"} reload=$tag")
        }
        return true
    }

    // ---- plugins outdated ----
    private fun handleOutdated(sender: CommandSender): Boolean {
        val plugins = Bukkit.getPluginManager().plugins
        val oldApi = plugins.filter {
            val v = it.description.apiVersion
            v != null && isApiVersionOlderThan(v, 1, 20)
        }
        val noApi = plugins.filter { it.description.apiVersion == null }

        sender.sendMessage("[Arc] Plugin compatibility check:")
        if (oldApi.isNotEmpty()) {
            sender.sendMessage("  §eOld api-version (<1.20):")
            oldApi.forEach { sender.sendMessage("    §e${it.name}: api=${it.description.apiVersion}") }
        }
        if (noApi.isNotEmpty()) {
            sender.sendMessage("  §7No api-version (legacy):")
            noApi.forEach { sender.sendMessage("    §7${it.name}") }
        }
        if (oldApi.isEmpty() && noApi.isEmpty()) {
            sender.sendMessage("  §aAll plugins declare modern api-version")
        }
        sender.sendMessage("  §7Note: api-version is a hint — some plugins work fine with older declarations")
        return true
    }

    internal fun isApiVersionOlderThan(version: String, major: Int, minor: Int): Boolean {
        val parts = version.substringBefore('-').split('.')
        val parsedMajor = parts.getOrNull(0)?.toIntOrNull() ?: return false
        val parsedMinor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return parsedMajor < major || parsedMajor == major && parsedMinor < minor
    }

    // ---- confirm handler ----
    private fun handleConfirm(sender: CommandSender, token: String): Boolean {
        val ok = ConfirmManager.confirm(sender, token)
        sender.sendMessage(if (ok) "[Arc] §aAction confirmed." else "[Arc] §cInvalid or expired token: $token")
        return true
    }

    private fun resolvePlugin(sender: CommandSender, name: String?): Plugin? {
        if (name == null) { sender.sendMessage("[Arc] specify a plugin name"); return null }
        val plugin = Bukkit.getPluginManager().getPlugin(name)
        if (plugin == null) sender.sendMessage("[Arc] plugin not found: $name")
        return plugin
    }
}
