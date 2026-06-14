---
title: Network Layer
nav_order: 4
---

# Layer 4 — Network

멀티서버 네트워크 레이어. **Redis 없이는 비활성화**된다.  
`arc-network.yml`에서 `enabled: false`가 기본값.

---

## 아키텍처

```
[Server A] ──LPUSH──▶ arc:inbox:broadcast:B ──▶ [Server B] → Bukkit.broadcastMessage()
[Server A] ──LPUSH──▶ arc:inbox:cmd:B       ──▶ [Server B] → dispatchCommand()
[Server A] ──SETEX──▶ arc:server:A          ◀── heartbeat every 2s
[Server A] ──SETEX──▶ arc:online:player:<name> → global player lookup O(1)
[Server A] ──ZADD ──▶ arc:queue:B           ←── ArcQueueDrainer polls & transfers
```

**메시지 전달**: JedisPubSub 대신 LPUSH/RPOP 인박스 패턴 사용. 리플렉션으로 Jedis를 로드하는 구조상 추상 클래스 서브클래싱이 불가하기 때문.  
**자동 재연결**: `ArcNetworkInbox`가 30초 쿨다운으로 Redis 연결 끊김을 감지·복구한다.

---

## 서버 등록

매 2초 heartbeat:
- `arc:server:<id>` — 서버 정보 JSON (TTL 10s)
- `arc:servers:online` — Set
- `arc:online:player:<name>` — 플레이어→서버 매핑 (TTL 30s)
- `arc:server:<id>:history` — 상태 변경 ZSet

---

## 네트워크 명령어 `/arc network *`

| 커맨드 | 설명 |
|--------|------|
| `servers` | 전체 서버 대시보드 (상태·플레이어·TPS·MSPT) |
| `server <id>` | 특정 서버 상세 정보 |
| `players` | 네트워크 전체 플레이어 목록 |
| `send <player> <server>` | 플레이어 서버 이동 |
| `sendall <server>` | 전체 플레이어 이동 |
| `hub` | 허브 서버로 이동 |
| `find <player>` | 플레이어가 있는 서버 O(1) 조회 |
| `evacuate <server>` | 서버 대피 (4틱 간격 스태거, 메인 스레드 차단 없음) |
| `broadcast <message>` | 네트워크 전체 방송 |
| `queue join <server>` | 대기열 진입 |
| `queue leave` | 대기열 이탈 |
| `queue status` | 현재 대기 순위 |
| `queue pause\|resume <server>` | 대기열 일시정지 |
| `maintenance server\|group\|network on\|off` | 유지보수 모드 |
| `maintenance status` | 유지보수 상태 조회 |
| `remote-cmd <server> <cmd...>` | 원격 서버에서 커맨드 실행 (DEFAULT OFF) |
| `audit` | 네트워크 감사 로그 최근 20개 (Redis 영속) |
| `cooldown check\|clear <player>` | 글로벌 쿨다운 |

---

## 큐 시스템

플레이어가 대상 서버에 빈 자리가 생길 때 자동으로 전송된다.

**우선순위**: 낮은 score = 높은 우선순위 (ZSet).
- 일반: `currentTimeMillis()` (FIFO)
- `arc.queue.vip`: -60,000 (1분 앞당김)
- `arc.queue.priority`: -300,000 (5분 앞당김)

**드레이너**: `transferCheckIntervalSeconds`(기본 5초)마다 ONLINE 서버의 빈 슬롯을 계산해 플레이어를 자동 전송.  
**위치 안내**: `positionMessageInterval`(기본 30초)마다 대기 플레이어에게 현재 순위 메시지.  
**연결 끊김 보존**: `keepOnDisconnectSeconds`(기본 300초) 동안 대기 위치가 유지된다. 재접속 시 순위 안내.

---

## 원격 커맨드

`arc-network.yml`에서 `remote-command.enabled: true` 설정 필요.

```yaml
arc-network:
  remote-command:
    enabled: true
    require-confirm: true
    rate-limit: 3          # 분당 최대 실행 수 per requester
    allowlist:
      - say
      - save-all
    blocklist:
      - op
      - deop
      - stop
      - reload
```

발신: `/arc network remote-cmd <target-server> say Hello from lobby!`  
수신: `ArcNetworkInbox`가 `arc:inbox:cmd:<serverId>` 폴링 → allowlist 검증 → `dispatchCommand()`.

---

## 감사 로그

모든 네트워크 작업(전송·방송·커맨드·대피 등)이 자동 기록된다.

- **인메모리**: 최근 500개 (현재 세션)
- **Redis**: `arc:audit:net` ZSet, timestamp score → 재시작 후에도 조회 가능
- **보존 기간**: `audit.retention-days` (기본 90일), 서버 시작 시 자동 정리

```
/arc network audit          → 최근 20개 조회
/arc network audit player <name>
/arc network audit action <action>
```

---

## 위험도별 기본값

| 기능 | 기본값 | 이유 |
|------|--------|------|
| 서버 등록·전송·대기열 | ON | 역방향 복구 가능 |
| Global Vault | OFF | 아이템 복제 위험 |
| Inventory Transfer | OFF | 데이터 손실 위험 |
| Remote Command | OFF | 임의 명령 실행 위험 |
| Plugin Deploy | OFF | 서버 자체 변조 위험 |

---

## 설정 파일 (`arc-network.yml`)

```yaml
arc-network:
  enabled: false
  server:
    id: server-1
    group: default
    tags: []

  relay:
    redis:
      host: 127.0.0.1
      port: 6379
      password: ""
      pool-size: 8

  heartbeat:
    interval-seconds: 2
    ttl-seconds: 10

  queue:
    enabled: true
    transfer-check-interval-seconds: 5
    position-message-interval: 30
    keep-on-disconnect-seconds: 300
    vip-permission: arc.queue.vip
    priority-permission: arc.queue.priority

  audit:
    enabled: true
    retention-days: 90
    sensitive-data-masking: true
```
