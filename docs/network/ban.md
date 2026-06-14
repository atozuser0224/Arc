---
title: 글로벌 밴 · 뮤트
parent: Network Layer
nav_order: 4
---

# 글로벌 밴 · 뮤트

한 서버에서 밴 또는 뮤트를 실행하면, 인박스 메시지를 통해 전체 네트워크의 모든 서버에 즉시 적용된다.

## 활성화

```yaml
# arc-network.yml
network:
  ban:
    enabled: true
```

---

## 밴 API: ArcGlobalBan

```java
import dev.arc.network.ban.ArcGlobalBan;
import dev.arc.network.ban.BanResult;
import java.util.UUID;

ArcGlobalBan ban = ArcGlobalBan.getInstance();
```

### 영구 밴

```java
UUID target = player.getUniqueId();
String name = player.getName();

BanResult result = ban.ban(
    target,              // 밴 대상 UUID
    name,                // 밴 대상 이름 (표시용)
    "핵 프로그램 사용",  // 밴 이유
    "AdminPlayer"        // 처리한 관리자 이름
    // durationSeconds 생략 → 영구 밴
);

if (result == BanResult.SUCCESS) {
    sender.sendMessage("§a" + name + "을(를) 영구 밴했습니다.");
}
```

### 기간 밴

```java
// 7일 밴 (7 * 24 * 3600 = 604,800초)
BanResult result = ban.ban(
    target,
    name,
    "욕설 반복",
    "AdminPlayer",
    604_800L  // durationSeconds
);
```

**기간 밴 Redis 저장 구조:**

```
arc:ban:<uuid>  (Hash, TTL = durationSeconds)
  reason     → "욕설 반복"
  actor      → "AdminPlayer"
  timestamp  → "1718400000000"
  expires    → "1719004800000"   (ms, 영구 밴 시 -1)
  name       → "PlayerName"
```

영구 밴은 TTL 없이 저장된다. 기간 밴은 TTL을 설정해 Redis가 자동으로 삭제한다.

### 밴 확인

```java
boolean isBanned = ban.isBanned(uuid);

if (isBanned) {
    ArcBanEntry entry = ban.getBanEntry(uuid);
    long expires = entry.getExpires(); // -1 = 영구
    String reason = entry.getReason();
    String actor = entry.getActor();

    if (expires == -1) {
        player.sendMessage("§c영구 밴: " + reason);
    } else {
        long remaining = expires - System.currentTimeMillis();
        player.sendMessage(String.format(
            "§c기간 밴: %s (남은 시간: %d시간)",
            reason, remaining / 3_600_000
        ));
    }
}
```

### 언밴

```java
BanResult result = ban.unban(uuid, "AdminPlayer"); // 처리 관리자 기록
```

---

## 뮤트 API

```java
import dev.arc.network.ban.ArcGlobalMute;

ArcGlobalMute mute = ArcGlobalMute.getInstance();

// 1시간 뮤트
long oneHour = 3600L;
mute.mute(
    target,
    name,
    "채팅 도배",
    "AdminPlayer",
    oneHour
);

// 영구 뮤트
mute.mute(target, name, "스팸봇", "AdminPlayer");

// 뮤트 확인
boolean isMuted = mute.isMuted(uuid);

// 뮤트 해제
mute.unmute(uuid, "AdminPlayer");
```

뮤트된 플레이어는 서버를 이동해도 뮤트 상태가 유지된다. 뮤트 정보가 Redis에 저장되어 있어 어느 서버에서 채팅해도 차단된다.

---

## IP 밴

```java
import dev.arc.network.ban.ArcGlobalBan;

ArcGlobalBan ban = ArcGlobalBan.getInstance();

// IP 밴 (영구)
ban.banIp("192.168.1.100", "계정 공유 의심", "AdminPlayer");

// IP 밴 확인
boolean isIpBanned = ban.isIpBanned("192.168.1.100");

// IP 밴 해제
ban.unbanIp("192.168.1.100", "AdminPlayer");
```

**Redis 저장:**

```
arc:ban:ip:192.168.1.100  (Hash)
  reason    → "계정 공유 의심"
  actor     → "AdminPlayer"
  timestamp → "1718400000000"
```

