@file:JvmName("AnvilInputs")

package dev.arc.api.input

import dev.arc.api.event.listen
import dev.arc.api.item.editMeta
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin

/**
 * Open an anvil GUI to collect a text string from the player — without NMS.
 *
 * The anvil rename field serves as a lightweight text input: place a prompt item in slot 0,
 * capture what the player types via [PrepareAnvilEvent], and deliver the result when they
 * click the output slot. Cancelling (closing without clicking) invokes [onCancel].
 *
 * ```kotlin
 * plugin.anvilInput(
 *     player = player,
 *     prompt = ItemStack(Material.PAPER).apply {
 *         editMeta<ItemMeta> { displayName(text("<gray>Enter a name")) }
 *     },
 *     onResult = { text -> player.sendMessage("You entered: $text") },
 *     onCancel = { player.sendMessage("Cancelled.") },
 * )
 * ```
 *
 * Limitations: the output slot only activates once the player types at least one character
 * (vanilla behaviour). If you need an empty-string submission, keep a fallback.
 */
public fun Plugin.anvilInput(
    player: Player,
    prompt: ItemStack = ItemStack(Material.PAPER).also {
        it.editMeta<org.bukkit.inventory.meta.ItemMeta> {
            displayName(Component.empty())
        }
    },
    onResult: (text: String) -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    val inv = Bukkit.createInventory(null, InventoryType.ANVIL)
    inv.setItem(0, prompt.clone())

    var currentText = prompt.itemMeta?.displayName()?.let {
        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it)
    } ?: ""
    var completed = false

    val listeners = mutableListOf<org.bukkit.event.Listener>()

    fun cleanup() {
        listeners.forEach { HandlerList.unregisterAll(it) }
        listeners.clear()
    }

    listeners += listen<PrepareAnvilEvent> { event ->
        if (event.view.player.uniqueId != player.uniqueId) return@listen
        currentText = event.inventory.renameText ?: currentText
        // Keep the output slot populated so the player can click it
        if (event.result == null || event.result?.type == Material.AIR) {
            event.result = prompt.clone().also {
                it.editMeta<org.bukkit.inventory.meta.ItemMeta> {
                    displayName(Component.text(currentText))
                }
            }
        }
    }

    listeners += listen<InventoryClickEvent> { event ->
        if (event.whoClicked.uniqueId != player.uniqueId) return@listen
        if (event.view.topInventory.type != InventoryType.ANVIL) return@listen
        if (event.rawSlot != 2) { event.isCancelled = true; return@listen }
        val clicked = event.currentItem ?: return@listen
        if (clicked.type == Material.AIR) return@listen
        event.isCancelled = true
        completed = true
        player.closeInventory()
        onResult(currentText)
        cleanup()
    }

    listeners += listen<InventoryCloseEvent> { event ->
        if (event.player.uniqueId != player.uniqueId) return@listen
        if (event.view.topInventory.type != InventoryType.ANVIL) return@listen
        if (!completed) {
            cleanup()
            onCancel?.invoke()
        }
    }

    player.openInventory(inv)
}
