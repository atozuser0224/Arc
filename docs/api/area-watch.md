---
title: 영역 감시
parent: Developer API
nav_order: 13
---

# 영역 감시 (AreaWatch)

`AreaWatchRegistry`는 `Cuboid` 영역에 플레이어 진입·이탈 감지 구독을 건다. `PlayerMoveEvent`를 직접 구독해 좌표를 비교하는 것보다 구독 코드가 짧고, 텔레포트·월드 이동·로그아웃 같은 엣지 케이스를 자동으로 처리한다.

---

## 기존 방식의 문제

Bukkit으로 영역 진입을 감지하려면 `PlayerMoveEvent`에서 이전/현재 위치를 매 틱 비교해야 한다.

```kotlin
// ❌ Bukkit — 매 PlayerMoveEvent마다 전체 영역 목록 순회
private val regions = listOf<Pair<String, BoundingBox>>()
private val playersInRegion = HashMap<UUID, MutableSet<String>>()

listen<PlayerMoveEvent> { e ->
    if (!e.hasChangedBlock()) return@listen
    val player = e.player
    val loc = e.to ?: return@listen

    regions.forEach { (name, box) ->
        val wasIn = playersInRegion[player.uniqueId]?.contains(name) == true
        val isIn = box.contains(loc.x, loc.y, loc.z)

        if (!wasIn && isIn) onEnter(player, name)
        else if (wasIn && !isIn) onExit(player, name)
    }
}

// 텔레포트, 월드 이동, 로그아웃 별도 처리도 필요
```

이 방식의 문제:
- 영역이 많아질수록 매 이동 이벤트마다 전체 목록을 순회한다.
- 텔레포트로 영역을 건너뛰면 진입 이벤트 없이 내부에 있게 된다.
- 플레이어 로그아웃·월드 이동 시 이탈 처리를 놓치기 쉽다.
- "지금 이 플레이어가 어떤 영역 안에 있나"를 추적하는 상태 관리 코드가 커진다.

---

## AreaWatchRegistry

블록 단위 위치 변경(`hasChangedBlock()`)만 처리하므로 픽셀 단위 이동 이벤트를 모두 받지 않는다.

```kotlin
val watcher = plugin.areaWatcher()
```

---

## AreaTransition — 전환 방향

| 값 | 의미 |
|----|------|
| `ENTER` | 영역 밖 → 영역 안 |
| `EXIT` | 영역 안 → 영역 밖 |

---

## 기본 사용 예시

### 단일 영역 감시

```kotlin
val pvpZone = Cuboid(
    world,
    Location(world, -100.0, 0.0, -100.0),
    Location(world, 100.0, 256.0, 100.0)
)

val handle = watcher.watch(pvpZone) { e ->
    when (e.transition) {
        AreaTransition.ENTER -> {
            e.player.sendTitle("§cPvP 구역 진입", "§7상대방 공격 가능", 10, 40, 10)
            e.player.sendMessage("§c주의: PvP 구역입니다.")
        }
        AreaTransition.EXIT -> {
            e.player.sendTitle("§aPvP 구역 이탈", "§7안전 구역으로 복귀", 10, 40, 10)
        }
    }
}
```

### Cuboid 확장 함수로 바로 구독

```kotlin
val handle = pvpZone.watch(plugin) { e ->
    if (e.transition == AreaTransition.ENTER) enablePvp(e.player)
    else disablePvp(e.player)
}
```

### 단일 구독 (레지스트리 없이)

```kotlin
val handle = plugin.watchArea(pvpZone) { e ->
    broadcastToAdmins("${e.player.name} → ${e.transition} pvpZone")
}
```

### Java

```java
AreaWatchHandle handle = watcher.watch(pvpZone, e -> {
    if (e.getTransition() == AreaTransition.ENTER) {
        e.getPlayer().sendMessage("§cPvP 구역 진입");
    }
});
```

---

## 엣지 케이스 자동 처리

