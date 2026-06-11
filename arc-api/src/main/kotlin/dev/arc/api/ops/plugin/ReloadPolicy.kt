package dev.arc.api.ops.plugin

import dev.arc.api.lifecycle.ReloadRating
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * Per-plugin reload policy. Operators can set minimum safety rating required
 * for reload, or block reload entirely per plugin.
 *
 * Stored in `arc-ops/reload-policy.yml`.
 *
 * `/arc reload-policy [list|set <plugin> <allow|warn|block>]`
 */
object ReloadPolicy {

    private val policyFile = File("arc-ops/reload-policy.yml")
    private var policies: MutableMap<String, Entry> = LinkedHashMap()

    enum class Entry {
        ALLOW,  // allow reload at any rating
        WARN,   // warn but allow (default for non-Arc plugins)
        BLOCK,  // never allow reload
    }

    init { load() }

    fun load() {
        if (!policyFile.isFile) { save(); return }
        val yml = YamlConfiguration.loadConfiguration(policyFile)
        policies.clear()
        for (key in yml.getKeys(false)) {
            val raw = yml.getString(key) ?: "WARN"
            policies[key] = runCatching { Entry.valueOf(raw.uppercase()) }.getOrDefault(Entry.WARN)
        }
    }

    fun save() {
        val yml = YamlConfiguration()
        yml.options().setHeader(listOf(
            "Arc Reload Policy — per-plugin reload permission.",
            "Values: ALLOW (always), WARN (default, confirm required), BLOCK (never)",
        ))
        policies.forEach { (name, entry) -> yml.set(name, entry.name) }
        policyFile.parentFile?.mkdirs()
        yml.save(policyFile)
    }

    fun get(pluginName: String): Entry = policies[pluginName] ?: Entry.WARN

    fun set(pluginName: String, entry: Entry) {
        policies[pluginName] = entry
        save()
    }

    fun canReload(pluginName: String, rating: ReloadRating): Boolean {
        return when (get(pluginName)) {
            Entry.ALLOW -> true
            Entry.WARN -> rating != ReloadRating.UNSAFE
            Entry.BLOCK -> false
        }
    }

    fun complete(args: Array<out String>): List<String> = when (args.size) {
        2 -> listOf("list", "set")
        3 -> when (args.getOrNull(1)?.lowercase()) {
            "set" -> Bukkit.getPluginManager().plugins.map { it.name }
            else -> emptyList()
        }
        4 -> when (args.getOrNull(1)?.lowercase()) {
            "set" -> listOf("allow", "warn", "block")
            else -> emptyList()
        }
        else -> emptyList()
    }

    fun dispatch(sender: CommandSender, args: Array<out String>): Boolean {
        when (args.getOrNull(1)?.lowercase()) {
            "list" -> {
                if (policies.isEmpty()) { sender.sendMessage("[Arc] No custom reload policies set (default: WARN for all)"); return true }
                sender.sendMessage("[Arc] Reload policies:")
                policies.forEach { (name, entry) ->
                    val tag = when (entry) { Entry.ALLOW -> "§aALLOW"; Entry.WARN -> "§eWARN"; Entry.BLOCK -> "§cBLOCK" }
                    sender.sendMessage("  §e$name §7→ $tag")
                }
            }
            "set" -> {
                val name = args.getOrNull(2)
                val raw = args.getOrNull(3)?.uppercase()
                if (name == null || raw == null) { sender.sendMessage("/arc reload-policy set <plugin> <allow|warn|block>"); return true }
                val entry = runCatching { Entry.valueOf(raw) }.getOrNull()
                if (entry == null) { sender.sendMessage("[Arc] invalid policy: $raw (use allow, warn, block)"); return true }
                set(name, entry)
                sender.sendMessage("[Arc] §aReload policy for $name set to $entry")
            }
            else -> { sender.sendMessage("/arc reload-policy <list|set <plugin> <allow|warn|block>>"); return true }
        }
        return true
    }
}
