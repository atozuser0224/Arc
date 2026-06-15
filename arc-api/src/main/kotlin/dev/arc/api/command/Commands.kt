@file:JvmName("Commands")

package dev.arc.api.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandMap
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.function.Consumer
import java.util.function.Function

/**
 * Context passed to every execute/complete handler.
 *
 * [args] contains arguments AFTER the matched subcommand name (if any).
 */
public data class CommandContext(
    val sender: CommandSender,
    val label: String,
    val args: List<String>,
) {
    /** Cast sender to [Player] — throws if sender is not a player. */
    val player: Player get() = sender as? Player ?: error("This command can only be used by players")
    val isPlayer: Boolean get() = sender is Player

    fun arg(index: Int): String? = args.getOrNull(index)
    fun argOrElse(index: Int, default: String): String = args.getOrNull(index) ?: default
    fun remaining(from: Int = 0): String = args.drop(from).joinToString(" ")
}

@DslMarker public annotation class CommandDsl

/** Builder for a single subcommand branch. */
@CommandDsl
public class SubCommandBuilder(public val name: String) {
    public var description: String = ""
    public var permission: String? = null
    public var isPlayerOnly: Boolean = false
    internal var onExecute: ((CommandContext) -> Unit)? = null
    internal var onComplete: ((CommandContext) -> List<String>)? = null

    public fun description(s: String) { description = s }
    public fun permission(s: String) { permission = s }
    public fun playerOnly() { isPlayerOnly = true }

    /** Called when the subcommand is dispatched. */
    public fun execute(block: (CommandContext) -> Unit) { onExecute = block }

    /** Return tab-complete suggestions. [ctx.args] holds arguments after the subcommand label. */
    public fun complete(block: (CommandContext) -> List<String>) { onComplete = block }

    // ── Java-friendly overloads ────────────────────────────────────────────
    /** Java: `sub.execute(ctx -> { ... });` */
    public fun execute(consumer: Consumer<CommandContext>) { onExecute = { consumer.accept(it) } }
    /** Java: `sub.complete(ctx -> List.of("a", "b"));` */
    public fun complete(fn: Function<CommandContext, List<String>>) { onComplete = { fn.apply(it) } }
}

/** Builder for the root command. */
@CommandDsl
public class CommandBuilder(public val name: String) {
    public var description: String = ""
    public var usage: String = ""
    public var permission: String? = null
    public var isPlayerOnly: Boolean = false
    internal val subs = LinkedHashMap<String, SubCommandBuilder>()
    internal var onExecute: ((CommandContext) -> Unit)? = null
    internal var onComplete: ((CommandContext) -> List<String>)? = null

    public fun description(s: String) { description = s }
    public fun usage(s: String) { usage = s }
    public fun permission(s: String) { permission = s }
    public fun playerOnly() { isPlayerOnly = true }

    /** Fallback execute when no subcommand matches (or no subcommands defined). */
    public fun execute(block: (CommandContext) -> Unit) { onExecute = block }

    /** Top-level tab-complete (called when the user is still typing the first argument). */
    public fun complete(block: (CommandContext) -> List<String>) { onComplete = block }

    /** Register a subcommand branch. */
    public fun sub(name: String, block: SubCommandBuilder.() -> Unit) {
        subs[name.lowercase()] = SubCommandBuilder(name).apply(block)
    }

    // ── Java-friendly overloads ────────────────────────────────────────────
    /** Java: `cmd.execute(ctx -> { ... });` */
    public fun execute(consumer: Consumer<CommandContext>) { onExecute = { consumer.accept(it) } }
    /** Java: `cmd.complete(ctx -> List.of("a", "b"));` */
    public fun complete(fn: Function<CommandContext, List<String>>) { onComplete = { fn.apply(it) } }
    /** Java: `cmd.sub("name", sub -> { sub.execute(ctx -> ...); });` */
    public fun sub(name: String, block: Consumer<SubCommandBuilder>) {
        subs[name.lowercase()] = SubCommandBuilder(name).also { block.accept(it) }
    }

    internal fun completeFor(
        sender: CommandSender,
        alias: String,
        args: Array<String>,
    ): List<String> {
        if (permission?.let(sender::hasPermission) == false) return emptyList()
        if (isPlayerOnly && sender !is Player) return emptyList()

        val current = args.lastOrNull().orEmpty()
        val matchedSub = args.firstOrNull()
            ?.lowercase()
            ?.let(subs::get)

        val candidates = if (matchedSub != null && args.size > 1) {
            if (matchedSub.permission?.let(sender::hasPermission) == false) return emptyList()
            if (matchedSub.isPlayerOnly && sender !is Player) return emptyList()
            matchedSub.onComplete?.invoke(CommandContext(sender, alias, args.drop(1))).orEmpty()
        } else {
            val subcommands = if (args.size <= 1) {
                subs.values
                    .asSequence()
                    .filter { it.permission?.let(sender::hasPermission) != false }
                    .filter { !it.isPlayerOnly || sender is Player }
                    .map { it.name }
                    .toList()
            } else {
                emptyList()
            }
            subcommands + onComplete?.invoke(CommandContext(sender, alias, args.toList())).orEmpty()
        }

        return candidates
            .filter { it.startsWith(current, ignoreCase = true) }
            .distinctBy(String::lowercase)
    }
}

