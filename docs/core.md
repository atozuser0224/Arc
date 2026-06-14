---
title: Fork Core
nav_order: 5
---

# Layer 1 — Fork Core

항상 활성화되는 기반 레이어. Leaf(Paper/Purpur) 위에서 실행되며 별도 설정 없이 동작한다.

---

## 성능 거버너

### 적응형 뷰 거버너 (AdaptiveGovernor)

서버 부하를 감시해 TPS가 떨어지면 자동으로 뷰·시뮬레이션 거리를 단계적으로 줄이고, 부하가 회복되면 원래 값으로 복원한다.

```kotlin
val governor = plugin.adaptiveGovernor().apply {
    minViewDistance = 3
    minSimulationDistance = 3
    checkPeriodTicks = 100L
}
governor.start()
```

- 월드별 원본 값을 저장 → 과부하 시 한 단계씩 감소 → 회복 시 즉시 전체 복원
- 진동 방지를 위해 내려갈 때만 단계적, 올라갈 때는 한 번에 복원
- 기본 비활성 (`arc-ops.yml` → `entity-optimization.enabled: false`)

### 엔티티 밀도 가드 (EntityThrottler)

청크당 몹 수가 임계값을 초과하면 새 스폰을 억제한다.

- `max-mobs-per-chunk` (기본 24): 초과 시 스폰 억제
- `activation-tps` (기본 18.0): TPS가 이 값 이하일 때만 작동
- 기본 비활성

### 틱 버짓 스프레더 (TickBudget)

메인 스레드 틱 예산을 여러 작업에 나눠준다. 한 작업이 틱 예산을 독점하지 않도록 한다.

---

## NMS 브리지

### NmsRef / ReflectiveArcNms

리플렉션 기반 NMS 접근 래퍼. NMS 클래스를 컴파일 타임 의존성 없이 런타임에 접근한다.

```kotlin
// 내부 사용 예
val server = NmsRef.craftServer()
val nmsWorld = NmsRef.craftWorldHandle(world)
```

- `NmsClientWorldStateBackend`: 클라이언트 월드 상태(날씨·시간) 변경
- `NmsNpcBackend`: NPC 엔티티 NMS 레벨 조작
- `NmsRegistryBackend`: Minecraft 레지스트리 접근
- `NmsThreadGuard`: 메인 스레드 외 NMS 접근 감지

### 패킷 직접 전송

```kotlin
player.sendPacket(packet)  // NMS 패킷을 플레이어에게 직접 전송
```

---

## 커스텀 이벤트

모두 Bukkit EventBus를 통해 일반 `@EventHandler`로 수신.

| 이벤트 | 발생 시점 |
|--------|-----------|
| `ServerLoadLevelChangeEvent` | 서버 부하 단계 변경 (NORMAL/ELEVATED/HIGH/CRITICAL) |
| `ServerLagSpikeEvent` | MSPT가 임계값(기본 100ms) 초과 |
| `PlayerChangeChunkEvent` | 플레이어가 청크 경계 이동 |
| `PlayerDoubleSneakEvent` | 플레이어 빠른 두 번 웅크리기 |
| `MenuOpenEvent` / `MenuCloseEvent` | Arc GUI 메뉴 열기/닫기 |
| `ArcInitializeEvent` | Arc 초기화 완료 |

```kotlin
@EventHandler
fun onLagSpike(event: ServerLagSpikeEvent) {
    logger.warning("Lag spike: ${event.mspt}ms")
}
```

---

## 서버 부하 모니터링

```kotlin
val load = ServerLoad.current()   // NORMAL / ELEVATED / HIGH / CRITICAL
val mspt = ServerLoad.mspt()      // 최근 평균 밀리초/틱
val tps  = ServerLoad.tps(1)      // 1분 평균 TPS
```

`MetricsHistory`는 최근 N개 MSPT 샘플을 순환 버퍼에 보관해 통계를 제공한다.

---

## StallWatchdog

오프스레드 감시자. 메인 스레드가 `threshold-ms`(기본 5000ms) 이상 응답하지 않으면 스택트레이스를 파일로 덤프한다.

- `stall-watchdog.full-thread-dump: true` → JVM 전체 스레드 덤프
- 항상 실행 (비활성 불가)

---

## 메모리 가드

JVM 힙 사용률을 감시해 임계값 초과 시 경고·조치한다.

```yaml
memory-guard:
  enabled: true
  warn-fraction: 0.85       # 85% 도달 시 경고
  critical-fraction: 0.95   # 95% 도달 시 조치
  allow-forced-gc: false    # System.gc() 강제 호출 허용
```

---

## 충돌 분석

서버 시작 시 이전 크래시 보고서를 자동 분석한다 (`crash.analyze-on-boot: true`). `/arc doctor`에 요약을 포함시킨다.

---

## 코루틴 · 비동기 레이어

`dev.arc.api.coroutine` 패키지. ServiceLoader SPI로 백엔드를 교환 가능하다.

```kotlin
// 틱 주기 반복
plugin.launchEveryTicks(20L) { /* 매 1초 */ }

// ArcAsync (Ops Suite 전용)
ArcAsync.runBlockingIO { heavyWork() }
    .thenSync { result -> /* 메인 스레드에서 */ }
    .exceptionallySync { e -> /* 에러 처리 */ }
```

디스패처: `ArcDispatchers.main` (Bukkit 메인), `ArcDispatchers.io` (IO 풀), `ArcDispatchers.default` (공용).
