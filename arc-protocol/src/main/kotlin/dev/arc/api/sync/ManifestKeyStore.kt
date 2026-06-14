package dev.arc.api.sync

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

public object ManifestKeyStore {
    private const val PUBLIC_FILE = "arcsync-public.key"
    private const val PRIVATE_FILE = "arcsync-private.key"

    @JvmStatic
    public fun loadOrCreate(directory: Path): KeyPair {
        directory.createDirectories()
        val publicPath = directory.resolve(PUBLIC_FILE)
        val privatePath = directory.resolve(PRIVATE_FILE)
        if (publicPath.exists() && privatePath.exists()) {
            val factory = KeyFactory.getInstance("Ed25519")
            return KeyPair(
                factory.generatePublic(X509EncodedKeySpec(publicPath.readBytes())),
                factory.generatePrivate(PKCS8EncodedKeySpec(privatePath.readBytes())),
            )
        }
        check(!publicPath.exists() && !privatePath.exists()) {
            "ArcSync key pair is incomplete in $directory"
        }
        val generated = ManifestSigner.generate()
        writeNew(publicPath, generated.public.encoded)
        writeNew(privatePath, generated.private.encoded)
        return generated
    }

    private fun writeNew(path: Path, bytes: ByteArray) {
        Files.createFile(path)
        path.writeBytes(bytes)
    }
}
