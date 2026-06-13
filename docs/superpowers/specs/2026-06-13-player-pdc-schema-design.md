# Player PDC Schema DSL Design

## Goal

Provide typed, validated, migration-aware player persistent fields without
repeating raw `PersistentDataContainer` operations throughout plugins.

## API

Existing calls remain source compatible:

```kotlin
val kills = plugin.playerStore("kills", PersistentDataType.INTEGER)
```

Definitions can opt into richer behavior:

```kotlin
val data = plugin.playerDataSchema {
    int("coins") {
        default { 0 }
        validate("coins must be non-negative") { it >= 0 }
        migrateFrom("balance")
    }
    string("rank") {
        default { "member" }
    }
    boolean("tutorial_complete")
}
```

Player operators continue to provide get, set, contains, delete, get-or-put,
mutation, and integer increment. Reads lazily migrate the first matching legacy
key to the current key. Defaults are explicit through `getOrDefault`.

## Rules

- Field definitions are immutable after construction.
- Current values win over legacy values.
- Migration writes the current key and removes the consumed legacy key.
- Validation runs before every write, default initialization, migration, and
  mutation result.
- Invalid values throw `IllegalArgumentException` with the field key and
  configured reason.
- Schema field names must be unique.

## Testing

Dynamic proxies provide server-free `Plugin`, `Player`, and
`PersistentDataContainer` implementations. Tests cover defaults, validation,
operators, mutation, increment, lazy migration, current-value precedence, and
duplicate schema names.

## Documentation

The API README gains a schema example and the implementation log records
validation and migration semantics.
