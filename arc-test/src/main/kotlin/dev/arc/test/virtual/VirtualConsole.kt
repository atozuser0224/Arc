package dev.arc.test.virtual

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock

private val plain = PlainTextComponentSerializer.plainText()

/**
 * Wraps MockBukkit's console sender for testing admin/console commands.
 *
 * ```kotlin
 * console.runCommand("reload")
 * console.assertMessage("Reloaded successfully")
 * ```
 */
class VirtualConsole internal constructor(private val server: ServerMock) {

    private val sender: ConsoleCommandSenderMock get() = server.consoleSender

    /** Execute a command as the console (without leading slash). */
    fun runCommand(command: String): VirtualConsole = apply {
        server.dispatchCommand(sender, command)
    }

    /** Assert the next message received by the console contains [text]. */
    fun assertMessage(text: String): VirtualConsole = apply {
        val component = sender.nextComponentMessage()
        checkNotNull(component) { "Expected console message containing '$text' but console received nothing" }
        val received = plain.serialize(component)
        check(received.contains(text)) {
            "Expected console message containing:\n  $text\nActual:\n  $received"
        }
    }

    /** Drain all pending console messages without asserting. */
    fun drainMessages(): VirtualConsole = apply {
        while (sender.nextComponentMessage() != null) { /* drain */ }
    }

    /** Collect all pending console messages as plain text. */
    fun messages(): List<String> = buildList {
        while (true) { add(plain.serialize(sender.nextComponentMessage() ?: break)) }
    }

    /** Run tab completion as console and return results for assertion. */
    fun tabComplete(commandLine: String): dev.arc.test.assert.TabCompletionScope =
        dev.arc.test.assert.TabCompletionScope(server.getCommandTabComplete(sender, commandLine))
}
