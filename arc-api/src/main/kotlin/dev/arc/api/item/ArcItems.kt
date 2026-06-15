package dev.arc.api.item

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.function.Consumer

/**
 * Java-friendly bridge for Arc item utilities.
 *
 * Kotlin callers should use the top-level [item], [defineItem], and [withBehavior] extensions directly.
 *
 * ```java
 * // Build an item
 * ItemStack sword = ArcItems.item(Material.DIAMOND_SWORD, builder -> {
 *     builder.name(Component.text("My Sword"));
 *     builder.unbreakable(true);
 *     builder.glowing(true);
 * });
 *
 * // Define a behavior — right-click / equip / drop handlers
 * NamespacedKey myKey = new NamespacedKey(plugin, "my_item");
 * ArcItems.defineItem(plugin, myKey, def -> {
 *     def.onRightClick(e -> e.getPlayer().sendMessage("Clicked!"));
 *     def.onEquip(e -> e.getPlayer().sendMessage("Equipped!"));
 * });
 *
 * // Attach the behavior to a stack (returns a copy)
 * ItemStack tagged = ArcItems.withBehavior(sword, myKey, plugin);
 * ```
 */
public object ArcItems {

    /**
     * Build an [ItemStack] of [material] via a [Consumer]-based builder.
     *
     * @param material base Bukkit [Material]
     * @param amount   stack size (default 1)
     * @param build    receives an [ItemBuilder]; configure name, lore, enchants, etc.
     */
    @JvmStatic
    @JvmOverloads
    public fun item(
        material: Material,
        amount: Int = 1,
        build: Consumer<ItemBuilder> = Consumer {},
    ): ItemStack = item(material, amount) { build.accept(this) }

    /**
     * Register a behavior for items tagged with [key].
     *
     * @param plugin owning plugin (used for listener lifecycle)
     * @param key    unique [NamespacedKey] identifying this behavior
     * @param block  receives an [ItemBehaviorBuilder]; attach handlers
     */
    @JvmStatic
    public fun defineItem(
        plugin: Plugin,
        key: NamespacedKey,
        block: Consumer<ItemBehaviorBuilder>,
    ): Unit = plugin.defineItem(key) { block.accept(this) }

    /**
     * Return a copy of [stack] with the behavior [key] written to its PDC.
     */
    @JvmStatic
    public fun withBehavior(stack: ItemStack, key: NamespacedKey, plugin: Plugin): ItemStack =
        stack.withBehavior(key, plugin)

    /**
     * Return the behavior key stored in [stack]'s PDC, or null if none.
     */
    @JvmStatic
    public fun behaviorKey(stack: ItemStack): String? = stack.behaviorKey
}
