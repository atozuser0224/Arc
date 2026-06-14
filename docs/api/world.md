---
title: 월드 · 청크 유틸리티
parent: Developer API
nav_order: 7
---

# 월드 · 청크 유틸리티

Arc API는 월드 조작에서 자주 필요한 세 가지 유틸리티를 제공합니다. 블록 영역 스냅샷/복원(`WorldSnapshot`), 레이트레이스(`RayTrace`), 청크 강제 로드(`WorldChunk`)입니다.

---

## WorldSnapshot — 블록 영역 스냅샷

`WorldSnapshot`은 직육면체 영역의 모든 블록 데이터를 메모리에 저장하고, 나중에 원래 상태로 복원합니다. 미니게임 맵을 게임 후 초기화할 때 서버 재시작 없이 즉시 복원할 수 있습니다.

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

플러그인 재시작 후에도 스냅샷을 유지하려면 직렬화하여 파일에 저장합니다.

```kotlin
// 파일에 저장
val file = File(plugin.dataFolder, "arena_snapshot.dat")
WorldSnapshot.save(snapshot, file)

// 파일에서 로드
val loaded = WorldSnapshot.load(file, world)
```

### 성능 고려사항

스냅샷의 메모리 사용량은 영역 내 블록 수에 비례합니다. 각 블록은 블록 타입, 블록 데이터(BlockData), 타일 엔티티 데이터(NBT)를 포함합니다.

| 영역 크기 | 대략적인 블록 수 | 예상 메모리 |
|----------|--------------|------------|
| 50×50×50 | 125,000 | ~5MB |
| 100×100×100 | 1,000,000 | ~40MB |
| 200×100×200 | 4,000,000 | ~160MB |

{: .warning }
너무 큰 영역을 스냅샷으로 저장하면 GC 압박이 발생할 수 있습니다. 미니게임 맵은 100×100 이하로 설계하는 것을 권장합니다.

복원 작업은 블록 수에 비례하는 시간이 걸립니다. 큰 맵의 경우 청크 단위로 비동기 복원하는 방식을 검토하세요.

---

## RayTrace — 레이트레이스

`RayTrace.cast()`는 특정 위치에서 방향을 따라 레이를 발사하여 첫 번째로 충돌하는 블록 또는 엔티티를 반환합니다.

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

`WorldChunk.forceLoad()`는 지정된 청크를 강제로 로드합니다. 청크 기반 시스템(몬스터 스포너, 경작 시스템 등)에서 청크가 로드되지 않아 엔티티가 실행되지 않는 문제를 해결합니다.

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
- 이벤트 아레나: 게임 시작 전 맵 청크를 미리 로드하여 지연 없이 시작.
- 경매장 시스템: 특정 위치의 엔티티(아이템 프레임 등)를 강제 로드.

{: .warning }
`forceLoad()`된 청크는 플레이어가 없어도 계속 로드 상태를 유지합니다. 서버 TPS에 영향을 미칩니다. 반드시 필요한 청크만 강제 로드하고, 필요 없어지면 `WorldChunk.unforceLoad(chunk)`로 해제하세요.

---

## 장점과 단점

### WorldSnapshot

**장점**:
- 서버 재시작 없이 맵 초기화 가능.
- 파일로 직렬화하여 서버 재시작 후에도 재사용 가능.
- 타일 엔티티(상자, 표시판 등)도 함께 복원.

**단점**:
- 메모리 사용량이 영역 크기에 비례. 대규모 맵은 수백 MB를 소비할 수 있음.
- 복원 시 영역 내 엔티티(플레이어 제외)는 자동으로 제거되지 않음 — 직접 처리 필요.
- 복원 작업 자체가 서버에 순간적인 부하를 줌.

### RayTrace

**장점**:
- Bukkit `player.getTargetBlock()`보다 세밀한 제어 (엔티티 충돌, 유체 옵션).
- 충돌 지점(`hitPosition`)의 정확한 벡터 위치 반환.
- 발사 엔티티 무시 등 옵션 지원.

**단점**:
- 매 틱마다 호출하면 성능에 영향. `PlayerMoveEvent`나 `runRepeat`에서 호출 시 최소화 권장.
- NMS 수준의 복잡한 레이트레이스(투명 블록 통과, 멀티블록 히트 등)는 직접 구현 필요.

### WorldChunk

**장점**:
- 플레이어 없이도 청크 유지.
- 간단한 API.

**단점**:
- TPS 영향. 과도한 강제 로드는 서버 성능 저하.
