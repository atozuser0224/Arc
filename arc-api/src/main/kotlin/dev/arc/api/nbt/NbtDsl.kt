@file:JvmName("NbtExtensions")

package dev.arc.api.nbt

import net.minecraft.nbt.ByteTag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.FloatTag
import net.minecraft.nbt.IntTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.LongTag
import net.minecraft.nbt.ShortTag
import net.minecraft.nbt.StringTag
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.entity.Entity
import org.bukkit.inventory.ItemStack

/**
 * Kotlin DSL scope for reading and writing a [CompoundTag].
 *
 * ```kotlin
 * item.editNbt {
 *     set("CustomModelData", 42)
 *     set("display.Name", """{"text":"Legendary Sword"}""")
 *     compound("display") {
 *         set("Name", """{"text":"Legendary Sword"}""")
 *         set("Lore", listOf("A very powerful sword"))
 *     }
 * }
 * entity.readNbt { health -> getFloat("Health") }
 * ```
 */
class NbtScope(@PublishedApi internal val tag: CompoundTag) {

    // ── Write ─────────────────────────────────────────────────────────────────

    operator fun set(key: String, value: Int)     { tag.put(key, IntTag.valueOf(value)) }
    operator fun set(key: String, value: Long)    { tag.put(key, LongTag.valueOf(value)) }
    operator fun set(key: String, value: Short)   { tag.put(key, ShortTag.valueOf(value)) }
    operator fun set(key: String, value: Byte)    { tag.put(key, ByteTag.valueOf(value)) }
    operator fun set(key: String, value: Float)   { tag.put(key, FloatTag.valueOf(value)) }
    operator fun set(key: String, value: Double)  { tag.put(key, DoubleTag.valueOf(value)) }
    operator fun set(key: String, value: String)  { tag.put(key, StringTag.valueOf(value)) }
    operator fun set(key: String, value: Boolean) { tag.put(key, ByteTag.valueOf(value)) }
    operator fun set(key: String, value: IntArray) { tag.putIntArray(key, value) }
    operator fun set(key: String, value: LongArray) { tag.putLongArray(key, value) }
    operator fun set(key: String, value: ByteArray) { tag.putByteArray(key, value) }

    /** Write a nested compound tag. Creates the sub-tag if absent. */
    fun compound(key: String, block: NbtScope.() -> Unit) {
        val child = if (tag.contains(key, 10)) tag.getCompound(key) else CompoundTag()
        NbtScope(child).block()
        tag.put(key, child)
    }

    /** Remove a key. */
    fun remove(key: String) { tag.remove(key) }

    // ── Read ──────────────────────────────────────────────────────────────────

    fun getInt(key: String): Int?     = if (tag.contains(key, 3)) tag.getInt(key) else null
    fun getLong(key: String): Long?   = if (tag.contains(key, 4)) tag.getLong(key) else null
    fun getFloat(key: String): Float? = if (tag.contains(key, 5)) tag.getFloat(key) else null
    fun getDouble(key: String): Double? = if (tag.contains(key, 6)) tag.getDouble(key) else null
    fun getString(key: String): String? = if (tag.contains(key, 8)) tag.getString(key) else null
    fun getBoolean(key: String): Boolean? = if (tag.contains(key, 1)) tag.getBoolean(key) else null
    fun getByte(key: String): Byte?   = if (tag.contains(key, 1)) tag.getByte(key) else null
    fun getIntArray(key: String): IntArray? = if (tag.contains(key, 11)) tag.getIntArray(key) else null
    fun getLongArray(key: String): LongArray? = if (tag.contains(key, 12)) tag.getLongArray(key) else null
    fun getByteArray(key: String): ByteArray? = if (tag.contains(key, 7)) tag.getByteArray(key) else null

    /** Read a nested compound tag. Returns null if absent. */
    fun compound(key: String): NbtScope? =
        if (tag.contains(key, 10)) NbtScope(tag.getCompound(key)) else null

    operator fun contains(key: String): Boolean = tag.contains(key)

    val keys: Set<String> get() = tag.allKeys
}

// ── ItemStack extensions ──────────────────────────────────────────────────────

/**
 * Edit this item's custom NBT data.
 * Returns a new [ItemStack] with the modified NBT — the original is unchanged.
 */
fun ItemStack.editNbt(block: NbtScope.() -> Unit): ItemStack {
    val nms = CraftItemStack.asNMSCopy(this)
    val tag = nms.tag ?: CompoundTag()
    NbtScope(tag).block()
    nms.tag = tag
    return CraftItemStack.asBukkitCopy(nms)
}

/**
 * Read this item's NBT data without modifying it.
 */
fun <T> ItemStack.readNbt(block: NbtScope.() -> T): T? {
    val nms = CraftItemStack.asNMSCopy(this)
    val tag = nms.tag ?: return null
    return NbtScope(tag).block()
}

/** Whether this item has any NBT data. */
val ItemStack.hasNbt: Boolean get() = CraftItemStack.asNMSCopy(this).tag != null

// ── Entity extensions ─────────────────────────────────────────────────────────

/**
 * Edit this entity's NBT data in-place.
 */
fun Entity.editNbt(block: NbtScope.() -> Unit) {
    val nms = (this as CraftEntity).handle
    val tag = CompoundTag()
    nms.save(tag)
    NbtScope(tag).block()
    nms.load(tag)
}

/**
 * Read this entity's NBT data without modifying it.
 */
fun <T> Entity.readNbt(block: NbtScope.() -> T): T {
    val nms = (this as CraftEntity).handle
    val tag = CompoundTag()
    nms.save(tag)
    return NbtScope(tag).block()
}
