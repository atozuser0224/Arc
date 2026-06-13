# Item Authenticator Design

## Goal

Provide a plugin-scoped, rotation-aware HMAC authentication API for Bukkit
`ItemStack` values. Signed items must detect metadata tampering without relying
on global mutable state or silently accepting unverifiable items.

## API

```kotlin
val authenticator = plugin.itemAuthenticator {
    primaryKey("2026-06", secretBytes)
    verificationKey("2026-01", previousSecretBytes)
    includeAmount = false
}

val signed = authenticator.sign(item)
when (authenticator.verify(signed)) {
    ItemVerification.VALID -> use(signed)
    else -> reject(signed)
}
```

The authenticator is an immutable instance after construction. It exposes
copying and in-place sign/strip operations, `hasToken`, and structured
verification results.

## Token Format

The PDC token uses `PersistentDataType.BYTE_ARRAY` and contains:

1. one format-version byte;
2. one unsigned key-ID length byte;
3. the UTF-8 key ID;
4. a 32-byte HMAC-SHA256 digest.

Key IDs are limited to printable ASCII and 1-64 bytes. Secrets must contain at
least 32 bytes. Signing always uses the primary key. Verification selects a key
by ID, allowing old items to remain valid during key rotation.

## Canonical Payload

The Bukkit adapter clones the item, removes the authentication token, and calls
`ItemStack.serializeAsBytes()` on the sanitized copy. This covers material,
damage, names, lore, enchantments, attributes, custom model data, and other
serialized metadata without maintaining a fragile hand-written field list.

Amount is normalized to one by default so normal consumption, stack splitting,
and merging do not invalidate authentication. `includeAmount = true` preserves
the original amount for currency-like stacks where quantity is part of the
protected value.

The token key namespace belongs to the plugin and defaults to
`<plugin>:item_auth`. A custom key can be supplied when migrating an existing
system.

## Verification

`verify` returns:

- `VALID`: token format, key ID, and digest are valid;
- `MISSING`: no token is present;
- `INVALID`: malformed token or digest mismatch;
- `UNKNOWN_KEY`: the token references a key not in the verification keyring;
- `UNSUPPORTED_VERSION`: the token version is not supported.

Digest comparison uses `MessageDigest.isEqual`. Missing initialization is not a
runtime state because construction requires a primary key.

## Error Handling

- Duplicate key IDs are rejected during construction.
- Blank, oversized, or non-ASCII key IDs are rejected.
- Secrets shorter than 32 bytes are rejected.
- The primary key is automatically included in the verification keyring.
- Signing fails if the item has no mutable metadata container.
- Verification never throws for untrusted token bytes; malformed input maps to
  `INVALID`.

## Testing

Pure tests cover token encoding, malformed input, key selection, key rotation,
constant payload verification, tamper detection, and unsupported versions.
Bukkit adapter tests cover amount normalization and token removal from the
canonical payload using a replaceable payload encoder/token store boundary, so
the tests do not require a running Minecraft server.

## Compatibility

The existing untracked `ItemAuthToken` prototype is replaced rather than
published. No stable Arc API depends on its global `init` contract.
