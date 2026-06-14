package dev.arc.api.content

public data class CreativeCatalogEntry(
    public val id: ContentId,
    public val type: ContentType,
    public val packId: ContentId,
    public val order: Int,
)

public data class CreativeCatalog(
    public val title: String,
    public val entries: List<CreativeCatalogEntry>,
)
