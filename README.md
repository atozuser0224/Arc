# Arc Bucket

Arc Bucket is a Minecraft 1.21.4 server built on Paper, Gale, and Leaf compatibility
layers. Arc adds a Kotlin-first server API, runtime operations tooling, diagnostics,
plugin management, and an Arc-branded distribution.

## Requirements

- Java 21 or newer
- A backup before migrating an existing server
- Paper-compatible plugins

## Highlights

- `/arc` administration command with permission-aware tab completion
- Plugin inspection, safety checks, reload policies, rollback, and leak diagnostics
- Server doctor, lag-spike history, profiler, memory, chunk, world, and network reports
- Config search, history, diff, validation, and runtime feature flags
- Typed player data, custom effects, signed items, event DSL, command DSL, and datapack DSL
- Paper/Purpur plugin compatibility
- Leaf configuration and API compatibility for existing deployments
- Async pathfinding, entity tracking, chunk sending, and optional parallel world ticking

Run `/arc help` in the console or as an operator to list the available operations.
The legacy `/leaf` command remains available only for compatibility.

## Build

```bash
./gradlew applyAllPatches
./gradlew createMojmapPaperclipJar
```

The distributable jar is written under `leaf-server/build/libs/` and is named
`arc-paperclip-...jar`.

## Sample Plugin

The `sample-pack/showcase-plugin` project demonstrates the public Arc API:

- `/showcase effect`
- `/showcase give`
- `/showcase verify`
- `/showcase stats`

## Repository

- Source and releases: https://github.com/atozuser0224/Arc
- Issues: https://github.com/atozuser0224/Arc/issues

## Compatibility

Internal `org.dreeam.leaf` packages, `leaf-*` Gradle modules, legacy permissions,
and Leaf configuration filenames are retained where changing them would break
plugins or existing server installations. They are compatibility surfaces, not
the product identity. User-facing build metadata, console branding, commands,
documentation, and artifacts identify the server as Arc Bucket.

## License

Arc Bucket contains code from multiple upstream projects and is distributed under
their respective licenses. See [LICENSE.md](LICENSE.md) for details and attribution.
