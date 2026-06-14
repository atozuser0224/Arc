---
title: 설정 레퍼런스
nav_order: 6
---

# 설정 레퍼런스

Arc는 서버 시작 시 `plugins/Arc/` 디렉토리에 두 개의 설정 파일을 자동으로 생성한다. 대부분의 설정은 서버 재시작 없이 `/arc config set <경로> <값>`으로 즉시 반영된다.

---

## arc-ops.yml

```yaml
# 렉 스파이크 자동 캡처 (기본 ON — 읽기 전용, 서버 상태 변경 없음)
lag-spike-capture:
  enabled: true
  mspt-threshold: 100.0        # 이 ms 초과 시 스파이크로 기록
  capture-duration-ticks: 40   # 스파이크 전후 40틱(2초) 캡처
  save-report: true
  max-reports: 50              # 최대 보관 보고서 수

# 플러그인별 메인 스레드 점유 비용 측정 (기본 ON)
plugin-cost:
  enabled: true

# 거리 기반 엔티티 AI 조절 (기본 OFF — AI 동작 변화 주의)
entity-optimization:
  enabled: false
  distance-based-ai:
    enabled: false
    near-range: 32.0           # 이 반경 내: 매 틱 AI
    far-range: 64.0            # near~far: far-ai-interval 간격으로 AI
    far-ai-interval: 5         # 원거리 몹 AI 실행 간격 (틱)
    check-interval-ticks: 20
  worlds: []                   # 빈 목록 = 모든 월드

# 청크당 몹 밀도 제한 (기본 OFF)
entity-density-guard:
  enabled: false
  max-mobs-per-chunk: 24
  activation-tps: 18.0         # TPS ≤ 이 값일 때만 작동
  check-interval-ticks: 100
  worlds: []

# JVM 힙 감시 (기본 ON)
memory-guard:
  enabled: true
  warn-fraction: 0.85          # 힙 85% → WARNING 로그
  critical-fraction: 0.95      # 힙 95% → 조치 시도
  check-interval-ticks: 100
  action-cooldown-seconds: 30
  allow-forced-gc: false       # System.gc() 강제 호출 허용 여부

# 메인 스레드 감시자 (항상 ON, 비활성화 불가)
stall-watchdog:
  enabled: true
  threshold-ms: 5000           # 5초 무응답 → 스택 트레이스 덤프
  full-thread-dump: false      # true = JVM 전체 스레드 덤프

# Prometheus 메트릭 엔드포인트 (기본 OFF)
status:
  enabled: false
  bind-address: "127.0.0.1"
  port: 9595
  snapshot-interval-ticks: 20  # 1초마다 스냅샷

# 시작 시 이전 크래시 자동 분석
crash:
  analyze-on-boot: true
```

### 퍼포먼스 관련 기본값 설명

| 설정 | 기본값 | 의미 |
|------|--------|------|
| `lag-spike-capture.mspt-threshold` | `100.0ms` | 5틱(0.25초) 이상 지연 시 기록 |
| `entity-density-guard.activation-tps` | `18.0` | TPS 18 이하(MSPT ~55ms)에서만 스폰 억제 |
| `memory-guard.warn-fraction` | `0.85` | 8GB 힙이면 6.8GB 도달 시 경고 |
| `stall-watchdog.threshold-ms` | `5000` | 5초 행 → 자동 진단 덤프 |

---

## arc-network.yml

