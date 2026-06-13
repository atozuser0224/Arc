@file:JvmName("FakeAdvancementProgresses")

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
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A fake advancement node with N discrete steps, displayed as checkboxes in the
 * advancement screen. Each [increment] call checks one box; when all N steps are
 * checked the node is visually complete.
 *
 * Works independently of [FakeAdvancementTree] but can be added to one via
 * [FakeAdvancementTree.addProgressNode].
 *
 * ```kotlin
 * val tree = plugin.fakeAdvancementTree("quests")
 *
 * val killNode = tree.progressNode("kill_players", steps = 10) {
 *     title("Warrior"); description("Kill 10 players")
 *     icon(Material.IRON_SWORD); type(ToastType.GOAL)
 * }
 *
 * // On each kill:
 * killNode.increment(player)   // checks one box, grants toast when all done
 *
 * // Or set directly:
 * killNode.setProgress(player, 7)
 *
 * // Query:
 * val done = killNode.getProgress(player)  // 0..steps
 * ```
 */
class ProgressNode internal constructor(
    val id: String,
    private val namespace: String,
    val steps: Int,
    private val holder: AdvancementHolder,
    private val showToastOnComplete: Boolean,
) {
    internal val resId: ResourceLocation = ResourceLocation.fromNamespaceAndPath(namespace, id)

    // playerUUID → completed step count
    private val progress = ConcurrentHashMap<UUID, Int>()

    /** Get the current step count for [player] (0 to [steps]). */
    fun getProgress(player: Player): Int = progress[player.uniqueId] ?: 0

    /** Whether [player] has completed all [steps]. */
    fun isComplete(player: Player): Boolean = getProgress(player) >= steps

    /**
     * Increment [player]'s progress by 1. If this reaches [steps], the node is
     * visually completed and a toast is shown (if [showToastOnComplete] is true).
     * Returns the new step count.
     */
    fun increment(player: Player): Int {
        val current = progress.getOrDefault(player.uniqueId, 0)
        if (current >= steps) return current
        return setProgress(player, current + 1)
    }

    /**
     * Set [player]'s step count to [step] (clamped to 0..[steps]).
     * Sends the appropriate progress update packet.
     * Returns the new step count.
     */
    fun setProgress(player: Player, step: Int): Int {
        val clamped = step.coerceIn(0, steps)
        progress[player.uniqueId] = clamped
        sendProgressPacket(player, clamped)
        return clamped
    }

    /** Reset [player]'s progress to 0. */
    fun reset(player: Player) = setProgress(player, 0)

    // ── Internal ──────────────────────────────────────────────────────────────

    internal fun buildNmsProgress(stepCount: Int): AdvancementProgress {
        val criteria = buildCriteriaMap()
        val reqs = buildRequirements()
        val p = AdvancementProgress()
        p.update(criteria, reqs)
        for (i in 0 until stepCount) p.grantProgress("step_$i")
        return p
    }

    internal fun buildCriteriaMap() = (0 until steps).associate {
        "step_$it" to net.minecraft.advancements.Criterion(ImpossibleTrigger.TriggerInstance())
    }

    internal fun buildRequirements() =
        AdvancementRequirements.allOf((0 until steps).map { "step_$it" })

    private fun sendProgressPacket(player: Player, stepCount: Int) {
        val p = buildNmsProgress(stepCount)
        (player as CraftPlayer).handle.connection.send(
            ClientboundUpdateAdvancementsPacket(false, emptyList(), emptySet(), mapOf(resId to p))
        )
    }
}

// ── FakeAdvancementTree extension ─────────────────────────────────────────

/**
 * Add a multi-step progress node to this tree.
 * The node is registered in the tree and shown to all viewers via [FakeAdvancementTree.show].
 */
fun FakeAdvancementTree.progressNode(
    id: String,
    steps: Int,
    showToastOnComplete: Boolean = true,
    configure: ProgressNodeBuilder.() -> Unit,
): ProgressNode {
    val builder = ProgressNodeBuilder(id, steps).apply(configure)
    return builder.build(namespace = this.namespace, tree = this, showToastOnComplete)
}

/** Builder for [ProgressNode] — used by [FakeAdvancementTree.progressNode]. */
class ProgressNodeBuilder internal constructor(
    private val id: String,
    private val steps: Int,
) {
    private var parentId: String? = null
    private var titleComp: Component = Component.text(id)
    private var descComp: Component = Component.empty()
    private var iconItem: ItemStack = ItemStack(Material.PAPER)
    private var frameType: ToastType = ToastType.TASK
    private var bgTex: Optional<ResourceLocation> = Optional.empty()

    fun parent(node: FakeAdvancementTree.AdvancementNode) { parentId = node.id }
    fun parent(node: ProgressNode) { parentId = node.id }
    fun title(t: String) { titleComp = Component.text(t) }
    fun title(t: Component) { titleComp = t }
    fun description(d: String) { descComp = Component.text(d) }
    fun description(d: Component) { descComp = d }
    fun icon(material: Material) { iconItem = ItemStack(material) }
    fun icon(item: ItemStack) { iconItem = item }
    fun type(t: ToastType) { frameType = t }
    fun background(path: String) { bgTex = Optional.of(ResourceLocation.parse(path)) }

    internal fun build(namespace: String, tree: FakeAdvancementTree, showToast: Boolean): ProgressNode {
        val resId = ResourceLocation.fromNamespaceAndPath(namespace, id)
        val parentResId = parentId?.let { ResourceLocation.fromNamespaceAndPath(namespace, it) }
        val nmsIcon = CraftItemStack.asNMSCopy(iconItem)
        val display = DisplayInfo(
            nmsIcon,
            PaperAdventure.asVanilla(titleComp),
            PaperAdventure.asVanilla(descComp),
            bgTex,
            when (frameType) {
                ToastType.TASK -> AdvancementType.TASK
                ToastType.GOAL -> AdvancementType.GOAL
                ToastType.CHALLENGE -> AdvancementType.CHALLENGE
            },
            showToast, false, false,
        )
        val criteria = (0 until steps).associate {
            "step_$it" to net.minecraft.advancements.Criterion(ImpossibleTrigger.TriggerInstance())
        }
        val requirements = AdvancementRequirements.allOf(criteria.keys.toList())
        val adv = Advancement(
            Optional.ofNullable(parentResId),
            Optional.of(display),
            AdvancementRewards.EMPTY,
            criteria,
            requirements,
            false,
            Optional.of(PaperAdventure.asVanilla(titleComp)),
        )
        val nodeHolder = AdvancementHolder(resId, adv)
        val node = ProgressNode(id, namespace, steps, nodeHolder, showToast)
        tree.addProgressNodeInternal(nodeHolder)
        return node
    }
}

// Expose the namespace and internal hook on FakeAdvancementTree
val FakeAdvancementTree.namespace: String get() = this.treeNamespace
