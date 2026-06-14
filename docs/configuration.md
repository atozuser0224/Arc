---
title: 설정 레퍼런스
nav_order: 6
---

# 설정 레퍼런스

Arc는 두 개의 설정 파일을 `plugins/Arc/` 디렉토리에 생성한다. 모든 설정은 서버 재시작 없이 `/arc reload`로 반영된다 (일부 네트워크 설정 제외).

---

## arc-ops.yml

Ops Suite 전체 기능 제어.

```yaml
# Arc Operations Suite configuration.
# Diagnostics default ON (read-only). Aggressive optimizations default OFF.

lag-spike-capture:
  enabled: true
  mspt-threshold: 100.0          # 이 값(ms) 초과 시 렉 스파이크로 기록
  capture-duration-ticks: 40     # 스파이크 전후 캡처 구간 (틱)
  save-report: true              # 파일로 저장
  max-reports: 50                # 보관할 최대 보고서 수

plugin-cost:
  enabled: true                  # 플러그인별 메인스레드 점유 비용 측정

entity-optimization:
  enabled: false                 # 엔티티 최적화 활성화 (기본 OFF)
  distance-based-ai:
    enabled: false               # 거리별 AI 틱 조절
    near-range: 32.0             # 이 거리 내 → 매 틱 AI
    far-range: 64.0              # near~far → far-ai-interval 간격으로 AI
    far-ai-interval: 5           # 원거리 AI 틱 간격
    check-interval-ticks: 20     # 거리 재계산 주기
  worlds: []                     # 빈 목록 = 모든 월드

entity-density-guard:
  enabled: false                 # 청크당 몹 밀도 제한 (기본 OFF)
  max-mobs-per-chunk: 24
  activation-tps: 18.0           # TPS ≤ 이 값일 때만 작동
  check-interval-ticks: 100
  worlds: []

memory-guard:
  enabled: true
  warn-fraction: 0.85            # 힙 85% 이상 → 경고
  critical-fraction: 0.95        # 힙 95% 이상 → 조치
  check-interval-ticks: 100
  action-cooldown-seconds: 30    # 연속 조치 최소 간격
  allow-forced-gc: false         # System.gc() 강제 호출 허용

stall-watchdog:
  enabled: true
  threshold-ms: 5000             # 메인 스레드 응답 없음 판정 (ms)
  full-thread-dump: false        # true → JVM 전체 스레드 덤프

status:
  enabled: false                 # Prometheus 메트릭 엔드포인트
  bind-address: "127.0.0.1"
  port: 9595
  snapshot-interval-ticks: 20

crash:
  analyze-on-boot: true          # 시작 시 이전 크래시 자동 분석
```

---

## arc-network.yml

멀티서버 네트워크 레이어. Redis 없이는 비활성화된다.

```yaml
arc-network:
  enabled: false                 # Redis 설정 후 true로 변경
  server:
    id: server-1                 # 이 서버의 고유 ID (네트워크에서 유일해야 함)
    group: default               # 서버 그룹 (lobby, game, hub 등)
    tags: []                     # 추가 태그 (선택)

  relay:
    redis:
      host: 127.0.0.1
      port: 6379
      password: ""               # 비밀번호 없으면 빈 문자열
      pool-size: 8               # JedisPool 크기

  heartbeat:
    interval-seconds: 2          # 서버 상태를 Redis에 쓰는 주기
    ttl-seconds: 10              # Redis 키 TTL (interval의 5배 권장)

  queue:
    enabled: true
    transfer-check-interval-seconds: 5    # 드레이너 실행 주기
    position-message-interval: 30         # 대기 순위 안내 주기 (초)
    keep-on-disconnect-seconds: 300       # 접속 끊김 후 대기 위치 유지 시간
    vip-permission: arc.queue.vip         # -60초 우선순위
    priority-permission: arc.queue.priority  # -300초 우선순위

  audit:
    enabled: true
    retention-days: 90           # Redis 감사 로그 보존 기간
    sensitive-data-masking: true # IP 등 민감 정보 마스킹

  remote-command:
    enabled: false               # /arc network remote-cmd 수신·발신 모두 제어
    require-confirm: true        # 위험 명령 실행 전 confirm 토큰 요구
    rate-limit: 3                # 분당 최대 실행 수 (발신자 기준)
    allowlist:
      - say
      - save-all
    blocklist:
      - op
      - deop
      - stop
      - reload
```

---

## 런타임 설정 변경

```
/arc config get <path>         # 현재 값 조회
/arc config set <path> <value> # 실행 중 변경 (이력 기록)
/arc config reset <path>       # 기본값 복원
/arc config diff               # 기본값과 비교
/arc config history            # 변경 이력
```

경로 예시: `lag-spike-capture.mspt-threshold`, `entity-optimization.enabled`

---

## 위험도별 기본값 요약

| 기능 | 기본값 | 이유 |
|------|--------|------|
| 렉 스파이크 캡처 | ON | 읽기 전용 |
| 플러그인 비용 측정 | ON | 읽기 전용 |
| 메모리 가드 | ON | 경고만, 조치 보수적 |
| StallWatchdog | ON | 덤프 파일만 생성 |
| 충돌 분석 | ON | 읽기 전용 |
| 엔티티 최적화 | OFF | AI 동작 변경 |
| 엔티티 밀도 가드 | OFF | 스폰 억제 |
| 네트워크 전체 | OFF | Redis 필요 |
| Global Vault | OFF | 아이템 복제 위험 |
| Remote Command | OFF | 임의 명령 실행 위험 |
| Plugin Deploy | OFF | 서버 변조 위험 |
