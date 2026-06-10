# arc-server

Server-side implementation of [`arc-api`](../arc-api).

Everything that normally forces plugins into NMS/reflection is bridged here and
exposed through `arc-api`'s `ArcNms`. The implementation (`dev.arc.server.nms`)
talks to the mojang-mapped runtime **purely through reflection** (`Reflect`), so
this module takes no compile dependency on the volatile internals and keeps
building across Minecraft / mapping bumps. Lookups are cached; every call
degrades gracefully if a name can't be resolved.

Wire it in once during start-up. `install(plugin)` also registers the built-in
`/arc` control command; `install()` wires the NMS bridge only:

```kotlin
dev.arc.server.ArcBootstrap.install(plugin)   // NMS bridge + /arc command
// or: dev.arc.server.ArcBootstrap.install()  // NMS bridge only
```

The bridge is wrapped in `GatedArcNms`, so every NMS group obeys its
`ArcFeatures` flag (`arc:nms`, `arc:nms.blocks`, …) live — disabled groups fall
back to no-ops.

After that `dev.arc.api.Arc.nms` resolves to a live, grouped `ArcNms`:

```kotlin
// entities
Arc.nms.entities.saveNbt(entity)            // full NBT -> SNBT (no Bukkit equivalent)
Arc.nms.entities.loadNbt(entity, snbt)      // write NBT back
Arc.nms.entities.byId(world, networkId)     // lookup by network id (Bukkit only does UUID)
Arc.nms.entities.ageTicks(entity)           // Entity#tickCount

// items / blocks
Arc.nms.items.describeNms(item)             // dump data components / NBT
Arc.nms.blocks.setNoPhysics(block, data)    // set block with no neighbour physics
Arc.nms.blocks.blockLight(block)            // raw light levels
Arc.nms.blocks.skyLight(block)

// players / server
Arc.nms.players.sendPacket(player, packet)  // raw packet to the connection
Arc.nms.players.ping(player)                // ServerPlayer#latency
Arc.nms.server.tps()                        // recent TPS averages
Arc.nms.server.mspt()                       // mean tick time
Arc.nms.server.diagnostics()                // reflection cache hit/miss counters

// generic escape hatch — reach ANY NMS handle not modelled above
val level = Arc.nms.handle(world)           // CraftWorld -> ServerLevel
val nmsItem = Arc.nms.items.nmsCopy(item)
```

Async entry points never move mutable world/entity access off-thread. They split
pure work from NMS mutation instead:

```kotlin
Arc.nms.entities.loadNbtAsync(plugin, entity, snbt)
    .whenComplete { _, error -> error?.printStackTrace() }

Arc.nms.blocks.setNoPhysicsBatchAsync(plugin, mutations, maxMillisPerTick = 2)
    .thenAccept { result -> logger.info("changed ${result.processed} blocks") }
```

`loadNbtAsync` parses SNBT on Bukkit's async executor, then resumes on the
entity's owning tick thread. Packet sends follow the player scheduler; chunk
packet construction and block changes follow the owning region scheduler.

Synchronous NMS calls validate Paper region ownership. The default
`Arc.settings.nmsThreadPolicy = STRICT` rejects unsafe access; `WARN` logs once
per operation and `UNSAFE` is available only for controlled server internals.

The bridge probes and pre-caches version-sensitive NMS classes during server
bootstrap. `/arc nms capabilities` reports unsupported groups after a mapping
or Minecraft update; `/arc nms` reports resolution and invocation failures.

The `handle(...)` escape hatch guarantees that anything reachable via NMS is
reachable via the API, even when there's no typed method for it yet.
