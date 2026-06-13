# Arc Server Software — Implementation Documentation

## Project Overview

Arc is a Leaf-based (Paper/Purpur fork) Minecraft server software that provides:

1. **Paper/Leaf plugin compatibility** — never breaks Bukkit/Paper API semantics
2. **Plugin Lifecycle Manager** — per-plugin enable/disable/reload with safety ratings
3. **Arc Developer API** — additional APIs without replacing JavaPlugin
4. **Operations suite** — diagnostics, monitoring, and admin tools
5. **Multi-server network** — Redis-based cross-server player transfer, item mail, and management

## 2026-06-13: Kotlin Custom Effect Registry

Arc now includes a plugin-scoped virtual effect registry in
`dev.arc.api.effect`:

- `plugin.customEffects { effect("id") { ... } }` Kotlin DSL;
- immutable `NamespacedKey` definitions and duplicate-key validation;
- `KEEP_STRONGER`, `REPLACE`, `EXTEND`, and `IGNORE` reapplication policies;
- application, interval tick, expiration, and reason-aware removal callbacks;
- optional Bukkit potion effects for vanilla-client HUD visibility;
- shared visual ownership reconciliation between custom effects;
- explicit lookup, active snapshots, unregister, clear, and idempotent shutdown;
- callback exception isolation with effect and player diagnostics;
- a Bukkit-independent deterministic state engine.

Focused tests cover application, all reapplication policies, tick ordering,
expiration, explicit and bulk removal, immutable snapshots, invalid inputs, and
duration-to-tick ceiling conversion.

Known limitation: Bukkit does not expose potion-effect ownership. If another
plugin applies the same visual potion type, Arc cannot reliably restore that
unrelated effect after the final Arc custom effect is removed.

### Supporting API fixes

- `ModChannel` no longer leaks `FriendlyByteBuf`, Netty, or NMS types through
  `arc-api`. The new `ModPacketBuffer` provides network-order primitives,
  Minecraft-compatible VarInt strings and byte arrays, UUIDs, bounds checks,
  and read/write mode validation.
- `WorldDataStore.keys()` now uses the Paper 1.21 `NamespacedKey` string
  property instead of the Adventure `Key` factory method.
- `ModPacketBuffer` has focused round-trip and malformed-input tests.

## 2026-06-13: Safe Datapack Kotlin DSL

The prototype datapack writer was replaced with a tested two-layer API:

- `DatapackContent`: Bukkit-independent resource model and deployment engine;
- `DatapackMaker`: Bukkit world-container, enable, and reload adapter;
- validated `ResourceId` and pack-relative path handling;
- Gson-backed biome, loot table, and tag generation;
- raw recipe, advancement, and predicate JSON resources;
- `.mcfunction` builder with one-command-per-line validation;
- Minecraft 1.21.4 singular resource and tag directory normalization;
- duplicate resource rejection and UTF-8 output;
- `.arc-manifest` cleanup that removes stale Arc files while preserving
  unrelated files in the same pack.

Focused tests cover path and ID rejection, JSON parsing and escaping, resource
directory layout, function output, invalid loot ranges, duplicate resources,
pack metadata, stale cleanup, and preservation of manually managed files.

## 2026-06-13: General PDC Schema DSL

Arc now provides immutable, reusable typed fields for every Bukkit
`PersistentDataHolder`:

- `pdcSchema("namespace") { int(...); string(...); custom(...) }`;
- plugin-derived namespaces through `plugin.pdcSchema`;
- built-in primitive, boolean, string, and array field factories;
- optional lazy defaults and value validation;
- duplicate and unsafe field-name rejection;
- typed get/set/remove/contains, `getOrPut`, and mutation operations;
- schema lookup and definition introspection.

The existing primitive PDC helpers and `NbtMap` remain source compatible.
Focused tests verify type tokens, lazy defaults, validation, duplicate
rejection, namespace safety, and schema lookup.

## 2026-06-13: Player PDC Schema DSL

