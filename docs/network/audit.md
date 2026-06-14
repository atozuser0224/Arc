---
title: 네트워크 감사 로그
parent: Network Layer
nav_order: 10
---

# 네트워크 감사 로그

Arc Network에서 발생하는 모든 관리 작업(전송, 방송, 커맨드, 밴, 배포 등)을 자동으로 기록한다. 메모리와 Redis 두 곳에 저장되며, 기본 90일 보존된다.

## 활성화

```yaml
# arc-network.yml
network:
  audit:
    enabled: true
    memory-size: 500          # 메모리 내 최근 로그 항목 수
    redis-ttl: 7776000        # Redis 보존 기간 (초, 기본 90일 = 7,776,000초)
    sensitive-data-masking: true   # IP 주소 마스킹 (기본 ON)
    log-to-file: false        # 파일 로그 출력 여부
    file-path: "logs/arc-audit.log"
```

---

## 자동 기록 항목

| 액션 | 기록 조건 |
|------|----------|
| `TRANSFER` | 플레이어 서버 간 이동 |
| `TRANSFER_ALL` | 서버 전체 플레이어 이동 |
| `EVACUATE` | 서버 비우기 실행 |
| `BROADCAST` | 전체/그룹 방송 전송 |
| `REMOTE_CMD` | 원격 커맨드 실행 |
| `BAN` | 플레이어 밴 |
| `UNBAN` | 플레이어 밴 해제 |
| `MUTE` | 플레이어 뮤트 |
| `UNMUTE` | 플레이어 뮤트 해제 |
| `IP_BAN` | IP 밴 |
| `IP_UNBAN` | IP 밴 해제 |
| `DEPLOY` | 플러그인 원격 배포 |
| `VAULT_OPEN` | Vault 열기 |
| `VAULT_CLOSE` | Vault 닫기 (저장 포함) |
| `ECONOMY_SET` | 잔액 직접 설정 (관리자) |
| `QUEUE_BYPASS` | 대기열 우선순위 수동 변경 |
| `CONFIG_CHANGE` | 런타임 설정 변경 |
| `SERVER_START` | 서버 온라인 감지 |
| `SERVER_STOP` | 서버 오프라인 감지 |

---

## Redis 저장 구조

각 감사 로그 항목은 ZSet으로 저장된다. score는 Unix timestamp(ms)다.

```
arc:audit:log  (ZSet)
  member: "{\"id\":\"a1b2c3\",\"action\":\"BAN\",\"actor\":\"AdminPlayer\",...}"
  score:  1718400000000

arc:audit:log:player:<uuid>  (ZSet)  ← 플레이어별 인덱스
arc:audit:log:action:<action>  (ZSet) ← 액션별 인덱스
```

각 로그 항목의 JSON 구조:

```json
{
  "id": "a1b2c3d4",
  "timestamp": 1718400000000,
  "action": "BAN",
  "actor": "AdminPlayer",
  "actor-server": "lobby-1",
  "target": "PlayerName",
  "target-uuid": "550e8400-e29b-41d4-a716-446655440000",
  "details": {
    "reason": "핵 프로그램 사용",
    "duration": -1,
    "ip": "192.168.***.***"
  },
  "source-ip": "10.0.0.***"
}
```

`sensitive-data-masking: true`(기본값)이면 IP 주소의 마지막 두 옥텟이 `***`으로 마스킹된다.

---

## 명령어

### 전체 최근 로그 조회

```
/arc network audit [page]
```

출력 예시:

```
=== 네트워크 감사 로그 (최근 10개) ===
[14:30:05] BAN | AdminPlayer → PlayerName | 핵 프로그램 사용
[14:28:12] TRANSFER | AdminPlayer → Steve | lobby-1 → game-1
[14:25:44] BROADCAST | AdminPlayer | "서버 점검 예정"
[14:20:33] DEPLOY | AdminPlayer → all | MyPlugin-2.0.jar
[14:15:11] REMOTE_CMD | AdminPlayer → game-1 | kick BadPlayer
...
[1/5페이지] /arc network audit 2
```

### 플레이어별 로그 조회

```
/arc network audit player <name-or-uuid> [page]
```

출력 예시:

```
=== PlayerName 감사 로그 ===
[14:30:05] BAN | 관리자: AdminPlayer | 이유: 핵 프로그램 사용
[13:45:22] TRANSFER | 관리자: AdminPlayer | lobby-1 → game-1
[12:10:08] MUTE | 관리자: AdminPlayer | 이유: 욕설 (1시간)
```

### 액션별 로그 조회

```
/arc network audit action <action> [page]
```

예시:

```
/arc network audit action BAN          # 밴 이력만
/arc network audit action DEPLOY       # 배포 이력만
/arc network audit action REMOTE_CMD   # 원격 커맨드 이력만
```

### 기간 검색

```
/arc network audit since <시간>
```

예시:

```
/arc network audit since 1h    # 최근 1시간
/arc network audit since 24h   # 최근 24시간
/arc network audit since 7d    # 최근 7일
```

