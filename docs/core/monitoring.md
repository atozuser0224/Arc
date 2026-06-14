---
title: 서버 모니터링
parent: Fork Core
nav_order: 2
---

# 서버 모니터링

Arc는 서버 상태를 실시간으로 진단하고 외부 모니터링 시스템과 연동하기 위한 다섯 가지 모니터링 서브시스템을 제공한다. 서버 운영자는 콘솔 커맨드와 Prometheus 메트릭을 통해 문제를 조기에 감지할 수 있고, 플러그인 개발자는 ServerLoad API를 통해 서버 상태에 따른 자체 최적화 로직을 구현할 수 있다.

---

## 1. ServerLoad API

### 개요

`ServerLoad`는 Arc 메인 스레드에서 수집한 실시간 부하 데이터를 플러그인에 제공하는 API다. MSPT, TPS, LoadLevel을 즉시 조회할 수 있으며, 이력 데이터와 임계값 커스터마이즈도 지원한다.

### 기본 조회

```kotlin
import dev.arc.api.perf.ServerLoad
import dev.arc.api.perf.LoadLevel

// 현재 LoadLevel (LOW / NORMAL / HIGH / CRITICAL)
val level: LoadLevel = ServerLoad.currentLevel()

// 최근 N틱 평균 MSPT
val mspt: Double = ServerLoad.currentMspt()       // 단기 평균 (20틱)
val mspt5m: Double = ServerLoad.averageMspt(6000) // 5분 평균

// 현재 TPS (1분, 5분, 15분)
val tps1m: Double = ServerLoad.tps(1)
val tps5m: Double = ServerLoad.tps(5)
val tps15m: Double = ServerLoad.tps(15)
```

**Java:**
```java
LoadLevel level = ServerLoad.currentLevel();
double mspt = ServerLoad.currentMspt();
double tps = ServerLoad.tps(1);
```

### 임계값 커스터마이즈

기본 임계값은 `arc-ops.yml`에서 설정하지만, 플러그인에서 런타임으로 변경할 수도 있다. 임계값 변경은 거버너와 이벤트 발생에 즉시 반영된다.

```kotlin
import dev.arc.api.perf.ServerLoadConfig

// 임계값 변경 (예: 더 보수적인 설정)
ServerLoadConfig.setHighMsptThreshold(40.0)      // 기본 45ms
ServerLoadConfig.setCriticalMsptThreshold(48.0)  // 기본 50ms
ServerLoadConfig.setLowMsptThreshold(20.0)       // 기본 25ms

// 설정 초기화 (arc-ops.yml 값으로 복원)
ServerLoadConfig.resetToDefaults()
```

### MetricsHistory (순환 버퍼)

Arc는 내부적으로 순환 버퍼(ring buffer)로 최근 N틱의 MSPT 이력을 보관한다. 이 데이터는 Prometheus 연동이나 자체 분석 플러그인에서 활용할 수 있다.

```kotlin
import dev.arc.api.perf.MetricsHistory

// 최근 100틱의 MSPT 이력 (DoubleArray)
val history: DoubleArray = MetricsHistory.getMsptHistory(100)

// 이력 중 최대값 / 최솟값
val peak = history.max()
val floor = history.min()

// 표준편차 (안정성 지표)
val stdDev = MetricsHistory.msptStdDev(100)
```

---

## 2. StallWatchdog

### 개요

StallWatchdog는 별도의 감시 스레드(watchdog thread)에서 메인 서버 스레드를 주기적으로 폴링한다. 메인 스레드가 일정 시간 이상 응답하지 않으면(기본 5초), 해당 시점의 스택 트레이스를 로그에 기록해 어떤 코드가 메인 스레드를 블로킹하는지 파악할 수 있게 한다.

### 동작 방식

1. 메인 스레드는 매 틱 시작 시 타임스탬프를 갱신한다.
2. Watchdog 스레드는 500ms마다 타임스탬프를 확인한다.
3. 마지막 갱신으로부터 `stall-threshold-seconds`(기본 5초) 이상 경과하면 스택 트레이스를 출력한다.
4. `full-thread-dump: true` 설정 시 JVM 전체 스레드 덤프(모든 스레드의 스택)를 기록한다.

