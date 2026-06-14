package dev.arc.server.content

import dev.arc.api.palette.ArcPalette
import dev.arc.api.palette.ArcPaletteCodec
import org.bukkit.Chunk
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType

public class ArcPaletteStore(
    private val readBytes: () -> ByteArray?,
    private val writeBytes: (ByteArray?) -> Unit,
) {
    public fun load(): ArcPalette =
        readBytes()?.let(ArcPaletteCodec::decode) ?: ArcPalette()

    public fun save(palette: ArcPalette) {
        if (palette.size == 0) {
            writeBytes(null)
        } else {
            writeBytes(ArcPaletteCodec.encode(palette))
        }
        palette.markClean()
    }
}

public object ArcChunkPalettes {
    private val key = NamespacedKey("arc", "palette")

    @JvmStatic
    public fun store(chunk: Chunk): ArcPaletteStore {
        val container = chunk.persistentDataContainer
        return ArcPaletteStore(
            readBytes = { container.get(key, PersistentDataType.BYTE_ARRAY) },
            writeBytes = { bytes ->
                if (bytes == null) {
                    container.remove(key)
                } else {
                    container.set(key, PersistentDataType.BYTE_ARRAY, bytes)
                }
            },
        )
    }

    @JvmStatic
    public fun load(chunk: Chunk): ArcPalette = store(chunk).load()

    @JvmStatic
    public fun save(chunk: Chunk, palette: ArcPalette) = store(chunk).save(palette)
}
