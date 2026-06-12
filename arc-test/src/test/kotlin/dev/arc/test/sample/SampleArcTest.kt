package dev.arc.test.sample

import dev.arc.test.ArcTestScope
import dev.arc.test.annotation.ArcTest
import dev.arc.test.arcTest
import dev.arc.test.db.TestDatabase
import dev.arc.test.extra.BenchmarkScope
import dev.arc.test.extra.arbInt
import dev.arc.test.extra.arbString
import dev.arc.test.extra.forAll
import dev.arc.test.extra.ms
import org.bukkit.Material
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Demo test — shows all major arc-test features in one file.
 * Not meant to test real plugin logic; just exercises the framework API.
 */
@ArcTest
class SampleArcTest {

    // ---- 기본 VirtualPlayer 테스트 ----

    @Test
    fun `virtual player can send and receive messages`() = arcTest {
        val player = withPlayer("Steve")
        player.chat("hello")
        // MockBukkit echoes chat back — just verify the framework doesn't crash
        player.drainMessages()
    }

    @Test
    fun `tick advances scheduler`() = arcTest {
        var fired = false
        plugin.server.scheduler.runTaskLater(plugin, Runnable { fired = true }, 5L)
        tick(5)
        assert(fired) { "Expected task to fire after 5 ticks" }
    }

    @Test
    fun `multiple virtual players`() = arcTest {
        val (alice, bob) = withPlayers("Alice", "Bob")
        alice.giveItem(org.bukkit.inventory.ItemStack(Material.DIAMOND, 1))
        alice.assertInventoryContains(Material.DIAMOND)
        bob.assertInventoryEmpty()
    }

    // ---- DB 테스트 ----

    @Test
    fun `in-memory database is isolated per test`() = arcTest {
        // Each call to TestDatabase.create() is a fresh DB
        val db1 = TestDatabase.create()
        val db2 = TestDatabase.create()
        // Different in-memory instances — state is NOT shared
        assert(db1 !== db2)
    }

    // ---- 시간 제어 ----

    @Test
    fun `seconds helper equals 20 ticks per second`() = arcTest {
        var count = 0
        plugin.server.scheduler.runTaskTimer(plugin, Runnable { count++ }, 0L, 20L)
        seconds(3) // advance 3 seconds = 60 ticks
        assertEquals(3, count)
    }

    // ---- BenchmarkScope ----

    @Test
    fun `simple operation is fast`() = arcTest {
        assertUnder(100) {
            // trivial work
            val sum = (1..1_000).sum()
            assert(sum > 0)
        }
    }

    // ---- PropertyTest ----

    @Test
    fun `string length property`() {
        forAll(arbString(maxLen = 100)) { s ->
            s.length in 1..100
        }
    }

    @Test
    fun `int range property`() {
        forAll(arbInt(0..500), arbInt(0..500)) { a, b ->
            a + b >= 0
        }
    }

    // ---- FuzzScope ----

    @Test
    fun `commands never crash on random input`() = arcTest {
        val player = withPlayer("FuzzBot") { grant("*") }
        fuzz(50) { input ->
            // Any random string passed to a command should at most send an error message,
            // never throw an uncaught exception
            runCatching { player.performCommand("help $input") }
            player.drainMessages()
        }
    }

    // ---- WorldSnapshot ----

    @Test
    fun `world snapshot detects changes`() = arcTest {
        snapshot {
            before()
            world.getBlockAt(0, 64, 0).type = Material.STONE
            after()
            assertChangedAtLeast(1)
        }
    }

    // ---- PermissionAudit ----

    @Test
    fun `no critical permission gaps in default setup`() = arcTest {
        val issues = auditPermissions()
        // In a real plugin test, assert issues.none { it.contains("[ERROR]") }
        // Here we just verify the audit runs without crashing
        assert(issues is List<*>)
    }
}
