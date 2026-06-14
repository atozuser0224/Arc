package dev.arc.client

import com.google.gson.JsonParser

public enum class ClientContentType {
    ITEM,
    BLOCK,
    FURNITURE,
    RECIPE,
}

public data class ClientCreativeEntry(
    public val id: String,
    public val type: ClientContentType,
    public val pack: String,
    public val order: Int,
    public val fallback: String,
)

public data class ArcCreativeCatalog(
    public val revision: String,
    public val title: String,
    public val entries: List<ClientCreativeEntry>,
) {
    public companion object {
        @JvmStatic
        public fun parse(bytes: ByteArray): ArcCreativeCatalog {
            val root = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
            val revision = root["revision"]?.asString?.takeIf { it.isNotBlank() }
                ?: error("Catalog revision is missing")
            val title = root["title"]?.asString?.takeIf { it.isNotBlank() }
                ?: error("Catalog title is missing")
            val entries = root.getAsJsonArray("entries")?.map { element ->
                val value = element.asJsonObject
                val id = validateContentId(value["id"].asString)
                val pack = validateContentId(value["pack"].asString)
                val fallback = validateContentId(value["fallback"].asString)
                val type = runCatching {
                    ClientContentType.valueOf(value["type"].asString.uppercase())
                }.getOrElse { error("Unknown catalog content type: ${value["type"].asString}") }
                ClientCreativeEntry(id, type, pack, value["order"].asInt, fallback)
            }.orEmpty()
            check(entries.map { it.id }.distinct().size == entries.size) {
                "Catalog entry IDs must be unique"
            }
            return ArcCreativeCatalog(
                revision,
                title,
                entries.sortedWith(compareBy(ClientCreativeEntry::order, ClientCreativeEntry::id)),
            )
        }

        private val CONTENT_ID = Regex("[a-z0-9._-]+:[a-z0-9/._-]+")

        private fun validateContentId(value: String): String {
            check(CONTENT_ID.matches(value)) { "Invalid content ID: $value" }
            check(value.substringAfter(':').split('/').none { it == "." || it == ".." }) {
                "Content ID must not contain traversal segments: $value"
            }
            return value
        }
    }
}