Typed player persistent fields now support policy-driven declarations:

- `plugin.playerDataSchema { int(...); string(...); boolean(...) }`;
- immutable `PlayerStore` definitions;
- optional default suppliers through `getOrDefault`;
- validation on writes, defaults, migrations, and mutation results;
- lazy migration from one or more legacy keys;
- current-key precedence without deleting untouched legacy data;
- typed get/set/contains/delete operators, `getOrPut`, `mutate`, and overflow
  checked integer `increment`;
- duplicate schema field rejection and definition introspection.

Dynamic-proxy tests exercise the actual Bukkit Player/PDC extension functions
without starting a Minecraft server.

## Architecture Principle

```
READ-ONLY DIAGNOSTICS → DEFAULT ON
BEHAVIOR-CHANGING OPTIMIZATIONS → DEFAULT OFF
DANGEROUS OPERATIONS → CONFIRM REQUIRED
ALL FEATURES → CONFIG CONTROLLABLE
```

---

## Implemented Features (Single Server)

### 1. Plugin Lifecycle Manager
**Files**: `ops/plugin/PluginLifecycle.kt`, `PluginCommands.kt`, `ReloadSafetyAnalyzer.kt`, `DependencyGraph.kt`, `ConfirmManager.kt`

| Command | Function |
|---------|----------|
| `/arc plugin list` | All plugins with status |
| `/arc plugin info <name>` | Full plugin report |
| `/arc plugin check <name>` | Reload safety rating (SAFE/WARNING/UNSAFE/UNKNOWN) |
| `/arc plugin enable <name>` | Enable a plugin |
| `/arc plugin disable <name>` | Disable a plugin (with dependent check) |
| `/arc plugin reload <name>` | Disable → LeakCheck → Enable |
| `/arc plugin restart <name>` | Disable + Enable |
| `/arc plugin dependents <name>` | Show dependency tree |
| `/arc plugin reload-chain <name> --dry-run\|--apply` | Chain reload with dependents |
| `/arc plugin leaks <name>` | Classloader leak detection |
| `/arc plugins report` | Safety report for all plugins |
| `/arc plugins outdated` | Compatibility check (api-version) |

### 2. Plugin Sandbox Check
**File**: `ops/plugin/PluginSandboxCheck.kt`

| Command | Function |
|---------|----------|
| `/arc sandbox check <jar>` | Analyze JAR before loading — NMS usage, threads, native libs, api-version |

### 3. Plugin Rollback Manager
**File**: `ops/plugin/PluginRollbackManager.kt`

| Command | Function |
|---------|----------|
| `/arc plugin rollback <name> --list` | List available rollbacks |
| `/arc plugin rollback <name> --restore <id>` | Restore previous JAR |

### 4. Reload Policy
**File**: `ops/plugin/ReloadPolicy.kt`

| Command | Function |
|---------|----------|
| `/arc reload-policy list` | Show per-plugin reload policies |
| `/arc reload-policy set <p> <allow\|warn\|block>` | Set policy |

### 5. Plugin Overrides
**File**: `ops/config/PluginOverrides.kt`

| Command | Function |
|---------|----------|
| `/arc plugin-overrides list` | Show all exclusions |
| `/arc plugin-overrides exclude <feature> <plugin>` | Exclude plugin from feature |
| `/arc plugin-overrides include <feature> <plugin>` | Re-include |

### 6. Diagnostics
**Files**: `ops/doctor/ServerDoctor.kt`, `LagSpikeMonitor.kt`, `PluginCostTracker.kt`, `MainThreadProfiler.kt`

| Command | Function |
|---------|----------|
| `/arc doctor [--world <w>] [--paste]` | Full server health report |
| `/arc lagspike [list\|last]` | Lag spike history |
| `/arc plugin-cost [top\|<plugin>]` | Per-plugin cost profiling |
| `/arc profiler [start\|stop\|report]` | Main thread profiler |
| `/arc memory` | Heap usage |
| `/arc ping` | Player ping list |
| `/arc startup-profile [detail <p>]` | Boot time analysis |

