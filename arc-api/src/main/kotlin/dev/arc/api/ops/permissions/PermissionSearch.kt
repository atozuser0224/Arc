package dev.arc.api.ops.permissions

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.permissions.Permission

/**
 * `/arc permission search <keyword>`, `/arc permission plugin <name>`, `/arc permission player <player> <node>`.
 * Read-only: never grants or revokes permissions.
 */
object PermissionSearch {

    fun complete(args: Array<out String>): List<String> = when (args.size) {
        2 -> listOf("search", "plugin", "player")
        3 -> when (args.getOrNull(1)?.lowercase()) {
            "plugin" -> Bukkit.getPluginManager().plugins.map { it.name }
            "player" -> Bukkit.getOnlinePlayers().map { it.name }
            else -> emptyList()
        }
        4 -> when (args.getOrNull(1)?.lowercase()) {
            "player" -> Bukkit.getPluginManager().permissions.map { it.name }
            else -> emptyList()
        }
        else -> emptyList()
    }

    fun dispatch(sender: CommandSender, args: Array<out String>): Boolean {
        when (args.getOrNull(1)?.lowercase()) {
            "search" -> search(sender, args.getOrNull(2))
            "plugin" -> byPlugin(sender, args.getOrNull(2))
            "player" -> playerCheck(sender, args.getOrNull(2), args.getOrNull(3))
            else -> { sender.sendMessage("/arc permission <search <kw>|plugin <p>|player <player> <node>>"); return true }
        }
        return true
    }

    private fun search(sender: CommandSender, keyword: String?) {
        if (keyword == null) { sender.sendMessage("[Arc] specify a keyword"); return }
        val matches = Bukkit.getPluginManager().permissions
            .filter { it.name.contains(keyword, true) || (it.description?.contains(keyword, true) == true) }
            .take(20)

        if (matches.isEmpty()) { sender.sendMessage("[Arc] no permissions matching '$keyword'"); return }
        sender.sendMessage("[Arc] Permissions matching '$keyword':")
        matches.forEach { p ->
            sender.sendMessage("  §e${p.name} §7default=${p.default} ${p.description ?: ""}")
        }
    }

    private fun byPlugin(sender: CommandSender, pluginName: String?) {
        if (pluginName == null) { sender.sendMessage("[Arc] specify a plugin name"); return }
        val plugin = Bukkit.getPluginManager().getPlugin(pluginName)
        if (plugin == null) { sender.sendMessage("[Arc] plugin not found: $pluginName"); return }
        val perms = plugin.description.permissions
        if (perms.isEmpty()) { sender.sendMessage("[Arc] §e${plugin.name} declares no permissions"); return }
        sender.sendMessage("[Arc] ${plugin.name} permissions (${perms.size}):")
        perms.forEach { p ->
            sender.sendMessage("  §e${p.name} §7default=${p.default} ${p.description ?: ""}")
        }
    }

    private fun playerCheck(sender: CommandSender, playerName: String?, node: String?) {
        if (playerName == null || node == null) { sender.sendMessage("[Arc] usage: /arc permission player <player> <node>"); return }
        val player = Bukkit.getPlayer(playerName)
        if (player == null) { sender.sendMessage("[Arc] player not found: $playerName"); return }
        val has = player.hasPermission(node)
        sender.sendMessage("[Arc] §e${player.name} has permission '$node': ${if (has) "§atrue" else "§cfalse"}")
        // Show effective permission source if possible
        val perm = Bukkit.getPluginManager().getPermission(node)
        if (perm != null) {
            sender.sendMessage("  default: ${perm.default}")
            perm.description?.let { sender.sendMessage("  description: $it") }
        }
    }
}
