@file:JvmName("NativeAntiCheats")

package dev.arc.api.player

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import org.dreeam.leaf.event.PlayerMoveRawEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * NMS-level movement validator using [PlayerMoveRawEvent] (Leaf fork only).
 *
 * Checks fire before Paper's movement clamping, giving access to the raw
 * displacement the client claims to have made. Violations trigger a callback
 * where you decide the response (cancel, flag, ban, etc.).
 *
 * ```kotlin
 * plugin.nativeAntiCheat {
 *     maxSpeedHorizontal = 0.7        // blocks per tick (~20 m/s sprint)
 *     maxSpeedVertical = 1.0          // blocks per tick (~20 blocks/s)
 *     maxYawChangeDeg = 180f          // max rotation per tick
 *
 *     onViolation { player, type, details ->
 *         player.sendMessage("§c[AC] ${type.name}: $details")
 *         // cancel is handled automatically; optionally flag/ban here
 *     }
 * }
 * ```
 *
 * Note: This requires the Leaf fork. On vanilla Paper the [PlayerMoveRawEvent]
 * will never fire and no checks will run.
 */
class NativeAntiCheat(private val plugin: Plugin) : Listener {

    enum class ViolationType {
        /** Horizontal speed exceeds [maxSpeedHorizontal]. */
        SPEED_HORIZONTAL,
        /** Vertical speed (upward) exceeds [maxSpeedVertical] without elytra/levitation. */
        SPEED_VERTICAL,
        /** Rotation changed more than [maxYawChangeDeg] in a single tick. */
        AIM_SNAP,
    }

    // ── Config ─────────────────────────────────────────────────────────────────

    /** Max horizontal blocks-per-tick before flagging. Default ≈ sprint speed + buffer. */
    var maxSpeedHorizontal: Double = 0.7

    /** Max upward blocks-per-tick before flagging. */
    var maxSpeedVertical: Double = 1.0

    /** Max yaw change per tick in degrees before flagging. */
    var maxYawChangeDeg: Float = 180f

    /** Number of consecutive violations before the callback is invoked. */
    var violationsBeforeFlag: Int = 3

    private var violationHandler: ((Player, ViolationType, String) -> Unit)? = null

    // violation counters
    private val vl = ConcurrentHashMap<UUID, MutableMap<ViolationType, Int>>()

    // ── DSL ────────────────────────────────────────────────────────────────────

    /** Register a violation handler. Called when [violationsBeforeFlag] is reached. */
    fun onViolation(handler: (player: Player, type: ViolationType, details: String) -> Unit) {
        violationHandler = handler
    }

    // ── Listener ───────────────────────────────────────────────────────────────

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onRawMove(event: PlayerMoveRawEvent) {
        val player = event.player
        if (player.isFlying || player.isGliding) return // legitimate flight
        if (!event.hasPosition()) return

        val dx = event.toX - event.fromX
        val dy = event.toY - event.fromY
        val dz = event.toZ - event.fromZ

        val hSpeed = Math.sqrt(dx * dx + dz * dz)
        val vSpeed = dy // positive = upward

        checkViolation(player, ViolationType.SPEED_HORIZONTAL, hSpeed, maxSpeedHorizontal,
            "hSpeed=${String.format("%.3f", hSpeed)} max=$maxSpeedHorizontal") { event.isCancelled = true }

        if (vSpeed > 0) {
            checkViolation(player, ViolationType.SPEED_VERTICAL, vSpeed, maxSpeedVertical,
                "vSpeed=${String.format("%.3f", vSpeed)} max=$maxSpeedVertical") { event.isCancelled = true }
        }

        if (event.hasRotation()) {
            val yawDelta = Math.abs(event.toYaw - event.fromYaw).let {
                if (it > 180f) 360f - it else it
            }
            checkViolation(player, ViolationType.AIM_SNAP, yawDelta.toDouble(), maxYawChangeDeg.toDouble(),
                "yawDelta=${String.format("%.1f", yawDelta)}° max=$maxYawChangeDeg") {}
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private inline fun checkViolation(
        player: Player,
        type: ViolationType,
        value: Double,
        limit: Double,
        details: String,
        onFlag: () -> Unit,
    ) {
        val counts = vl.getOrPut(player.uniqueId) { mutableMapOf() }
        if (value > limit) {
            val count = (counts[type] ?: 0) + 1
            counts[type] = count
            if (count >= violationsBeforeFlag) {
                counts[type] = 0
                onFlag()
                violationHandler?.invoke(player, type, details)
            }
        } else {
            // decay on legit tick
            counts[type] = maxOf(0, (counts[type] ?: 0) - 1)
        }
    }

    /** Reset all violation counters for [player] (e.g. after a teleport). */
    fun resetViolations(player: Player) { vl.remove(player.uniqueId) }
}

/**
 * Create and register a [NativeAntiCheat] for this plugin.
 *
 * ```kotlin
 * plugin.nativeAntiCheat {
 *     maxSpeedHorizontal = 0.65
 *     onViolation { player, type, details -> player.kick(Component.text("Cheating")) }
 * }
 * ```
 */
fun Plugin.nativeAntiCheat(configure: NativeAntiCheat.() -> Unit): NativeAntiCheat =
    NativeAntiCheat(this).apply(configure)
