package dev.arc.api.ops.commands

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.command.PluginCommand

/**
 * `/arc command search <keyword>` and `/arc command info <name>`.
 */
object CommandSearch {

    fun complete(args: Array<out String>): List<String> = when (args.size) {
        2 -> listOf("search", "info")
        3 -> Bukkit.getCommandMap().knownCommands.keys.toList()
        else -> emptyList()
    }

    fun dispatch(sender: CommandSender, args: Array<out String>): Boolean {
        when (args.getOrNull(1)?.lowercase()) {
            "search" -> search(sender, args.getOrNull(2))
            "info" -> info(sender, args.getOrNull(2))
            else -> { sender.sendMessage("/arc command <search <keyword>|info <name>>"); return true }
        }
        return true
    }

    private fun info(sender: CommandSender, name: String?) {
        if (name == null) { sender.sendMessage("[Arc] specify a command name"); return }
        val cmd = Bukkit.getCommandMap().knownCommands.entries.find { it.key.equals(name, true) }?.value
        if (cmd == null) { sender.sendMessage("[Arc] command not found: $name"); return }

        val owner = when (cmd) {
            is PluginCommand -> cmd.plugin.name
            else -> "(server)"
        }
        sender.sendMessage("[Arc] Command: §e${cmd.name}")
        sender.sendMessage("  owner       : $owner")
        sender.sendMessage("  description : ${cmd.description ?: "(none)"}")
        sender.sendMessage("  usage       : ${cmd.usage ?: "/${cmd.name}"}")
        sender.sendMessage("  aliases     : ${cmd.aliases.joinToString()}")
        sender.sendMessage("  permission  : ${cmd.permission ?: "(none)"}")
        sender.sendMessage("  label       : ${cmd.label}")
    }

    private fun search(sender: CommandSender, keyword: String?) {
        if (keyword == null) { sender.sendMessage("[Arc] specify a keyword"); return }
        val matches = Bukkit.getCommandMap().knownCommands
            .filter { it.key.contains(keyword, true) || (it.value.description?.contains(keyword, true) == true) }
            .entries.take(20)

        if (matches.isEmpty()) { sender.sendMessage("[Arc] no commands matching '$keyword'"); return }
        sender.sendMessage("[Arc] Commands matching '$keyword':")
        matches.forEach { (key, cmd) ->
            val owner = (cmd as? PluginCommand)?.plugin?.name ?: "server"
            sender.sendMessage("  §e/$key §7($owner) ${cmd.description ?: ""}")
        }
    }
}
