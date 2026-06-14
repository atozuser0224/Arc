---
title: Network Layer
nav_order: 4
---

# Layer 4 — Network

Arc Network는 Redis 하나로 여러 Minecraft 서버를 하나의 네트워크로 묶는 레이어다. BungeeCord/Velocity 플러그인 메시징 채널을 활용해 기존 프록시 구성을 바꾸지 않고 추가된다. `arc-network.yml`에서 `enabled: false`가 기본값이며, Redis 정보를 입력하고 활성화하면 즉시 작동한다.

---

## 아키텍처

```
[Server A] ──LPUSH──▶ arc:inbox:broadcast:B  ──RPOP──▶ [Server B] → broadcastMessage()
[Server A] ──LPUSH──▶ arc:inbox:cmd:B        ──RPOP──▶ [Server B] → dispatchCommand()
[Server A] ──SETEX──▶ arc:server:A (TTL 10s) ◀── heartbeat every 2s
[Server A] ──SETEX──▶ arc:online:player:<name> → 글로벌 플레이어 위치 O(1) 조회
[Server A] ──ZADD ──▶ arc:queue:B  ◀── ArcQueueDrainer polls every 5s & transfers
```

메시지 전달에 JedisPubSub 대신 **LPUSH/RPOP 인박스 패턴**을 사용한다. 수신 서버가 재시작 중이어도 메시지가 Redis에 남아 복구 후 처리된다. 자동 재연결은 30초 쿨다운으로 동작해, Redis 순단 후 수동 개입 없이 회복된다.

---

## 서버 등록 · 상태 동기화

모든 서버는 2초마다 자신의 상태를 Redis에 기록한다. TTL은 10초 — 서버가 비정상 종료되어도 최대 10초 안에 오프라인 판정된다.

| Redis 키 | 내용 | TTL |
|----------|------|-----|
| `arc:server:<id>` | 서버 ID, 그룹, 태그, 플레이어 수, TPS, MSPT | 10s |
| `arc:servers:online` | 온라인 서버 ID Set | - |
| `arc:online:player:<name>` | 플레이어가 있는 서버 ID | 30s |
| `arc:server:<id>:history` | 상태 변경 이력 ZSet | 설정에 따름 |

---

## 플레이어 전송 · 이동

```
/arc network send <player> <server>     — 플레이어 이동
/arc network sendall <server>           — 전체 플레이어 이동 (4틱 간격 스태거)
/arc network hub                        — 허브 서버로 이동
/arc network find <player>              — 플레이어가 있는 서버 O(1) 조회
/arc network evacuate <server>          — 서버 대피
```

인벤토리 전송이 활성화되어 있으면 서버 이동 전 현재 상태를 Redis에 저장하고 도착 서버에서 자동 복원한다.

플러그인에서 직접 전송:

```kotlin
// 플레이어를 game-1 서버로 이동
ArcPlayerTransfer.send(player, targetServer = "game-1")

// 이동 이벤트 훅
@EventHandler
fun onTransfer(event: ArcNetworkPlayerTransferEvent) {
    val player = event.player
    val from = event.fromServer  // 출발 서버 ID
    val to = event.toServer      // 도착 서버 ID
    // 이동 전 데이터 저장, 로그 기록 등
}
```

---

## 대기열 시스템

플레이어가 만석인 서버에 입장하려 할 때 대기열에 들어간다.

**우선순위 (Redis ZSet score):**

| 권한 | 시각 조정 | 효과 |
|------|----------|------|
| 없음 | 현재 시각(ms) | FIFO |
| `arc.queue.vip` | -60,000ms | 1분 앞당김 |
| `arc.queue.priority` | -300,000ms | 5분 앞당김 |

**드레이너**: 5초마다 빈 슬롯 계산 → 순위 높은 플레이어 자동 전송

```kotlin
// 대기열 진입 이벤트
@EventHandler
fun onQueueJoin(event: ArcNetworkQueueJoinEvent) {
    val player = event.player
    val target = event.targetServer
    val position = event.position
    // 대기 화면 표시
    player.sendTitle("§e대기 중", "§f${target} 서버 — ${position}번째", 10, 72000, 10)
}

// 대기열 이탈 이벤트
@EventHandler
fun onQueueLeave(event: ArcNetworkQueueLeaveEvent) {
    player.clearTitle()
}
```

연결이 끊겨도 기본 300초 동안 대기 위치가 유지된다. 재접속하면 순위를 안내하고 자리를 유지한다.

---

## 글로벌 경제

Redis에 플레이어 잔액을 저장하는 네트워크 공유 경제 시스템이다. 어느 서버에서 재화를 획득해도 다른 서버에서 즉시 반영된다.

```kotlin
val uuid = player.uniqueId

// 잔액 조회
val balance: Long = ArcGlobalEconomy.getBalance(uuid)

// 입금 (INCRBY — 원자적)
val newBalance = ArcGlobalEconomy.deposit(uuid, 1000L)

// 출금 (Lua eval — 원자적, 잔액 부족 시 false)
val success = ArcGlobalEconomy.withdraw(uuid, 500L)
if (!success) player.sendMessage("§c잔액이 부족합니다.")

// 이체 (Lua eval — 두 잔액 동시 원자 변경)
val transferred = ArcGlobalEconomy.transfer(from = playerA, to = playerB, amount = 200L)

// 잔액 확인
val canAfford = ArcGlobalEconomy.hasBalance(uuid, 100L)
```

