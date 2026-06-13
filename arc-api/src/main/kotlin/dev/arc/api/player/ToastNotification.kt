@file:JvmName("Toasts")

package dev.arc.api.player

import net.kyori.adventure.text.Component
import net.minecraft.advancements.Advancement
import net.minecraft.advancements.AdvancementHolder
import net.minecraft.advancements.AdvancementProgress
import net.minecraft.advancements.AdvancementRequirements
import net.minecraft.advancements.AdvancementRewards
import net.minecraft.advancements.AdvancementType
import net.minecraft.advancements.DisplayInfo
import net.minecraft.advancements.critereon.ImpossibleTrigger
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket
import net.minecraft.resources.ResourceLocation
import io.papermc.paper.adventure.PaperAdventure
import org.bukkit.Material
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

/**
 * Send advancement-style toast notifications to players without creating real advancements.
 *
 * Toasts appear in the top-right corner and use the same UI as achievement/challenge
 * notifications. They disappear automatically after ~5 seconds.
 *
 * ```kotlin
 * // Simple task-style toast
 * player.sendToast(
 *     title = Component.text("§6Zone Captured!"),
 *     description = Component.text("§7Your team controls §aNorth Base"),
 *     icon = ItemStack(Material.BEACON),
 * )
 *
 * // Challenge-style (gold star frame)
 * player.sendToast(
 *     title = Component.text("§cFirst Blood"),
 *     description = Component.text("§7You got the first kill"),
 *     icon = ItemStack(Material.DIAMOND_SWORD),
 *     type = ToastType.CHALLENGE,
 * )
 * ```
 */

enum class ToastType {
    /** Standard rectangular frame. */
    TASK,
    /** Rounded frame ("Goal" in vanilla). */
    GOAL,
    /** Gold star frame ("Challenge" in vanilla). */
    CHALLENGE;

    internal fun toNms(): AdvancementType = when (this) {
        TASK -> AdvancementType.TASK
        GOAL -> AdvancementType.GOAL
        CHALLENGE -> AdvancementType.CHALLENGE
    }
}

private val toastCounter = AtomicLong(0)

/**
 * Show a toast notification to this player.
 *
 * @param title      Main bold text.
 * @param description Sub-text shown below the title.
 * @param icon       Item displayed as the toast icon.
 * @param type       Frame style — TASK (default), GOAL, or CHALLENGE.
 */
fun Player.sendToast(
    title: Component,
    description: Component = Component.empty(),
    icon: ItemStack = ItemStack(Material.PAPER),
    type: ToastType = ToastType.TASK,
) {
    val id = ResourceLocation.fromNamespaceAndPath("arc", "toast_${toastCounter.getAndIncrement()}")
    val nmsIcon = CraftItemStack.asNMSCopy(icon)
    val display = DisplayInfo(
        nmsIcon,
        PaperAdventure.asVanilla(title),
        PaperAdventure.asVanilla(description),
        Optional.empty(),
        type.toNms(),
        true,   // showToast
        false,  // announceChat
        false,  // hidden
    )
    val criterion = net.minecraft.advancements.Criterion(ImpossibleTrigger.TriggerInstance())
    val advancement = Advancement(
        Optional.empty(),
        Optional.of(display),
        AdvancementRewards.EMPTY,
        mapOf("impossible" to criterion),
        AdvancementRequirements.allOf(listOf("impossible")),
        false,
        Optional.of(PaperAdventure.asVanilla(title)),
    )
    val holder = AdvancementHolder(id, advancement)

    val progress = AdvancementProgress()
    progress.update(mapOf("impossible" to criterion), AdvancementRequirements.allOf(listOf("impossible")))
    progress.grantProgress("impossible")

    val conn = (this as CraftPlayer).handle.connection
    // Add the fake advancement (this triggers the toast)
    conn.send(ClientboundUpdateAdvancementsPacket(false, listOf(holder), emptySet(), mapOf(id to progress)))
    // Remove it immediately after — the toast animation keeps playing on client side
    conn.send(ClientboundUpdateAdvancementsPacket(false, emptyList(), setOf(id), emptyMap()))
}

/**
 * Show a toast to multiple players at once.
 */
fun sendToastTo(
    vararg players: Player,
    title: Component,
    description: Component = Component.empty(),
    icon: ItemStack = ItemStack(Material.PAPER),
    type: ToastType = ToastType.TASK,
) = players.forEach { it.sendToast(title, description, icon, type) }
