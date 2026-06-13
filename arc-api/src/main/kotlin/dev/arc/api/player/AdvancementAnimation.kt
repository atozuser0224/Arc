@file:JvmName("AdvancementAnimations")

package dev.arc.api.player

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask

/**
 * Sequence of timed toast notifications for storytelling, cut-scenes, or tutorial flows.
 * Each step fires a [ToastNotification] after the given tick delay from the sequence start.
 *
 * ```kotlin
 * plugin.toastSequence(player) {
 *     at(0)   { toast("Chapter 1", "The journey begins…", Material.BOOK) }
 *     at(60)  { toast("Objective", "Find the ancient temple", Material.MAP) }
 *     at(100) { toast("Warning", "A dark force approaches…", Material.OMINOUS_BOTTLE, ToastType.CHALLENGE) }
 *     at(180) { toast("The End", "You have survived.", Material.NETHER_STAR, ToastType.GOAL) }
 *     onFinish { player.sendMessage("§aCut-scene complete.") }
 * }
 *
 * // Cancel mid-sequence:
 * val anim = plugin.toastSequence(player) { ... }
 * anim.cancel()
 * ```
 *
 * All tick delays are relative to the moment [Plugin.toastSequence] is called.
 */
class AdvancementAnimation private constructor(
    private val plugin: Plugin,
    private val players: List<Player>,
) {
    data class Step(val delayTicks: Long, val action: (List<Player>) -> Unit)

    private val steps = mutableListOf<Step>()
    private val tasks = mutableListOf<BukkitTask>()
    private var finishCallback: (() -> Unit)? = null

    /** Schedule an action at [ticks] ticks after the sequence starts. */
    fun at(ticks: Long, action: StepScope.() -> Unit): AdvancementAnimation {
        steps += Step(ticks) { ps -> StepScope(ps).action() }
        return this
    }

    /** Callback invoked after the last step completes. */
    fun onFinish(block: () -> Unit): AdvancementAnimation {
        finishCallback = block
        return this
    }

    internal fun start(): AdvancementAnimation {
        val sorted = steps.sortedBy { it.delayTicks }
        sorted.forEachIndexed { i, step ->
            val task = plugin.server.scheduler.runTaskLater(plugin, {
                step.action(players.filter { it.isOnline })
                if (i == sorted.lastIndex) finishCallback?.invoke()
            }, step.delayTicks)
            tasks += task
        }
        return this
    }

    /** Cancel all pending steps. */
    fun cancel() {
        tasks.forEach { it.cancel() }
        tasks.clear()
    }

    /** Whether the sequence has any pending steps still scheduled. */
    val isRunning: Boolean get() = tasks.any { !it.isCancelled }
}

/** DSL scope for a single step in an [AdvancementAnimation]. */
class StepScope(private val players: List<Player>) {

    /** Show a toast to all players in this sequence. */
    fun toast(
        title: String,
        description: String = "",
        icon: Material = Material.PAPER,
        type: ToastType = ToastType.TASK,
    ) = players.forEach { player ->
        player.sendToast(
            Component.text(title),
            Component.text(description),
            ItemStack(icon),
            type,
        )
    }

    /** Show a Component-based toast. */
    fun toast(
        title: Component,
        description: Component = Component.empty(),
        icon: ItemStack = ItemStack(Material.PAPER),
        type: ToastType = ToastType.TASK,
    ) = players.forEach { it.sendToast(title, description, icon, type) }

    /** Send a chat message to all players in this sequence. */
    fun message(text: String) = players.forEach { it.sendMessage(text) }

    /** Send a title to all players in this sequence. */
    fun title(title: String, subtitle: String = "", fadeIn: Int = 10, stay: Int = 40, fadeOut: Int = 10) =
        players.forEach { it.sendTitle(title, subtitle, fadeIn, stay, fadeOut) }

    /** Run arbitrary code on each player. */
    fun forEachPlayer(block: (Player) -> Unit) = players.forEach(block)
}

// ── Factory ────────────────────────────────────────────────────────────────

/**
 * Create and immediately start a timed toast sequence for [players].
 *
 * ```kotlin
 * plugin.toastSequence(player) {
 *     at(0)  { toast("Round Start", "Fight!", Material.IRON_SWORD) }
 *     at(40) { toast("Tip", "Capture bases for points", Material.MAP) }
 * }
 * ```
 */
fun Plugin.toastSequence(
    vararg players: Player,
    configure: AdvancementAnimation.() -> Unit,
): AdvancementAnimation =
    AdvancementAnimation(this, players.toList()).apply(configure).start()

/**
 * Create a sequence without starting it. Call [AdvancementAnimation.start] manually
 * when you're ready.
 */
fun Plugin.buildToastSequence(
    vararg players: Player,
    configure: AdvancementAnimation.() -> Unit,
): AdvancementAnimation =
    AdvancementAnimation(this, players.toList()).apply(configure)
