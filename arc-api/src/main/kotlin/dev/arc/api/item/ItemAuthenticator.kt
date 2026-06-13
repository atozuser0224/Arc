@file:JvmName("ItemAuthenticators")

package dev.arc.api.item

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Result of checking an item authentication token. */
public enum class ItemVerification {
    VALID,
    MISSING,
    INVALID,
    UNKNOWN_KEY,
    UNSUPPORTED_VERSION,
}

internal interface ItemAuthBackend {
    fun copy(item: ItemStack): ItemStack
    fun readToken(item: ItemStack, key: NamespacedKey): ByteArray?
    fun writeToken(item: ItemStack, key: NamespacedKey, token: ByteArray)
    fun removeToken(item: ItemStack, key: NamespacedKey)
    fun payload(item: ItemStack, key: NamespacedKey, includeAmount: Boolean): ByteArray
}

private object BukkitItemAuthBackend : ItemAuthBackend {
    override fun copy(item: ItemStack): ItemStack = item.clone()

    override fun readToken(item: ItemStack, key: NamespacedKey): ByteArray? =
        item.persistentDataContainer.get(key, PersistentDataType.BYTE_ARRAY)

    override fun writeToken(item: ItemStack, key: NamespacedKey, token: ByteArray) {
        check(item.editPersistentDataContainer { container ->
            container.set(key, PersistentDataType.BYTE_ARRAY, token)
        }) { "Unable to write item authentication token" }
    }

    override fun removeToken(item: ItemStack, key: NamespacedKey) {
        check(item.editPersistentDataContainer { container -> container.remove(key) }) {
            "Unable to remove item authentication token"
        }
    }

    override fun payload(
        item: ItemStack,
        key: NamespacedKey,
        includeAmount: Boolean,
    ): ByteArray {
        val sanitized = item.clone()
        removeToken(sanitized, key)
        if (!includeAmount) sanitized.amount = 1
        return sanitized.serializeAsBytes()
    }
}

internal data class ItemAuthKey(
    val id: String,
    val secret: ByteArray,
)

private data class DecodedItemToken(
    val keyId: String,
    val digest: ByteArray,
)

private sealed interface TokenDecodeResult {
    data class Success(val token: DecodedItemToken) : TokenDecodeResult
    data object Invalid : TokenDecodeResult
    data object UnsupportedVersion : TokenDecodeResult
}

private object ItemAuthTokenCodec {
    private const val VERSION: Int = 1
    private const val DIGEST_SIZE: Int = 32

    fun encode(keyId: String, digest: ByteArray): ByteArray {
        require(digest.size == DIGEST_SIZE) { "Expected a SHA-256 digest" }
        val keyBytes = keyId.toByteArray(StandardCharsets.US_ASCII)
        return ByteArray(2 + keyBytes.size + digest.size).also { token ->
            token[0] = VERSION.toByte()
            token[1] = keyBytes.size.toByte()
            keyBytes.copyInto(token, destinationOffset = 2)
            digest.copyInto(token, destinationOffset = 2 + keyBytes.size)
        }
    }

    fun decode(token: ByteArray): TokenDecodeResult {
        if (token.isEmpty()) return TokenDecodeResult.Invalid
        if (token[0].toInt() and 0xff != VERSION) {
            return TokenDecodeResult.UnsupportedVersion
        }
        if (token.size < 2) return TokenDecodeResult.Invalid
        val keyLength = token[1].toInt() and 0xff
        if (keyLength !in 1..64 || token.size != 2 + keyLength + DIGEST_SIZE) {
            return TokenDecodeResult.Invalid
        }
        val keyId = token.copyOfRange(2, 2 + keyLength).toString(StandardCharsets.US_ASCII)
        if (!isValidKeyId(keyId)) return TokenDecodeResult.Invalid
        return TokenDecodeResult.Success(
            DecodedItemToken(
                keyId = keyId,
                digest = token.copyOfRange(2 + keyLength, token.size),
            ),
        )
    }
}

