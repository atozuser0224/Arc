package dev.arc.api.network

import org.bukkit.inventory.ItemStack
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Item serialization for cross-server transfer.
 * Uses Bukkit's built-in serialization + NBT envelope.
 */
object ArcItemSerializer {

    const val FORMAT_VERSION = 2
    val MAGIC = "ARCITEM".toByteArray()

    fun serialize(item: ItemStack): ByteArray? {
        return runCatching {
            val baos = ByteArrayOutputStream()
            val dos = DataOutputStream(baos)
            dos.write(MAGIC)
            dos.writeInt(FORMAT_VERSION)

            // Serialize ItemStack via Bukkit's built-in NBT serialization
            val itemBytes = item.serializeAsBytes()
            dos.writeInt(itemBytes.size)
            dos.write(itemBytes)

            dos.writeUTF(item.type.name)
            dos.writeInt(item.amount)
            dos.flush()
            baos.toByteArray()
        }.getOrNull()
    }

    fun deserialize(data: ByteArray): ItemStack? {
        return runCatching {
            val dis = DataInputStream(ByteArrayInputStream(data))
            val magic = ByteArray(MAGIC.size); dis.readFully(magic)
            if (!magic.contentEquals(MAGIC)) return@runCatching null

            val formatVersion = dis.readInt()
            val itemLen = dis.readInt()
            val itemBytes = ByteArray(itemLen); dis.readFully(itemBytes)

            ItemStack.deserializeBytes(itemBytes)
        }.getOrNull()
    }

    fun getItemType(data: ByteArray): String {
        return runCatching {
            val dis = DataInputStream(ByteArrayInputStream(data))
            dis.skipBytes(MAGIC.size + 4) // magic + version
            val itemLen = dis.readInt()
            dis.skipBytes(itemLen)
            dis.readUTF()
        }.getOrDefault("unknown")
    }
}
