---
title: 성능 거버너
parent: Fork Core
nav_order: 1
---

# 성능 거버너

성능 거버너는 Arc의 메인 스레드 부하를 자동으로 관리하는 세 가지 메커니즘으로 구성된다. 각 메커니즘은 독립적으로 동작하며, `arc-ops.yml`에서 개별 설정이 가능하다.

---

## 1. 적응형 뷰 거버너 (Adaptive Governor)

### 개요

적응형 뷰 거버너는 100틱(약 5초)마다 서버의 평균 MSPT(밀리초/틱)를 측정하고, 측정값에 따라 뷰 거리(view-distance)와 시뮬레이션 거리(simulation-distance)를 자동으로 조절한다. 부하가 내려가면 즉시 원래 설정값으로 복원한다.

### 동작 원리

서버가 시작될 때 `arc-ops.yml`의 `view-distance`와 `simulation-distance` 값을 "목표 거리(target distance)"로 저장한다. 이후 100틱마다 `ServerLoad.currentLevel()`을 조회해 아래 표에 따라 실제 거리를 조절한다.

**LoadLevel별 동작**

| LoadLevel | MSPT 범위 | 거버너 동작 |
|-----------|-----------|-------------|
| `LOW` | ≤ 25ms | 목표 거리로 즉시 복원 |
| `NORMAL` | 25ms ~ 45ms | 개입 없음 (현재 거리 유지) |
| `HIGH` | ≥ 45ms | 현재 거리에서 1단계 축소 |
| `CRITICAL` | ≥ 50ms | 매 100틱마다 추가 1단계 축소 |

최솟값은 `governor.min-view-distance`(기본값 4)와 `governor.min-simulation-distance`(기본값 4)로 제한되어, 아무리 부하가 높아도 플레이어가 완전히 눈 앞도 못 보는 상황은 발생하지 않는다.

### 왜 뷰/시뮬레이션 거리인가

청크 틱, 엔티티 틱, 패킷 전송량은 모두 뷰 거리의 제곱에 비례한다. 반경 r인 원 안의 청크 수는 π·r²이므로, 뷰 거리를 10에서 4로 줄이면 활성 청크 수가 약 84% 감소한다. 이는 서버 부하 감소 효과 대비 플레이어 경험에 가장 적은 영향을 주는 조절 수단이다.

**계산 예시 (반경 기준, 정사각형 근사):**

| 뷰 거리 | 활성 청크 수 (근사) | 부하 비율 |
|---------|------------------|----------|
| 10 | 441 | 100% |
| 8 | 289 | 65% |
| 6 | 169 | 38% |
| 4 | 81 | 18% |

CRITICAL 상태에서 뷰 거리 10→4 전환 시 청크 틱 부하가 약 **75~82%** 감소한다 (엔티티 분포, 청크 내용에 따라 다름).

### 장점

- **완전 자동화**: 운영자가 수동으로 개입하지 않아도 부하 상황에 자동 대응한다.
- **즉시 복원**: LoadLevel이 LOW로 내려가면 목표 거리로 즉시 되돌아간다.
- **최솟값 보장**: 최소 거리 설정으로 플레이어 경험이 무너지는 상황을 방지한다.
- **플러그인 투명성**: 커스텀 이벤트(`ServerLoadLevelChangeEvent`)로 거버너 동작을 플러그인에서 감지할 수 있다.

### 단점 및 주의사항

- **반응 지연**: 부하가 급격히 오르는 경우 첫 번째 축소는 최대 100틱(5초) 뒤에 발생한다. 즉, 갑작스러운 스파이크에는 즉각 대응이 불가능하다.
- **거리 축소의 한계**: 뷰 거리 축소는 이미 로드된 청크의 틱을 중단하지 않는다. 새 청크 로딩만 억제한다. 이미 로드된 청크의 엔티티는 계속 처리된다.
- **고정 멀티플레이어 환경**: 일부 미니게임 서버처럼 특정 뷰 거리가 게임 로직에 영향을 주는 경우 거버너를 비활성화해야 한다.

### 플레이어 경험에 미치는 영향