/** Immutable HMAC-SHA256 item authentication service. */
public class ItemAuthenticator internal constructor(
    private val tokenKey: NamespacedKey,
    private val primaryKey: ItemAuthKey,
    verificationKeys: Map<String, ItemAuthKey>,
    private val includeAmount: Boolean,
    private val backend: ItemAuthBackend,
) {
    private val keys: Map<String, ItemAuthKey> = verificationKeys.toMap()

    /** Return an authenticated copy of [item]. */
    public fun sign(item: ItemStack): ItemStack =
        backend.copy(item).also(::signInPlace)

    /** Add or replace the authentication token on [item]. */
    public fun signInPlace(item: ItemStack) {
        val digest = hmac(primaryKey.secret, backend.payload(item, tokenKey, includeAmount))
        backend.writeToken(item, tokenKey, ItemAuthTokenCodec.encode(primaryKey.id, digest))
    }

    /** Verify [item] without mutating it. */
    public fun verify(item: ItemStack): ItemVerification {
        val encoded = backend.readToken(item, tokenKey) ?: return ItemVerification.MISSING
        return when (val decoded = ItemAuthTokenCodec.decode(encoded)) {
            TokenDecodeResult.Invalid -> ItemVerification.INVALID
            TokenDecodeResult.UnsupportedVersion -> ItemVerification.UNSUPPORTED_VERSION
            is TokenDecodeResult.Success -> {
                val key = keys[decoded.token.keyId] ?: return ItemVerification.UNKNOWN_KEY
                val expected = hmac(key.secret, backend.payload(item, tokenKey, includeAmount))
                if (MessageDigest.isEqual(decoded.token.digest, expected)) {
                    ItemVerification.VALID
                } else {
                    ItemVerification.INVALID
                }
            }
        }
    }

    /** Return whether [item] contains any authentication token. */
    public fun hasToken(item: ItemStack): Boolean =
        backend.readToken(item, tokenKey) != null

    /** Return a copy of [item] without its authentication token. */
    public fun strip(item: ItemStack): ItemStack =
        backend.copy(item).also(::stripInPlace)

    /** Remove the authentication token from [item]. */
    public fun stripInPlace(item: ItemStack) {
        backend.removeToken(item, tokenKey)
    }
}

/** Builder for a plugin-scoped [ItemAuthenticator]. */
public class ItemAuthenticatorBuilder internal constructor(initialTokenKey: NamespacedKey) {
    public var tokenKey: NamespacedKey = initialTokenKey
    public var includeAmount: Boolean = false

    private var primary: ItemAuthKey? = null
    private val verificationKeys = LinkedHashMap<String, ItemAuthKey>()

    /** Configure the key used for new signatures. */
    public fun primaryKey(id: String, secret: ByteArray) {
        val key = validatedKey(id, secret)
        require(id !in verificationKeys) { "Duplicate item authentication key ID: $id" }
        require(primary == null) { "Primary item authentication key is already configured" }
        primary = key
    }

    /** Add a key accepted only while verifying existing signatures. */
    public fun verificationKey(id: String, secret: ByteArray) {
        val key = validatedKey(id, secret)
        require(primary?.id != id && id !in verificationKeys) {
            "Duplicate item authentication key ID: $id"
        }
        verificationKeys[id] = key
    }

    internal fun build(backend: ItemAuthBackend = BukkitItemAuthBackend): ItemAuthenticator {
        val signingKey = checkNotNull(primary) {
            "A primary item authentication key is required"
        }
        val allKeys = LinkedHashMap(verificationKeys)
        allKeys[signingKey.id] = signingKey
        return ItemAuthenticator(tokenKey, signingKey, allKeys, includeAmount, backend)
    }
}

/** Build an item authenticator isolated to this plugin. */
public fun Plugin.itemAuthenticator(
    block: ItemAuthenticatorBuilder.() -> Unit,
): ItemAuthenticator =
    ItemAuthenticatorBuilder(NamespacedKey(this, "item_auth")).apply(block).build()

private fun validatedKey(id: String, secret: ByteArray): ItemAuthKey {
    require(isValidKeyId(id)) {
        "Item authentication key ID must be 1-64 ASCII letters, digits, '.', '_', or '-'"
    }
    require(secret.size >= 32) { "Item authentication secrets must contain at least 32 bytes" }
    return ItemAuthKey(id, secret.copyOf())
}

private fun isValidKeyId(id: String): Boolean =
    id.length in 1..64 && id.all { it.isAsciiKeyChar() }

private fun Char.isAsciiKeyChar(): Boolean =
    this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' ||
        this == '.' || this == '_' || this == '-'

private fun hmac(secret: ByteArray, payload: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret, "HmacSHA256"))
    return mac.doFinal(payload)
}