### 7. Config Management
**Files**: `ops/config/ConfigCommands.kt`, `ConfigChangeHistory.kt`, `ConfigValidator.kt`

| Command | Function |
|---------|----------|
| `/arc config check` | Validate all configs |
| `/arc config explain <path>` | Show meaning, default, performance impact |
| `/arc config get <path>` | Read current value |
| `/arc config set <path> <value>` | Change value (with backup) |
| `/arc config reset <path>` | Reset to default |
| `/arc config reload` | Reload config from disk |
| `/arc config diff` | Show changes since last backup |
| `/arc config search <keyword>` | Find config entries |
| `/arc config-history list\|diff\|search` | Who changed what |

### 8. Server Tools
**Files**: `commands/CommandSearch.kt`, `permissions/PermissionSearch.kt`, `worlds/WorldCommands.kt`, `logs/LogCommands.kt`

| Command | Function |
|---------|----------|
| `/arc command search <kw>` | Find commands |
| `/arc command info <name>` | Command details (owner, permission, aliases) |
| `/arc permission search <kw>` | Find permissions |
| `/arc permission plugin <p>` | Plugin's permission nodes |
| `/arc permission player <p> <node>` | Check player permission |
| `/arc world report [world]` | World status, entities, chunks |
| `/arc world gamerules <world>` | Game rules |
| `/arc chunks report\|tickets\|backlog` | Chunk diagnostics |
| `/arc logs summary\|errors\|plugin <p>` | Log inspection |

### 9. Security & Maintenance
**Files**: `security/SecurityAudit.kt`, `proxy/ProxySetupChecker.kt`, `safe/SafeModeCommands.kt`, `audit/AuditLog.kt`

| Command | Function |
|---------|----------|
| `/arc security-audit` | Offline-mode, RCON, OP count checks |
| `/arc proxy-check` | Velocity/BungeeCord config validation |
| `/arc safe-mode enable\|disable` | Safe mode toggle |
| `/arc maintenance on\|off\|allow\|status` | Maintenance mode |
| `/arc audit [last\|since\|search]` | Who ran dangerous commands |

### 10. Data Tools
**Files**: `paste/PasteCommands.kt`, `snapshot/SnapshotCommands.kt`, `bundle/IssueBundle.kt`

| Command | Function |
|---------|----------|
| `/arc paste doctor\|config\|logs\|plugin` | Upload to paste service (masked) |
| `/arc snapshot create\|list\|compare` | Server state snapshots |
| `/arc issue-bundle` | Diagnostic zip for bug reports |

---

## Implemented Features (Network)

### Network Architecture

```
Arc Core (each server) → Redis (Arc Relay) → Arc Proxy Module (Velocity)
                         ↓
                    PostgreSQL (Arc Storage)
```

**Files**:
- `network/ArcNetworkConfig.kt` — Configuration loader (`arc-network.yml`)
- `network/ArcRelayClient.kt` — Redis abstraction (pub/sub, locks, cooldowns)
- `network/ArcServerRegistry.kt` — Server heartbeat + registration
- `network/ArcPlayerTransfer.kt` — Cross-server player movement
- `network/ArcQueue.kt` — Server queue (Redis sorted sets)
- `network/ArcItemMail.kt` — Item delivery between servers
- `network/ArcItemSerializer.kt` — Item serialization for transfer
- `network/ArcGlobalCooldown.kt` — Cross-server cooldowns
- `network/ArcNetworkAudit.kt` — Network operation audit trail
- `network/ArcNetworkServices.kt` — Broadcast, Maintenance, Evacuation, Player Lookup
- `ops/network/ArcNetworkCommands.kt` — `/arc network *` command routing

### Network Commands