뷰 거리 4 이상에서는 대부분의 플레이어가 축소를 체감하기 어렵다. 특히 PvP, 미니게임, 건물 작업처럼 원거리 시야를 필요로 하지 않는 상황에서는 사실상 무관하다. 단, 탐험 서버나 원거리 지형 감상이 중요한 서버에서는 고려가 필요하다.

### 설정 옵션 (arc-ops.yml)

```yaml
performance:
  governor:
    enabled: true
    # 부하 감지 주기 (틱 단위, 기본 100 = 5초)
    check-interval-ticks: 100
    # 거버너가 줄일 수 없는 최솟값
    min-view-distance: 4
    min-simulation-distance: 4
    # LoadLevel 임계값 (밀리초)
    high-mspt-threshold: 45.0
    critical-mspt-threshold: 50.0
    low-mspt-threshold: 25.0
```

### 플러그인 API 예시

**Kotlin:**
```kotlin
import dev.arc.api.perf.ServerLoad
import dev.arc.api.perf.LoadLevel

// 현재 부하 단계 조회
val level: LoadLevel = ServerLoad.currentLevel()
val mspt: Double = ServerLoad.currentMspt()

// 부하 단계 변경 감지
@EventHandler
fun onLoadLevelChange(event: ServerLoadLevelChangeEvent) {
    val from = event.previousLevel
    val to = event.newLevel
    logger.info("서버 부하 단계 변경: $from → $to (현재 MSPT: ${ServerLoad.currentMspt()}ms)")

    if (to == LoadLevel.CRITICAL) {
        // 부하가 심각할 때 자체 최적화 로직 실행
        suspendNonEssentialTasks()
    }
}
```

**Java:**
```java
import dev.arc.api.perf.ServerLoad;
import dev.arc.api.perf.LoadLevel;

LoadLevel level = ServerLoad.currentLevel();
double mspt = ServerLoad.currentMspt();

if (level == LoadLevel.HIGH || level == LoadLevel.CRITICAL) {
    // 고부하 시 플러그인 자체 작업 축소
    reduceTaskFrequency();
}
```

---

## 2. 엔티티 밀도 가드 (Entity Density Guard)

### 개요

엔티티 밀도 가드는 특정 청크에 몹이 과도하게 밀집되어 TPS를 떨어뜨리는 상황을 방지한다. **TPS가 임계값 이하일 때만** 스폰을 억제하므로, 정상 TPS에서는 완전히 비활성 상태다.

### 동작 원리

매 틱마다 몹이 스폰될 청크의 현재 몹 수를 확인한다. 다음 두 조건을 **모두** 만족할 때만 스폰을 억제한다.

1. 해당 청크의 몹 수가 `max-mobs-per-chunk`를 초과
2. 현재 TPS가 `activation-tps` 이하

두 조건 중 하나라도 거짓이면 스폰은 정상적으로 진행된다.

### 기본값이 OFF인 이유

스폰 억제는 게임플레이에 직접적인 영향을 준다. 몬스터 팜, 드랍 수집기, 특정 퀘스트 시스템 등 몹 스폰량에 의존하는 컨텐츠가 예상과 다르게 동작할 수 있다. 따라서 기본값은 비활성화이며, 해당 서버의 특성과 플레이어 기대치를 고려해 명시적으로 활성화해야 한다.

### 장점

- **TPS 보호**: 몹 밀집에 의한 TPS 드랍을 예방한다.
- **선택적 적용**: 정상 TPS에서는 완전히 투명하게 동작한다.
- **청크 단위 제어**: 서버 전체가 아닌 문제가 되는 청크에만 적용된다.

### 단점 및 주의사항

- **팜 영향**: 청크 기반 몹 팜이 `max-mobs-per-chunk` 상한에 의해 효율이 줄어들 수 있다.
- **불균등한 경험**: 밀집된 청크에서 플레이하는 플레이어는 스폰률이 낮아지는 것을 체감할 수 있다.
- **진단 필요**: 활성화 전에 `/arc debug entities`로 어느 청크가 문제인지 파악하는 것을 권장한다.

### 언제 활성화해야 하나

다음 상황에서 활성화를 고려한다:

- 특정 청크(보통 몬스터 팜, 스포너 지역)가 원인인 TPS 드랍이 반복적으로 발생하는 경우
- `StallWatchdog` 스택 트레이스에 엔티티 틱 관련 메서드가 반복 등장하는 경우
- `/arc doctor` 출력에서 엔티티 밀도 경고가 표시되는 경우

### 설정 옵션 (arc-ops.yml)

```yaml
performance:
  entity-density-guard:
    enabled: false          # 기본 비활성화
    max-mobs-per-chunk: 24  # 청크당 최대 몹 수
    activation-tps: 18.0    # 이 TPS 이하일 때만 억제 작동
```

### 플러그인 API 예시

```kotlin
// 특정 청크의 엔티티 수 조회
val chunk: Chunk = player.location.chunk
val mobCount = chunk.entities.count { it is LivingEntity && it !is Player }

// 밀도 가드 상태 확인 (API 미제공 시 설정에서 직접 읽기)
if (mobCount > ArcConfig.getInt("performance.entity-density-guard.max-mobs-per-chunk")) {
    player.sendMessage("이 청크에 몹이 너무 많습니다: $mobCount")
}
```

---

## 3. 틱 버짓 스프레더 (Tick Budget Spreader)

### 개요

틱 버짓 스프레더는 메인 스레드 틱 처리 시간을 여러 작업 카테고리에 균등하게 배분해, 단일 작업이 한 틱을 독점하는 상황을 방지한다. 결과적으로 렉 스파이크의 크기를 줄이고 평균 MSPT를 더 일정하게 유지한다.

### 문제: 틱 독점이란

Minecraft 서버는 한 틱(50ms 목표) 안에 청크 처리, 엔티티 틱, 블록 업데이트, 플러그인 스케줄러 작업 등 수십 가지 작업을 순서대로 처리한다. 특정 플러그인이 스케줄러 작업에서 대용량 반복문을 실행하거나, 청크 한 개에 수백 개의 엔티티가 몰려 있으면, 그 작업 하나가 수십 ms를 잡아먹어 전체 틱이 100ms~200ms로 늘어나는 렉 스파이크가 발생한다.

### 동작 원리

스프레더는 각 작업 카테고리(플러그인 스케줄러, 엔티티 틱, 청크 틱 등)에 틱당 처리 시간 예산(budget)을 할당한다. 한 카테고리가 예산을 소진하면 남은 작업은 다음 틱으로 미뤄진다. 이 방식으로 단일 카테고리가 전체 틱을 소비하는 것을 막는다.

### 장점

- **렉 스파이크 완화**: 특정 작업으로 인한 급격한 MSPT 상승을 억제한다.
- **일정한 체감 성능**: 평균 MSPT가 높더라도 일정하게 유지되면 플레이어가 체감하는 렉이 줄어든다.
- **자동 조정**: 별도의 플러그인 수정 없이 서버 레벨에서 적용된다.

### 단점 및 주의사항

- **처리량 감소 가능성**: 예산 제한으로 인해 한 틱에 처리 가능한 작업 수가 줄어들 수 있다. 대규모 배치 작업(예: 한 번에 수백 개의 블록 변경)은 여러 틱에 걸쳐 처리된다.
- **지연 민감 작업**: 틱당 완료가 보장되어야 하는 작업(예: 즉시 피드백이 필요한 상호작용)에는 예산 제한이 부적합할 수 있다.
- **디버깅 복잡도**: "왜 이 작업이 여러 틱에 걸쳐 실행되나?"를 이해하려면 스프레더 설정을 알아야 한다.

### 설정 옵션 (arc-ops.yml)

```yaml
performance:
  tick-budget:
    enabled: true
    # 각 카테고리별 틱당 최대 처리 시간 (밀리초)
    plugin-scheduler-budget-ms: 10
    entity-tick-budget-ms: 15
    chunk-tick-budget-ms: 10
    # 예산 초과 시 다음 틱으로 미룰 수 있는 최대 틱 수
    max-defer-ticks: 3
```

### 모니터링

스프레더가 작동 중일 때 `/arc timings` 커맨드로 카테고리별 틱 사용 시간을 확인할 수 있다. 특정 카테고리가 지속적으로 예산을 초과한다면 해당 카테고리의 예산을 늘리거나, 원인이 되는 플러그인/설정을 최적화하는 것을 고려한다.
