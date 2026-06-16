---
title: 월드 · 청크 유틸리티
parent: Developer API
nav_order: 7
---

# 월드 · 청크 유틸리티

Arc API는 월드를 다룰 때 자주 쓰는 세 가지 유틸리티를 제공한다. 블록 영역을 스냅샷으로 떠 두고 복원하는 `WorldSnapshot`, 레이트레이스를 쏘는 `RayTrace`, 청크를 강제로 로드하는 `WorldChunk`다.

---

## WorldSnapshot — 블록 영역 스냅샷

`WorldSnapshot`은 직육면체 영역의 블록 데이터를 통째로 메모리에 담아 두었다가 나중에 원래 상태로 되돌린다. 미니게임이 끝나고 맵을 초기화할 때, 서버를 재시작하지 않고도 곧바로 복원할 수 있다.

### 기본 사용법

```kotlin
// 맵 영역 정의
val corner1 = Location(world, -50.0, 60.0, -50.0)
val corner2 = Location(world, 50.0, 120.0, 50.0)

// 스냅샷 캡처 — 게임 시작 전 또는 플러그인 로드 시
val snapshot: WorldSnapshot = WorldSnapshot.capture(corner1, corner2)

// ... 게임 진행 중 블록이 변경됨 ...

// 게임 종료 후 복원
WorldSnapshot.restore(snapshot, corner1)
```

### 미니게임 통합 예시

```kotlin
class ArenaBedwars(val plugin: JavaPlugin, val world: World) {
    private val corner1 = Location(world, -100.0, 50.0, -100.0)
    private val corner2 = Location(world, 100.0, 150.0, 100.0)
    private var snapshot: WorldSnapshot? = null

    fun prepareArena() {
        // 서버 시작 시 또는 게임 준비 시 스냅샷 저장
        plugin.runAsync {
            // 스냅샷 캡처는 메인 스레드에서 실행해야 함 (블록 읽기)
            runNext {
                snapshot = WorldSnapshot.capture(corner1, corner2)
                logger.info("베드워즈 아레나 스냅샷 저장 완료 (${getBlockCount()}블록)")
            }
        }
    }

    fun startGame(players: List<Player>) {
        // 이전 게임 흔적을 스냅샷으로 초기화 후 시작
        val snap = snapshot ?: run {
            logger.warning("스냅샷이 없습니다. prepareArena()를 먼저 호출하세요.")
            return
        }

        plugin.runAsync {
            runNext {
                WorldSnapshot.restore(snap, corner1)
                // 복원 완료 후 게임 시작
                players.forEach { it.sendMessage("§a경기가 시작됩니다!") }
                startGameLogic(players)
            }
        }
    }

    private fun getBlockCount(): Int {
        val dx = Math.abs(corner2.blockX - corner1.blockX) + 1
        val dy = Math.abs(corner2.blockY - corner1.blockY) + 1
        val dz = Math.abs(corner2.blockZ - corner1.blockZ) + 1
        return dx * dy * dz
    }
}
```

### 스냅샷 영속화

플러그인을 재시작해도 스냅샷을 살려 두려면 직렬화해 파일로 저장한다.

```kotlin
// 파일에 저장
val file = File(plugin.dataFolder, "arena_snapshot.dat")
WorldSnapshot.save(snapshot, file)

// 파일에서 로드
val loaded = WorldSnapshot.load(file, world)
```

### 성능 고려사항

스냅샷이 쓰는 메모리는 영역 안의 블록 수에 비례한다. 블록마다 블록 타입과 블록 데이터(BlockData), 타일 엔티티 데이터(NBT)를 담기 때문이다.

| 영역 크기 | 대략적인 블록 수 | 예상 메모리 |
|----------|--------------|------------|
| 50×50×50 | 125,000 | ~5MB |
| 100×100×100 | 1,000,000 | ~40MB |
| 200×100×200 | 4,000,000 | ~160MB |

{: .warning }
영역이 너무 크면 스냅샷이 GC를 압박할 수 있다. 미니게임 맵은 100×100 이하로 잡길 권한다.

복원에 걸리는 시간도 블록 수에 비례한다. 맵이 크다면 청크 단위로 나눠 비동기로 복원하는 방식을 검토해 보자.

---

## RayTrace — 레이트레이스

`RayTrace.cast()`는 특정 위치에서 방향을 따라 레이를 쏘아, 가장 먼저 부딪히는 블록이나 엔티티를 돌려준다.

### 기본 사용법

