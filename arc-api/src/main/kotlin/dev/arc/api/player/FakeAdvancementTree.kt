@file:JvmName("FakeAdvancements")

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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Build a fake advancement tree visible in the player's advancement screen (L key).
 * Useful for quest systems, milestone trackers, or skill trees — without touching
 * real datapack/advancement files.
 *
 * ```kotlin
 * val tree = plugin.fakeAdvancementTree("quests")
 *
 * // Define nodes
 * val root = tree.node("root") {
 *     title("§6Quest Board"); description("Complete quests!")
 *     icon(Material.BOOK); background("minecraft:textures/gui/advancements/backgrounds/adventure.png")
 * }
 * val kill = tree.node("kill_10") {
 *     parent(root); title("§cFighter"); description("Kill 10 players")
 *     icon(Material.IRON_SWORD); type(ToastType.GOAL)
 * }
 *
 * // Show tree to player
 * tree.show(player)
 *
 * // Grant a node (shows toast + marks it done in advancement screen)
 * tree.grant(player, kill)
 *
 * // Remove tree from player
 * tree.hide(player)
 * ```
 */
class FakeAdvancementTree(internal val treeNamespace: String, private val plugin: Plugin) {
    private val namespace: String get() = treeNamespace

    inner class NodeBuilder(private val id: String) {
        private var parentId: String? = null
        private var titleComp: Component = Component.text(id)
        private var descComp: Component = Component.empty()
        private var iconMat: ItemStack = ItemStack(Material.PAPER)
        private var frameType: ToastType = ToastType.TASK
        private var backgroundTex: Optional<ResourceLocation> = Optional.empty()

        fun parent(node: AdvancementNode) { parentId = node.id }
        fun title(t: String) { titleComp = Component.text(t) }
        fun title(t: Component) { titleComp = t }
        fun description(d: String) { descComp = Component.text(d) }
        fun description(d: Component) { descComp = d }
        fun icon(material: Material) { iconMat = ItemStack(material) }
        fun icon(item: ItemStack) { iconMat = item }
        fun type(t: ToastType) { frameType = t }
        fun background(path: String) {
            backgroundTex = Optional.of(ResourceLocation.parse(path))
        }

        internal fun build(): AdvancementNode {
            val resId = ResourceLocation.fromNamespaceAndPath(namespace, id)
            val parentResId = parentId?.let { ResourceLocation.fromNamespaceAndPath(namespace, it) }
            val nmsIcon = CraftItemStack.asNMSCopy(iconMat)
            val display = DisplayInfo(
                nmsIcon,
                PaperAdventure.asVanilla(titleComp),
                PaperAdventure.asVanilla(descComp),
                backgroundTex,
                when (frameType) {
                    ToastType.TASK -> AdvancementType.TASK
                    ToastType.GOAL -> AdvancementType.GOAL
                    ToastType.CHALLENGE -> AdvancementType.CHALLENGE
                },
                false, false, false,
            )
            val criterion = net.minecraft.advancements.Criterion(ImpossibleTrigger.TriggerInstance())
            val adv = Advancement(
                Optional.ofNullable(parentResId),
                Optional.of(display),
                AdvancementRewards.EMPTY,
                mapOf("done" to criterion),
                AdvancementRequirements.allOf(listOf("done")),
                false,
                Optional.of(PaperAdventure.asVanilla(titleComp)),
            )
            return AdvancementNode(id, resId, AdvancementHolder(resId, adv))
        }
    }

    data class AdvancementNode(
        val id: String,
        internal val resId: ResourceLocation,
        internal val holder: AdvancementHolder,
    )

    private val nodes = mutableListOf<AdvancementNode>()
    // Extra AdvancementHolders registered by ProgressNode extensions
    private val extraHolders = mutableListOf<AdvancementHolder>()
    @PublishedApi
    internal fun addProgressNodeInternal(holder: AdvancementHolder) { extraHolders += holder }

    private fun allHolders(): List<AdvancementHolder> = nodes.map { it.holder } + extraHolders
    // viewerUUID → set of granted node IDs
    private val granted = ConcurrentHashMap<UUID, MutableSet<String>>()
    // viewers
    private val viewers = ConcurrentHashMap.newKeySet<UUID>()

    /** Define a node. Must be called before [show]. */
    fun node(id: String, configure: NodeBuilder.() -> Unit): AdvancementNode {
        val node = NodeBuilder(id).apply(configure).build()
        nodes += node
        return node
    }

    /** Send the full tree to [player]. */
    fun show(player: Player) {
        viewers.add(player.uniqueId)
        val progress = buildProgressMap(player.uniqueId)
        (player as CraftPlayer).handle.connection.send(
            ClientboundUpdateAdvancementsPacket(false, allHolders(), emptySet(), progress)
        )
    }

    /** Resync the full tree to [player] (call on rejoin). */
    fun resync(player: Player) {
        if (player.uniqueId !in viewers) return
        show(player)
    }

    /** Remove the tree from [player]'s advancement screen. */
    fun hide(player: Player) {
        viewers.remove(player.uniqueId)
        val ids = (nodes.map { it.resId } + extraHolders.map { it.id() }).toSet()
        (player as CraftPlayer).handle.connection.send(
            ClientboundUpdateAdvancementsPacket(false, emptyList(), ids, emptyMap())
        )
    }

    /** Mark [node] as completed for [player]. Triggers a toast notification. */
    fun grant(player: Player, node: AdvancementNode) {
        granted.getOrPut(player.uniqueId) { mutableSetOf() }.add(node.id)
        val progress = AdvancementProgress()
        val criterion = net.minecraft.advancements.Criterion(ImpossibleTrigger.TriggerInstance())
        progress.update(mapOf("done" to criterion), AdvancementRequirements.allOf(listOf("done")))
        progress.grantProgress("done")
        (player as CraftPlayer).handle.connection.send(
            ClientboundUpdateAdvancementsPacket(false, emptyList(), emptySet(), mapOf(node.resId to progress))
        )
    }

    /** Revoke completion of [node] for [player]. */
    fun revoke(player: Player, node: AdvancementNode) {
        granted[player.uniqueId]?.remove(node.id)
        val progress = AdvancementProgress()
        val criterion = net.minecraft.advancements.Criterion(ImpossibleTrigger.TriggerInstance())
        progress.update(mapOf("done" to criterion), AdvancementRequirements.allOf(listOf("done")))
        (player as CraftPlayer).handle.connection.send(
            ClientboundUpdateAdvancementsPacket(false, emptyList(), emptySet(), mapOf(node.resId to progress))
        )
    }

    /** Whether [node] is granted for [player]. */
    fun isGranted(player: Player, node: AdvancementNode): Boolean =
        granted[player.uniqueId]?.contains(node.id) == true

    private fun buildProgressMap(uuid: UUID): Map<ResourceLocation, AdvancementProgress> {
        val grantedIds = granted[uuid] ?: emptySet()
        return nodes.associate { node ->
            val progress = AdvancementProgress()
            val criterion = net.minecraft.advancements.Criterion(ImpossibleTrigger.TriggerInstance())
            progress.update(mapOf("done" to criterion), AdvancementRequirements.allOf(listOf("done")))
            if (node.id in grantedIds) progress.grantProgress("done")
            node.resId to progress
        }
    }
}

/** Create a [FakeAdvancementTree] with the given namespace. */
fun Plugin.fakeAdvancementTree(namespace: String): FakeAdvancementTree =
    FakeAdvancementTree(namespace, this)
