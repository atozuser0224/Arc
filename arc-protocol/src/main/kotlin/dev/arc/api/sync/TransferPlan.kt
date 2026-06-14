package dev.arc.api.sync

public data class TransferPlan(
    public val blobs: List<SyncBlob>,
    public val totalBytes: Long,
) {
    public companion object {
        @JvmStatic
        public fun create(
            manifest: ArcSyncManifest,
            cachedHashes: Set<String>,
        ): TransferPlan {
            val missing = manifest.blobs
                .filterNot { it.sha256 in cachedHashes }
                .sortedBy { it.path }
            return TransferPlan(missing, missing.sumOf { it.size })
        }
    }
}
