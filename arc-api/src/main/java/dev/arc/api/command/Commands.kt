package dev.arc.api.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin

/**
 * Declarative spec for a command built with the [command] DSL.
 *
 * ```
 * plugin.command("heal") {
 *     description = "Heal yourself"
 *     permission = "arc.heal"
 *     aliases = listOf("h")
 *     executes { sender, _ ->
 *         (sender as? Player)?.health = 20.0
 *         true
 *     }
 *     completes { _, _ -> emptyList() }
 * }
 * ```
 */
class CommandSpec internal constructor(val name: String) {
    var description: String = ""
    var usage: String = "/$name"
    var permission: String? = null
    var aliases: List<String> = emptyList()

    internal var executor: (CommandSender, Array<out String>) -> Boolean = { _, _ -> true }
    internal var completer: (CommandSender, Array<out String>) -> List<String> = { _, _ -> emptyList() }

    /** What the command does. Return false to show the usage message. */
    fun executes(block: (sender: CommandSender, args: Array<out String>) -> Boolean) {
        executor = block
    }

    /** Tab-completion suggestions for the current arguments. */
    fun completes(block: (sender: CommandSender, args: Array<out String>) -> List<String>) {
        completer = block
    }
}

private class ArcCommand(private val spec: CommandSpec) : Command(spec.name) {
    init {
        description = spec.description
        usage = spec.usage
        spec.permission?.let { permission = it }
        if (spec.aliases.isNotEmpty()) aliases = spec.aliases
    }

    override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean =
        spec.executor(sender, args)

    override fun tabComplete(sender: CommandSender, alias: String, args: Array<out String>): MutableList<String> =
        spec.completer(sender, args).toMutableList()
}

/**
 * Register a command at runtime — no `plugin.yml` entry required. The plugin's
 * (lowercased) name is used as the fallback prefix.
 */
fun Plugin.command(name: String, build: CommandSpec.() -> Unit) {
    command(name, this.name, build)
}

/**
 * Register a command without a plugin owner. Intended for server-integrated
 * modules that are initialized before the plugin lifecycle is available.
 */
fun command(name: String, fallbackPrefix: String, build: CommandSpec.() -> Unit) {
    val spec = CommandSpec(name).apply(build)
    Bukkit.getCommandMap().register(name.lowercase(), fallbackPrefix.lowercase(), ArcCommand(spec))
}
