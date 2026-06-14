---
title: 설정 레퍼런스
nav_order: 6
---

# 설정 레퍼런스

Arc는 서버 시작 시 `plugins/Arc/` 디렉토리에 두 개의 설정 파일을 자동으로 생성한다. `arc-ops.yml`은 운영 도구와 성능 기능을 제어하고, `arc-network.yml`은 멀티서버 네트워크 레이어를 제어한다. 대부분의 설정은 `/arc config set <경로> <값>`으로 서버 재시작 없이 변경할 수 있다.

---

## arc-ops.yml

### lag-spike-capture

렉 스파이크를 자동으로 감지하고 그 시점의 상태를 파일로 저장하는 기능이다.

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `enabled` | `true` | 렉 스파이크 캡처 활성화 여부. 읽기 전용이라 서버 상태에 영향을 주지 않는다 |
| `mspt-threshold` | `100.0` | 이 밀리초를 초과하면 렉 스파이크로 판정. 낮출수록 민감해진다 |
| `capture-duration-ticks` | `40` | 스파이크 전후 몇 틱을 함께 기록할지. 40틱 = 약 2초 |
| `save-report` | `true` | 캡처 결과를 파일로 저장. false면 메모리에만 유지된다 |
| `max-reports` | `50` | 보관할 최대 보고서 수. 초과 시 오래된 것부터 삭제 |

권장: 프로덕션 서버에서는 기본값 그대로 두는 것이 좋다. 보고서가 너무 많이 쌓이면 `max-reports`를 줄이되, 비활성화는 권장하지 않는다.

---

### plugin-cost

플러그인별로 메인 스레드를 얼마나 점유하는지 측정한다.

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `enabled` | `true` | 플러그인 비용 측정 활성화. 측정 오버헤드는 무시할 수준이다 |

`/arc plugin-cost top`으로 상위 플러그인 목록을, `/arc plugin-cost <이름>`으로 특정 플러그인의 이벤트·틱별 세부 비용을 확인할 수 있다.

---

### entity-optimization

거리 기반으로 원거리 몹의 AI 틱 빈도를 줄인다. 기본 OFF.

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `enabled` | `false` | 엔티티 최적화 활성화. AI 동작이 달라지므로 테스트 후 활성화 권장 |
| `distance-based-ai.enabled` | `false` | 거리별 AI 틱 조절 활성화 |
| `distance-based-ai.near-range` | `32.0` | 이 블록 반경 안 몹은 매 틱 AI를 실행한다 |
| `distance-based-ai.far-range` | `64.0` | near-range 바깥, far-range 안 몹은 아래 간격으로 AI를 실행한다 |
| `distance-based-ai.far-ai-interval` | `5` | 원거리 몹의 AI 실행 간격 (틱). 5 = 매 5틱마다 |
| `distance-based-ai.check-interval-ticks` | `20` | 거리를 재계산하는 주기 |
| `worlds` | `[]` | 적용할 월드 목록. 빈 목록이면 모든 월드에 적용 |

주의: PvP 서버나 몹 AI가 중요한 게임 서버에서는 원거리 AI 감소가 의도치 않은 몹 행동 변화를 유발할 수 있다. 테스트 서버에서 충분히 검증한 뒤 활성화한다.

---

### entity-density-guard

특정 청크에 몹이 과밀하면 추가 스폰을 억제한다. 기본 OFF.

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `enabled` | `false` | 밀도 가드 활성화 |
| `max-mobs-per-chunk` | `24` | 청크당 허용 최대 몹 수. 이 값을 초과하면 추가 스폰이 억제된다 |
| `activation-tps` | `18.0` | TPS가 이 값 이하일 때만 스폰 억제가 작동한다. TPS 정상 시 간섭 없음 |
| `check-interval-ticks` | `100` | 밀도 확인 주기 (틱). 5초마다 확인 |
| `worlds` | `[]` | 적용할 월드 목록. 빈 목록이면 모든 월드 |

TPS 조건부 작동이 핵심이다. 서버가 정상일 때는 자연 스폰에 전혀 간섭하지 않다가, 렉이 시작될 때만 과밀 청크의 추가 스폰을 억제한다.

---

### memory-guard

JVM 힙 사용률을 감시하고 임계값 도달 시 경고 및 조치를 취한다.

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `enabled` | `true` | 메모리 가드 활성화 |
| `warn-fraction` | `0.85` | 힙의 85% 이상 사용 시 로그에 경고를 남긴다 |
| `critical-fraction` | `0.95` | 힙의 95% 이상 사용 시 추가 조치를 취한다 |
| `check-interval-ticks` | `100` | 힙 사용률 확인 주기 (틱) |
| `action-cooldown-seconds` | `30` | 연속 조치 사이 최소 간격. 잦은 GC 호출 방지 |
| `allow-forced-gc` | `false` | true로 설정하면 critical 도달 시 System.gc()를 호출한다. 기본 비활성화 권장 |

