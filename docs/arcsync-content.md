# Arc Content and ArcSync

Arc lets plugins and file packs declare items, blocks, furniture, and recipes
without registering new vanilla numeric IDs. One Fabric client mod downloads the
signed content revision for each Arc server, activates its resource pack, and
builds the `Arc Content` creative tab automatically.

## Plugin API

```kotlin
plugin.arcContent("magic", "1.0.0") {
    item("ruby_wand") {
        fallback = "minecraft:blaze_rod"
        maxStackSize = 1
        durability = 512
        order = 20
    }
    block("ruby_altar") {
        fallback = "minecraft:redstone_block"
        hardness = 4.0f
        blastResistance = 8.0f
        order = 10
    }
    recipe("ruby_wand_recipe") {
        result = ContentId.parse("magic:ruby_wand")
        ingredients += ContentId.parse("minecraft:stick")
        ingredients += ContentId.parse("minecraft:ruby")
    }
}
```

Publishing is transactional. Duplicate IDs or invalid required definitions
reject the new revision and leave the previous revision active. Disable or
reload a plugin with `plugin.removeArcContent()`.

## File Packs

Place each pack in its own directory under `arc-content/`:

```text
arc-content/magic/
  pack.json
  assets/
    magic/models/item/ruby_wand.json
    magic/textures/item/ruby_wand.png
    magic/items/ruby_wand.json
```

Example `pack.json`:

```json
{
  "namespace": "magic",
  "version": "1.0.0",
  "items": [
    {
      "id": "ruby_wand",
      "fallback": "minecraft:blaze_rod",
      "maxStackSize": 1,
      "durability": 512,
      "order": 20
    }
  ],
  "blocks": [
    {
      "id": "ruby_altar",
      "fallback": "minecraft:redstone_block",
      "hardness": 4.0,
      "blastResistance": 8.0,
      "order": 10
    }
  ],
  "furniture": [],
  "recipes": []
}
```

Asset paths are relative to the resource pack's `assets/` directory. Arc accepts
PNG, OGG, JSON, and MCMETA files only. Defaults are 8 MiB per asset and 128 MiB
per pack. Symbolic links, traversal segments, duplicate paths, executable files,
and oversized assets are rejected.

## Client Behavior

Install the built `arc-client` Fabric mod, Fabric Loader, Fabric API, and Fabric
Language Kotlin for Minecraft 1.21.4. No per-server setup is required.

On connection, the client:

1. negotiates ArcSync on `arc:sync`;
2. verifies the Ed25519-signed manifest;
3. pins the server key on first use and rejects unexpected key changes;
4. downloads only missing SHA-256 blobs into an isolated server cache;
5. creates and enables an immutable revision resource pack;
6. reloads resources and fills the always-present `Arc Content` creative tab.

Creative stacks retain their vanilla fallback item while carrying
`item_model=<content ID>` and `CUSTOM_DATA["arc:id"]`. This gives Arc clients the
server model and stable identity. Vanilla clients continue to see the declared
fallback.

Disconnect disables the active server pack and clears the live catalog. Cached
content remains content-addressed for later connections.

## Blocks and Removal

Custom block identities are stored by namespaced ID in the chunk
`arc:palette` persistent data entry. The palette keeps unknown IDs and state
bytes even when a pack is temporarily missing. Vanilla block states remain the
declared fallback, so chunks never depend on unstable custom registry numbers.

Removing content does not rewrite the palette automatically. Reintroducing the
same ID restores it. Operators or plugins may explicitly migrate old IDs before
permanently deleting a pack.

## Security and Limits

ArcSync transfers declarative assets and metadata only. It never downloads or
loads server JVM bytecode, native libraries, scripts, or arbitrary executables.
Packets, strings, collections, blob sizes, chunks, and in-flight buffers are
bounded. Server requests are restricted to hashes negotiated for that player.

The first server key is stored under the Arc client trust directory. A legitimate
key rotation requires deleting or replacing that pin intentionally. Server
private keys live in `arc-sync/` and should be backed up like other server
identity material.

`ContentBudget` provides bounded queue permits and per-content handler timing.
Repeated budget violations throttle only the offending content ID and recover
after the configured cooldown. `ContentMetrics` records compile time, revision
size, cache hits and misses, transferred bytes, and palette encode time.