### 출력 예시

```
[Arc/StallWatchdog] 메인 스레드 응답 없음 (5.2초) — 스택 트레이스:
  at com.example.plugin.MyTask.run(MyTask.java:42)
  at org.bukkit.craftbukkit.scheduler.CraftTask.run(CraftTask.java:...)
  at net.minecraft.server.MinecraftServer.w(MinecraftServer.java:...)
  ...
```

이 정보로 어느 플러그인의 어느 코드가 메인 스레드를 막는지 즉시 확인할 수 있다.

### 비활성화 불가 이유

StallWatchdog는 읽기 전용 진단 도구다. 메인 스레드에 아무런 개입을 하지 않으며, 별도 스레드에서 실행되므로 서버 틱에 영향을 주지 않는다. 오버헤드는 측정 불가 수준이며(수백 µs/초 이하), 문제 발생 시 원인 파악에 필수적이므로 비활성화 옵션을 제공하지 않는다.

### 장점

- 어떤 플러그인이 메인 스레드를 블로킹하는지 즉시 파악 가능
- 추가 프로파일링 도구 없이 운영 중에도 진단 가능
- `full-thread-dump`로 교착 상태(deadlock) 탐지 가능

### 설정 옵션 (arc-ops.yml)

```yaml
monitoring:
  stall-watchdog:
    stall-threshold-seconds: 5     # 스택 트레이스 출력 기준 (초)
    full-thread-dump: false        # true: JVM 전체 스레드 덤프
    dump-to-file: false            # true: logs/arc-stall-<timestamp>.txt에 저장
```

---

## 3. 메모리 가드 (Memory Guard)

### 개요

메모리 가드는 100틱(5초)마다 JVM 힙 사용률을 확인하고, 임계값에 따라 경고 로그를 출력하거나 GC를 유도한다. `/arc memory` 커맨드로 현재 힙 상태를 즉시 확인할 수도 있다.

### 임계값과 동작

| 힙 사용률 | 동작 |
|-----------|------|
| ≥ 85% | `WARNING` 레벨 로그 출력 |
| ≥ 95% | `CRITICAL` 로그 + `allow-forced-gc: true`면 `System.gc()` 호출 |

95% 이상에서 GC를 강제 유도하더라도 30초 쿨다운이 있어 과도한 강제 GC를 방지한다.

### `allow-forced-gc: false`가 기본인 이유

`System.gc()`는 JVM에 전체 GC(Full GC) 실행을 권고한다. 전체 GC는 STW(Stop-The-World) 단계를 포함하므로, 수백 ms에서 수 초의 렉 스파이크를 유발할 수 있다. 힙이 95% 찼을 때 강제 GC로 인해 서버가 더 심각한 렉을 경험하는 역효과가 발생할 수 있으므로, 기본값은 비활성화다.

**`allow-forced-gc: true`를 권장하는 경우:**
- G1GC 또는 ZGC를 사용 중이고 STW 시간이 짧음을 확인한 경우
- 메모리 누수 없이 힙이 95%까지 차는 상황이 반복되는 경우 (힙 크기 증설도 고려)
- 테스트 환경

### `/arc memory` 커맨드

```
/arc memory
> [Arc] 힙 상태
>   사용중: 3.2 GB / 4.0 GB (80%)
>   GC 횟수 (Young/Full): 142 / 2
>   최근 GC 일시: 00:12:34 (38초 전)
>   메모리 가드: NORMAL
```

### 설정 옵션 (arc-ops.yml)

```yaml
monitoring:
  memory-guard:
    enabled: true
    check-interval-ticks: 100    # 확인 주기 (틱)
    warn-heap-percent: 85        # WARNING 임계값 (%)
    critical-heap-percent: 95    # CRITICAL 임계값 (%)
    allow-forced-gc: false       # System.gc() 허용 여부
    gc-cooldown-seconds: 30      # GC 쿨다운
```

---

## 4. 충돌 분석기 (Crash Analyzer)

### 개요

