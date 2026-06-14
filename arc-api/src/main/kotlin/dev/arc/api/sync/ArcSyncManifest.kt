package dev.arc.api.sync

public data class SyncBlob(
    public val path: String,
    public val sha256: String,
    public val size: Long,
) {
    init {
        require(path.isNotBlank() && !path.startsWith('/') && '\\' !in path) {
            "Blob path must be relative and use forward slashes: $path"
        }
        require(path.split('/').none { it == "." || it == ".." }) {
            "Blob path must not contain traversal segments: $path"
        }
        require(sha256.isNotBlank()) { "Blob hash must not be blank" }
        require(size >= 0) { "Blob size must not be negative" }
    }
}

public data class ArcSyncManifest(
    public val protocolVersion: Int,
    public val serverId: String,
    public val revision: String,
    public val requiredFeatures: Set<ArcSyncFeature>,
    public val blobs: List<SyncBlob>,
) {
    init {
        require(protocolVersion > 0) { "Protocol version must be positive" }
        require(serverId.isNotBlank()) { "Server ID must not be blank" }
        require(revision.isNotBlank()) { "Revision must not be blank" }
        require(blobs.map { it.path }.distinct().size == blobs.size) { "Blob paths must be unique" }
        require(blobs.map { it.sha256 }.distinct().size == blobs.size) { "Blob hashes must be unique" }
    }

    public fun canonicalBytes(): ByteArray = buildString {
        append("arc-sync-manifest\n")
        append(protocolVersion).append('\n')
        append(serverId).append('\n')
        append(revision).append('\n')
        requiredFeatures.sortedBy { it.name }.forEach {
            append("feature|").append(it.name).append('\n')
        }
        blobs.sortedWith(compareBy(SyncBlob::path, SyncBlob::sha256)).forEach {
            append("blob|").append(it.path).append('|')
                .append(it.sha256).append('|').append(it.size).append('\n')
        }
    }.toByteArray(Charsets.UTF_8)
}