**원자성 보장**: 출금과 이체는 Redis Lua 스크립트로 처리된다. 여러 서버에서 동시에 같은 플레이어의 잔액을 차감하려 해도 음수 잔액이 생기지 않는다.

---

## 글로벌 밴 · 뮤트

네트워크 전체에 적용되는 밴/뮤트 시스템이다. 밴이 실행되면 모든 서버에 인박스 메시지가 전송되어 해당 플레이어를 즉시 강제 퇴장시킨다.

```kotlin
// 영구 밴
ArcGlobalBan.ban(
    uuid = player.uniqueId,
    playerName = player.name,
    reason = "치팅",
    actor = sender.name
)

// 7일 기간 밴 (초 단위)
ArcGlobalBan.ban(
    uuid = player.uniqueId,
    playerName = player.name,
    reason = "언어 위반",
    actor = sender.name,
    durationSeconds = 7 * 24 * 3600L
)

// 언밴
ArcGlobalBan.unban(player.uniqueId)

// 밴 확인 (만료 시 자동 삭제)
val banned: Boolean = ArcGlobalBan.isBanned(player.uniqueId)

// 1시간 뮤트
ArcGlobalBan.mute(player.uniqueId, player.name, "스팸", sender.name, 3600L)
val muted: Boolean = ArcGlobalBan.isMuted(player.uniqueId)

// IP 밴
ArcGlobalBan.banIp("192.168.1.100", "불법 봇", sender.name)
```

밴 이벤트는 자동으로 Discord Webhook을 호출한다 (Webhook URL이 설정된 경우).

```kotlin
// 밴 이벤트 수신
@EventHandler
fun onBanReceived(event: ArcNetworkPlayerBanEvent) {
    val uuid = event.uuid
    val reason = event.reason
    val actor = event.actor
    // 추가 처리 (UI 업데이트, 로그 등)
}
```

---

## 글로벌 Vault (공유 인벤토리)

플레이어가 어느 서버에서든 같은 아이템 창고에 접근하는 시스템이다. **분산 잠금(Redis SET NX EX)**으로 아이템 복제를 방지한다.

```kotlin
// 자신의 Vault 열기
ArcGlobalVault.open(player)

// 다른 그룹의 Vault 열기 (VIP 전용 창고 등)
ArcGlobalVault.open(player, group = "vip")

// 관리자가 특정 플레이어의 Vault 확인
ArcGlobalVault.openFor(admin, targetPlayer.uniqueId)

// /arc network vault <player> 명령어로도 가능
```

두 서버에서 동시에 같은 Vault를 열려 하면 "이미 다른 서버에서 사용 중" 메시지가 표시되고 열리지 않는다.

---

## 인벤토리 전송

서버 이동 시 인벤토리 유실을 방지한다. `arc-network.yml`에서 `inventory-transfer.enabled: true`로 활성화 (기본 OFF).

이동 직전에 전체 상태를 Redis에 저장한다:
- 인벤토리 컨텐츠 (Base64)
- 갑옷 슬롯, 추가 칸
- XP 레벨·포인트
- 체력, 배고픔

도착 서버 접속 시 `PlayerJoinEvent`에서 자동 복원된다. 저장 데이터 TTL은 5분 — 이동 실패 시 자동 소멸한다.

---

## 크로스서버 채팅

모든 서버의 플레이어가 같은 채팅 채널을 공유한다.

```kotlin
// 설정에서 포맷 지정
// arc-network.yml → chat.format: "&7[{server}] &f{player}: &7{message}"

// 런타임 토글
/arc network chat on
/arc network chat off

// 프로그램으로 토글
ArcNetworkChat.enabled = false
```

뮤트된 플레이어의 메시지는 채팅 리스너에서 전송 전에 차단된다. 서버를 옮겨도 뮤트가 유지된다.

---

## 탭 리스트 동기화

5초마다 네트워크 전체 플레이어 수를 집계해 탭 리스트 상단/하단에 표시한다. NMS 없이 `setPlayerListHeaderFooter()`만 사용해 버전 호환성이 높다.

```
헤더 예시:
§6§lARC NETWORK  §f총 플레이어: §a127명
  lobby: 45  game-1: 28  game-2: 31  minigame: 23

풋터 예시:
§7현재 서버 TPS: §a19.8  MSPT: §a12.4ms
```

---

## Discord Webhook

Discord 채널에 중요 이벤트를 자동으로 알린다. Webhook URL은 런타임에 설정 가능하다 — 서버 재시작 불필요.

