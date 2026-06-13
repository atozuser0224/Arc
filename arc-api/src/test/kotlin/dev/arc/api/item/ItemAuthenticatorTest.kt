package dev.arc.api.item

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItemAuthenticatorTest {

    @Test
    fun `signs verifies and rotates keys`() {
        val backend = FakeItemAuthBackend()
        val old = authenticator(backend, "old", secret(1))
        val rotating = authenticator(
            backend = backend,
            primaryId = "new",
            primarySecret = secret(2),
            verificationKeys = mapOf("old" to secret(1)),
        )
        val item = backend.item("minecraft:diamond")

        val oldSigned = old.sign(item)
        val newSigned = rotating.sign(item)

        assertEquals(ItemVerification.VALID, rotating.verify(oldSigned))
        assertEquals(ItemVerification.VALID, rotating.verify(newSigned))
        assertEquals(ItemVerification.MISSING, rotating.verify(item))
    }

    @Test
    fun `classifies malformed unsupported unknown and tampered tokens`() {
        val backend = FakeItemAuthBackend()
        val auth = authenticator(backend, "current", secret(3))
        val item = backend.item("minecraft:diamond")

        backend.setToken(item, byteArrayOf(1))
        assertEquals(ItemVerification.INVALID, auth.verify(item))

        backend.setToken(item, byteArrayOf(2, 0))
        assertEquals(ItemVerification.UNSUPPORTED_VERSION, auth.verify(item))

        val other = authenticator(backend, "other", secret(4))
        val unknownKeyItem = other.sign(backend.item("minecraft:diamond"))
        assertEquals(ItemVerification.UNKNOWN_KEY, auth.verify(unknownKeyItem))

        val tampered = auth.sign(backend.item("minecraft:diamond"))
        backend.setType(tampered, "minecraft:emerald")
        assertEquals(ItemVerification.INVALID, auth.verify(tampered))
    }

    @Test
    fun `supports copy in-place strip and amount policies`() {
        val backend = FakeItemAuthBackend()
        val auth = authenticator(backend, "current", secret(5))
        val original = backend.item("minecraft:diamond", amount = 4)

        val signed = auth.sign(original)
        assertFalse(auth.hasToken(original))
        assertTrue(auth.hasToken(signed))
        assertEquals(ItemVerification.VALID, auth.verify(signed))

        backend.setAmount(signed, 2)
        assertEquals(ItemVerification.VALID, auth.verify(signed))

        val stripped = auth.strip(signed)
        assertTrue(auth.hasToken(signed))
        assertFalse(auth.hasToken(stripped))

        auth.signInPlace(original)
        assertTrue(auth.hasToken(original))
        auth.stripInPlace(original)
        assertFalse(auth.hasToken(original))

        val strict = authenticator(
            backend = backend,
            primaryId = "strict",
            primarySecret = secret(6),
            includeAmount = true,
        )
        val strictSigned = strict.sign(backend.item("minecraft:diamond", amount = 4))
        backend.setAmount(strictSigned, 2)
        assertEquals(ItemVerification.INVALID, strict.verify(strictSigned))
    }

    @Test
    fun `rejects weak secrets and invalid or duplicate key ids`() {
        val key = NamespacedKey("arc", "item_auth")

        assertFailsWith<IllegalArgumentException> {
            ItemAuthenticatorBuilder(key).primaryKey("", secret(1))
        }
        assertFailsWith<IllegalArgumentException> {
            ItemAuthenticatorBuilder(key).primaryKey("키", secret(1))
        }
        assertFailsWith<IllegalArgumentException> {
            ItemAuthenticatorBuilder(key).primaryKey("short", ByteArray(31))
        }
        assertFailsWith<IllegalArgumentException> {
            ItemAuthenticatorBuilder(key).apply {
                primaryKey("same", secret(1))
                verificationKey("same", secret(2))
            }
        }
        assertFailsWith<IllegalStateException> {
            ItemAuthenticatorBuilder(key).build(FakeItemAuthBackend())
        }
    }

    private fun authenticator(
        backend: ItemAuthBackend,
        primaryId: String,
        primarySecret: ByteArray,
        verificationKeys: Map<String, ByteArray> = emptyMap(),
        includeAmount: Boolean = false,
    ): ItemAuthenticator =
        ItemAuthenticatorBuilder(NamespacedKey("arc", "item_auth")).apply {
            this.includeAmount = includeAmount
            primaryKey(primaryId, primarySecret)
            verificationKeys.forEach { (id, secret) -> verificationKey(id, secret) }
        }.build(backend)

    private fun secret(seed: Int): ByteArray =
        ByteArray(32) { index -> (seed + index).toByte() }

    private class FakeItemAuthBackend : ItemAuthBackend {
        private data class State(var type: String, var amount: Int)

        private val tokens = IdentityHashMap<ItemStack, ByteArray>()
        private val states = IdentityHashMap<ItemStack, State>()

        override fun copy(item: ItemStack): ItemStack =
            blankItem().also { copy ->
                states[copy] = checkNotNull(states[item]).copy()
                tokens[item]?.let { tokens[copy] = it.copyOf() }
            }

        override fun readToken(item: ItemStack, key: NamespacedKey): ByteArray? =
            tokens[item]?.copyOf()

        override fun writeToken(item: ItemStack, key: NamespacedKey, token: ByteArray) {
            tokens[item] = token.copyOf()
        }

        override fun removeToken(item: ItemStack, key: NamespacedKey) {
            tokens.remove(item)
        }

        override fun payload(
            item: ItemStack,
            key: NamespacedKey,
            includeAmount: Boolean,
        ): ByteArray {
            val state = checkNotNull(states[item])
            val amount = if (includeAmount) state.amount else 1
            return "${state.type}:$amount".toByteArray()
        }

        fun setToken(item: ItemStack, token: ByteArray) {
            tokens[item] = token.copyOf()
        }

        fun item(type: String, amount: Int = 1): ItemStack =
            blankItem().also { states[it] = State(type, amount) }

        fun setType(item: ItemStack, type: String) {
            checkNotNull(states[item]).type = type
        }

        fun setAmount(item: ItemStack, amount: Int) {
            checkNotNull(states[item]).amount = amount
        }

        private fun blankItem(): ItemStack {
            val constructor = ItemStack::class.java.getDeclaredConstructor()
            constructor.isAccessible = true
            return constructor.newInstance()
        }
    }
}
