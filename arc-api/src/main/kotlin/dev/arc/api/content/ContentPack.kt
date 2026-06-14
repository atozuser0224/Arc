package dev.arc.api.content

public data class ContentPack(
    public val id: ContentId,
    public val version: String,
    public val definitions: List<ContentDefinition>,
)

@DslMarker
public annotation class ContentDsl

@ContentDsl
public class ContentPackBuilder internal constructor(
    private val namespace: String,
    private val version: String,
) {
    private val definitions = mutableListOf<ContentDefinition>()

    public fun item(path: String, block: ItemBuilder.() -> Unit) {
        val builder = ItemBuilder(ContentId(namespace, path)).apply(block)
        definitions += builder.build()
    }

    public fun block(path: String, block: BlockBuilder.() -> Unit) {
        val builder = BlockBuilder(ContentId(namespace, path)).apply(block)
        definitions += builder.build()
    }

    internal fun build(): ContentPack =
        ContentPack(ContentId(namespace, "pack"), version, definitions.toList())
}

@ContentDsl
public class ItemBuilder internal constructor(
    public var id: ContentId,
) {
    public var fallback: String = "minecraft:paper"
    public var maxStackSize: Int = 64
    public var durability: Int = 0
    public var required: Boolean = true
    public var order: Int = 0

    internal fun build(): ItemDefinition = ItemDefinition(
        id = id,
        fallback = ContentId.parse(fallback),
        maxStackSize = maxStackSize,
        durability = durability,
        required = required,
        order = order,
    )
}

@ContentDsl
public class BlockBuilder internal constructor(
    public var id: ContentId,
) {
    public var fallback: String = "minecraft:stone"
    public var hardness: Float = 1.0f
    public var blastResistance: Float = 1.0f
    public var required: Boolean = true
    public var order: Int = 0

    internal fun build(): BlockDefinition = BlockDefinition(
        id = id,
        fallback = ContentId.parse(fallback),
        hardness = hardness,
        blastResistance = blastResistance,
        required = required,
        order = order,
    )
}

public fun contentPack(
    namespace: String,
    version: String,
    block: ContentPackBuilder.() -> Unit,
): ContentPack = ContentPackBuilder(namespace, version).apply(block).build()