| Command | Function |
|---------|----------|
| `/arc network servers` | All server status dashboard |
| `/arc network server <id>` | Single server details |
| `/arc network players` | All online players across network |
| `/arc network send <player> <server>` | Transfer player |
| `/arc network sendall <from> <to>` | Transfer all players |
| `/arc network hub` | Go to hub server |
| `/arc network queue join\|leave\|status` | Queue management |
| `/arc network maintenance server\|group\|network on\|off` | Maintenance mode |
| `/arc network evacuate <from> <to>` | Move all players before shutdown |
| `/arc network broadcast <msg>` | Network-wide message |
| `/arc network itemmail send\|inbox\|claim` | Cross-server item delivery |
| `/arc network find <player>` | Find player across network |
| `/arc network audit [last]` | Network audit log |
| `/arc network cooldown check\|clear` | Global cooldown management |

### Item Mail State Machine

```
send() → PENDING → claim() → CLAIMING → CLAIMED
                     cancel() → CANCELLED
                     timeout → EXPIRED
                     error → FAILED
```

**Duplication prevention**: Distributed lock (`arc:lock:mail:<id>`) ensures only one server claims a mail item.

---

## Configuration Reference

### arc-ops.yml (Operations Suite)
```yaml
lag-spike-capture:
  enabled: true
  mspt-threshold: 100
  capture-duration-ticks: 40

plugin-cost.enabled: true
entity-optimization.enabled: false   # DEFAULT OFF
memory-guard.enabled: true
stall-watchdog.enabled: true
crash.analyze-on-boot: true
```

### arc-network.yml (Multi-Server)
```yaml
arc-network:
  enabled: false                     # DEFAULT OFF
  server:
    id: "survival-1"
    group: "survival"
  relay:
    type: redis
    redis:
      host: "127.0.0.1"
      port: 6379
  server-groups:
    survival:
      servers: [survival-1, survival-2]
      item-mail: true
      queue-enabled: true
  item-mail.enabled: true
  global-vault.enabled: false        # DEFAULT OFF
  inventory-transfer.enabled: false  # DEFAULT OFF — HIGH RISK
  remote-command.enabled: false      # DEFAULT OFF — HIGH RISK
  audit.enabled: true
```

---

## Development Priorities

| Phase | Features | Risk | Default |
|-------|----------|------|---------|
| 1 | Plugin Manager, Doctor, Config UX | Low | ON |
| 2 | Plugin Inspection, Sandbox | Low | ON |
| 3 | Disable/Enable, Safe Mode | Medium | ON |
| 4 | Reload, Reload-chain, Confirm | **High** | Confirm req. |
| 5 | Developer API (ArcContext, Scheduler, Commands) | Medium | ON |
| 6 | Diagnostics (Lag Spike, Cost Profiler) | Low | ON |
| 7 | Monitoring, Maintenance, Logs/Paste/Snapshot | Low | ON |
| 8 | Optimization (Entity AI, Chunk, Packet) | **High** | **OFF** |

### Network Priorities

| Phase | Features | Default |
|-------|----------|---------|
| 1 | Server Registry, Heartbeat, Groups | ON |
| 2 | Transfer, Queue, Broadcast, Audit | ON |
| 3 | Evacuation, Player Lookup | ON |
| 4 | Item Mail, Serialization, Lock, Cooldown | Mail ON |
| 5 | Global Vault, Abuse Prevention | **OFF** |
| 6 | Config Sync, Remote Command, Inventory Transfer | **OFF** |

---

## Risk Matrix

| Feature | Compatibility Risk | Data Loss Risk | Default |
|---------|-------------------|---------------|---------|
| Plugin reload | High | None | Confirm req. |
| Reload-chain | High | None | Confirm req. |
| Distance-based AI | Medium | None | OFF |
| Chunk queue rewrite | High | High | OFF |
| Inventory Transfer | High | **Critical** | OFF |
| Remote Command | High | High | OFF |
| Plugin Deploy | Highest | High | OFF |

---

## File Index

