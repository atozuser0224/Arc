# Arc Network — Multi-Server Architecture

## Overview

Arc Network enables multiple Arc game servers to operate as a unified network with:

- **Server Registry** — auto-discovery and health monitoring via Redis heartbeat
- **Player Transfer** — cross-server movement with permission and group validation
- **Queue System** — fair waiting with priority support
- **Item Mail** — safe cross-server item delivery with anti-duplication
- **Global Vault** — shared storage (opt-in)
- **Distributed Lock** — Redis-backed mutex for concurrent operations
- **Network Audit** — all cross-server actions logged

## Quick Start

```yaml
# arc-network.yml
arc-network:
  enabled: true
  server:
    id: "survival-1"
    group: "survival"
    registration-token: "${ARC_NETWORK_TOKEN}"
  relay:
    type: redis
    redis:
      host: "127.0.0.1"
      port: 6379
      password: "${ARC_REDIS_PASSWORD}"
  server-groups:
    lobby:
      servers: [lobby-1, lobby-2]
      default-hub: true
    survival:
      servers: [survival-1, survival-2, survival-3]
      item-mail: true
      queue-enabled: true
  item-mail:
    enabled: true
    expire-days: 7
  audit.enabled: true
```

## Commands Reference

### Dashboard
```bash
/arc network servers              # All servers with TPS/players/status
/arc network server <id>          # Single server details
/arc network players              # All online players
/arc network find <player>        # Locate a player
```

### Transfer
```bash
/arc network send <player> <server>     # Move one player
/arc network sendall <from> <to>        # Move all players
/arc network hub                        # Go to hub
/arc network queue join <server>        # Join queue
/arc network queue leave                # Leave queue
/arc network queue status               # View position
```

### Maintenance
```bash
/arc network maintenance server <id> on|off
/arc network maintenance group <name> on|off
/arc network maintenance network on|off
/arc network maintenance status
/arc network evacuate <from> <to>
```

### Item Mail
```bash
/arc network itemmail send <player>     # Send held item
/arc network itemmail inbox             # View pending mail
/arc network itemmail claim <id>        # Claim item
/arc network itemmail cancel <id>       # Cancel sent mail
```

### Other
```bash
/arc network broadcast <message>        # Network-wide announcement
/arc network audit [last]               # View audit log
/arc network cooldown check <p> <key>   # Check cooldown
/arc network cooldown clear <p> <key>   # Clear cooldown
```

## Architecture Design

### Heartbeat Flow
```
Every 2 seconds (Core):
  → Redis: SETEX arc:server:<id> 10 <json-status>
  → Redis: SADD arc:servers:online <id>

Every 3 seconds (Proxy):
  → Redis: SMEMBERS arc:servers:online
  → Redis: MGET arc:server:* for each
  → Status TTL expired → mark OFFLINE
```

### Item Mail State Machine
```
send() → PENDING ──claim()→ CLAIMING ──success→ CLAIMED
    │       │                      └failure→ PENDING (retry)
    │       ├──cancel()→ CANCELLED
    │       └──timeout→ EXPIRED
    └──DB fail→ item returned to sender
```

**Duplicate claim prevention**: `arc:lock:mail:<id>` Redis lock ensures only one server processes a claim.

### Queue Priority Formula
```
score = current_time_ms - priority_offset
  Normal:   offset = 0
  VIP:      offset = 60,000 (1 min)
  Priority: offset = 300,000 (5 min)
```
Redis `ZPOPMIN` atomically pops the lowest score (highest priority).

## SQL Schema

### arc_item_mail
```sql
CREATE TABLE arc_item_mail (
    id BIGSERIAL PRIMARY KEY,
    sender_uuid UUID NOT NULL,
    sender_name VARCHAR(36),
    receiver_uuid UUID NOT NULL,
    receiver_name VARCHAR(36),
    source_server VARCHAR(64) NOT NULL,
    item_data BYTEA NOT NULL,
    item_format_version INT DEFAULT 1,
    minecraft_version VARCHAR(16),
    item_type VARCHAR(64),
    item_amount INT DEFAULT 1,
    status VARCHAR(16) DEFAULT 'PENDING',  -- PENDING,CLAIMING,CLAIMED,CANCELLED,EXPIRED,FAILED
    fail_reason TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '7 days'),
    claimed_at TIMESTAMPTZ,
    INDEX idx_mail_receiver (receiver_uuid, status),
    INDEX idx_mail_status_expires (status, expires_at)
);
```

### arc_network_audit
```sql
CREATE TABLE arc_network_audit (
    id BIGSERIAL PRIMARY KEY,
    trace_id UUID NOT NULL,
    actor_name VARCHAR(36),
    action VARCHAR(64) NOT NULL,
    source_server VARCHAR(64),
    target_server VARCHAR(64),
    player_uuid UUID,
    result VARCHAR(16),  -- SUCCESS, FAILED
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    INDEX idx_audit_action (action, created_at),
    INDEX idx_audit_player (player_uuid, created_at)
);
```

### arc_global_vault
```sql
CREATE TABLE arc_global_vault (
    id BIGSERIAL PRIMARY KEY,
    player_uuid UUID NOT NULL,
    player_name VARCHAR(36),
    server_group VARCHAR(64) NOT NULL,
    slot INT NOT NULL,
    item_data BYTEA,
    item_format_version INT DEFAULT 1,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    updated_by_server VARCHAR(64),
    UNIQUE (player_uuid, server_group, slot)
);
```

## Distributed Lock Implementation

```kotlin
// Acquire
val token = ArcRelayClient.acquireLock("mail:$mailId", ttlSeconds = 10)
if (token == null) return "Mail is being processed"

// Safe release (Lua script)
val script = """
    if redis.call('GET', KEYS[1]) == ARGV[1] then
        return redis.call('DEL', KEYS[1])
    else return 0 end
"""
```

## Failure Recovery

| Scenario | Protection |
|----------|------------|
| Redis disconnect | Core retries with backoff; Proxy uses cached state (30s) |
| SQL disconnect | HikariCP pool auto-reconnects; writes queued in memory (60s) |
| Item send crash | DB transaction ensures atomicity; item returned on failure |
| Item claim crash | Lock released by TTL; status=CLAIMING retries on next attempt |
| Dual claim attempt | Distributed lock fails for second claimer |
| Network partition | Partitioned servers operate local-only until reconnect |

## Risk Warnings

| Feature | Risk Level | Default |
|---------|-----------|---------|
| Item Mail | Low | ON |
| Player Transfer | Low | ON |
| Queue | Low | ON |
| Global Vault | **Medium** | OFF |
| Inventory Transfer | **High** | **OFF** |
| Remote Command | **High** | **OFF** |
| Plugin Deploy | **Critical** | **OFF** |

**Full inventory sync across servers is the highest-risk feature.** It can cause item duplication if any step fails. Arc recommends Item Mail + Global Vault as the safe alternatives.

---

**Version**: 1.0.0-draft
**Minecraft**: 1.21.4
**Base**: Leaf MC (Paper/Purpur fork)
