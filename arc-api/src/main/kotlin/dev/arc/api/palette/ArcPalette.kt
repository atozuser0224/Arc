package dev.arc.api.palette

public data class ArcPalettePosition(
    public val x: Int,
    public val y: Int,
    public val z: Int,
) : Comparable<ArcPalettePosition> {
    init {
        require(x in 0..15) { "Local x must be in 0..15: $x" }
        require(z in 0..15) { "Local z must be in 0..15: $z" }
    }

    override fun compareTo(other: ArcPalettePosition): Int =
        compareValuesBy(this, other, ArcPalettePosition::y, ArcPalettePosition::z, ArcPalettePosition::x)
}

public data class ArcPaletteEntry(
    public val position: ArcPalettePosition,
    public val block: ArcBlockState,
)

public class ArcPalette {
    private val entries = linkedMapOf<ArcPalettePosition, ArcBlockState>()

    public var dirty: Boolean = false
        private set

    public val size: Int get() = entries.size

    public fun set(x: Int, y: Int, z: Int, block: ArcBlockState): ArcBlockState? {
        val previous = entries.put(ArcPalettePosition(x, y, z), copy(block))
        dirty = true
        return previous?.let(::copy)
    }

    public fun get(x: Int, y: Int, z: Int): ArcBlockState? =
        entries[ArcPalettePosition(x, y, z)]?.let(::copy)

    public fun remove(x: Int, y: Int, z: Int): ArcBlockState? {
        val removed = entries.remove(ArcPalettePosition(x, y, z)) ?: return null
        dirty = true
        return copy(removed)
    }

    public fun entries(): List<ArcPaletteEntry> =
        entries.entries
            .sortedBy { it.key }
            .map { ArcPaletteEntry(it.key, copy(it.value)) }

    public fun markClean() {
        dirty = false
    }

    private fun copy(block: ArcBlockState): ArcBlockState = ArcBlockState(block.id, block.state)
}
