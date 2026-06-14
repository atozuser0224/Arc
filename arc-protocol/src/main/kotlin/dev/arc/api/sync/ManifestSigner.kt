package dev.arc.api.sync

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

public data class SignedManifest(
    public val manifest: ArcSyncManifest,
    public val signature: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is SignedManifest &&
            manifest == other.manifest &&
            signature.contentEquals(other.signature)

    override fun hashCode(): Int = 31 * manifest.hashCode() + signature.contentHashCode()
}

public object ManifestSigner {

    @JvmStatic
    public fun generate(): KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    @JvmStatic
    public fun sign(manifest: ArcSyncManifest, privateKey: PrivateKey): SignedManifest {
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(privateKey)
        signer.update(manifest.canonicalBytes())
        return SignedManifest(manifest, signer.sign())
    }

    @JvmStatic
    public fun verify(signed: SignedManifest, publicKey: PublicKey): Boolean =
        runCatching {
            val verifier = Signature.getInstance("Ed25519")
            verifier.initVerify(publicKey)
            verifier.update(signed.manifest.canonicalBytes())
            verifier.verify(signed.signature)
        }.getOrDefault(false)
}
