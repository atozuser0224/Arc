package dev.arc.api.sync

import dev.arc.api.content.CompiledContentRevision
import dev.arc.api.content.ContentType
import dev.arc.api.content.asset.ContentAsset
import java.security.KeyPair
import java.util.concurrent.atomic.AtomicReference

public class ArcSyncService(
    public val serverId: String,
    private val keys: KeyPair,
) {
    private data class Snapshot(
        val signed: SignedManifest,
        val blobs: Map<String, ContentAsset>,
    )

    private val snapshotRef = AtomicReference<Snapshot?>(null)

    init {
        require(serverId.isNotBlank()) { "serverId must not be blank" }
    }

    public val current: SignedManifest?
        get() = snapshotRef.get()?.signed

    public fun publish(
        revision: CompiledContentRevision,
        assets: List<ContentAsset>,
    ): SignedManifest {
        require(assets.map { it.path }.distinct().size == assets.size) {
            "Asset paths must be unique"
        }
        require(assets.map { it.sha256 }.distinct().size == assets.size) {
            "Asset hashes must be unique"
        }
        val features = buildSet {
            add(ArcSyncFeature.CREATIVE_TABS)
            revision.definitions.values.forEach {
                when (it.type) {
                    ContentType.ITEM -> add(ArcSyncFeature.CUSTOM_ITEMS)
                    ContentType.BLOCK -> add(ArcSyncFeature.CUSTOM_BLOCKS)
                    ContentType.FURNITURE -> add(ArcSyncFeature.FURNITURE)
                    ContentType.RECIPE -> Unit
                }
            }
        }
        val manifest = ArcSyncManifest(
            protocolVersion = ArcSyncProtocol.VERSION,
            serverId = serverId,
            revision = revision.hash,
            requiredFeatures = features,
            blobs = assets.map { SyncBlob(it.path, it.sha256, it.bytes.size.toLong()) },
        )
        val signed = ManifestSigner.sign(manifest, keys.private)
        snapshotRef.set(Snapshot(signed, assets.associateBy { it.sha256 }))
        return signed
    }

    public fun plan(hello: ClientHello): TransferPlan {
        check(hello.protocolVersion == ArcSyncProtocol.VERSION) {
            "Unsupported ArcSync protocol ${hello.protocolVersion}"
        }
        val snapshot = snapshotRef.get() ?: error("ArcSync content has not been published")
        return TransferPlan.create(snapshot.signed.manifest, hello.cachedHashes)
    }

    public fun blob(sha256: String): ByteArray? =
        snapshotRef.get()?.blobs?.get(sha256)?.bytes?.copyOf()
}

public object ArcSync {
    private val serviceRef = AtomicReference<ArcSyncService?>(null)

    @JvmStatic
    public fun install(service: ArcSyncService?) {
        serviceRef.set(service)
    }

    @JvmStatic
    public val service: ArcSyncService?
        get() = serviceRef.get()
}
