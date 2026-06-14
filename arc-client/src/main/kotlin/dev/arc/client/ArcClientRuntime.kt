package dev.arc.client

import dev.arc.api.sync.SignedManifest
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.PublicKey
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

public class ArcClientRuntime(
    private val root: Path,
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

    @JvmOverloads
    public fun activate(resourcePackRoot: Path = root.resolve("packs")): Path {
        session.activate()
        val signed = manifest ?: error("ArcSync session has not begun")
        val catalogBlob = signed.manifest.blobs
            .singleOrNull { it.path == CATALOG_PATH }
            ?: error("ArcSync manifest does not contain $CATALOG_PATH")
        val bytes = session.read(catalogBlob.sha256)
            ?: error("ArcSync catalog is unavailable")
        catalog = ArcCreativeCatalog.parse(bytes)
        return materialize(signed, resourcePackRoot)
    }

    public fun disconnect() {
        session.disconnect()
        manifest = null
        catalog = null
    }

    private companion object {
        const val CATALOG_PATH = "arc/catalog.json"
        const val PACK_FORMAT = 46
    }

    private fun materialize(signed: SignedManifest, resourcePackRoot: Path): Path {
        val packs = resourcePackRoot.also(Path::createDirectories)
        val target = packs.resolve("arc-${signed.manifest.revision}")
        if (isMaterialized(target, signed)) return target
        deleteTree(target)

        val staging = Files.createTempDirectory(packs, "${signed.manifest.revision}.tmp-")
        try {
            staging.resolve("pack.mcmeta").writeText(
                """{"pack":{"pack_format":$PACK_FORMAT,"description":"Arc server content"}}""",
            )
            signed.manifest.blobs.forEach { blob ->
                val destination = staging.resolve("assets").resolve(blob.path).normalize()
                check(destination.startsWith(staging.resolve("assets"))) {
                    "ArcSync asset escapes resource pack: ${blob.path}"
                }
                destination.parent.createDirectories()
                destination.writeBytes(
                    session.read(blob.sha256) ?: error("ArcSync blob is unavailable: ${blob.sha256}"),
                )
            }
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: FileAlreadyExistsException) {
                deleteTree(staging)
            }
            return target
        } catch (error: Throwable) {
            deleteTree(staging)
            throw error
        }
    }

    private fun deleteTree(root: Path) {
        if (!root.exists()) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private fun isMaterialized(target: Path, signed: SignedManifest): Boolean {
        if (!target.resolve("pack.mcmeta").exists()) return false
        return signed.manifest.blobs.all { blob ->
            val path = target.resolve("assets").resolve(blob.path).normalize()
            path.startsWith(target.resolve("assets")) &&
                path.exists() &&
                session.read(blob.sha256)?.contentEquals(path.readBytes()) == true
        }
    }
}
