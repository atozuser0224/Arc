@file:JvmName("BlockBreakAnimations")

package dev.arc.api.entity

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sends block crack (destruction progress) animations to specific players without
 * actually breaking the block. Useful for mining progress indicators, countdown visuals,
 * and interactive block-based UI.
 *
 * ```kotlin
 * // Show crack stages 0–9 to a player over time
 * val anim = BlockBreakAnimation(block)
 * anim.setProgress(5, player)    // half-cracked
 * anim.setProgress(9, player)    // nearly broken
 * anim.clear(player)             // remove crack
 * ```
 *
 * Multiple independent animations on different blocks each need their own instance
 * (each uses a unique entity ID slot for the crack packet).
 */
class BlockBreakAnimation(val block: Block) {

    /** Unique ID for this animation (negative to not clash with real entities). */
    val animationId: Int = ID_COUNTER.decrementAndGet()

    private val pos = BlockPos(block.x, block.y, block.z)

    /**
     * Set crack progress for [players].
     * [progress] is 0–9 (0 = just starting, 9 = nearly broken). Any value outside this
     * range clears the animation (equivalent to [clear]).
     */
    fun setProgress(progress: Int, vararg players: Player) {
        val packet = ClientboundBlockDestructionPacket(animationId, pos, progress)
        players.forEach { (it as CraftPlayer).handle.connection.send(packet) }
    }

    /** Remove the crack animation from [players]' view. */
    fun clear(vararg players: Player) = setProgress(-1, *players)

    /**
     * Show a timed crack animation that advances every [ticksPerStage] ticks.
     * Automatically removes itself when done. Requires a [plugin] for scheduling.
     *
     * @param totalTicks Total animation duration in ticks (20 stages of `totalTicks/10` each).
     * @param onComplete Called on the main thread when the animation finishes.
     */
    fun animate(
        totalTicks: Long,
        plugin: org.bukkit.plugin.Plugin,
        vararg players: Player,
        onComplete: (() -> Unit)? = null,
    ) {
        val ticksPerStage = (totalTicks / 10L).coerceAtLeast(1L)
        var stage = 0
        val task = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, { task ->
            if (stage > 9) {
                clear(*players)
                task.cancel()
                onComplete?.invoke()
            } else {
                setProgress(stage++, *players)
            }
        }, 0L, ticksPerStage)
    }

    companion object {
        private val ID_COUNTER = AtomicInteger(-100_000)
    }
}

/** Create a [BlockBreakAnimation] on this block. */
fun Block.breakAnimation(): BlockBreakAnimation = BlockBreakAnimation(this)

/** Create a [BlockBreakAnimation] at this location (uses the block at the location). */
fun Location.breakAnimation(): BlockBreakAnimation = BlockBreakAnimation(block)