| 상황 | 동작 |
|------|------|
| 텔레포트로 영역 진입 | `PlayerTeleportEvent`를 캐치해 `ENTER` 발생 |
| 텔레포트로 영역 이탈 | `ENTER`한 기록이 있으면 `EXIT` 발생 |
| 월드 이동 (엔더 포탈 등) | `PlayerChangedWorldEvent`에서 이전 월드 영역 전부 `EXIT` 발생 |
| 로그아웃 | `PlayerQuitEvent`에서 진입 중인 영역 전부 `EXIT` 발생, 상태 정리 |

---

## AreaTransitionEvent 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `player` | `Player` | 이동한 플레이어 |
| `transition` | `AreaTransition` | `ENTER` 또는 `EXIT` |
| `region` | `Cuboid` | 해당 영역 |

---

## 전체 예시 — 미니게임 지역 입장 관리

```kotlin
class ZonePlugin : JavaPlugin() {

    private lateinit var watcher: AreaWatchRegistry
    private val playersInLobby = mutableSetOf<UUID>()

    override fun onEnable() {
        watcher = areaWatcher()

        val lobby = Cuboid(
            world,
            Location(world, -20.0, 60.0, -20.0),
            Location(world, 20.0, 80.0, 20.0)
        )

        watcher.watch(lobby) { e ->
            val player = e.player
            when (e.transition) {
                AreaTransition.ENTER -> {
                    playersInLobby.add(player.uniqueId)
                    player.sendMessage("§a로비에 입장했습니다. 현재 대기자: ${playersInLobby.size}명")
                    broadcastToLobby("§e${player.name}님이 로비에 들어왔습니다.")
                    if (playersInLobby.size >= 4) startCountdown()
                }
                AreaTransition.EXIT -> {
                    playersInLobby.remove(player.uniqueId)
                    player.sendMessage("§7로비를 떠났습니다.")
                    broadcastToLobby("§7${player.name}님이 로비를 떠났습니다.")
                }
            }
        }
    }

    override fun onDisable() {
        watcher.close()
    }

    private fun broadcastToLobby(msg: String) {
        playersInLobby.mapNotNull { Bukkit.getPlayer(it) }
            .forEach { it.sendMessage(msg) }
    }
}
```

---

## 여러 영역 관리

```kotlin
data class Zone(val name: String, val cuboid: Cuboid)

val zones = listOf(
    Zone("pvp", pvpCuboid),
    Zone("shop", shopCuboid),
    Zone("spawn", spawnCuboid)
)

val handles = zones.map { zone ->
    watcher.watch(zone.cuboid) { e ->
        val action = if (e.transition == AreaTransition.ENTER) "진입" else "이탈"
        logger.info("${e.player.name} → ${zone.name} $action")
        applyZoneEffects(e.player, zone, e.transition)
    }
}

// 일괄 해제
handles.forEach { it.cancel() }
```

---

## API 레퍼런스

| 메서드 | 설명 |
|--------|------|
| `AreaWatchRegistry(plugin)` | 레지스트리 생성 |
| `watch(region, handler)` | 영역 구독, `AreaWatchHandle` 반환 |
| `watch(region, Consumer<AreaTransitionEvent>)` | Java 친화적 오버로드 |
| `close()` | 모든 구독 해제, 상태 정리 |
| `Plugin.areaWatcher()` | 확장 함수로 레지스트리 생성 |
| `Plugin.watchArea(region, handler)` | 레지스트리 없이 단일 구독 |
| `Cuboid.watch(plugin, handler)` | Cuboid 확장 함수 |
| `AreaWatchHandle.cancel()` | 해당 구독만 해제 |

---

## 이점과 한계

**이점**
- 블록 단위 이동만 처리해 `PlayerMoveEvent`보다 훨씬 적게 호출됨
- 텔레포트·월드 이동·로그아웃의 엣지 케이스를 레지스트리가 자동 처리
- "지금 어떤 플레이어가 이 영역 안에 있나"를 직접 추적하지 않아도 됨
- `cancel()`로 특정 영역 구독만 제거 가능

**한계**
- `Cuboid`(직육면체)만 지원한다. 구(sphere) 또는 불규칙 다각형 영역이 필요하면 `PlayerMoveEvent`를 직접 써야 한다.
- 블록 단위 감지라 플레이어가 블록 경계를 넘지 않고 영역 모서리에서 아주 조금씩 이동하는 경우 이벤트가 늦게 발생할 수 있다.