Arc는 서버 시작 시 이전 실행의 크래시 보고서(`crash-reports/` 디렉터리)를 자동으로 분석한다. 분석 결과는 `/arc doctor` 출력에 포함되어, 어떤 플러그인이나 코드 경로가 크래시를 유발했는지 빠르게 파악할 수 있다.

### 분석 항목

- 스택 트레이스에서 플러그인 패키지명 추출 (가장 가능성 높은 원인 플러그인 표시)
- `java.lang.OutOfMemoryError` 등 JVM 레벨 예외 감지
- 반복 크래시 패턴 (같은 코드 경로에서 N회 이상 크래시)
- 마지막 정상 종료 이후 변경된 플러그인 목록 (플러그인 날짜 비교)

### `/arc doctor` 출력 예시

```
/arc doctor
> [Arc Doctor] 서버 진단 결과
> ✔ 성능 거버너: 정상
> ✔ 메모리: 62% (정상)
> ⚠ 최근 크래시: 2회 (지난 24시간)
>   원인 추정: com.example.myplugin.SomeTask (2/2 크래시에서 발견)
>   마지막 크래시: 2024-01-15 03:42:11
> ✔ StallWatchdog: 정상
```

### 주의사항

충돌 분석기는 *추정* 원인을 제공한다. 스택 트레이스에 플러그인 패키지가 나타나더라도 해당 플러그인이 직접 원인이 아닐 수 있다. 확인을 위해 플러그인을 하나씩 제거하며 재현 테스트를 권장한다.

---

## 5. Prometheus 메트릭

### 개요

`arc-ops.yml`에서 `status.enabled: true`로 설정하면 HTTP 서버가 시작되어 Prometheus 호환 메트릭을 제공한다. 기본 포트는 9595이며, Grafana와 연동해 실시간 대시보드를 구성할 수 있다.

### 메트릭 엔드포인트

- **메트릭**: `http://<host>:9595/metrics`
- **헬스 체크**: `http://<host>:9595/health`

`/health` 엔드포인트는 서버가 응답 중이면 HTTP 200을 반환한다. Docker/Kubernetes의 liveness probe로 활용할 수 있다.

### 제공 메트릭 목록

| 메트릭 이름 | 타입 | 설명 |
|-------------|------|------|
| `arc_tps` | Gauge | 현재 1분 TPS |
| `arc_tps_5m` | Gauge | 5분 평균 TPS |
| `arc_tps_15m` | Gauge | 15분 평균 TPS |
| `arc_mspt` | Gauge | 현재 평균 MSPT (ms) |
| `arc_players_online` | Gauge | 현재 접속 플레이어 수 |
| `arc_memory_used_bytes` | Gauge | JVM 힙 사용량 (bytes) |
| `arc_memory_max_bytes` | Gauge | JVM 힙 최대 크기 (bytes) |
| `arc_load_level` | Gauge | LoadLevel (0=LOW, 1=NORMAL, 2=HIGH, 3=CRITICAL) |
| `arc_chunks_loaded` | Gauge | 현재 로드된 청크 수 |
| `arc_entities_total` | Gauge | 서버 전체 엔티티 수 |

### Grafana 연동 권장 시점

30인 이상의 플레이어가 상시 접속하는 서버에서는 Prometheus + Grafana 연동을 강력히 권장한다. 이하 소규모 서버에서도 `/arc memory`, `/arc doctor` 커맨드만으로 충분히 모니터링이 가능하다.

**Prometheus 스크레이프 설정 예시:**
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'arc-minecraft'
    static_configs:
      - targets: ['minecraft-server:9595']
    scrape_interval: 10s
```

### 설정 옵션 (arc-ops.yml)

```yaml
status:
  enabled: false           # Prometheus HTTP 서버 활성화
  port: 9595               # 메트릭 포트
  bind-address: "0.0.0.0" # 바인드 주소 (내부망만 허용 권장)
  metrics-prefix: "arc"   # 메트릭 이름 접두사
```

### 보안 주의사항

`bind-address`는 내부 네트워크 또는 localhost로 제한하는 것을 강력히 권장한다. `/metrics` 엔드포인트에는 인증이 없으므로, 외부에 노출되면 서버 내부 상태가 공개된다. Nginx 리버스 프록시나 방화벽 규칙으로 접근을 제한하라.
