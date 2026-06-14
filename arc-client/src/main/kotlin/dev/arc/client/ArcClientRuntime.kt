package dev.arc.client

import dev.arc.api.sync.SignedManifest
import java.nio.file.Path
import java.security.PublicKey

public class ArcClientRuntime(
    root: Path,
) {
    private val session = ArcClientSession(root)
    private var manifest: SignedManifest? = null

    public val active: Boolean
        get() = session.active

    public var catalog: ArcCreativeCatalog? = null
        private set

    public fun begin(
        serverId: String,
        publicKey: PublicKey,
        signed: SignedManifest,
    ): ClientTransferPlan {
        catalog = null
        manifest = signed
        return session.begin(serverId, publicKey, signed)
    }

    public fun accept(sha256: String, bytes: ByteArray) {
        session.store(sha256, bytes)
    }

    public fun activate() {
        session.activate()
        val catalogBlob = manifest?.manifest?.blobs
            ?.singleOrNull { it.path == CATALOG_PATH }
            ?: error("ArcSync manifest does not contain $CATALOG_PATH")
        val bytes = session.read(catalogBlob.sha256)
            ?: error("ArcSync catalog is unavailable")
        catalog = ArcCreativeCatalog.parse(bytes)
    }

    public fun disconnect() {
        session.disconnect()
        manifest = null
        catalog = null
    }

    private companion object {
        const val CATALOG_PATH = "arc/catalog.json"
    }
}
