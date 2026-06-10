@file:JvmName("Merchants")

package dev.arc.api.gui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.HumanEntity
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.MerchantRecipe

/**
 * Custom villager-trade GUIs via Bukkit's [org.bukkit.inventory.Merchant] API - open a trade window with
 * arbitrary trades, no real villager and no NMS container.
 *
 * ```kotlin
 * player.openMerchant("<dark_green>Trader".mini(), listOf(
 *     trade(result = diamond, ItemStack(Material.EMERALD, 5)),
 * ))
 * ```
 */

/** Open a merchant window titled [title] offering [trades]. */
public fun HumanEntity.openMerchant(title: Component, trades: List<MerchantRecipe>) {
    val merchant = Bukkit.createMerchant(title)
    merchant.recipes = trades
    openMerchant(merchant, true)
}

/** Build a single trade: [result] in exchange for up to two [ingredients], usable [maxUses] times. */
public fun trade(result: ItemStack, vararg ingredients: ItemStack, maxUses: Int = Int.MAX_VALUE): MerchantRecipe {
    val recipe = MerchantRecipe(result, maxUses)
    for (ingredient in ingredients) {
        recipe.addIngredient(ingredient)
    }
    return recipe
}