```kotlin
// 플레이어 시선 방향으로 레이 발사
val hit: HitResult? = RayTrace.cast(
    origin = player.eyeLocation,
    direction = player.eyeLocation.direction,
    maxDistance = 10.0
)

when {
    hit == null -> player.sendMessage("§7아무것도 없습니다.")
    hit.hitBlock != null -> {
        player.sendMessage("§e블록 맞음: §f${hit.hitBlock.type}")
        // 명중 블록의 면 정보
        player.sendMessage("§7면: ${hit.hitBlockFace}")
    }
    hit.hitEntity != null -> {
        player.sendMessage("§e엔티티 맞음: §f${hit.hitEntity.name}")
        (hit.hitEntity as? LivingEntity)?.damage(10.0, player)
    }
}
```

### 엔티티 무시 옵션

```kotlin
// 특정 엔티티 무시 (예: 발사한 플레이어 자신)
val hit = RayTrace.cast(
    origin = player.eyeLocation,
    direction = player.eyeLocation.direction,
    maxDistance = 20.0,
    ignoreEntities = setOf(player),  // 발사자 무시
    fluid = FluidCollisionMode.NEVER  // 물 무시
)
```

### 사용 시나리오

**조준 아이템 — 레이저 지팡이**:

```kotlin
ItemBehavior.on(plugin, Material.BLAZE_ROD) {
    rightClick { player, item ->
        val hit = RayTrace.cast(
            player.eyeLocation,
            player.eyeLocation.direction,
            maxDistance = 15.0,
            ignoreEntities = setOf(player)
        ) ?: return@rightClick

        // 파티클 경로
        val origin = player.eyeLocation
        val hitPoint = hit.hitPosition
        val steps = (origin.distance(hitPoint.toLocation(player.world!!)) * 4).toInt()
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val pos = origin.clone().add(
                hitPoint.x * t - origin.x * t,
                hitPoint.y * t - origin.y * t,
                hitPoint.z * t - origin.z * t
            )
            player.world!!.spawnParticle(Particle.FLAME, pos, 1, 0.0, 0.0, 0.0, 0.0)
        }

        // 명중 처리
        (hit.hitEntity as? LivingEntity)?.damage(15.0, player)
        hit.hitBlock?.type = Material.AIR
    }
}
```

**블록 위치 선택기 — 원거리 상호작용**:

```kotlin
// 플레이어가 보는 곳에 표시판 설치
fun placeSignAt(player: Player, text: String) {
    val hit = RayTrace.cast(
        player.eyeLocation,
        player.eyeLocation.direction,
        maxDistance = 8.0
    ) ?: run {
        player.sendMessage("§c8블록 내에 블록이 없습니다.")
        return
    }

    val block = hit.hitBlock ?: return
    val signLoc = block.getRelative(hit.hitBlockFace ?: return).location
    signLoc.block.type = Material.OAK_SIGN
    val sign = signLoc.block.state as? Sign ?: return
    sign.getSide(Side.FRONT).line(0, text)
    sign.update()
}
```

---

## WorldChunk 유틸리티

`WorldChunk.forceLoad()`는 지정한 청크를 강제로 로드한다. 몬스터 스포너나 경작 시스템처럼 청크에 기대는 시스템에서, 청크가 로드되지 않아 엔티티가 돌지 않는 문제를 해결해 준다.

### 기본 사용법

```kotlin
// 단일 청크 강제 로드
val chunk = world.getChunkAt(chunkX, chunkZ)
WorldChunk.forceLoad(chunk)

// 로드 후 엔티티 수 확인
val entityCount = chunk.entities.size
logger.info("청크 (${chunkX}, ${chunkZ}) 로드 완료 — 엔티티 ${entityCount}개")
```

### 범위 청크 로드

```kotlin
// 특정 위치 주변 3×3 청크 강제 로드
fun forceLoadArea(center: Location, radius: Int) {
    val centerChunk = center.chunk
    for (dx in -radius..radius) {
        for (dz in -radius..radius) {
            val chunk = world.getChunkAt(centerChunk.x + dx, centerChunk.z + dz)
            WorldChunk.forceLoad(chunk)
        }
    }
}
```

**사용 시나리오**:

- 자동 팜(automatic farm): 플레이어가 없어도 작물이 자라야 할 때.
- 이벤트 아레나: 게임 시작 전 맵 청크를 미리 로드해 지연 없이 시작하고 싶을 때.
- 경매장 시스템: 특정 위치의 엔티티(아이템 프레임 등)를 강제로 로드해 둘 때.

{: .warning }
`forceLoad()`한 청크는 플레이어가 없어도 계속 로드된 채로 남아 서버 TPS를 갉아먹는다. 꼭 필요한 청크만 강제 로드하고, 쓸 일이 없어지면 `WorldChunk.unforceLoad(chunk)`로 풀어 주자.

---

