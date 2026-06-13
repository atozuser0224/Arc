# PDC Schema DSL Design

## Goal

Add a reusable Kotlin schema DSL over Bukkit's `PersistentDataContainer`
without replacing existing PDC extension functions.

## Design

`PdcSchema(namespace) { ... }` owns immutable `PdcField` definitions. Each
field stores a validated `NamespacedKey`, Bukkit `PersistentDataType`, optional
default factory, and optional value validator.

```kotlin
val playerData = pdcSchema("myplugin") {
    val kills = int("kills") { default(0); require { it >= 0 } }
    val title = string("title") { default("Rookie") }
}

player[playerData.kills] = player[playerData.kills]
player.mutate(playerData.kills) { it + 1 }
```

The schema rejects duplicate names and invalid namespaces at definition time.
Fields support custom `PersistentDataType` instances in addition to built-in
primitive helpers.

Operations target any `PersistentDataHolder`: nullable lookup, required/default
lookup, assignment, removal, containment, `getOrPut`, and validated mutation.
Default factories are evaluated only when needed. Validation runs for explicit
writes, defaults, and mutation results.

The existing `PersistentData.kt`, `NbtMap`, and player-specific helpers remain
source compatible. The schema is an additional strongly typed layer.

## Testing

Pure definition tests cover duplicate keys, namespace and key validation,
default behavior, validation, field lookup, and built-in type tokens. Container
operations are compiled against Bukkit and kept thin delegates to its API.

## Documentation

`arc-api/README.md` gains a schema example. `ARC_IMPLEMENTATION.md` records the
new API, compatibility policy, and focused tests.
