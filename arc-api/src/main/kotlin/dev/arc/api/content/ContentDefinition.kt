package dev.arc.api.content

public enum class ContentType {
    ITEM,
    BLOCK,
    FURNITURE,
    RECIPE,
}

public sealed interface ContentDefinition {
    public val id: ContentId
    public val type: ContentType
    public val fallback: ContentId
    public val required: Boolean
    public val order: Int
}

public data class ItemDefinition(
    override val id: ContentId,
    override val fallback: ContentId,
    public val maxStackSize: Int,
    public val durability: Int,
    override val required: Boolean,
    override val order: Int,
) : ContentDefinition {
    override val type: ContentType = ContentType.ITEM
}

public data class BlockDefinition(
    override val id: ContentId,
    override val fallback: ContentId,
    public val hardness: Float,
    public val blastResistance: Float,
    override val required: Boolean,
    override val order: Int,
) : ContentDefinition {
    override val type: ContentType = ContentType.BLOCK
}

public data class FurnitureDefinition(
    override val id: ContentId,
    override val fallback: ContentId,
    public val seats: Int,
    public val width: Int,
    public val depth: Int,
    override val required: Boolean,
    override val order: Int,
) : ContentDefinition {
    override val type: ContentType = ContentType.FURNITURE
}

public data class RecipeDefinition(
    override val id: ContentId,
    public val result: ContentId,
    public val ingredients: List<ContentId>,
    override val required: Boolean,
    override val order: Int,
) : ContentDefinition {
    override val type: ContentType = ContentType.RECIPE
    override val fallback: ContentId = result
}