강제 GC는 Stop-the-World 시간을 늘릴 수 있어 기본적으로 비활성화다. 힙 설정 자체를 조정하거나 메모리 누수를 찾아 해결하는 것이 근본 해결책이다.

---

### stall-watchdog

메인 스레드가 멈췄을 때 스택 트레이스를 자동으로 덤프한다. 항상 활성이며 비활성화할 수 없다.

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `enabled` | `true` | 상시 활성. 이 설정을 false로 바꿔도 무시된다 |
| `threshold-ms` | `5000` | 메인 스레드가 이 시간(ms) 동안 응답 없으면 덤프 생성. 5000 = 5초 |
| `full-thread-dump` | `false` | true로 설정하면 메인 스레드 외에 JVM 전체 스레드 상태를 덤프한다. 원인 분석에 더 많은 정보를 제공하지만 파일이 크다 |

서버가 영구적으로 응답 없이 멈췄을 때 덤프 파일을 분석하면 어느 플러그인의 어느 코드에서 멈췄는지 즉시 파악할 수 있다. 원인 불명의 서버 행 현상 사후 분석에 필수적이다.

---

### status

Prometheus 호환 메트릭 HTTP 엔드포인트. Grafana 등 모니터링 시스템과 연동할 때 사용한다.

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `enabled` | `false` | 메트릭 엔드포인트 활성화 |
| `bind-address` | `127.0.0.1` | 바인딩할 IP 주소. 외부에 노출하려면 `0.0.0.0`으로 변경 |
| `port` | `9595` | HTTP 포트 |
| `snapshot-interval-ticks` | `20` | 메트릭 스냅샷 수집 주기 (틱). 20 = 1초 |

제공 메트릭: `arc_tps`, `arc_tps_5m`, `arc_mspt`, `arc_players_online`, `arc_players_max`, `arc_memory_used_bytes`, `arc_memory_max_bytes`, `arc_memory_used_fraction`

`/metrics` 엔드포인트가 Prometheus 텍스트 형식으로 응답하고, `/health`는 단순 상태 확인용이다.

---

### crash

시작 시 이전 크래시 보고서를 자동으로 분석한다.

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `analyze-on-boot` | `true` | 서버 시작 시 crash-reports/ 디렉토리를 스캔하고 `/arc doctor` 출력에 포함한다 |

---

## arc-network.yml

### enabled 및 서버 식별

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `enabled` | `false` | 네트워크 레이어 전체 활성화. Redis 설정 완료 후 true로 변경 |
| `server.id` | `server-1` | 이 서버의 고유 ID. 네트워크 내 다른 서버와 겹치면 안 된다 |
| `server.group` | `default` | 서버 그룹. `lobby`, `game`, `minigame` 등 운영 구조에 맞게 설정 |
| `server.tags` | `[]` | 추가 태그 목록. 유지보수 모드 적용 범위 지정 등에 활용 |

`server.id`는 변경 시 모든 관련 Redis 키가 새 ID로 생성되므로, 서버를 처음 시작하기 전에 결정하고 이후 바꾸지 않는 것을 권장한다.

---

### relay (Redis 연결)

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `relay.redis.host` | `127.0.0.1` | Redis 서버 호스트 |
| `relay.redis.port` | `6379` | Redis 포트 |
| `relay.redis.password` | `""` | Redis 인증 비밀번호. 없으면 빈 문자열 |
| `relay.redis.pool-size` | `8` | JedisPool 최대 커넥션 수. 서버가 많으면 늘린다 |

Redis는 같은 네트워크 안의 모든 서버가 접근할 수 있어야 한다. 프로덕션에서는 반드시 `requirepass`를 설정해 인증을 활성화한다.

---

### heartbeat

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `heartbeat.interval-seconds` | `2` | Redis에 서버 상태를 쓰는 주기 (초) |
| `heartbeat.ttl-seconds` | `10` | Redis 키 만료 시간. interval의 5배 이상으로 유지 권장 |

TTL이 interval보다 너무 짧으면 heartbeat 사이에 키가 만료되어 서버가 오프라인으로 오인될 수 있다. 기본값(2초 / 10초)은 5배 여유가 있어 네트워크 순단 상황에서도 안정적이다.

---

### queue (대기열)

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `queue.enabled` | `true` | 대기열 시스템 활성화 |
| `queue.transfer-check-interval-seconds` | `5` | 드레이너가 빈 슬롯을 확인하고 대기 플레이어를 전송하는 주기 |
| `queue.position-message-interval` | `30` | 대기 플레이어에게 현재 순위를 알리는 주기 (초) |
| `queue.keep-on-disconnect-seconds` | `300` | 연결 끊김 후 대기 위치를 보존하는 시간 (초). 5분 |
| `queue.vip-permission` | `arc.queue.vip` | 이 권한 보유자는 대기 시각을 60초 앞당긴다 |
| `queue.priority-permission` | `arc.queue.priority` | 이 권한 보유자는 대기 시각을 300초 앞당긴다 |

