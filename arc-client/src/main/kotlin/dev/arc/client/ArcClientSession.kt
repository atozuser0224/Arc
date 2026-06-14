package dev.arc.client

import dev.arc.api.sync.ManifestSigner
import dev.arc.api.sync.SignedManifest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.PublicKey
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

public class ServerTrustException(message: String) : SecurityException(message)

public data class ClientTransferPlan(
    public val missingHashes: List<String>,
    public val totalBytes: Long,
)

public class ArcClientSession(
    private val root: Path,
) {
    private var serverId: String? = null
    private var signedManifest: SignedManifest? = null
    private var cacheDirectory: Path? = null
    private val activeRef = AtomicBoolean()

    public val active: Boolean get() = activeRef.get()

    public fun begin(
        serverId: String,
        publicKey: PublicKey,
        signed: SignedManifest,
    ): ClientTransferPlan {
        require(serverId.isNotBlank()) { "serverId must not be blank" }
        check(signed.manifest.serverId == serverId) { "Manifest server ID does not match connection" }
        check(ManifestSigner.verify(signed, publicKey)) { "ArcSync manifest signature is invalid" }
        pin(serverId, publicKey)

        val serverDirectory = root.resolve("servers").resolve(digest(serverId.toByteArray()))
        val blobsDirectory = serverDirectory.resolve("blobs")
        blobsDirectory.createDirectories()
        this.serverId = serverId
        this.signedManifest = signed
        this.cacheDirectory = blobsDirectory
        activeRef.set(false)

        val missing = signed.manifest.blobs.filterNot { blob ->
            val path = blobsDirectory.resolve(blob.sha256)
            path.exists() &&
                Files.size(path) == blob.size &&
                digest(path.readBytes()) == blob.sha256
        }
        return ClientTransferPlan(missing.map { it.sha256 }, missing.sumOf { it.size })
    }

    public fun store(sha256: String, bytes: ByteArray) {
        val manifest = signedManifest ?: error("ArcSync session has not begun")
        val descriptor = manifest.manifest.blobs.singleOrNull { it.sha256 == sha256 }
            ?: error("Blob is not in active manifest: $sha256")
        require(bytes.size.toLong() == descriptor.size) { "Blob size does not match manifest" }
        require(digest(bytes) == sha256) { "Blob SHA-256 does not match manifest" }
        val directory = cacheDirectory ?: error("ArcSync cache is unavailable")
        val temporary = directory.resolve("$sha256.tmp")
        temporary.writeBytes(bytes)
        Files.move(
            temporary,
            directory.resolve(sha256),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    public fun activate() {
        val manifest = signedManifest ?: error("ArcSync session has not begun")
        val directory = cacheDirectory ?: error("ArcSync cache is unavailable")
        manifest.manifest.blobs.forEach { blob ->
            val path = directory.resolve(blob.sha256)
            check(path.exists()) { "Missing ArcSync blob: ${blob.sha256}" }
            check(Files.size(path) == blob.size && digest(path.readBytes()) == blob.sha256) {
                "Invalid ArcSync blob: ${blob.sha256}"
            }
        }
        activeRef.set(true)
    }

    public fun read(sha256: String): ByteArray? {
        if (!active) return null
        val manifest = signedManifest ?: return null
        if (manifest.manifest.blobs.none { it.sha256 == sha256 }) return null
        val path = cacheDirectory?.resolve(sha256) ?: return null
        return path.takeIf { it.exists() }?.readBytes()
    }

    public fun disconnect() {
        activeRef.set(false)
        serverId = null
        signedManifest = null
        cacheDirectory = null
    }

    public fun replaceTrust(serverId: String, publicKey: PublicKey) {
        val path = trustPath(serverId)
        path.parent.createDirectories()
        path.writeBytes(publicKey.encoded)
    }

    private fun pin(serverId: String, publicKey: PublicKey) {
        val path = trustPath(serverId)
        if (path.exists()) {
            if (!path.readBytes().contentEquals(publicKey.encoded)) {
                throw ServerTrustException("ArcSync key changed for $serverId")
            }
            return
        }
        path.parent.createDirectories()
        path.writeBytes(publicKey.encoded)
    }

    private fun trustPath(serverId: String): Path =
        root.resolve("trust").resolve("${digest(serverId.toByteArray())}.key")

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