## getBlockIfLoaded — 청크를 강제 로드하지 않는 블록 접근

`World.getBlock(x, y, z)`는 해당 청크가 로드되지 않았으면 청크를 강제 로드한다. 이 동작은 의도치 않은 청크 로딩을 유발해 서버 성능에 영향을 줄 수 있다. `getBlockIfLoaded()`는 청크가 이미 로드된 경우에만 블록을 반환하고, 로드되지 않았으면 `null`을 돌려준다.

### 기본 사용법

```kotlin
// 청크가 로드돼 있으면 Block, 아니면 null
val block: Block? = world.getBlockIfLoaded(x, y, z)
if (block != null) {
    // 안전하게 접근
    if (block.type == Material.CHEST) {
        doSomething(block)
    }
}

// Location 확장 프로퍼티
val block: Block? = location.blockIfLoaded
```

### Bukkit 방식과 비교

```kotlin
// ❌ Bukkit — 청크가 로드되지 않아도 강제 로드 후 반환
val block = world.getBlockAt(x, y, z)

// ✅ Arc — 청크가 로드된 경우에만 반환
val block = world.getBlockIfLoaded(x, y, z) ?: return
```

### 전형적인 사용 시나리오

**대규모 영역 스캔 (이미 로드된 청크만)**:

```kotlin
// 넓은 범위를 순회하되, 로드되지 않은 청크는 건너뜀
for (x in -200..200) {
    for (z in -200..200) {
        val block = world.getBlockIfLoaded(x, 64, z) ?: continue
        if (block.type == Material.DIAMOND_ORE) {
            logger.info("다이아몬드 발견: $x, 64, $z")
        }
    }
}
```

**이벤트 핸들러에서 인접 블록 확인**:

```kotlin
listen<BlockBreakEvent> { e ->
    // 인접 블록이 로드돼 있으면 확인, 아니면 무시
    val above = e.block.location.add(0.0, 1.0, 0.0).blockIfLoaded ?: return@listen
    if (above.type == Material.TORCH) above.type = Material.AIR
}
```

**반복 태스크에서 안전한 블록 접근**:

```kotlin
plugin.runRepeat(period = 20L) {
    monitoredLocations.forEach { loc ->
        val block = loc.blockIfLoaded ?: return@forEach  // 청크 언로드 시 스킵
        if (block.type != Material.DIAMOND_BLOCK) {
            alert("감시 블록이 변경됐습니다: $loc")
        }
    }
}
```

### API 레퍼런스

| 메서드 | 설명 |
|--------|------|
| `World.getBlockIfLoaded(x, y, z)` | 청크가 로드된 경우 `Block` 반환, 아니면 `null` |
| `Location.blockIfLoaded` | `getBlockIfLoaded(blockX, blockY, blockZ)` 단축 프로퍼티 |

### 이점

- 의도치 않은 청크 로딩을 막아 서버 TPS를 보호함
- 청크 로드 여부를 `world.isChunkLoaded()`로 직접 확인하는 코드보다 간결
- `null` 반환으로 처리 여부를 강제해 로드되지 않은 청크에 접근하는 실수를 방지

---

## 장점과 단점

### WorldSnapshot

**장점**:
- 서버를 재시작하지 않고 맵을 초기화한다.
- 파일로 직렬화해 두면 서버를 재시작한 뒤에도 다시 쓸 수 있다.
- 타일 엔티티(상자, 표시판 등)까지 함께 복원한다.

**단점**:
- 메모리를 영역 크기만큼 잡아먹는다. 큰 맵은 수백 MB까지 쓸 수 있다.
- 복원할 때 영역 안 엔티티(플레이어 제외)는 자동으로 지워지지 않으니 직접 처리해야 한다.
- 복원 작업 자체가 서버에 순간적인 부하를 준다.

### RayTrace

**장점**:
- Bukkit `player.getTargetBlock()`보다 세밀하게 제어한다(엔티티 충돌, 유체 옵션).
- 충돌 지점(`hitPosition`)의 정확한 벡터 위치를 돌려준다.
- 발사 엔티티 무시 같은 옵션을 지원한다.

**단점**:
- 매 틱 호출하면 성능에 부담을 준다. `PlayerMoveEvent`나 `runRepeat`에서 부를 때는 호출을 최대한 줄이자.
- 투명 블록 통과, 멀티블록 히트 같은 NMS 수준의 복잡한 레이트레이스는 직접 구현해야 한다.

### WorldChunk

**장점**:
- 플레이어가 없어도 청크를 유지한다.
- API가 간단하다.

**단점**:
- TPS에 영향을 준다. 너무 많이 강제 로드하면 서버 성능이 떨어진다.
