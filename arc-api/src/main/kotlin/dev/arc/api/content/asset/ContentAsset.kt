package dev.arc.api.content.asset

import java.security.MessageDigest

public class ContentAsset(
    public val path: String,
    bytes: ByteArray,
) {
    public val bytes: ByteArray = bytes.copyOf()
    public val sha256: String = MessageDigest.getInstance("SHA-256")
        .digest(this.bytes)
        .joinToString("") { "%02x".format(it) }

    override fun equals(other: Any?): Boolean =
        other is ContentAsset &&
            path == other.path &&
            sha256 == other.sha256 &&
            bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * path.hashCode() + bytes.contentHashCode()
}
