package dev.arc.test.assert

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.mockbukkit.mockbukkit.entity.PlayerMock

private val plain = PlainTextComponentSerializer.plainText()

/**
 * Captures action-bar and title packets sent to a [PlayerMock].
 *
 * ```kotlin
 * val player = withPlayer("Steve")
 * // ... trigger some HUD update ...
 * player.packets.assertActionBar("❤ 20")
 * player.packets.assertTitle("LEVEL UP")
 * ```
 */
class PacketCapture internal constructor(private val mock: PlayerMock) {

    // ---- Action bar ----

    /** Dequeue and return the next action-bar text, or null if none pending. */
    fun nextActionBar(): String? = mock.nextActionBar()?.let { plain.serialize(it) }

    /** Assert the next action-bar message contains [text]. */
    fun assertActionBar(text: String) {
        val bar = nextActionBar()
        checkNotNull(bar) { "Expected action bar containing '$text' but none was sent" }
        check(bar.contains(text)) {
            "Expected action bar containing:\n  $text\nActual:\n  $bar"
        }
    }

    /** Assert no action-bar messages are pending. */
    fun assertNoActionBar() {
        val bar = nextActionBar()
        check(bar == null) { "Expected no action bar but received: $bar" }
    }

    /** Drain all pending action-bar messages. */
    fun drainActionBars(): List<String> = buildList {
        while (true) { add(nextActionBar() ?: break) }
    }

    // ---- Title ----

    /**
     * Dequeue and return the next title string (MockBukkit encodes title+subtitle as
     * "title\nsubtitle" — split on newline to get each part), or null if none pending.
     */
    fun nextTitle(): String? = mock.nextTitle()

    /** Assert the next title contains [text] in either title or subtitle portion. */
    fun assertTitle(text: String) {
        val raw = nextTitle()
        checkNotNull(raw) { "Expected title containing '$text' but none was sent" }
        check(raw.contains(text)) {
            "Expected title containing:\n  $text\nActual:\n  $raw"
        }
    }

    /** Assert no titles are pending. */
    fun assertNoTitle() {
        val t = nextTitle()
        check(t == null) { "Expected no title but received: $t" }
    }

    /** Drain all pending titles. */
    fun drainTitles(): List<String> = buildList {
        while (true) { add(nextTitle() ?: break) }
    }

    /** Drain everything (action bars + titles). */
    fun drain() { drainActionBars(); drainTitles() }
}
