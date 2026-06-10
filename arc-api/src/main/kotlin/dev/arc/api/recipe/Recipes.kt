@file:JvmName("Recipes")

package dev.arc.api.recipe

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe

/**
 * Crafting-recipe registration via Bukkit API. Registers immediately with the server's recipe manager.
 *
 * ```kotlin
 * shapedRecipe(plugin.key("super_block"), result,
 *     "AAA", "ABA", "AAA",
 *     ingredients = mapOf('A' to Material.IRON_INGOT, 'B' to Material.DIAMOND))
 * ```
 */

/** Register and return a shaped recipe. [shape] is up to 3 rows; [ingredients] maps shape chars to materials. */
public fun shapedRecipe(
    key: NamespacedKey,
    result: ItemStack,
    vararg shape: String,
    ingredients: Map<Char, Material>,
): ShapedRecipe {
    val recipe = ShapedRecipe(key, result)
    recipe.shape(*shape)
    for ((symbol, material) in ingredients) {
        recipe.setIngredient(symbol, material)
    }
    Bukkit.addRecipe(recipe)
    return recipe
}

/** Register and return a shapeless recipe from its [ingredients]. */
public fun shapelessRecipe(
    key: NamespacedKey,
    result: ItemStack,
    vararg ingredients: Material,
): ShapelessRecipe {
    val recipe = ShapelessRecipe(key, result)
    for (material in ingredients) {
        recipe.addIngredient(material)
    }
    Bukkit.addRecipe(recipe)
    return recipe
}