`transfer-check-interval-seconds`를 줄이면 대기 플레이어가 빠르게 들어오지만 Redis 조회 빈도가 높아진다. 기본값 5초가 대부분의 서버에서 충분하다.

---

### audit (감사 로그)

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `audit.enabled` | `true` | 감사 로그 활성화. 비활성화하면 Redis에 기록되지 않는다 |
| `audit.retention-days` | `90` | Redis 감사 로그 보존 기간. 이 기간이 지난 항목은 자동 삭제 |
| `audit.sensitive-data-masking` | `true` | IP 주소 등 개인정보를 마스킹해서 저장 |

감사 로그는 서버 재시작 후에도 Redis에 남아 있어 이전 관리자 작업을 소급 조회할 수 있다. 법적 요구사항이나 사내 정책에 따라 보존 기간을 조정한다.

---

### remote-command (원격 커맨드)

기본 OFF. 활성화 후 허용/차단 목록을 반드시 설정한다.

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `remote-command.enabled` | `false` | 원격 커맨드 수신·발신 모두 제어하는 마스터 스위치 |
| `remote-command.require-confirm` | `true` | 실행 전 confirm 토큰 입력 요구. 실수 방지 |
| `remote-command.rate-limit` | `3` | 발신자 기준 분당 최대 실행 횟수 |
| `remote-command.allowlist` | `[say, save-all]` | 허용된 커맨드 목록. 이 목록에 없는 커맨드는 실행되지 않는다 |
| `remote-command.blocklist` | `[op, deop, stop, reload]` | 차단할 커맨드 목록. allowlist에 있어도 blocklist에 있으면 차단 |

allowlist와 blocklist를 모두 비워두면 모든 커맨드가 허용된다. 보안상 allowlist에 꼭 필요한 커맨드만 명시적으로 열거하는 방식을 권장한다.

---

### inventory-transfer (인벤토리 전송)

서버 이동 시 인벤토리를 자동으로 저장하고 복원한다.

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `inventory-transfer.enabled` | `false` | 인벤토리 전송 활성화 |

활성화 전 두 가지를 확인한다. 두 서버의 Minecraft 버전이 동일해야 하고, 아이템 클래스에 영향을 주는 플러그인 구성이 서버 간에 크게 다르지 않아야 역직렬화가 올바르게 동작한다.

---

### plugin-deploy (플러그인 원격 배포)

기본 OFF. 서버 코드에 직접 영향을 주므로 신중하게 활성화한다.

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `plugin-deploy.enabled` | `false` | 원격 JAR 수신 및 자동 설치 활성화 |

배포 전 PluginSandboxCheck를 통과하지 못한 DANGEROUS 등급 JAR는 자동 설치가 차단된다. CAUTION 등급은 분석 결과와 함께 confirm 토큰 입력을 요구한다.

---

## 런타임 설정 변경

파일을 직접 편집하지 않고 서버가 실행 중인 상태에서 설정을 바꿀 수 있다.

| 커맨드 | 설명 |
|--------|------|
| `/arc config get <경로>` | 현재 설정값 조회 |
| `/arc config set <경로> <값>` | 실행 중 설정 변경. 변경자·시각·이전값이 이력에 기록된다 |
| `/arc config reset <경로>` | 해당 설정을 기본값으로 복원 |
| `/arc config diff` | 현재 설정과 기본값의 차이 출력 |
| `/arc config history` | 누가 언제 무엇을 바꿨는지 변경 이력 조회 |

경로 예시: `lag-spike-capture.mspt-threshold`, `entity-optimization.enabled`, `memory-guard.warn-fraction`

네트워크 레이어 런타임 설정 중 Redis 연결 정보, 서버 ID, 포트 번호 등은 재시작이 필요하다. 그 외의 값(대기열 주기, 감사 설정 등)은 `/arc config set`으로 즉시 반영된다.

---

## 시나리오별 권장 설정

### 소규모 싱글 서버 (30인 이하)

arc-ops.yml 기본값을 그대로 사용한다. 엔티티 최적화와 밀도 가드는 OFF로 둔다. Prometheus 엔드포인트도 별도 모니터링 인프라가 없다면 OFF로 충분하다.

### 중규모 서버 (30~100인)

렉 스파이크 임계값을 `50.0ms`로 낮춰 민감하게 감지한다. 엔티티 밀도 가드를 활성화하되 `activation-tps`를 `17.0`으로 낮춰 정상 시에는 간섭이 없도록 한다. Prometheus 엔드포인트를 열고 Grafana로 TPS/MSPT 추세를 모니터링한다.

### 대규모 멀티서버 네트워크 (100인 이상)

Redis 커넥션 풀을 `pool-size: 16`으로 늘린다. 대기열 드레이너를 `transfer-check-interval-seconds: 3`으로 줄여 빠른 입장을 제공한다. 감사 로그는 반드시 활성화하고 보존 기간을 규정에 맞게 설정한다. Remote Command와 Plugin Deploy는 CI/CD 파이프라인이 준비된 경우에만 활성화한다.
