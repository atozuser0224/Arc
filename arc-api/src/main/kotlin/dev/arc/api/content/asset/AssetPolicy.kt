package dev.arc.api.content.asset

public data class AssetValidation(
    public val accepted: Boolean,
    public val reason: String? = null,
)

public class AssetPolicy(
    public val maxAssetBytes: Int = 8 * 1024 * 1024,
    public val maxPackBytes: Long = 128L * 1024 * 1024,
    public val allowedExtensions: Set<String> = DEFAULT_EXTENSIONS,
) {
    init {
        require(maxAssetBytes > 0) { "maxAssetBytes must be positive" }
        require(maxPackBytes > 0) { "maxPackBytes must be positive" }
        require(allowedExtensions.isNotEmpty()) { "allowedExtensions must not be empty" }
    }

    public fun validate(path: String, bytes: ByteArray): AssetValidation {
        if (path.isBlank() || path.startsWith('/') || path.startsWith('\\') || '\\' in path) {
            return AssetValidation(false, "Asset path must be relative and use forward slashes")
        }
        val segments = path.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) {
            return AssetValidation(false, "Asset path escapes its pack")
        }
        val extension = path.substringAfterLast('.', "").lowercase()
        if (extension !in allowedExtensions) {
            return AssetValidation(false, "Asset extension is not allowed: .$extension")
        }
        if (bytes.size > maxAssetBytes) {
            return AssetValidation(false, "Asset exceeds $maxAssetBytes bytes")
        }
        return AssetValidation(true)
    }

    public companion object {
        @JvmField
        public val DEFAULT_EXTENSIONS: Set<String> =
            setOf("png", "ogg", "json", "mcmeta")
    }
}

public class AssetCollector(
    private val policy: AssetPolicy,
) {
    private val collected = linkedMapOf<String, ContentAsset>()
    private var totalBytes: Long = 0

    public val assets: List<ContentAsset>
        get() = collected.values.toList()

    public fun add(path: String, bytes: ByteArray): AssetValidation {
        val validation = policy.validate(path, bytes)
        if (!validation.accepted) return validation
        if (path in collected) return AssetValidation(false, "Duplicate asset path: $path")
        if (totalBytes + bytes.size > policy.maxPackBytes) {
            return AssetValidation(false, "Pack exceeds ${policy.maxPackBytes} bytes")
        }
        collected[path] = ContentAsset(path, bytes)
        totalBytes += bytes.size
        return AssetValidation(true)
    }
}
