---
title: 플레이어 전송 · 대기열
parent: Network Layer
nav_order: 2
---

# 플레이어 전송 · 대기열

## 플레이어 전송

### 명령어 목록

| 명령어 | 권한 | 설명 |
|--------|------|------|
| `/arc network send <player> <server>` | `arc.network.send` | 특정 플레이어를 지정 서버로 전송 |
| `/arc network sendall <server>` | `arc.network.sendall` | 현재 서버의 모든 플레이어를 전송 |
| `/arc network hub [player]` | `arc.network.hub` | 플레이어(또는 자신)를 허브 서버로 전송 |
| `/arc network find <player>` | `arc.network.find` | 플레이어가 접속 중인 서버 조회 |
| `/arc network evacuate` | `arc.network.evacuate` | 현재 서버 비우기 (유지보수 모드 진입 전) |

### API: ArcPlayerTransfer

```java
import dev.arc.network.player.ArcPlayerTransfer;
import dev.arc.network.player.TransferResult;

// 단일 플레이어 전송
ArcPlayerTransfer transfer = ArcPlayerTransfer.getInstance();
TransferResult result = transfer.send(player, "game-1");

switch (result) {
    case SUCCESS:
        player.sendMessage("§a전송 중...");
        break;
    case SERVER_OFFLINE:
        player.sendMessage("§c해당 서버가 오프라인입니다.");
        break;
    case SERVER_FULL:
        // 대기열 시스템이 자동으로 큐에 추가
        player.sendMessage("§e서버가 만석입니다. 대기열에 추가됐습니다.");
        break;
    case ALREADY_ON_SERVER:
        player.sendMessage("§7이미 해당 서버에 있습니다.");
        break;
}

// 조건 기반 전송 (태그 매칭)
transfer.sendToTag(player, "minigame"); // "minigame" 태그가 있는 서버 중 가장 적게 찬 곳으로

// 전체 전송
transfer.sendAll("hub-1"); // 현재 서버 전체 플레이어를 hub-1로
```

### 이벤트 훅

전송 전에 `ArcNetworkPlayerTransferEvent`가 발생한다. 취소 가능하다.

```java
@EventHandler
public void onTransfer(ArcNetworkPlayerTransferEvent event) {
    Player player = event.getPlayer();
    String targetServer = event.getTargetServer();
    String sourceServer = event.getSourceServer();

    // 특정 서버로의 전송 차단 (예: 밴된 게임 모드)
    if (targetServer.startsWith("pvp-") && player.hasPermission("arc.nopvp")) {
        event.setCancelled(true);
        player.sendMessage("§cPvP 서버 접근이 제한되어 있습니다.");
        return;
    }

    // 전송 전 처리 (예: 아이템 저장)
    savePlayerData(player);
}
```

### 전송 데이터 흐름

```
[전송 요청]
  └─▶ ArcNetworkPlayerTransferEvent 발생 → 취소 가능
  └─▶ arc:transfer:<uuid> 에 전송 메타데이터 저장 (TTL: 5분)
  └─▶ arc:inbox:<target-server> 에 TRANSFER 메시지 LPUSH
  └─▶ 현재 서버에서 플레이어 킥 (전송 메시지와 함께)

[대상 서버 수신]
  └─▶ RPOP으로 TRANSFER 메시지 수신
  └─▶ 플레이어 접속 시 PlayerJoinEvent에서 arc:transfer:<uuid> 조회
  └─▶ 전송 데이터 적용 후 DEL arc:transfer:<uuid>
```

**전송 데이터 TTL 5분**: 이동에 실패해도 5분 후 자동으로 Redis에서 삭제된다. 고아 데이터가 누적되지 않는다.

**장점**: Redis 기반이므로 수신 서버가 재시작 중이어도 데이터가 보존된다. 서버가 올라온 후 플레이어가 접속하면 정상적으로 전송 데이터가 적용된다.

**단점**: `inventory-transfer`는 기본 OFF다. 데이터 손실 위험이 있으므로 충분히 테스트한 뒤 활성화하라.

---

## 대기열 시스템

### 작동 원리

목표 서버가 만석(`players >= max`)이면 플레이어는 자동으로 대기열에 추가된다. 대기열은 Redis ZSet으로 구현되어 있으며, score가 낮을수록 우선순위가 높다.

```
arc:queue:game-1 (ZSet)
  member: "uuid-alice"   score: 1718400000000    (입장 시각 ms)
  member: "uuid-bob"     score: 1718399940000    (VIP: -60,000ms 앞당김)
  member: "uuid-charlie" score: 1718399700000    (Priority: -300,000ms 앞당김)
```

### 우선순위 체계

| 퍼미션 | score 조정 | 효과 |
|--------|-----------|------|
| 없음 | 현재 ms | FIFO (먼저 온 순서) |
| `arc.queue.vip` | 현재 ms − 60,000 | 1분 앞당김 |
| `arc.queue.priority` | 현재 ms − 300,000 | 5분 앞당김 |

퍼미션이 여러 개 있으면 가장 유리한 조정값이 적용된다.

### 드레이너 (Drainer)

5초마다 각 서버의 빈 슬롯을 계산하고, 슬롯 수만큼 score 낮은 플레이어를 자동 전송한다.