/**
 * Register a command with the server's [CommandMap] using a Kotlin DSL.
 * No plugin.yml entry is needed — registration happens at call time.
 *
 * ```kotlin
 * plugin.command("warp") {
 *     description("Warp to a location")
 *     permission("myplugin.warp")
 *
 *     sub("set") {
 *         playerOnly()
 *         execute { ctx ->
 *             val name = ctx.arg(0) ?: return@execute ctx.sender.sendMessage("Usage: /warp set <name>")
 *             saveWarp(ctx.player, name)
 *             ctx.player.sendMessage("Warp §e$name§r saved.")
 *         }
 *         complete { ctx -> listOf("<name>") }
 *     }
 *
 *     sub("go") {
 *         execute { ctx -> teleportToWarp(ctx.player, ctx.arg(0) ?: "spawn") }
 *         complete { ctx -> getWarps(ctx.player) }
 *     }
 *
 *     execute { ctx ->
 *         ctx.sender.sendMessage("Usage: /warp <set|go>")
 *     }
 * }
 * ```
 */
public fun Plugin.command(name: String, block: CommandBuilder.() -> Unit) {
    val builder = CommandBuilder(name).apply(block)

    val cmd = object : Command(
        name,
        builder.description,
        builder.usage.ifEmpty { "/$name" },
        emptyList(),
    ) {
        init { builder.permission?.let { permission = it } }

        override fun execute(sender: CommandSender, label: String, args: Array<String>): Boolean {
            val perm = builder.permission
            if (perm != null && !sender.hasPermission(perm)) {
                sender.sendMessage("§cYou don't have permission to use this command.")
                return true
            }
            if (builder.isPlayerOnly && sender !is Player) {
                sender.sendMessage("§cOnly players can use this command.")
                return true
            }

            val subName = args.getOrNull(0)?.lowercase()
            val sub = subName?.let { builder.subs[it] }

            if (sub != null) {
                val subPerm = sub.permission
                if (subPerm != null && !sender.hasPermission(subPerm)) {
                    sender.sendMessage("§cYou don't have permission.")
                    return true
                }
                if (sub.isPlayerOnly && sender !is Player) {
                    sender.sendMessage("§cOnly players can use this command.")
                    return true
                }
                val ctx = CommandContext(sender, label, args.drop(1))
                sub.onExecute?.invoke(ctx) ?: sender.sendMessage("§c/${label} ${sub.name}: no handler registered.")
            } else {
                val ctx = CommandContext(sender, label, args.toList())
                builder.onExecute?.invoke(ctx) ?: run {
                    if (builder.subs.isNotEmpty()) {
                        val subList = builder.subs.keys.joinToString("§7, §e") { "§e$it" }
                        sender.sendMessage("§7Subcommands: $subList")
                    }
                }
            }
            return true
        }

        override fun tabComplete(sender: CommandSender, alias: String, args: Array<String>): List<String> {
            return builder.completeFor(sender, alias, args)
        }
    }

    val commandMap = Bukkit.getServer()
        .javaClass.getMethod("getCommandMap").invoke(Bukkit.getServer()) as CommandMap
    commandMap.register(this.name.lowercase(), cmd)
}

/**
 * Register a command without a [Plugin] instance — useful for server-integrated modules.
 * [prefix] is used as the CommandMap namespace (e.g. `"arc"`).
 */
public fun command(name: String, prefix: String, block: CommandBuilder.() -> Unit) {
    val builder = CommandBuilder(name).apply(block)

    val cmd = object : Command(
        name,
        builder.description,
        builder.usage.ifEmpty { "/$name" },
        emptyList(),
    ) {
        init { builder.permission?.let { permission = it } }

        override fun execute(sender: CommandSender, label: String, args: Array<String>): Boolean {
            val perm = builder.permission
            if (perm != null && !sender.hasPermission(perm)) {
                sender.sendMessage("§cYou don't have permission to use this command.")
                return true
            }
            if (builder.isPlayerOnly && sender !is Player) {
                sender.sendMessage("§cOnly players can use this command.")
                return true
            }
            val subName = args.getOrNull(0)?.lowercase()
            val sub = subName?.let { builder.subs[it] }
            if (sub != null) {
                val subPerm = sub.permission
                if (subPerm != null && !sender.hasPermission(subPerm)) {
                    sender.sendMessage("§cYou don't have permission.")
                    return true
                }
                if (sub.isPlayerOnly && sender !is Player) {
                    sender.sendMessage("§cOnly players can use this command.")
                    return true
                }
                sub.onExecute?.invoke(CommandContext(sender, label, args.drop(1)))
                    ?: sender.sendMessage("§cNo handler for this subcommand.")
            } else {
                val ctx = CommandContext(sender, label, args.toList())
                builder.onExecute?.invoke(ctx) ?: run {
                    if (builder.subs.isNotEmpty()) {
                        sender.sendMessage("§eSubcommands: ${builder.subs.keys.joinToString("§7, §e") { "§e$it" }}")
                    }
                }
            }
            return true
        }

        override fun tabComplete(sender: CommandSender, alias: String, args: Array<String>): List<String> {
            return builder.completeFor(sender, alias, args)
        }
    }

    val commandMap = Bukkit.getServer()
        .javaClass.getMethod("getCommandMap").invoke(Bukkit.getServer()) as CommandMap
    commandMap.register(prefix.lowercase(), cmd)
}
