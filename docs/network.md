---
title: Network Layer
nav_order: 5
has_children: true
---

# Network Layer

Redis 하나로 여러 Minecraft 서버를 하나의 논리적 네트워크로 묶는 레이어다. 각 기능 모듈은 독립적으로 활성화/비활성화할 수 있으며, 필요한 기능만 골라 쓸 수 있다.

`arc-network.yml`에서 `network.enabled: false`가 기본값이다. Redis를 설정한 뒤 `enabled: true`로 바꾸고 서버를 재시작하면 활성화된다.

## 핵심 설계 철학

- **Redis 단일 백엔드**: Kafka, RabbitMQ 같은 별도 브로커 없이 Redis 하나로 메시징·상태 공유·잠금·경제·밴 전부 처리
- **인박스 패턴**: JedisPubSub 대신 LPUSH/RPOP 큐를 사용해 수신 서버 재시작 중에도 메시지를 유실 없이 보존
- **자동 복구**: Redis 순단 후 30초 쿨다운으로 자동 재연결, 운영자 개입 불필요
- **기능별 기본값 OFF**: 데이터 손실·보안 위험이 있는 기능은 충분히 검토 후 명시적으로 활성화

## 빠른 시작

```yaml
# arc-network.yml
network:
  enabled: true          # 전체 네트워크 레이어 활성화
  server:
    id: "lobby-1"        # 네트워크 내 고유 식별자
  redis:
    host: "127.0.0.1"
    port: 6379
```

서버를 재시작하면 `arc:server:lobby-1` 키가 Redis에 등록되고, 2초마다 하트비트가 갱신된다.

## 하위 섹션

| 섹션 | 설명 |
|------|------|
| [아키텍처 · 초기 설정](network/setup) | Redis 키 구조, arc-network.yml 전체 설정, 자동 재연결 메커니즘 |
| [플레이어 전송 · 대기열](network/player) | 서버 간 이동 명령어, 우선순위 대기열, 인벤토리 전송 |
| [글로벌 경제](network/economy) | Redis 기반 잔액 공유, Lua 원자적 출금, Vault 연동 |
| [글로벌 밴 · 뮤트](network/ban) | 전 서버 동시 밴/뮤트, IP 밴, Discord Webhook 연동 |
| [Global Vault](network/vault) | 서버 간 공유 아이템 창고, Redis 분산 잠금으로 복제 방지 |
| [크로스서버 채팅 · 탭 리스트](network/chat) | 전 서버 채팅 통합, 탭 리스트 플레이어 수 동기화 |
| [Discord Webhook](network/discord) | 밴·서버 상태·방송 자동 알림, 재시작 없이 URL 변경 |
| [글로벌 리더보드](network/leaderboard) | Redis Sorted Set 기반 다중 리더보드, 경제 자동 동기화 |
| [원격 배포 · 커맨드](network/deploy) | JAR 원격 배포, 서버 간 명령 실행, 보안 샌드박스 |
| [네트워크 감사 로그](network/audit) | 모든 관리 작업 자동 추적, 90일 보존, IP 마스킹 |

## 기능별 활성화 상태 요약

| 기능 | 기본값 | 활성화 조건 |
|------|--------|-------------|
| 서버 등록 / 하트비트 | ON | network.enabled: true 시 자동 |
| 플레이어 전송 | ON | network.enabled: true 시 자동 |
| 우선순위 대기열 | ON | network.enabled: true 시 자동 |
| 인벤토리 전송 | **OFF** | 데이터 손실 테스트 완료 후 활성화 |
| 글로벌 경제 | ON | economy.enabled: true |
| 글로벌 밴 | ON | ban.enabled: true |
| Global Vault | **OFF** | 아이템 복제 위험 검토 후 활성화 |
| 크로스서버 채팅 | ON | chat.enabled: true |
| 탭 리스트 동기화 | ON | tablist.enabled: true |
| Discord Webhook | **OFF** | webhook.url 설정 시 활성화 |
| 글로벌 리더보드 | ON | leaderboard.enabled: true |
| 원격 배포 | **OFF** | 보안 검토 완료 후 활성화 |
| 원격 커맨드 | **OFF** | 보안 검토 완료 후 활성화 |
| 감사 로그 | ON | audit.enabled: true |

## 의존성

- **Redis 6.0 이상** 필수 (Lua 스크립트, SET NX EX 사용)
- Jedis 클라이언트 (Arc에 번들 포함, 별도 설치 불필요)
- 선택: Vault API (글로벌 경제의 Vault 호환 모드)

## 주의사항

{: .warning }
`server.id`를 변경하면 해당 서버의 Redis 키가 전부 새로 생성된다. 기존 `arc:server:<old-id>` 키는 TTL 만료 전까지 남아 있어 혼선이 생길 수 있다. 운영 중 ID 변경은 최대한 피하고, 변경 시 `DEL arc:server:<old-id>`로 수동 정리하라.

{: .note }
Redis 장애 시 밴·경제·Vault 등 Redis 의존 기능이 모두 마비된다. Redis HA(Sentinel 또는 Cluster) 구성을 강력히 권장한다.
