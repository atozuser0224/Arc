---
title: 아키텍처 · 초기 설정
parent: Network Layer
nav_order: 1
---

# 아키텍처 · 초기 설정

## 아키텍처 개요

Arc Network는 Redis를 유일한 미들웨어로 사용하는 단순한 구조다. 별도의 프록시 서버(BungeeCord, Velocity)가 없어도 동작하며, 각 Minecraft 서버가 Redis에 직접 연결해 상태를 공유하고 메시지를 교환한다.

### 인박스 패턴 (LPUSH / RPOP)

전통적인 Redis Pub/Sub(`JedisPubSub`) 대신 리스트 기반 인박스 패턴을 사용한다.

```
[Server A] ──LPUSH──▶ arc:inbox:B ──RPOP──▶ [Server B]
[Server A] ──LPUSH──▶ arc:inbox:broadcast:all ──RPOP──▶ [Server B, C, D ...]
```

**왜 Pub/Sub 대신 인박스인가?**

| 구분 | JedisPubSub | LPUSH/RPOP 인박스 |
|------|-------------|-------------------|
| 수신 서버 재시작 중 메시지 | **유실** | Redis에 보존 |
| 연결 수 | 1개 구독 연결 필요 | 일반 커넥션 풀 재사용 |
| 메시지 내구성 | 구독 중에만 수신 | 서버가 죽어도 큐에 잔류 |
| 구현 복잡도 | 낮음 | 낮음 (BRPOP 또는 폴링) |

Arc는 200ms 폴링으로 각 서버의 인박스를 소비한다. 대용량 트래픽 환경에서는 폴링 주기를 줄이거나 BRPOP(블로킹 팝)으로 전환을 검토할 수 있다.

### 전체 아키텍처 다이어그램

```
┌─────────────────────────────────────────────────────────────────┐
│                          Redis                                  │
│                                                                 │
│  arc:server:lobby-1  (Hash, TTL 10s) ◀── SETEX every 2s        │
│  arc:server:game-1   (Hash, TTL 10s) ◀── SETEX every 2s        │
│  arc:servers:online  (Set)                                      │
│                                                                 │
│  arc:inbox:game-1    (List) ◀── LPUSH ── lobby-1               │
│  arc:inbox:lobby-1   (List) ◀── LPUSH ── game-1                │
│                                                                 │
│  arc:online:player:Steve  (String, TTL 30s) = "game-1"         │
│  arc:online:player:Alex   (String, TTL 30s) = "lobby-1"        │
│                                                                 │
│  arc:economy:balance:uuid-xxx  (String) = "1500"               │
│  arc:ban:uuid-xxx  (Hash) = {reason, actor, expires}           │
└─────────────────────────────────────────────────────────────────┘
         ▲ RPOP poll (200ms)          ▲ RPOP poll (200ms)
         │ SETEX (2s heartbeat)       │ SETEX (2s heartbeat)
┌────────┴──────────┐        ┌────────┴──────────┐
│   lobby-1 서버    │        │   game-1 서버     │
│  (Minecraft 1.21) │        │  (Minecraft 1.21) │
└───────────────────┘        └───────────────────┘
```

### 서버 등록 및 하트비트

각 서버는 시작 시 Redis에 자신을 등록하고, 2초마다 상태를 갱신한다. TTL은 10초로 설정되어 있어, 서버가 비정상 종료(kill -9, 전원 차단 등)된 경우 최대 10초 이내에 오프라인으로 판정된다.

```
[Server 시작]
  └─▶ HSET arc:server:lobby-1 id lobby-1 group survival ...
  └─▶ EXPIRE arc:server:lobby-1 10
  └─▶ SADD arc:servers:online lobby-1

[2초마다 반복]
  └─▶ HSET arc:server:lobby-1 players 42 tps 19.8 mspt 12.3
  └─▶ EXPIRE arc:server:lobby-1 10   ← TTL 갱신

[정상 종료]
  └─▶ DEL arc:server:lobby-1
  └─▶ SREM arc:servers:online lobby-1

[비정상 종료]
  └─▶ TTL 10초 후 arc:server:lobby-1 자동 만료
  └─▶ 다른 서버의 상태 폴러가 SREM arc:servers:online lobby-1 처리
```

### 자동 재연결

Redis 연결이 끊기면 30초 쿨다운 후 자동으로 재연결을 시도한다. 재연결 성공 시 인박스에 남아 있던 메시지가 자동으로 처리되므로 운영자 개입 없이 회복된다.

```
Redis 연결 끊김
  └─▶ 에러 로그: "[Arc-Network] Redis 연결 끊김, 30초 후 재시도"
  └─▶ 30초 대기
  └─▶ 재연결 시도
      ├─ 성공: 인박스 폴링 재개, 하트비트 재개
      └─ 실패: 다시 30초 대기 후 재시도 (무한 반복)
```

---

## Redis 키 구조

### 서버 상태

| 키 패턴 | 타입 | TTL | 설명 |
|---------|------|-----|------|
| `arc:server:<id>` | Hash | 10s | 서버 전체 상태 |
| `arc:servers:online` | Set | 없음 | 현재 온라인 서버 ID 목록 |
| `arc:server:<id>:history` | ZSet | 없음 | 상태 변경 이력 (score = timestamp) |

`arc:server:<id>` Hash 필드:

```
id        → "lobby-1"
group     → "survival"
tags      → "hub,lobby"
players   → "42"
max       → "100"
tps       → "19.8"
mspt      → "12.3"
version   → "1.21.1"
started   → "1718400000000"
```

### 플레이어 위치

| 키 패턴 | 타입 | TTL | 설명 |
|---------|------|-----|------|
| `arc:online:player:<name>` | String | 30s | 플레이어가 접속 중인 서버 ID |