```
[드레이너 사이클: 5초마다]
  └─▶ HGET arc:server:game-1 players → 85
  └─▶ HGET arc:server:game-1 max    → 100
  └─▶ 빈 슬롯: 15개
  └─▶ ZRANGE arc:queue:game-1 0 14 WITHSCORES → 상위 15명
  └─▶ 각 플레이어에게 전송 요청
  └─▶ ZREM arc:queue:game-1 <전송된 uuid들>
```

**단점**: 드레이너 주기(기본 5초) 동안 빈 슬롯이 즉시 채워지지 않는다. 높은 트래픽 환경에서는 `drainer-interval`을 줄이는 것을 검토하라 (최소 1초 권장).

### 연결 끊김 후 대기 위치 유지

대기열 중 연결이 끊겨도 300초(5분) 동안 대기 위치가 유지된다.

```
arc:queue:game-1:disconnect:<uuid>  (String, TTL 300s) = "1718400000000"
```

재접속 시 기존 score로 대기열에 복귀한다. 300초 초과 후 재접속하면 대기열에서 제거되어 새로 입장해야 한다.

### 이벤트

```java
// 대기열 진입 이벤트
@EventHandler
public void onQueueJoin(ArcNetworkQueueJoinEvent event) {
    Player player = event.getPlayer();
    String targetServer = event.getTargetServer();
    int position = event.getPosition();      // 현재 대기 순위
    long estimatedWait = event.getEstimatedWaitMs(); // 예상 대기 시간 (ms)

    player.sendMessage(String.format(
        "§e%s 서버 대기 중... §f%d번째 §7(예상 대기: §f%ds§7)",
        targetServer, position, estimatedWait / 1000
    ));

    // 대기열 취소
    // event.setCancelled(true); // VIP 전용 서버에서 일반 유저 차단
}

// 대기열 이탈 이벤트
@EventHandler
public void onQueueLeave(ArcNetworkQueueLeaveEvent event) {
    Player player = event.getPlayer();
    ArcNetworkQueueLeaveEvent.Reason reason = event.getReason();

    switch (reason) {
        case TRANSFERRED:
            // 정상적으로 서버로 이동됨
            break;
        case DISCONNECTED:
            // 연결 끊김 (300초 유지 후 자동 제거)
            break;
        case CANCELLED:
            // 플레이어 또는 플러그인이 직접 취소
            player.sendMessage("§c대기열에서 취소됐습니다.");
            break;
        case SERVER_OFFLINE:
            // 대상 서버가 오프라인 됨
            player.sendMessage("§c서버가 오프라인됐습니다. 대기열이 해제됩니다.");
            break;
    }
}
```

### 대기열 직접 조작 API

```java
import dev.arc.network.player.ArcNetworkQueue;

ArcNetworkQueue queue = ArcNetworkQueue.getInstance();

// 플레이어를 특정 서버 대기열에 추가
queue.enqueue(player, "game-1");

// 대기열에서 제거
queue.dequeue(player, "game-1");

// 현재 순위 조회
int rank = queue.getRank(player.getUniqueId(), "game-1"); // 0-based

// 전체 대기열 크기
int size = queue.getSize("game-1");
```

---

## 인벤토리 전송

{: .warning }
`inventory-transfer.enabled: false`가 기본값이다. 인벤토리 전송은 데이터 손실 위험이 있으므로 충분히 테스트한 뒤 활성화하라. 특히 플러그인 커스텀 아이템(NBT 확장 포함)이 있는 경우 직렬화 호환성을 반드시 검증하라.

### 작동 원리

인벤토리 전송이 활성화되면, 플레이어가 서버를 이동하기 직전에 전체 상태를 Redis에 저장하고 도착 서버에서 자동으로 복원한다.

```yaml
# arc-network.yml
player:
  inventory-transfer:
    enabled: true
```

### 저장 항목

```
arc:invtransfer:<uuid>  (Hash, TTL 300s)
  inventory   → Base64 인코딩된 인벤토리 컨텐츠 (모든 슬롯)
  armor       → Base64 인코딩된 갑옷 슬롯 (4개)
  offhand     → Base64 인코딩된 오프핸드 슬롯
  xp-level    → 경험치 레벨
  xp-progress → 경험치 진행도 (0.0 ~ 1.0)
  health      → 체력
  max-health  → 최대 체력
  food        → 음식 레벨
  saturation  → 포만도
  effects     → Base64 인코딩된 포션 효과 목록
```

### 복원 흐름

```
[도착 서버 PlayerJoinEvent]
  └─▶ HGETALL arc:invtransfer:<uuid> 조회
  └─▶ 데이터 있으면:
      └─▶ 인벤토리 복원 (Base64 역직렬화)
      └─▶ 체력 / 음식 / XP 복원
      └─▶ 포션 효과 복원
      └─▶ DEL arc:invtransfer:<uuid>
  └─▶ 데이터 없으면: 서버 기본값 유지
```

### 주의사항

- **플러그인 커스텀 아이템**: 일부 플러그인이 NBT에 추가 데이터를 저장하는 경우, Arc의 Base64 직렬화가 해당 데이터를 보존하는지 확인해야 한다.
- **다른 경제 플러그인과 충돌**: 도착 서버에 별도의 인벤토리 초기화 로직이 있는 경우 충돌이 발생할 수 있다.
- **TTL 5분**: 전송 데이터는 5분 후 자동 삭제된다. 서버 유지보수로 인해 도착이 5분을 초과하면 인벤토리가 복원되지 않는다.
