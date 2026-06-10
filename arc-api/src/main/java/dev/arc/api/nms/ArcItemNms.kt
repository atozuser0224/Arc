package dev.arc.api.nms

import dev.arc.api.scheduling.callGlobal
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.concurrent.CompletableFuture

/** ItemStack capabilities that require server internals (NMS). */
interface ArcItemNms {

    /** String dump of the item's NMS form (data components / NBT). */
    fun describeNms(item: ItemStack): String

    /** A `net.minecraft.world.item.ItemStack` copy of [item] (escape hatch). */
    fun nmsCopy(item: ItemStack): Any?

    fun describeNmsAsync(plugin: Plugin, item: ItemStack): CompletableFuture<String> =
        plugin.callGlobal { describeNms(item) }

    fun nmsCopyAsync(plugin: Plugin, item: ItemStack): CompletableFuture<Any?> =
        plugin.callGlobal { nmsCopy(item) }
}