{: .warning }
IP 밴은 NAT 환경(공유기, 학교/회사 네트워크)에서 무고한 플레이어를 차단할 수 있다. VPN 우회도 가능하다. IP 밴은 신중하게 사용하고 주기적으로 검토하라.

---

## 전체 서버 즉시 적용

밴 실행 시 대상 플레이어가 접속 중인 서버를 Redis에서 조회하고, 해당 서버의 인박스에 BAN 메시지를 LPUSH한다.

```
[밴 실행 (서버 A)]
  └─▶ arc:ban:<uuid> 저장 (Redis)
  └─▶ arc:online:player:<name> 조회 → "game-1" (접속 중인 서버)
  └─▶ LPUSH arc:inbox:game-1  {type: "BAN", uuid: "...", reason: "..."}
  └─▶ LPUSH arc:inbox:broadcast:all {type: "BAN_NOTIFY", ...} (전체 방송)

[game-1 서버 수신]
  └─▶ RPOP으로 BAN 메시지 수신
  └─▶ 대상 플레이어 강제 퇴장 (kick)
  └─▶ ArcNetworkPlayerBanEvent 발생
```

### ArcNetworkPlayerBanEvent

```java
@EventHandler
public void onPlayerBan(ArcNetworkPlayerBanEvent event) {
    String playerName = event.getPlayerName();
    String reason = event.getReason();
    String actor = event.getActor();
    boolean isPermanent = event.isPermanent();

    // 관리자 채널 알림
    for (Player admin : Bukkit.getOnlinePlayers()) {
        if (admin.hasPermission("arc.admin.notify")) {
            admin.sendMessage(String.format(
                "§c[밴] %s → %s (처리: %s)",
                actor, playerName, reason
            ));
        }
    }

    // 이벤트 취소는 불가 (밴은 이미 Redis에 저장됨)
    // 취소가 필요하면 즉시 unban() 호출
}
```

---

## Discord Webhook 자동 연동

`discord.webhook.url`이 설정되어 있으면 밴 실행 시 자동으로 Discord 채널에 메시지가 전송된다.

```yaml
discord:
  webhook:
    url: "https://discord.com/api/webhooks/..."
    events:
      ban: true       # 밴/언밴 알림
```

전송되는 메시지 예시:

```
🔨 플레이어 밴
이름: PlayerName
이유: 핵 프로그램 사용
처리: AdminPlayer
종류: 영구 밴
시각: 2026-06-15 14:30:00 KST
```

---

## 장단점

### 장점

- **즉시 전파**: 한 서버에서 밴하면 전체 네트워크에 즉시 적용된다. 플레이어가 다른 서버로 도망쳐도 차단된다.
- **기간 밴 자동 만료**: TTL 기반으로 Redis가 자동으로 만료를 처리한다. 별도의 스케줄러가 필요 없다.
- **Discord 통합**: 재시작 없이 Webhook URL만 변경하면 알림 채널을 바꿀 수 있다.

### 단점

- **Redis 의존**: Redis 장애 시 밴 정보 조회가 불가능하다. 신규 접속자의 밴 여부를 확인할 수 없어 밴된 플레이어가 접속할 수 있다. Redis HA가 필수다.
- **IP 밴 우회 가능**: VPN, Tor, 프록시를 사용하면 IP 밴을 우회할 수 있다.
- **히스토리 없음**: 기본적으로 밴 이력이 쌓이지 않는다. 감사 로그와 함께 사용하면 `/arc network audit action ban`으로 조회 가능하다.

---

## 명령어

| 명령어 | 권한 | 설명 |
|--------|------|------|
| `/arc ban <player> [reason]` | `arc.ban` | 영구 밴 |
| `/arc ban <player> <duration> [reason]` | `arc.ban` | 기간 밴 (예: `7d`, `24h`, `30m`) |
| `/arc unban <player>` | `arc.unban` | 밴 해제 |
| `/arc mute <player> [duration] [reason]` | `arc.mute` | 뮤트 |
| `/arc unmute <player>` | `arc.unmute` | 뮤트 해제 |
| `/arc banip <ip> [reason]` | `arc.banip` | IP 밴 |
| `/arc unbanip <ip>` | `arc.unbanip` | IP 밴 해제 |
| `/arc baninfo <player>` | `arc.baninfo` | 밴 정보 조회 |
