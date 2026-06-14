---
title: Fork Core
nav_order: 5
---

# Layer 1 — Fork Core

Fork Core는 Arc의 기반 레이어다. Leaf(Paper/Purpur 계열)를 베이스로 하며, 서버를 시작하는 순간부터 자동으로 작동한다. 설정 파일을 건드리지 않아도 기본 진단·안전 기능이 켜진 상태로 시작된다.

---

## 성능 거버너

### 적응형 뷰 거버너 (Adaptive Governor)

서버 부하를 실시간으로 감시하고, TPS가 떨어지기 시작하면 자동으로 뷰 거리와 시뮬레이션 거리를 단계적으로 줄인다. 부하가 회복되면 원래 값으로 즉시 복원한다.

**작동 임계값 (arc-server 기본값)**

| LoadLevel | MSPT | 동작 |
|-----------|------|------|
| `LOW` | ≤ 25ms | 원래 거리 즉시 복원 |
| `NORMAL` | 25 ~ 45ms | 개입 없음 |
| `HIGH` | ≥ 45ms | 뷰/시뮬레이션 거리 1단계 축소 |
| `CRITICAL` | ≥ 50ms | 추가 1단계 축소 (매 100틱마다 반복) |

**왜 뷰/시뮬레이션 거리인가?** 이 두 값은 서버 틱 비용의 가장 큰 비중을 차지한다. 청크 틱, 엔티티 틱, 패킷 전송량이 모두 이 값에 비례한다. 다른 최적화보다 효과 대비 플레이어 경험 영향이 적다.

플러그인에서도 동일 API를 활용할 수 있다:

```kotlin
// 플러그인에서 거버너 직접 사용
val governor = plugin.adaptiveGovernor().apply {
    minViewDistance = 3       // 최소 뷰 거리 (기본 4)
    minSimulationDistance = 3 // 최소 시뮬레이션 거리 (기본 4)
    checkPeriodTicks = 100L   // 100틱(5초)마다 평가
}
governor.start()

// 현재 부하 수준 실시간 조회
val load: LoadLevel = ServerLoad.level  // LOW / NORMAL / HIGH / CRITICAL
val mspt: Double = ServerLoad.mspt      // 현재 ms/tick
val tps: Double = ServerLoad.tps        // 현재 TPS

// 이벤트로 감지
@EventHandler
fun onLoadChange(event: ServerLoadLevelChangeEvent) {
    if (event.newLevel == LoadLevel.CRITICAL) {
        // 긴급 조치 (몹 소환 중단 등)
    }
}
```

### 엔티티 밀도 가드 (Entity Density Guard)

특정 청크에 몹이 너무 많이 몰리면 추가 스폰을 억제한다. **TPS가 임계값 이상일 때는 작동하지 않아**, 서버가 정상 상태일 때는 일반 스폰 행동을 유지한다.

- 청크당 최대 몹 수 초과 시 스폰 억제 (기본: 24마리)
- TPS 18.0 이하일 때만 활성화
- `arc-ops.yml`에서 `entity-density-guard.enabled: true`로 활성화 (기본 OFF)

### 틱 버짓 스프레더 (Tick Budget)

메인 스레드의 틱 처리 시간을 여러 작업에 균등하게 배분한다. 단일 작업이 한 틱을 독점해 렉 스파이크가 발생하는 현상을 방지한다.

---

## NMS 브리지

Minecraft 내부 코드(NMS)에 접근해야 하는 기능들은 버전이 바뀔 때마다 호환성 문제를 일으킨다. Arc의 NMS 브리지는 리플렉션 기반 추상화 레이어로, NMS에 직접 의존하지 않고도 저수준 서버 기능을 사용할 수 있게 한다.

**제공 기능:**
- **NmsRef** — NMS 클래스와 메서드에 대한 안전한 리플렉션 래퍼. 내부적으로 캐싱되어 반복 호출 비용이 낮다.
- **패킷 직접 전송** — 특정 플레이어에게 NMS 수준 패킷을 직접 전송.
- **NmsClientWorldStateBackend** — 날씨·시간 등 클라이언트 월드 상태를 플레이어별로 독립적으로 제어.
- **NmsNpcBackend** — NPC 엔티티의 스킨, 이름, 이동을 NMS 수준에서 조작.
- **NmsRegistryBackend** — Minecraft 레지스트리(아이템, 엔티티, 인챈트 등)에 직접 접근.
- **NmsThreadGuard** — 메인 스레드 외부에서 NMS 접근이 발생하면 경고.

---

## 커스텀 이벤트

Arc는 Bukkit의 기본 이벤트 시스템으로 감지할 수 없는 서버 수준 이벤트를 추가한다. 표준 `@EventHandler`로 수신한다.

