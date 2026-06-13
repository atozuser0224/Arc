# Datapack DSL Design

## Goal

Turn the prototype datapack writer into a safe, testable Kotlin DSL for
Minecraft 1.21.4 resources while preserving the existing plugin entry point.

## Architecture

`DatapackContent` is a Bukkit-independent in-memory pack model. It validates
resource IDs and paths, stores text resources by normalized relative path, and
writes packs to an explicitly supplied directory. `DatapackMaker` extends that
model with Bukkit deployment, enable, and reload operations.

JSON builders use Gson trees instead of interpolated JSON strings. This ensures
correct escaping and gives tests structural JSON assertions.

## Public Surface

The existing entry point remains:

```kotlin
val pack = plugin.datapackMaker("arc_content") {
    lootTable("arc:dungeon") {
        pool(rolls = 1..3) {
            entry("minecraft:diamond", weight = 1, count = 1..2)
        }
    }
    tag("blocks", "arc:ores") {
        add("minecraft:diamond_ore")
    }
    function("arc:load") {
        +"say Arc datapack loaded"
    }
    advancement("arc:first_join", rawJson)
    predicate("arc:is_night", rawJson)
}
```

`blocks`, `items`, and other legacy plural tag registry names are normalized to
the singular 1.21.4 directory names.

## Safety

- Pack names allow lowercase alphanumeric characters, dots, underscores, and
  hyphens only.
- Resource IDs require a valid lowercase namespace and non-empty safe path.
- Raw paths must be relative, normalized, and remain inside the pack root.
- Duplicate paths fail instead of silently overwriting content.
- Deploy uses UTF-8 and creates `pack.mcmeta` with pack format 61 by default.
- An Arc manifest records generated files. Later deploys delete only stale
  files listed in the previous manifest, never unrelated user files.

## Builders

- `BiomeBuilder` validates ranges, spawn counts, weights, and resource IDs.
- `LootTableBuilder` validates non-empty positive roll ranges and entries.
- `TagBuilder` emits escaped IDs and supports nested tag references.
- `FunctionBuilder` emits newline-terminated `.mcfunction` files and rejects
  embedded newline characters per command.
- Advancement, predicate, and recipe APIs accept validated raw JSON in this
  increment; typed builders can be added independently later.

## Testing

Tests cover resource ID validation, JSON escaping, directory naming, duplicate
rejection, path traversal rejection, function output, deployment layout,
manifest-based stale cleanup, and preservation of unrelated files.

## Documentation

`arc-api/README.md` gains a datapack example. `ARC_IMPLEMENTATION.md` records
the resource types, safety model, tests, and runtime reload limitations.
