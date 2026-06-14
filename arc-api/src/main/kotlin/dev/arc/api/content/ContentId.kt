package dev.arc.api.content

public data class ContentId(
    public val namespace: String,
    public val path: String,
) : Comparable<ContentId> {

    init {
        require(NAMESPACE.matches(namespace)) { "Invalid content namespace: $namespace" }
        require(PATH.matches(path)) { "Invalid content path: $path" }
        require(path.split('/').none { it == "." || it == ".." }) {
            "Content path must not contain traversal segments: $path"
        }
    }

    override fun compareTo(other: ContentId): Int =
        compareValuesBy(this, other, ContentId::namespace, ContentId::path)

    override fun toString(): String = "$namespace:$path"

    public companion object {
        private val NAMESPACE = Regex("[a-z0-9_.-]+")
        private val PATH = Regex("[a-z0-9_.-]+(?:/[a-z0-9_.-]+)*")

        @JvmStatic
        public fun parse(value: String): ContentId {
            val separator = value.indexOf(':')
            require(separator > 0 && separator == value.lastIndexOf(':') && separator < value.lastIndex) {
                "Content ID must use namespace:path: $value"
            }
            return ContentId(value.substring(0, separator), value.substring(separator + 1))
        }
    }
}