| 이벤트 | 발생 시점 | 활용 사례 |
|--------|-----------|-----------|
| `ServerLoadLevelChangeEvent` | 서버 부하 단계 변경 | 부하 단계별 서버 행동 조절 |
| `ServerLagSpikeEvent` | MSPT가 설정 임계값 초과 | 렉 발생 시점 로깅·알림 |
| `PlayerChangeChunkEvent` | 플레이어가 청크 경계를 이동 | 청크 기반 지역 시스템 |
| `PlayerDoubleSneakEvent` | 빠른 두 번 웅크리기 | 능력 발동, 날개 토글 등 |
| `MenuOpenEvent` / `MenuCloseEvent` | Arc GUI 메뉴 열기·닫기 | 인벤토리 UI 상태 추적 |
| `ArcInitializeEvent` | Arc 초기화 완료 | 다른 플러그인이 Arc 준비를 기다릴 때 |
| `ArcNetworkPlayerTransferEvent` | 플레이어가 다른 서버로 이동 | 이동 전 데이터 저장 훅 |
| `ArcNetworkPlayerBanEvent` | 네트워크 밴이 이 서버로 수신됨 | 밴 이벤트 로깅, 알림 |
| `ArcNetworkQueueJoinEvent` | 플레이어가 서버 대기열에 진입 | 대기 화면 표시 |
| `ArcNetworkQueueLeaveEvent` | 플레이어가 대기열을 떠남 | 대기 UI 제거 |

```kotlin
// 예시: 렉 스파이크 발생 시 Discord 알림
@EventHandler
fun onLagSpike(event: ServerLagSpikeEvent) {
    val mspt = event.mspt
    if (mspt > 200) {
        plugin.logger.warning("심각한 렉 스파이크: ${mspt}ms")
        // ArcDiscordWebhook.send(...) 등으로 외부 알림
    }
}

// 예시: 부하 단계별 동작 전환
@EventHandler
fun onLoadChange(event: ServerLoadLevelChangeEvent) {
    when (event.newLevel) {
        LoadLevel.CRITICAL -> disableNonEssentialFeatures()
        LoadLevel.LOW -> restoreAllFeatures()
        else -> {}
    }
}
```

---

## 서버 부하 모니터링

`ServerLoad` API로 서버 현재 상태를 실시간 조회한다.

```kotlin
// 현재 부하 수준
val level: LoadLevel = ServerLoad.level

// 직접 수치
val mspt = ServerLoad.mspt  // 현재 ms/tick (arc-server 환경에서 정확한 값)
val tps  = ServerLoad.tps   // 현재 TPS

// 임계값 커스터마이즈
ServerLoad.highMsptThreshold     = 45.0  // HIGH 기준 (기본 45ms)
ServerLoad.criticalMsptThreshold = 50.0  // CRITICAL 기준 (기본 50ms)
ServerLoad.lowMsptThreshold      = 25.0  // 회복 기준 (기본 25ms)
```

`MetricsHistory`는 최근 수백 개의 MSPT 샘플을 순환 버퍼에 유지해 Prometheus 엔드포인트 또는 `/arc memory`에서 추세 분석이 가능하다.

---

## StallWatchdog

메인 스레드가 응답하지 않는 상황을 감지하는 별도 스레드 감시자다.

- **임계값**: 기본 5초 (`stall-watchdog.threshold-ms: 5000`)
- **동작**: 설정 시간 초과 시 스택 트레이스를 파일로 기록
- **full-thread-dump**: `true`로 설정하면 JVM 전체 스레드 덤프 (원인 분석에 유리)
- **항상 실행**: 비활성화 불가 — 진단 데이터 확보를 위해 의도적으로 설계

서버가 응답 없이 멈췄을 때 덤프 파일 분석으로 어느 플러그인이 메인 스레드를 차단했는지 즉시 파악할 수 있다.

---

## 메모리 가드

JVM 힙 사용률을 주기적으로 확인하고, 임계값 도달 시 경고 및 조치를 취한다.

| 단계 | 기본 임계값 | 동작 |
|------|------------|------|
| 경고 | 힙 85% | 로그에 WARNING 출력 |
| 긴급 | 힙 95% | `allow-forced-gc: true`면 System.gc() 시도 |

- **점검 주기**: 100틱 (5초)
- **조치 쿨다운**: 30초 (연속 조치 방지)
- `/arc memory`로 현재 힙 상태와 GC 통계를 즉시 확인

---

## 충돌 분석

서버 시작 시 이전 크래시 보고서를 자동으로 분석한다. 분석 결과는 `/arc doctor` 출력에 포함되어, 어떤 플러그인이나 코드 경로가 충돌을 일으켰는지 빠르게 파악할 수 있다.

---

## 코루틴 · 비동기 처리

Arc는 `dev.arc.api.coroutine` 패키지를 통해 Kotlin 코루틴 기반의 비동기 처리를 지원한다.

```kotlin
// 무거운 DB 작업을 비동기로, 결과를 메인 스레드에서 처리
ArcAsync.runBlockingIO {
    database.query("SELECT * FROM players WHERE uuid = ?", uuid)
}.thenSync { result ->
    player.sendMessage("데이터: ${result.name}")
}.exceptionallySync { e ->
    player.sendMessage("오류: ${e.message}")
}

// 코루틴 스타일
plugin.launchEveryTicks(100L) {
    // 100틱마다 실행 (메인 스레드)
    checkServerLoad()
}
```

- **ArcAsync** — 무거운 I/O 작업을 비동기로 처리하고, 결과를 메인 스레드로 안전하게 돌려받는 체인 API
- **ArcDispatchers** — 메인 스레드, IO 스레드 풀, 공용 풀 디스패처 제공
- ServiceLoader SPI 기반이라 다른 구현체로 교체 가능
