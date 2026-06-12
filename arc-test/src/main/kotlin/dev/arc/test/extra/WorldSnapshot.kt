package dev.arc.test.extra

import org.mockbukkit.mockbukkit.world.WorldMock
import org.bukkit.Location
import org.bukkit.Material

/**
 * Captures world block state before and after an operation for diff-based assertions.
 *
 * ```kotlin
 * snapshot {
 *     before()
 *     world.fillAsync(region, Material.STONE.createBlockData())
 *     after()
 *     assertChangedAtLeast(1)
 *     assertAllMaterial(Material.STONE)
 * }
 * ```
 */
class WorldSnapshot(private val world: WorldMock) {

    private data class BlockState(val x: Int, val y: Int, val z: Int, val material: Material)

    private var beforeState: List<BlockState> = emptyList()
    private var afterState: List<BlockState> = emptyList()

    /** Capture the current state of the world as a baseline. */
    fun before(
        x1: Int = -16, y1: Int = world.minHeight, z1: Int = -16,
        x2: Int =  16, y2: Int = world.maxHeight - 1, z2: Int =  16,
    ) {
        beforeState = capture(x1, y1, z1, x2, y2, z2)
    }

    /** Capture the current state after the operation. */
    fun after(
        x1: Int = -16, y1: Int = world.minHeight, z1: Int = -16,
        x2: Int =  16, y2: Int = world.maxHeight - 1, z2: Int =  16,
    ) {
        afterState = capture(x1, y1, z1, x2, y2, z2)
    }

    /** Assert that exactly [count] blocks changed between before/after. */
    fun assertChanged(count: Int) {
        val changed = diff().size
        check(changed == count) { "Expected $count changed block(s), found $changed" }
    }

    /** Assert that at least [count] blocks changed. */
    fun assertChangedAtLeast(count: Int) {
        val changed = diff().size
        check(changed >= count) { "Expected at least $count changed block(s), found $changed" }
    }

    /** Assert every block in the after-snapshot is [material]. */
    fun assertAllMaterial(material: Material) {
        val wrong = afterState.filter { it.material != material }
        check(wrong.isEmpty()) {
            "Expected all blocks to be $material but found: ${wrong.take(5).map { "${it.x},${it.y},${it.z}=${it.material}" }}"
        }
    }

    /** Assert no blocks changed. */
    fun assertUnchanged() {
        val changed = diff().size
        check(changed == 0) { "Expected no changes but $changed block(s) changed" }
    }

    /** Assert a specific block changed. */
    fun assertBlockChanged(loc: Location) {
        val x = loc.blockX; val y = loc.blockY; val z = loc.blockZ
        val before = beforeState.find { it.x == x && it.y == y && it.z == z }
        val after  = afterState.find  { it.x == x && it.y == y && it.z == z }
        check(before?.material != after?.material) {
            "Expected block at ($x,$y,$z) to change but it stayed ${before?.material}"
        }
    }

    private fun diff(): List<BlockState> = afterState.filter { a ->
        beforeState.none { b -> b.x == a.x && b.y == a.y && b.z == a.z && b.material == a.material }
    }

    private fun capture(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int): List<BlockState> =
        buildList {
            for (y in y1..y2) for (z in z1..z2) for (x in x1..x2) {
                val block = world.getBlockAt(x, y, z)
                add(BlockState(x, y, z, block.type))
            }
        }
}
