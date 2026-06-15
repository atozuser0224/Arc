package dev.arc.api.content

import java.util.function.Consumer

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

    public fun furniture(path: String, block: FurnitureBuilder.() -> Unit) {
        val builder = FurnitureBuilder(ContentId(namespace, path)).apply(block)
        definitions += builder.build()
    }

    public fun recipe(path: String, block: RecipeBuilder.() -> Unit) {
        val builder = RecipeBuilder(ContentId(namespace, path)).apply(block)
        definitions += builder.build()
    }

    // ── Java-friendly Consumer overloads ──────────────────────────────────
    /** Java: `pack.item("path", b -> { b.setFallback("minecraft:paper"); b.setOrder(1); });` */
    public fun item(path: String, block: Consumer<ItemBuilder>): Unit =
        item(path) { block.accept(this) }
    /** Java: `pack.block("path", b -> { b.setHardness(3.0f); });` */
    public fun block(path: String, block: Consumer<BlockBuilder>): Unit =
        block(path) { block.accept(this) }
    /** Java: `pack.furniture("path", f -> { f.setSeats(2); });` */
    public fun furniture(path: String, block: Consumer<FurnitureBuilder>): Unit =
        furniture(path) { block.accept(this) }
    /** Java: `pack.recipe("path", r -> { r.setResult(id); r.getIngredients().add(ing); });` */
    public fun recipe(path: String, block: Consumer<RecipeBuilder>): Unit =
        recipe(path) { block.accept(this) }

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

@ContentDsl
public class FurnitureBuilder internal constructor(
    public var id: ContentId,
) {
    public var fallback: String = "minecraft:barrier"
    public var seats: Int = 0
    public var width: Int = 1
    public var depth: Int = 1
    public var required: Boolean = true
    public var order: Int = 0

    internal fun build(): FurnitureDefinition = FurnitureDefinition(
        id = id,
        fallback = ContentId.parse(fallback),
        seats = seats,
        width = width,
        depth = depth,
        required = required,
        order = order,
    )
}

@ContentDsl
public class RecipeBuilder internal constructor(
    public var id: ContentId,
) {
    public lateinit var result: ContentId
    public val ingredients: MutableList<ContentId> = mutableListOf()
    public var required: Boolean = true
    public var order: Int = 0

    internal fun build(): RecipeDefinition {
        check(::result.isInitialized) { "Recipe $id requires a result" }
        return RecipeDefinition(id, result, ingredients.toList(), required, order)
    }
}

public fun contentPack(
    namespace: String,
    version: String,
    block: ContentPackBuilder.() -> Unit,
): ContentPack = ContentPackBuilder(namespace, version).apply(block).build()