```kotlin
// Runtime 설정 (서버 재시작 없음)
ArcGlobalConfig.set("discord.webhook.url", "https://discord.com/api/webhooks/...")
ArcGlobalConfig.set("discord.events", "ban,serverdown,serverup,broadcast")

// 수동 호출
ArcDiscordWebhook.sendBan(playerName = "BadPlayer", reason = "치팅", actor = "Admin", permanent = true)
ArcDiscordWebhook.sendBroadcast("서버 점검 5분 전입니다.")
ArcDiscordWebhook.sendServerDown("game-2")
ArcDiscordWebhook.sendServerUp("game-2")
```

JDK 내장 `HttpClient`를 사용해 별도 HTTP 라이브러리 의존성이 없다.

---

## 글로벌 리더보드

Redis Sorted Set 기반 네트워크 전체 순위 시스템이다. 리더보드 이름을 지정해 여러 항목을 독립적으로 관리한다.

```kotlin
// 점수 설정 / 증감
ArcLeaderboard.set("kills", player.uniqueId, 150.0)
ArcLeaderboard.increment("kills", player.uniqueId, 1.0)  // +1

// 제거
ArcLeaderboard.remove("kills", player.uniqueId)

// 순위 조회 (0부터 시작, null이면 순위 없음)
val rank: Long? = ArcLeaderboard.getRank("kills", player.uniqueId)
val score: Double? = ArcLeaderboard.getScore("kills", player.uniqueId)
val totalPlayers: Long = ArcLeaderboard.getSize("kills")

// 상위 10명 조회 → (uuid 문자열, 점수) 쌍
val top10: List<Pair<String, Double>> = ArcLeaderboard.getTop("kills", n = 10)
top10.forEachIndexed { idx, (uuidStr, score) ->
    val name = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr)).name ?: uuidStr
    sender.sendMessage("§6${idx + 1}위 §f$name — §a${score.toInt()}킬")
}

// 경제 잔액을 "coins" 리더보드에 자동 동기화
ArcLeaderboard.syncEconomy(player.uniqueId)
```

---

## 글로벌 설정 저장소

모든 서버가 같은 설정을 공유하는 Redis 해시 기반 런타임 설정 저장소다. 한 서버에서 값을 바꾸면 전체 서버에 즉시 반영된다.

```kotlin
// 설정 읽기
val webhookUrl = ArcGlobalConfig.get("discord.webhook.url") ?: ""
val maxKits = ArcGlobalConfig.getInt("kit.max-per-day", default = 3)
val vipEnabled = ArcGlobalConfig.getBoolean("vip.enabled", default = false)
val multiplier = ArcGlobalConfig.getDouble("economy.multiplier", default = 1.0)

// 설정 쓰기 (전체 서버에 즉시 반영)
ArcGlobalConfig.set("economy.multiplier", "2.0")    // 이벤트 기간 2배
ArcGlobalConfig.set("vip.enabled", "true")

// 모든 설정 조회
val all: Map<String, String> = ArcGlobalConfig.getAll()

// 삭제
ArcGlobalConfig.delete("temp.event.active")
```

---

## 플러그인 원격 배포

빌드된 JAR를 다른 서버에 전송하고 자동 설치한다. 기본 OFF. `arc-network.yml`에서 `plugin-deploy.enabled: true`로 활성화.

```
/arc network deploy MyPlugin-1.2.jar game-1     — game-1 서버에만 배포
/arc network deploy MyPlugin-1.2.jar all        — 전체 서버에 배포
```

배포 전 PluginSandboxCheck가 실행된다. DANGEROUS 판정 JAR는 자동 설치가 차단된다.

---

## 원격 커맨드

다른 서버에서 커맨드를 실행한다. 기본 OFF.

```
/arc network remote-cmd game-1 say 서버 점검 5분 전!
/arc network remote-cmd lobby save-all
```

허용/차단 목록으로 실행 가능한 커맨드를 제한한다. `require-confirm: true`가 기본이어서 위험한 명령은 confirm 토큰 입력이 필요하다.

---

## 네트워크 감사 로그

모든 네트워크 작업(전송, 방송, 커맨드, 밴, 배포)이 자동 기록된다.

```
/arc network audit                      — 최근 20개
/arc network audit player <이름>        — 특정 플레이어 관련 로그
/arc network audit action <액션>        — 특정 액션 필터링
```

현재 세션 최근 500개는 메모리에 유지되고, Redis에도 영속 저장된다 (기본 90일 보존). 민감 정보 마스킹이 기본 활성화되어 IP 주소가 감사 로그에 그대로 남지 않는다.

---

## 기능별 기본값

| 기능 | 기본값 | 이유 |
|------|--------|------|
| 서버 등록 · 전송 · 대기열 | 활성 | 역방향 복구 가능 |
| 글로벌 경제 / 밴 / 채팅 | 활성 | 되돌리기 가능 |
| 탭 리스트 동기화 | 활성 | UI 표시만 |
| Global Vault | 비활성 | 아이템 복제 위험 |
| 인벤토리 전송 | 비활성 | 데이터 손실 위험 |
| 원격 커맨드 | 비활성 | 임의 명령 실행 위험 |
| 플러그인 배포 | 비활성 | 서버 코드 변조 위험 |
