# Item Authenticator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add plugin-scoped, rotation-aware HMAC authentication for Bukkit item stacks.

**Architecture:** `ItemAuthenticator` owns an immutable primary key and verification keyring. An internal backend isolates token storage and canonical payload generation, allowing the cryptographic and policy behavior to be tested without a running server while the production backend uses Paper item PDC and `serializeAsBytes()`.

**Tech Stack:** Kotlin, Bukkit/Paper ItemStack and PDC APIs, JCA HMAC-SHA256, JUnit 5 through `kotlin.test`.

---

### Task 1: Token Format and Keyring Verification

**Files:**
- Create: `arc-api/src/test/kotlin/dev/arc/api/item/ItemAuthenticatorTest.kt`
- Create: `arc-api/src/main/kotlin/dev/arc/api/item/ItemAuthenticator.kt`

- [ ] **Step 1: Write failing keyring tests**

Add tests that build an authenticator with primary key `new` and verification
key `old`, sign a payload, verify `VALID`, verify a token signed by the old key,
and classify missing, malformed, unknown-key, unsupported-version, and
digest-mismatch tokens.

```kotlin
assertEquals(ItemVerification.VALID, auth.verify(auth.sign(item)))
assertEquals(ItemVerification.MISSING, auth.verify(unsigned))
assertEquals(ItemVerification.UNKNOWN_KEY, auth.verify(unknownKeyItem))
assertEquals(ItemVerification.UNSUPPORTED_VERSION, auth.verify(futureItem))
assertEquals(ItemVerification.INVALID, auth.verify(tamperedItem))
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
rtk .\gradlew.bat :arc-api:test --no-daemon -I .codex-item-auth-test.init.gradle
```

Expected: compilation fails because `ItemAuthenticator` and
`ItemVerification` do not exist.

- [ ] **Step 3: Implement the token codec and immutable keyring**

Define:

```kotlin
public enum class ItemVerification {
    VALID, MISSING, INVALID, UNKNOWN_KEY, UNSUPPORTED_VERSION
}

internal data class ItemAuthKey(
    val id: String,
    val secret: ByteArray,
)

internal object ItemAuthTokenCodec {
    const val VERSION: Byte = 1
    fun encode(keyId: String, digest: ByteArray): ByteArray
    fun decode(token: ByteArray): DecodedToken
}
```

Validate key IDs as 1-64 printable ASCII bytes, require secrets of at least 32
bytes, copy all secret arrays defensively, compute HMAC-SHA256 with `Mac`, and
compare digests with `MessageDigest.isEqual`.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the focused Gradle command again. Expected: all keyring and token
classification tests pass.

### Task 2: Bukkit Item Adapter and Amount Policy

**Files:**
- Modify: `arc-api/src/test/kotlin/dev/arc/api/item/ItemAuthenticatorTest.kt`
- Modify: `arc-api/src/main/kotlin/dev/arc/api/item/ItemAuthenticator.kt`
- Delete: `arc-api/src/main/kotlin/dev/arc/api/player/ItemAuthToken.kt`

- [ ] **Step 1: Write failing item-operation tests**

Add tests for copy signing, in-place signing, copy stripping, in-place stripping,
`hasToken`, metadata/type tampering, and both amount policies. Use an internal
fake backend that copies `ItemStack` values, stores token bytes by item identity,
and emits deterministic payload bytes.

```kotlin
val signed = auth.sign(original)
assertFalse(auth.hasToken(original))
assertTrue(auth.hasToken(signed))
assertEquals(ItemVerification.VALID, auth.verify(signed))

signed.amount = 2
assertEquals(ItemVerification.VALID, auth.verify(signed))

val strict = authenticator(includeAmount = true)
val strictSigned = strict.sign(original)
strictSigned.amount = 2
assertEquals(ItemVerification.INVALID, strict.verify(strictSigned))
```

- [ ] **Step 2: Run focused tests and verify RED**

Expected: failures for the missing item operations and backend policy.

- [ ] **Step 3: Implement the public authenticator and production backend**

Expose:

```kotlin
public class ItemAuthenticator {
    public fun sign(item: ItemStack): ItemStack
    public fun signInPlace(item: ItemStack)
    public fun verify(item: ItemStack): ItemVerification
    public fun hasToken(item: ItemStack): Boolean
    public fun strip(item: ItemStack): ItemStack
    public fun stripInPlace(item: ItemStack)
}

public fun Plugin.itemAuthenticator(
    block: ItemAuthenticatorBuilder.() -> Unit,
): ItemAuthenticator
```

The production backend must use `ItemStack.editPersistentDataContainer` for
write/remove, the read-only item PDC view for reads, and a clone with the token
removed before `serializeAsBytes()`. Set the clone amount to one when
`includeAmount` is false.

- [ ] **Step 4: Run focused tests and verify GREEN**

Expected: all authenticator tests pass.

### Task 3: Documentation and Integrated Verification

**Files:**
- Modify: `arc-api/README.md`
- Modify: `ARC_IMPLEMENTATION.md`

- [ ] **Step 1: Document construction, rotation, and verification**

Add a feature-table entry and a concise example showing a primary key, previous
verification key, signing, and exhaustive handling of `ItemVerification`.
Document that amounts are excluded by default and that secrets must be loaded
from configuration rather than source code.

- [ ] **Step 2: Run integrated Arc API verification**

Run focused tests, then compile/test `arc-api` with an init script that excludes
only unrelated untracked NMS prototypes.

Expected: `BUILD SUCCESSFUL`, no test failures, and no compile errors from the
new item API.

- [ ] **Step 3: Check and commit the exact feature scope**

Run:

```powershell
rtk git diff --check
rtk git status --short
```

Stage only the authenticator implementation, tests, documentation, and this
plan. Commit with:

```text
feat(api): add item authentication
```
