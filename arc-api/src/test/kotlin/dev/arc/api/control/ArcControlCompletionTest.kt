package dev.arc.api.control

import dev.arc.api.ops.plugin.PluginCommands
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArcControlCompletionTest {
    @Test
    fun `completion reaches deeply nested operations`() {
        assertEquals(
            listOf("--dry-run", "--apply", "--force"),
            ArcControlCommand.complete(listOf("plugin", "reload-chain", "Example", "")),
        )
        assertEquals(
            listOf("on", "off"),
            ArcControlCommand.complete(listOf("network", "maintenance", "network", "")),
        )
    }

    @Test
    fun `reload chain completion omits flags already supplied`() {
        assertEquals(
            listOf("--dry-run", "--force"),
            ArcControlCommand.complete(listOf("plugin", "reload-chain", "Example", "--apply", "")),
        )
    }

    @Test
    fun `api version comparison is numeric`() {
        assertTrue(PluginCommands.isApiVersionOlderThan("1.9", 1, 20))
        assertFalse(PluginCommands.isApiVersionOlderThan("1.20", 1, 20))
        assertFalse(PluginCommands.isApiVersionOlderThan("1.21.4", 1, 20))
        assertFalse(PluginCommands.isApiVersionOlderThan("not-a-version", 1, 20))
    }
}
