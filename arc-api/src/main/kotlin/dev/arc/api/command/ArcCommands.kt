package dev.arc.api.command

import org.bukkit.plugin.Plugin
import java.util.function.Consumer

/**
 * Java-friendly entry point for Arc command registration.
 *
 * Kotlin callers should use the [command] extension function directly.
 *
 * ```java
 * ArcCommands.register(plugin, "shop", cmd -> {
 *     cmd.description("Server shop");
 *     cmd.permission("myplugin.shop");
 *
 *     cmd.sub("buy", sub -> {
 *         sub.playerOnly();
 *         sub.execute(ctx -> {
 *             String item = ctx.arg(0);
 *             if (item == null) { ctx.getSender().sendMessage("Usage: /shop buy <item>"); return; }
 *             openBuyMenu(ctx.getPlayer(), item);
 *         });
 *         sub.complete(ctx -> getShopItems());
 *     });
 *
 *     cmd.sub("sell", sub -> {
 *         sub.playerOnly();
 *         sub.execute(ctx -> openSellMenu(ctx.getPlayer()));
 *     });
 *
 *     cmd.execute(ctx ->
 *         ctx.getSender().sendMessage("§eUsage: /shop <buy|sell>")
 *     );
 * });
 * ```
 */
public object ArcCommands {

    /**
     * Register a root command using a [Consumer]-based builder.
     * No plugin.yml entry is required — registration happens immediately.
     *
     * @param plugin owning plugin (used as CommandMap namespace)
     * @param name   root command name, e.g. `"shop"`
     * @param block  receives a [CommandBuilder]; configure description, permission, subcommands, and execute handler
     */
    @JvmStatic
    public fun register(plugin: Plugin, name: String, block: Consumer<CommandBuilder>): Unit =
        plugin.command(name) { block.accept(this) }
}