```yaml
arc-network:
  enabled: false               # Redis 설정 후 true로 변경
  server:
    id: server-1               # 네트워크 내 고유 ID — 변경 시 관련 키 전부 새로 생성됨
    group: default             # 서버 그룹 (lobby, game, minigame 등)
    tags: []

  relay:
    redis:
      host: 127.0.0.1
      port: 6379
      password: ""             # 프로덕션에서 반드시 설정
      pool-size: 8             # 동시 연결 수 (서버 많으면 16 권장)

  heartbeat:
    interval-seconds: 2        # 2초마다 상태 기록
    ttl-seconds: 10            # TTL = interval × 5 이상 권장

  queue:
    enabled: true
    transfer-check-interval-seconds: 5   # 드레이너 실행 주기
    position-message-interval: 30        # 대기 순위 안내 주기 (초)
    keep-on-disconnect-seconds: 300      # 접속 끊김 후 대기 위치 보존 시간
    vip-permission: arc.queue.vip        # 1분 우선순위
    priority-permission: arc.queue.priority  # 5분 우선순위

  audit:
    enabled: true
    retention-days: 90
    sensitive-data-masking: true         # IP 등 개인정보 마스킹

  remote-command:
    enabled: false
    require-confirm: true
    rate-limit: 3              # 분당 최대 실행 수
    allowlist:
      - say
      - save-all
    blocklist:
      - op
      - deop
      - stop
      - reload

  inventory-transfer:
    enabled: false             # 서버 간 인벤토리 자동 이전

  plugin-deploy:
    enabled: false             # 원격 JAR 배포
```

---

## Prometheus 메트릭

`status.enabled: true` 설정 시 `http://<host>:9595/metrics` 에서 조회 가능하다.

```
# HELP arc_tps 현재 TPS
# TYPE arc_tps gauge
arc_tps 19.8

# HELP arc_tps_5m 5분 평균 TPS
arc_tps_5m 19.5

# HELP arc_mspt 현재 ms/tick
arc_mspt 12.4

# HELP arc_players_online 현재 접속 플레이어 수
arc_players_online 47.0

# HELP arc_players_max 최대 플레이어 수
arc_players_max 100.0

# HELP arc_memory_used_bytes JVM 힙 사용량
arc_memory_used_bytes 4509715456.0

# HELP arc_memory_max_bytes JVM 힙 최대값
arc_memory_max_bytes 8589934592.0

# HELP arc_memory_used_fraction 힙 사용률 (0.0 ~ 1.0)
arc_memory_used_fraction 0.525
```

`/health` 엔드포인트는 서버가 살아있으면 단순히 `OK`를 반환한다.

---

## 런타임 설정 변경

```
/arc config get lag-spike-capture.mspt-threshold
→ 현재 값: 100.0

/arc config set lag-spike-capture.mspt-threshold 50.0
→ 변경됨 (이전: 100.0 → 새 값: 50.0)

/arc config diff
→ 기본값과 다른 설정:
   lag-spike-capture.mspt-threshold: 100.0 → 50.0
   entity-density-guard.enabled: false → true

/arc config history
→ [15:32:11] Admin: mspt-threshold 100.0 → 50.0
   [14:21:05] Operator: entity-density-guard.enabled false → true

/arc config reset lag-spike-capture.mspt-threshold
→ 기본값 100.0으로 복원됨
```

---

## 시나리오별 권장 설정

### 소규모 싱글 서버 (30인 이하)

모든 기본값 유지. `entity-optimization`, `entity-density-guard` OFF 상태가 적합하다. Prometheus 모니터링 인프라가 없다면 `status.enabled: false`로 충분하다.

### 중규모 서버 (30~100인)

```yaml
lag-spike-capture:
  mspt-threshold: 50.0       # 더 민감하게 감지

entity-density-guard:
  enabled: true
  max-mobs-per-chunk: 20
  activation-tps: 17.0       # 더 보수적 — 정상 시 간섭 최소화

status:
  enabled: true              # Grafana 연동 권장
  port: 9595
```

### 대규모 멀티서버 네트워크 (100인 이상)

```yaml
# arc-ops.yml
stall-watchdog:
  full-thread-dump: true     # 더 많은 진단 정보

# arc-network.yml
relay:
  redis:
    pool-size: 16            # 커넥션 풀 증가

queue:
  transfer-check-interval-seconds: 3   # 더 빠른 대기열 처리

audit:
  retention-days: 180        # 6개월 감사 로그 보존
```

위험 기능(remote-command, plugin-deploy)은 CI/CD 파이프라인이 준비된 경우에만 활성화한다.
