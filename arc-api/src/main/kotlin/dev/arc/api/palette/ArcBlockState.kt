package dev.arc.api.palette

import dev.arc.api.content.ContentId

public class ArcBlockState(
    public val id: ContentId,
    state: ByteArray = byteArrayOf(),
) {
    public val state: ByteArray = state.copyOf()

    override fun equals(other: Any?): Boolean =
        other is ArcBlockState && id == other.id && state.contentEquals(other.state)

    override fun hashCode(): Int = 31 * id.hashCode() + state.contentHashCode()

    override fun toString(): String = "ArcBlockState(id=$id, stateBytes=${state.size})"
}
