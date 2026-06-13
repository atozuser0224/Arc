# Arc API Showcase — Server Pack

A ready-to-run Arc (Leaf 1.21.4) server bundled with the **ArcShowcase** plugin,
which demonstrates the flagship arc-api developer features.

## Run

1. Requires **JDK 21** (the pack's `start.bat` points at `corretto-21.0.10`; edit it if yours differs).
2. Double-click `start.bat` (or run `java -Xmx2G -jar server.jar nogui`).
3. Connect with a 1.21.4 client to `localhost:25599` (offline mode, creative, flat world).

`eula.txt` is already set to `true`.

## What the plugin shows (`/showcase ...`)

| Command | Arc API feature |
|---------|-----------------|
| `/showcase effect` | **Custom effect registry** — applies `mana_surge` (visual Regeneration, per-tick callback, removal callback) |
| `/showcase give`   | **Item builder + Item authenticator** — gives an HMAC-SHA256-signed "Arc Blade" |
| `/showcase verify` | **Item authenticator** — verifies the item in your main hand (VALID / INVALID / MISSING / …) |
| `/showcase stats`  | **Player PDC schema** — typed, validated, migration-aware persistent counter |

Also active automatically:

- **Datapack DSL** — on enable the plugin generates + deploys the `arc_showcase`
  datapack (loot table, block tag, load function). Check `world/datapacks/`.
- **Event API** — join message reads your stored command count (no Listener class).
- **Command DSL** — `/showcase` is registered at runtime, no `plugin.yml` command block.

Built-in Arc admin command is also available: `/arc features`, `/arc settings`, `/arc nms capabilities`.

## Layout

```
server/
├── server.jar            # Arc/Leaf 1.21.4 runnable paperclip jar
├── start.bat
├── eula.txt
├── server.properties     # offline, flat, creative, port 25599
└── plugins/
    └── ArcShowcase.jar    # the showcase plugin
```

Plugin source: `../showcase-plugin/` (Gradle Kotlin project, compiles against arc-api).
