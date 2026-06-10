# arc-api

Pure-Kotlin developer API on top of the Bukkit / Paper / Leaf API.

It introduces ergonomic, allocation-conscious helpers that the Java API does not:

| Area | Entry point | What you get |
|------|-------------|--------------|
| Scheduling | `dev.arc.api.scheduling.*` | scheduler wrappers plus non-blocking `callSync` / `callAsync` future pipelines |
| Coroutines | `dev.arc.api.coroutine.*` | plugin-lifecycle scopes and tick-accurate `plugin.delayTicks(20)` |
| Events | `dev.arc.api.event.on` | `plugin.on<PlayerJoinEvent> { }` — no Listener class, no annotations |
| Commands | `dev.arc.api.command.command` | `plugin.command("heal") { executes { … } }` — runtime registration, no `plugin.yml` |
| Items | `dev.arc.api.item.*` | `item(Material.X) { name(...); lore(...) }` builder DSL |
| Menus | `dev.arc.api.menu.menu` | `plugin.menu(3, title) { slot(13, item) { … } }` chest-GUI DSL with click routing |
| Players | `dev.arc.api.player.*` | `player.send("<red>hi")`, `actionBar`, `title` (MiniMessage) |
| Geometry | `dev.arc.api.math.*` | Vector/Location operators, **lazy** `a.blocksTo(b)` cuboid `Sequence` (O(1) memory) |
| Text | `dev.arc.api.text.*` | `"<red>hi".mm()` MiniMessage helpers |
| Cooldowns | `dev.arc.api.util.Cooldown` | per-key rate-limit tracker: `if (cd.tryUse(id)) …` |
| Load spreading | `dev.arc.api.scheduling.TickDispatcher` | drains bursty work under a per-tick ms budget — turns one-tick freezes into smooth spread |
| Async batches | `dispatchRegionBatch(...)` | region-owned, future-based batches with time and concurrency backpressure |
| TTL cache | `dev.arc.api.util.TtlCache` | memoize expensive per-tick lookups to once per interval |
| Object pool | `dev.arc.api.util.ObjectPool` | reuse scratch objects in hot paths to cut GC pressure |
| NMS bridge | `dev.arc.api.Arc.nms` | grouped `ArcNms` — `entities`/`items`/`blocks`/`players`/`server` + generic `handle()` escape hatch for anything only the server internals can provide |
| **Control hub** | `dev.arc.api.Arc` | `Arc.settings` (live tunables) + `Arc.features` (runtime on/off flags) — retune & toggle everything Arc adds, from the API |

`ArcNms` is an interface only; the implementation ships in **arc-server** and is
registered at start-up via `ArcBootstrap.install()`.

## Controlling Arc at runtime

Everything Arc adds is steerable from `dev.arc.api.Arc` — no restart, no config
file required:

```kotlin
// Tunables (thread-safe, take effect on next use):
Arc.settings.tickBudgetMillis = 4        // TickDispatcher budget, live
Arc.settings.regionBatchConcurrency = 2 // max active chunk-region slices
Arc.settings.dispatcherMaxPending = 10_000 // bounded queue backpressure
Arc.settings.onChange { key -> log("arc setting changed: $key") }

// Feature flags (built-ins registered on by default):
Arc.features.disable(ArcFeatures.TICK_DISPATCHER)  // stop load-spreading
Arc.features.enable(ArcFeatures.MENUS)
Arc.features.all().forEach { println("${it.id} = ${it.isEnabled}") }
Arc.features.onToggle { f -> log("${f.id} -> ${f.isEnabled}") }

// Per-NMS-group flags — disable just block edits, or all NMS at once:
Arc.features.disable(ArcFeatures.NMS_BLOCKS)
Arc.features.disable(ArcFeatures.NMS)              // master kill-switch
// (disabled groups fall back to harmless no-ops, live — see GatedArcNms)

// Register your own toggle and gate your code on it:
val mine = Arc.features.register("myplugin:fancy", default = true)
if (mine.isEnabled) doFancyThing()
```

Every common NMS operation has a non-blocking future form (`saveNbtAsync`,
`pingAsync`, `blockLightAsync`, `sendPacketAsync`, etc.). Entity work follows
the entity scheduler and block work follows the owning region scheduler.
Use `byIdInChunkAsync` instead of world-wide `byIdAsync` when an entity's chunk
is known; it performs the lookup on that chunk's owning region.

On Leaf 1.21.4 with parallel world ticking enabled, `arc-server` replaces
Paper's global fallback RegionScheduler with the target world's serialized
tick executor. With PWT disabled, normal Paper scheduler behavior is retained.

`TickDispatcher.trySubmit` is the non-blocking producer path: it returns false
when its bounded queue is full. `submit` throws `RejectedExecutionException`
instead of allowing unbounded memory growth, and `close()` cancels its repeating
scheduler task and discards pending work.

`/arc nms` displays reflection cache and thread-safety diagnostics;
`/arc nms clear-cache` invalidates all version-sensitive cached members.

Built-in feature ids (`ArcFeatures.BUILTINS`): `MENUS`, `TICK_DISPATCHER`,
`COROUTINES`, `NMS`, `NMS_ENTITIES`, `NMS_ITEMS`, `NMS_BLOCKS`, `NMS_PLAYERS`,
`NMS_SERVER`.

Helpers can pull live defaults from settings:

```kotlin
val cache = TtlCache.withDefaults<Chunk, Int> { it.entities.size }   // uses Arc.settings.defaultTtlMillis
val pool  = ObjectPool.withDefaults(factory = { StringBuilder() })   // uses Arc.settings.defaultPoolSize
```

### Built-in `/arc` command

`arc-server`'s `ArcBootstrap.install(plugin)` also registers an admin command
(permission `arc.admin`) to drive all of the above in-game:

```
/arc features                     # list flags + state
/arc settings                     # list tunables
/arc disable arc:tick-dispatcher  # toggle a flag live
/arc set tickBudgetMillis 4       # retune a setting live
/arc nms capabilities             # mapping/version compatibility report
```
