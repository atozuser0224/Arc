---
title: 블록 감시
parent: Developer API
nav_order: 12
---

# 블록 감시 (BlockWatch)

`BlockWatchRegistry`는 특정 블록 위치에 변경 감지 구독을 걸고, 해당 위치에서 이벤트가 발생할 때만 핸들러를 호출한다. 글로벌 Bukkit 이벤트를 구독한 뒤 좌표를 일일이 비교하는 것보다 구조적으로 빠르고 코드도 간결하다.

---

## 기존 방식의 문제

특정 블록 위치를 감시하려면 전역 이벤트를 구독하고 좌표를 비교해야 한다.

```kotlin
// ❌ Bukkit — 모든 블록 파괴 이벤트에서 내 블록인지 확인
listen<BlockBreakEvent> { e ->
    if (e.block.location != watchedLocation) return@listen
    handleBreak(e)
}

listen<BlockPlaceEvent> { e ->
    if (e.block.location != watchedLocation) return@listen
    handlePlace(e)
}

// 폭발, 화재, 성장, 피스톤... 이벤트마다 같은 비교 반복
```

이 방식의 문제:
- 감시 위치가 늘어날수록 비교 비용이 O(감시 위치 수)로 증가한다.
- 감시할 이벤트 종류마다 리스너를 따로 등록해야 한다.
- 구독 해제 시 특정 위치에 대한 핸들러만 제거하는 게 번거롭다.

---

## BlockWatchRegistry

내부적으로 청크 키로 인덱싱한다. 이벤트가 발생하면 해당 청크의 구독자 목록만 순회하므로 전체 구독자 수가 아니라 그 청크 안의 구독자 수에만 비례한다.

```kotlin
// 플러그인에 묶인 레지스트리 생성
val watcher = plugin.blockWatcher()
// 물리 업데이트(레드스톤 등)도 감시하려면:
val watcher = plugin.blockWatcher(includePhysics = true)
```

---

## BlockChangeCause — 변경 원인

| 값 | 트리거 Bukkit 이벤트 |
|----|----------------------|
| `BREAK` | `BlockBreakEvent` |
| `PLACE` | `BlockPlaceEvent` |
| `EXPLODE` | `BlockExplodeEvent`, `EntityExplodeEvent` |
| `BURN` | `BlockBurnEvent` |
| `FADE` | `BlockFadeEvent` (눈·얼음 녹음 등) |
| `GROW` | `BlockGrowEvent` |
| `SPREAD` | `BlockSpreadEvent` (불 확산 등) |
| `FLOW` | `BlockFromToEvent` (물·용암 흐름) |
| `PISTON` | `BlockPistonExtendEvent`, `BlockPistonRetractEvent` |
| `PHYSICS` | `BlockPhysicsEvent` — `includePhysics = true`일 때만 |

---

## 기본 사용 예시

### 단일 블록 감시

```kotlin
val handle = watcher.watch(chestLocation) { e ->
    when (e.cause) {
        BlockChangeCause.BREAK -> {
            logger.info("${e.player?.name ?: "환경"} 이 상자를 부쉈습니다!")
            restoreChest(e.block.location)
        }
        BlockChangeCause.EXPLODE -> {
            logger.warning("폭발로 상자가 파괴됨: ${e.block.location}")
        }
        else -> {}
    }
}

// 나중에 감시 중단
handle.cancel()
```

### 빠른 단일 구독 (레지스트리 없이)

```kotlin
val handle = plugin.watchBlock(importantLocation) { e ->
    if (e.cause == BlockChangeCause.BREAK) {
        broadcastToAdmins("중요 블록이 파괴됐습니다!")
    }
}
```

### Java

```java
BlockWatchHandle handle = watcher.watch(location, e -> {
    if (e.getCause() == BlockChangeCause.BREAK) {
        e.getPlayer().sendMessage("이 블록은 감시 중입니다.");
    }
});

// 해제
handle.cancel();
```

---

## 전체 예시 — 보호 구역 블록 복원

```kotlin
class ProtectedZonePlugin : JavaPlugin() {

    private lateinit var watcher: BlockWatchRegistry
    private val protectedBlocks = mutableSetOf<Location>()

    override fun onEnable() {
        watcher = blockWatcher()

        // 스폰 주변 블록 보호
        val spawnLoc = world.spawnLocation
        for (x in -5..5) for (z in -5..5) {
            val loc = spawnLoc.clone().add(x.toDouble(), 0.0, z.toDouble())
            protectedBlocks.add(loc)

            watcher.watch(loc) { e ->
                when (e.cause) {
                    BlockChangeCause.BREAK, BlockChangeCause.EXPLODE -> {
                        // 다음 틱에 복원 (이벤트 핸들러에서 직접 블록 변경 시 충돌 위험)
                        runLater(1L) {
                            e.block.type = Material.STONE
                        }
                        e.player?.sendMessage("§c이 블록은 보호 구역입니다.")
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onDisable() {
        watcher.close()  // 모든 구독 해제
    }
}
```

---

## 여러 위치 일괄 등록 · 해제

```kotlin
// 핸들 목록으로 일괄 관리
val handles = mutableListOf<BlockWatchHandle>()

chestLocations.forEach { loc ->
    handles += watcher.watch(loc) { e ->
        onChestChange(e)
    }
}

// 모두 해제
handles.forEach { it.cancel() }
// 또는 레지스트리 전체 해제
watcher.close()
```

---

## API 레퍼런스

| 메서드 | 설명 |
|--------|------|
| `BlockWatchRegistry(plugin, includePhysics)` | 레지스트리 생성. `includePhysics` 기본 `false` |
| `watch(location, handler)` | 위치 구독 등록, `BlockWatchHandle` 반환 |
| `watch(location, Consumer<BlockChangeEvent>)` | Java 친화적 오버로드 |
| `close()` | 모든 구독 해제 |
| `Plugin.blockWatcher(includePhysics)` | 확장 함수로 레지스트리 생성 |
| `Plugin.watchBlock(location, handler)` | 레지스트리 없이 단일 구독 |
| `BlockWatchHandle.cancel()` | 해당 구독만 해제 |

---

## 이점과 한계

**이점**
- 청크 단위 인덱싱으로 전체 구독자를 순회하지 않음 — 감시 위치가 수백 개여도 이벤트당 비용은 해당 청크 내 구독자 수에만 비례
- `BlockChangeCause` 하나로 BREAK·EXPLODE·PISTON 등 모든 물리 원인을 구분
- `BlockWatchHandle.cancel()`로 특정 구독만 정밀하게 해제 가능
- Java `Consumer<BlockChangeEvent>` 오버로드 제공

**한계**
- 핸들러 안에서 Bukkit 이벤트를 취소할 수 없다. BlockWatch는 통지 레이어이지 이벤트 가로채기 레이어가 아니다. 블록 파괴를 막으려면 별도로 `BlockBreakEvent`를 구독해야 한다.
- `includePhysics = true`로 설정하면 레드스톤·유체 틱마다 이벤트가 몰려온다. 성능에 민감한 서버에서는 꼭 필요한 경우에만 켜야 한다.
- 이벤트는 메인 스레드에서 발생한다. 핸들러에서 무거운 작업을 돌리면 그 틱이 지연된다.