---

## API: ArcAuditLog

플러그인에서 직접 감사 로그를 기록하거나 조회할 수 있다.

```java
import dev.arc.network.audit.ArcAuditLog;
import dev.arc.network.audit.AuditEntry;
import java.util.List;

ArcAuditLog auditLog = ArcAuditLog.getInstance();
```

### 커스텀 이벤트 기록

```java
// 커스텀 감사 항목 기록
AuditEntry entry = AuditEntry.builder()
    .action("CUSTOM_ACTION")
    .actor(player.getName())
    .actorServer("lobby-1")
    .target(targetPlayer.getName())
    .targetUuid(targetPlayer.getUniqueId())
    .detail("reason", "커스텀 이유")
    .detail("value", "100")
    .build();

auditLog.log(entry);
```

### 플레이어 이력 조회

```java
// 특정 플레이어의 최근 20개 로그
List<AuditEntry> history = auditLog.getPlayerHistory(
    targetPlayer.getUniqueId(),
    20  // 개수
);

for (AuditEntry e : history) {
    sender.sendMessage(String.format(
        "[%s] %s | %s → %s",
        new java.util.Date(e.getTimestamp()),
        e.getAction(),
        e.getActor(),
        e.getTarget()
    ));
}
```

### 기간별 통계

```java
// 지난 24시간 동안의 밴 횟수
long banCount = auditLog.countAction(
    "BAN",
    System.currentTimeMillis() - 86_400_000L,  // 시작 시각 (24시간 전)
    System.currentTimeMillis()                  // 종료 시각
);

sender.sendMessage("지난 24시간 밴 횟수: " + banCount + "건");
```

### 메모리 캐시 조회

Redis를 거치지 않고 메모리에서 최근 로그를 빠르게 조회할 수 있다.

```java
// 메모리 캐시에서 최근 50개 조회 (Redis 조회보다 빠름)
List<AuditEntry> recent = auditLog.getRecent(50);
```

---

## 저장 용량 계산

| 설정 | 하루 로그 수 | 90일 항목 수 | 예상 Redis 용량 |
|------|------------|-------------|----------------|
| 소규모 (10명/일) | ~50건 | 4,500건 | ~4.5MB |
| 중규모 (100명/일) | ~500건 | 45,000건 | ~45MB |
| 대규모 (1,000명/일) | ~5,000건 | 450,000건 | ~450MB |

각 로그 항목의 평균 크기는 약 1KB다. 대규모 서버에서 장기 보존이 필요하면 `redis-ttl`을 줄이거나 외부 로그 시스템(Elasticsearch, ClickHouse)으로 내보내는 것을 검토하라.

---

## 장단점

### 장점

- **자동 추적**: 별도 코드 없이 모든 관리 작업이 자동으로 기록된다.
- **메모리 + Redis 이중 저장**: 메모리에서 빠른 조회, Redis에서 영속 저장.
- **플레이어·액션별 인덱스**: 특정 플레이어나 액션만 빠르게 필터링 가능.
- **IP 마스킹**: 기본값으로 개인정보 보호가 적용된다.

### 단점

- **Redis 저장 용량 소비**: 로그가 많을수록 Redis 메모리를 소비한다. 기본 90일 보존은 대부분의 서버에서 감당 가능한 수준이지만, 장기 보관이 필요한 경우 별도 아카이빙 전략이 필요하다.
- **실시간 스트리밍 없음**: 로그를 실시간으로 모니터링하는 기능이 없다. 5초 폴링이나 웹소켓 기반 대시보드가 필요하면 별도 구현이 필요하다.
- **검색 기능 제한**: 현재 플레이어·액션·기간별 필터만 지원한다. 복잡한 쿼리(이유 텍스트 검색, 다중 조건 필터)는 지원하지 않는다.

---

## 로그 내보내기

감사 로그를 외부 시스템으로 내보내는 예시:

```java
// 모든 로그를 JSON 파일로 내보내기
public void exportLogs(Path outputFile) throws IOException {
    ArcAuditLog auditLog = ArcAuditLog.getInstance();
    List<AuditEntry> all = auditLog.getAll(); // 전체 로그 조회

    try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
        for (AuditEntry entry : all) {
            writer.write(entry.toJson());
            writer.newLine();
        }
    }

    Bukkit.getLogger().info("감사 로그 내보내기 완료: " + outputFile);
}
```

또는 명령어로 내보내기:

```
/arc network audit export <파일경로>
/arc network audit export /home/minecraft/audit-2026-06.jsonl
```

---

## 보존 기간 변경

```yaml
# 30일로 줄이기 (용량 절약)
audit:
  redis-ttl: 2592000   # 30일

# 365일로 늘리기 (장기 보관)
audit:
  redis-ttl: 31536000  # 365일
```

{: .note }
`redis-ttl` 변경은 새로 기록되는 로그에만 적용된다. 이미 저장된 로그의 TTL은 변경되지 않는다. 기존 로그의 TTL을 일괄 갱신하려면 Redis에서 직접 처리해야 한다.
