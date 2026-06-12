@file:JvmName("VirtualPlayers")

package dev.arc.test.virtual

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.mockbukkit.mockbukkit.entity.PlayerMock
import org.mockbukkit.mockbukkit.ServerMock
import dev.arc.test.assert.MessageAssertions
import dev.arc.test.assert.PacketCapture
import dev.arc.test.assert.TabCompletionScope
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.UUID

private val plain = PlainTextComponentSerializer.plainText()

/**
 * A scriptable fake player that wraps MockBukkit's [PlayerMock].
 *
 * ```kotlin
 * val player = withPlayer("Steve") { grant("shop.buy") }
 * player.chat("hello")
 * player.performCommand("shop buy sword")
 * player.assertMessage("구매 완료!")
 * player.assertInventoryContains(Material.DIAMOND_SWORD)
 * ```
 */
class VirtualPlayer internal constructor(
    internal val mock: PlayerMock,
    val plugin: Plugin,
) : MessageAssertions {

    /** Captures action-bar and title packets sent to this player. */
    val packets: PacketCapture = PacketCapture(mock)

    val name: String get() = mock.name
    val uniqueId: UUID get() = mock.uniqueId

    // ---- Actions ----

    /** Simulate this player sending a chat message. */
    fun chat(message: String): VirtualPlayer = apply { mock.chat(message) }

    /** Simulate this player running a command (without the leading slash). */
    fun performCommand(command: String): VirtualPlayer = apply { mock.performCommand(command) }

    /** Simulate this player breaking a block. */
    fun interactLeft(x: Int, y: Int, z: Int): VirtualPlayer = apply {
        mock.simulateBlockBreak(mock.world.getBlockAt(x, y, z))
    }

    /** Simulate this player placing/right-clicking a block. */
    fun interactRight(x: Int, y: Int, z: Int): VirtualPlayer = apply {
        mock.simulateBlockPlace(mock.world.getBlockAt(x, y, z).type, mock.world.getBlockAt(x, y, z).location)
    }

    /** Teleport this virtual player. */
    fun move(x: Double, y: Double, z: Double): VirtualPlayer = apply {
        val loc = mock.location.clone()
        loc.x = x; loc.y = y; loc.z = z
        mock.teleport(loc)
    }

    /** Give an item directly to this player's inventory. */
    fun giveItem(item: ItemStack): VirtualPlayer = apply { mock.inventory.addItem(item) }

    /** Set the item in the player's main hand. */
    fun holdItem(item: ItemStack): VirtualPlayer = apply { mock.inventory.setItemInMainHand(item) }

    // ---- Message assertions ----

    /** Assert the next message received contains [text] (plain-text comparison). */
    override fun assertMessage(text: String): VirtualPlayer = apply {
        val component = mock.nextComponentMessage()
        checkNotNull(component) { "Expected message containing '$text' but player received nothing" }
        val received = plain.serialize(component)
        check(received.contains(text, ignoreCase = false)) {
            "Expected message containing:\n  $text\nActual:\n  $received"
        }
    }

    override fun assertNoMessages(): VirtualPlayer = apply {
        val next = mock.nextComponentMessage()
        check(next == null) { "Expected no messages but player received: ${next?.let { plain.serialize(it) }}" }
    }

    // ---- Inventory assertions ----

    fun assertInventoryContains(material: Material): VirtualPlayer = apply {
        check(mock.inventory.contains(material)) {
            "Expected inventory to contain $material but it did not"
        }
    }

    fun assertInventoryEmpty(): VirtualPlayer = apply {
        val contents = mock.inventory.contents.filterNotNull()
        check(contents.isEmpty()) { "Expected empty inventory but found: ${contents.map { it.type }}" }
    }

    // ---- Permission assertions ----

    fun assertHasPermission(permission: String): VirtualPlayer = apply {
        check(mock.hasPermission(permission)) { "Expected player to have permission '$permission'" }
    }

    fun assertNoPermission(permission: String): VirtualPlayer = apply {
        check(!mock.hasPermission(permission)) { "Expected player NOT to have permission '$permission'" }
    }

    // ---- Util ----

    /** Grant a permission for this test session. */
    fun grant(permission: String): VirtualPlayer = apply { mock.addAttachment(plugin, permission, true) }

    /** Revoke a permission for this test session. */
    fun revoke(permission: String): VirtualPlayer = apply { mock.addAttachment(plugin, permission, false) }

    /** Drain ALL pending messages without asserting. */
    fun drainMessages(): VirtualPlayer = apply { while (mock.nextComponentMessage() != null) { /* drain */ } }

    /** Collect all pending messages as plain text strings. */
    fun messages(): List<String> = buildList {
        while (true) { add(plain.serialize(mock.nextComponentMessage() ?: break)) }
    }

    // ---- Tab completion ----

    /**
     * Run tab completion for [commandLine] (without leading slash) and return a
     * [TabCompletionScope] for chained assertions.
     *
     * ```kotlin
     * player.tabComplete("shop ").assertContains("buy", "sell")
     * player.tabComplete("shop b").assertExact("buy")
     * ```
     */
    fun tabComplete(commandLine: String): TabCompletionScope {
        val server = mock.server as ServerMock
        val results = server.getCommandTabComplete(mock, commandLine)
        return TabCompletionScope(results)
    }
}