TTL 30초: 플레이어가 서버 간 이동 중(연결 끊김~새 서버 접속)에도 30초간 "온라인"으로 유지된다.

### 메시징

| 키 패턴 | 타입 | TTL | 설명 |
|---------|------|-----|------|
| `arc:inbox:<server-id>` | List | 없음 | 특정 서버의 메시지 큐 |
| `arc:inbox:broadcast:all` | List | 없음 | 전체 방송 큐 |
| `arc:inbox:broadcast:<group>` | List | 없음 | 그룹 방송 큐 |

### 기타

| 키 패턴 | 타입 | 설명 |
|---------|------|------|
| `arc:economy:balance:<uuid>` | String | 글로벌 경제 잔액 |
| `arc:ban:<uuid>` | Hash | 밴 정보 |
| `arc:mute:<uuid>` | Hash | 뮤트 정보 |
| `arc:vault:lock:<uuid>` | String | Vault 분산 잠금 |
| `arc:queue:<server-id>` | ZSet | 입장 대기열 |
| `arc:lb:<name>` | ZSet | 리더보드 |

---

## 초기 설정

### arc-network.yml 전체 설정

```yaml
network:
  enabled: false            # 네트워크 레이어 활성화 여부 (기본: false)

  server:
    id: "server-1"          # 네트워크 내 고유 ID (변경 시 기존 키 고아 발생 주의)
    group: "default"        # 서버 그룹 (방송 그룹 필터링에 사용)
    tags:
      - "survival"          # 태그 목록 (플레이어 전송 조건 필터)
      - "hub"

  redis:
    host: "127.0.0.1"
    port: 6379
    password: ""            # 비밀번호 없으면 빈 문자열
    database: 0
    pool-size: 8            # 커넥션 풀 크기 (서버 8대 이하: 8, 초과: 16 권장)
    timeout: 3000           # 연결 타임아웃 (ms)
    reconnect-cooldown: 30  # 재연결 쿨다운 (초)

  heartbeat:
    interval: 2             # 하트비트 주기 (초)
    ttl: 10                 # 서버 키 TTL (초, heartbeat interval의 5배 이상 권장)

  player:
    transfer-data-ttl: 300  # 전송 데이터 유지 시간 (초, 기본 5분)
    inventory-transfer:
      enabled: false        # 인벤토리 전송 (기본 OFF, 충분한 테스트 후 활성화)

  economy:
    enabled: true

  ban:
    enabled: true

  vault:
    enabled: false          # Global Vault (기본 OFF, 복제 위험 검토 후 활성화)
    lock-ttl: 300           # Vault 분산 잠금 만료 시간 (초)

  chat:
    enabled: true
    format: "&7[{server}] &f{player}&7: &f{message}"

  tablist:
    enabled: true
    update-interval: 5      # 탭 리스트 갱신 주기 (초)
    header: "&b네트워크 전체: {total}명"
    footer: "&7현재 서버: {server} · {players}/{max}명"

  discord:
    webhook:
      url: ""               # 비어 있으면 비활성화
      events:
        ban: true
        server-down: true
        server-up: true
        broadcast: false

  leaderboard:
    enabled: true

  deploy:
    enabled: false          # 원격 배포 (기본 OFF, CI/CD 준비 후 활성화)
    sandbox-check: true     # DANGEROUS 클래스 차단

  remote-cmd:
    enabled: false          # 원격 커맨드 (기본 OFF, 보안 검토 후 활성화)
    require-confirm: true
    rate-limit: 3           # 분당 최대 실행 횟수
    allowlist: []
    blocklist:
      - "stop"
      - "op"
      - "deop"

  audit:
    enabled: true
    memory-size: 500        # 메모리 내 최근 로그 수
    redis-ttl: 7776000      # Redis 보존 기간 (초, 기본 90일)
    sensitive-data-masking: true  # IP 주소 마스킹
```

### pool-size 권장값

| 서버 대수 | pool-size 권장값 | 이유 |
|-----------|-----------------|------|
| 1~4 | 4 | 각 서버 폴링 + 하트비트 동시 처리 |
| 5~8 | 8 | 방송 큐 처리 병렬화 |
| 9~16 | 16 | 인박스 수 증가에 대응 |
| 17+ | 32 | 각 기능별 독립 커넥션 확보 |

### 기능별 기본값이 OFF인 이유

| 기능 | OFF 이유 |
|------|----------|
| 인벤토리 전송 | 전송 중 연결 끊김 시 아이템 유실 가능. 충분한 QA 필요 |
| Global Vault | 잠금 실패 시 아이템 복제 위험. 잠금 로직 검토 필수 |
| 원격 배포 | 악의적 JAR 배포 시 서버 전체 코드 변조 가능 |
| 원격 커맨드 | 잘못된 명령이 다른 서버에 영향. 철저한 명령 허용 목록 필요 |

{: .warning }
`server.id`는 한번 정하면 변경하지 않는 것을 강력히 권장한다. 변경 시 `arc:server:<old-id>`, `arc:server:<old-id>:history`, `arc:queue:<old-id>` 등 모든 관련 키가 고아(orphan) 상태가 되며 TTL 만료까지 Redis에 잔류한다.

---

## TLS/SSL 설정 (선택)

Redis 6.0+ TLS를 사용하는 경우:

```yaml
redis:
  host: "redis.example.com"
  port: 6380
  tls:
    enabled: true
    truststore: "/opt/arc/ssl/truststore.jks"
    truststore-password: "changeme"
```

{: .note }
TLS 미사용 환경에서는 Redis를 외부 네트워크에 노출하지 말고 VPC/내부망에서만 운영하라. `requirepass`와 `bind 127.0.0.1`을 함께 설정하면 기본 보안을 확보할 수 있다.
