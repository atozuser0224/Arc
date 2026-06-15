package dev.arc.api.content

import org.bukkit.plugin.Plugin
import java.util.function.Consumer

/**
 * Java-friendly entry point for Arc content pack registration.
 *
 * Kotlin callers should use [arcContent] and [removeArcContent] directly.
 *
 * ```java
 * ArcContentDsl.register(plugin, pack -> {
 *     pack.item("showcase/ruby", item -> {
 *         item.setFallback("minecraft:redstone");
 *         item.setMaxStackSize(64);
 *         item.setOrder(10);
 *     });
 *     pack.block("showcase/ruby_ore", block -> {
 *         block.setFallback("minecraft:redstone_ore");
 *         block.setHardness(3.0f);
 *         block.setBlastResistance(3.0f);
 *         block.setOrder(20);
 *     });
 *     pack.recipe("showcase/ruby_recipe", recipe -> {
 *         recipe.setResult(new ContentId("showcase", "showcase/ruby"));
 *         recipe.getIngredients().add(new ContentId("minecraft", "redstone"));
 *     });
 * });
 *
 * // In onDisable:
 * ArcContentDsl.unregister(plugin);
 * ```
 */
public object ArcContentDsl {

    /**
     * Register a content pack for [plugin] using a [Consumer]-based builder.
     * The namespace is derived from the plugin name (lowercased, sanitized).
     *
     * @param plugin  owning plugin
     * @param block   receives a [ContentPackBuilder]; add items, blocks, furniture, and recipes
     * @return [ContentPublishResult] — check [ContentPublishResult.accepted] for success
     */
    @JvmStatic
    public fun register(plugin: Plugin, block: Consumer<ContentPackBuilder>): ContentPublishResult =
        plugin.arcContent { block.accept(this) }

    /**
     * Register a content pack with an explicit [namespace].
     */
    @JvmStatic
    public fun register(
        plugin: Plugin,
        namespace: String,
        block: Consumer<ContentPackBuilder>,
    ): ContentPublishResult = plugin.arcContent(namespace) { block.accept(this) }

    /**
     * Unregister the content pack previously registered by [plugin].
     */
    @JvmStatic
    public fun unregister(plugin: Plugin): ContentPublishResult = plugin.removeArcContent()
}

