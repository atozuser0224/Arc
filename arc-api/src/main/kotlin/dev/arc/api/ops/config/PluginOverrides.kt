package dev.arc.api.ops.config

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * Per-plugin exclusion from Arc features.
 *
 * `/arc plugin-overrides list | exclude <feature> <plugin> | include <feature> <plugin>`
 *
 * Features: entity-ai, density-guard, profiler, cost-tracker, thread-monitor, all
 * Stored in `arc-ops/plugin-overrides.yml`.
 */
object PluginOverrides {

    private val file = File("arc-ops/plugin-overrides.yml")
    private val overrides: MutableMap<String, MutableSet<String>> = LinkedHashMap() // feature -> pluginNames
    val features = listOf("entity-ai", "density-guard", "profiler", "cost-tracker", "thread-monitor", "all")

    init { load() }

    fun load() {
        if (!file.isFile) { save(); return }
        val yml = YamlConfiguration.loadConfiguration(file)
        overrides.clear()
        for (feature in yml.getKeys(false)) {
            overrides[feature] = LinkedHashSet(yml.getStringList(feature))
        }
    }

    fun save() {
        val yml = YamlConfiguration()
        yml.options().setHeader(listOf(
            "Arc Plugin Overrides — exclude specific plugins from specific Arc features.",
            "Features: ${features.joinToString()}. 'all' excludes from ALL Arc optimizations.",
        ))
        overrides.forEach { (f, names) -> yml.set(f, names.toList()) }
        file.parentFile?.mkdirs()
        yml.save(file)
    }

    fun isExcluded(pluginName: String, feature: String): Boolean {
        if (overrides["all"]?.contains(pluginName) == true) return true
        return overrides[feature]?.contains(pluginName) == true
    }

    fun complete(args: Array<out String>): List<String> = when (args.size) {
        2 -> listOf("list", "exclude", "include")
        3 -> when (args.getOrNull(1)?.lowercase()) {
            "exclude", "include" -> features
            else -> emptyList()
        }
        4 -> when (args.getOrNull(1)?.lowercase()) {
            "exclude", "include" -> Bukkit.getPluginManager().plugins.map { it.name }
            else -> emptyList()
        }
        else -> emptyList()
    }

    fun dispatch(sender: CommandSender, args: Array<out String>): Boolean {
        when (args.getOrNull(1)?.lowercase()) {
            "list" -> {
                if (overrides.isEmpty()) { sender.sendMessage("[Arc] No plugin overrides set"); return true }
                sender.sendMessage("[Arc] Plugin overrides:")
                overrides.forEach { (feature, names) ->
                    sender.sendMessage("  §e$feature: §7${names.joinToString()}")
                }
            }
            "exclude", "include" -> {
                val action = args[1].lowercase()
                val feature = args.getOrNull(2)?.lowercase()
                val plugin = args.getOrNull(3)
                if (feature == null || plugin == null) {
                    sender.sendMessage("/arc plugin-overrides <exclude|include> <feature> <plugin>")
                    return true
                }
                if (feature !in features && feature != "all") { sender.sendMessage("[Arc] unknown feature: $feature. Options: ${features.joinToString()}"); return true }
                if (action == "exclude") {
                    overrides.getOrPut(feature) { LinkedHashSet() }.add(plugin)
                    sender.sendMessage("[Arc] §e$plugin §7excluded from §e$feature")
                } else {
                    overrides[feature]?.remove(plugin)
                    sender.sendMessage("[Arc] §a$plugin §7re-included in §e$feature")
                }
                save()
            }
            else -> { sender.sendMessage("/arc plugin-overrides <list|exclude <feature> <plugin>|include <feature> <plugin>>"); return true }
        }
        return true
    }
}