### Single-Server (arc-api)
```
src/main/kotlin/dev/arc/api/
├── Arc.kt                              # Central hub
├── lifecycle/ArcReloadable.kt          # Reload-safe contract
├── control/ArcConfig.kt                # arc-config.yml loader
├── control/ArcControlCommand.kt        # /arc command registration
├── control/ArcFeatures.kt             # Feature flags
├── control/ArcSettings.kt             # Live tunables
├── command/Commands.kt                # Command DSL
│
├── ops/
│   ├── ArcOps.kt                      # Operations suite bootstrap
│   ├── ArcOpsCommand.kt               # All subcommand routing
│   ├── OpsConfig.kt                   # arc-ops.yml
│   │
│   ├── plugin/
│   │   ├── PluginCommands.kt          # /arc plugin *
│   │   ├── PluginLifecycle.kt         # enable/disable/reload/reloadChain
│   │   ├── PluginInspector.kt         # Read-only inspection
│   │   ├── ReloadSafetyAnalyzer.kt    # SAFE/WARNING/UNSAFE/UNKNOWN
│   │   ├── DependencyGraph.kt         # chainPlan computation
│   │   ├── ConfirmManager.kt          # Token-based confirm
│   │   ├── PluginSandboxCheck.kt      # JAR analysis
│   │   ├── PluginRollbackManager.kt   # Version rollback
│   │   └── ReloadPolicy.kt           # Per-plugin policy
│   │
│   ├── config/
│   │   ├── ConfigCommands.kt          # get/set/reset/reload/diff/search
│   │   ├── ConfigChangeHistory.kt     # Who changed what
│   │   └── PluginOverrides.kt         # Feature exclusions
│   │
│   ├── configcheck/ConfigValidator.kt # Config validation
│   ├── doctor/ServerDoctor.kt         # Health report
│   ├── lagspike/LagSpikeMonitor.kt    # MSPT threshold capture
│   ├── plugincost/PluginCostTracker.kt # Per-plugin profiling
│   ├── profiler/MainThreadProfiler.kt # Stack sampling
│   ├── audit/AuditLog.kt              # Dangerous command log
│   ├── safe/SafeModeCommands.kt       # Safe mode + maintenance
│   ├── security/SecurityAudit.kt      # Security checks
│   ├── proxy/ProxySetupChecker.kt     # Velocity/Bungee validation
│   ├── startup/StartupProfile.kt      # Boot time analysis
│   ├── bundle/IssueBundle.kt          # Diagnostic zip
│   │
│   ├── commands/CommandSearch.kt      # /arc command search
│   ├── permissions/PermissionSearch.kt # /arc permission *
│   ├── worlds/WorldCommands.kt        # /arc world *
│   ├── logs/LogCommands.kt            # /arc logs *
│   ├── paste/PasteCommands.kt         # /arc paste *
│   ├── snapshot/SnapshotCommands.kt   # /arc snapshot *
│   │
│   └── network/ArcNetworkCommands.kt  # /arc network *
│
└── network/
    ├── ArcNetworkConfig.kt            # arc-network.yml
    ├── ArcRelayClient.kt              # Redis abstraction
    ├── ArcServerRegistry.kt           # Heartbeat + registration
    ├── ArcPlayerTransfer.kt           # send/sendAll/hub
    ├── ArcQueue.kt                    # Queue system
    ├── ArcItemMail.kt                 # Item delivery
    ├── ArcItemSerializer.kt           # Serialization format
    ├── ArcGlobalCooldown.kt           # Cross-server cooldowns
    ├── ArcNetworkAudit.kt             # Audit trail
    └── ArcNetworkServices.kt          # Broadcast/Maintenance/Evac/Lookup
```

### Server Integration (arc-server)
```
src/main/
├── java/dev/arc/server/
│   ├── ArcBootstrap.kt               # NMS + ArcOps install
│   ├── nms/ReflectiveArcNms.kt       # NMS bridge
│   └── scheduling/LeafParallelWorldScheduler.kt
└── kotlin/dev/arc/server/
    └── packet/NettyPacketBridge.kt
```

---

**Total files implemented: 42**
**Total commands: ~80**
**Lines of code: ~8,000+**
