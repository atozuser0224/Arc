---
title: 크로스서버 채팅 · 탭 리스트
parent: Network Layer
nav_order: 6
---

# 크로스서버 채팅 · 탭 리스트

## 크로스서버 채팅

### 개요

모든 서버의 플레이어가 하나의 채팅 채널을 공유한다. 서버 A에서 채팅하면 서버 B, C, D의 플레이어도 동일한 메시지를 받는다.

### 활성화

```yaml
# arc-network.yml
network:
  chat:
    enabled: true
    format: "&7[{server}] &f{player}&7: &f{message}"
    # 사용 가능한 변수:
    # {server}   - 발신 서버 ID
    # {player}   - 플레이어 이름
    # {message}  - 채팅 메시지
    # {group}    - 발신 서버 그룹
    # {prefix}   - 권한 플러그인 접두사 (있는 경우)
```

### 작동 흐름

```
[서버 A - 플레이어 "Steve" 채팅: "안녕하세요"]
  └─▶ AsyncPlayerChatEvent 캡처
  └─▶ 뮤트 여부 확인 (arc:mute:<uuid> 조회)
      ├─ 뮤트됨 → 메시지 차단, 플레이어에게 알림
      └─ 정상 → 계속 진행
  └─▶ 포맷 적용: "&7[lobby-1] &fSteve&7: &f안녕하세요"
  └─▶ 로컬 서버에 채팅 표시
  └─▶ LPUSH arc:inbox:broadcast:all {type:"CHAT", server:"lobby-1", player:"Steve", message:"안녕하세요", formatted:"..."}

[서버 B, C, D]
  └─▶ RPOP으로 CHAT 메시지 수신
  └─▶ 포맷된 메시지를 모든 플레이어에게 표시
```

### 뮤트 연동

뮤트된 플레이어는 서버를 이동해도 채팅이 차단된다. 메시지 전송 전 Redis에서 뮤트 여부를 확인하므로 서버 이동 후에도 일관되게 적용된다.

```java
@EventHandler(priority = EventPriority.HIGHEST)
public void onChat(AsyncPlayerChatEvent event) {
    Player player = event.getPlayer();

    // 뮤트 확인 (Redis 조회)
    if (ArcGlobalMute.getInstance().isMuted(player.getUniqueId())) {
        event.setCancelled(true);
        ArcMuteEntry muteEntry = ArcGlobalMute.getInstance().getMuteEntry(player.getUniqueId());
        long remaining = muteEntry.getExpires() - System.currentTimeMillis();

        if (muteEntry.isPermanent()) {
            player.sendMessage("§c영구 뮤트 상태입니다: " + muteEntry.getReason());
        } else {
            player.sendMessage(String.format(
                "§c뮤트 상태입니다 (남은 시간: %d분): %s",
                remaining / 60_000, muteEntry.getReason()
            ));
        }
        return;
    }
    // 크로스서버 채팅 처리 계속...
}
```

### 런타임 토글

서버 재시작 없이 채팅 기능을 켜고 끌 수 있다.

```
/arc network chat on     # 크로스서버 채팅 활성화
/arc network chat off    # 크로스서버 채팅 비활성화 (로컬 채팅만)
/arc network chat status # 현재 상태 확인
```

개별 플레이어 단위로도 토글 가능하다:

```
/arc network chat ignore <player>  # 특정 서버의 채팅 무시
/arc network chat unignore <player>
```

### 채팅 포맷 예시

```yaml
# 기본 포맷
format: "&7[{server}] &f{player}&7: &f{message}"
# 출력: [lobby-1] Steve: 안녕하세요

# 그룹 표시
format: "&8[{group}] &7[{server}] &f{player}&7: &f{message}"
# 출력: [survival] [game-1] Steve: 안녕하세요

# 권한 접두사 포함 (LuckPerms 등과 연동)
format: "{prefix} &7[{server}] &f{player}&7: &f{message}"
# 출력: [관리자] [lobby-1] Steve: 안녕하세요
```

### 장단점

**장점:**
- 설정 한 줄(`chat.enabled: true`)로 전 서버 채팅 통합
- 뮤트가 서버 이동 후에도 유지됨
- 재시작 없이 런타임 토글 가능

**단점:**
- 채팅 포맷 커스터마이징이 단순 문자열 치환 수준이다. MiniMessage 기반 그라디언트 색상, 클릭 이벤트, 호버 텍스트 등 고급 포맷은 직접 구현해야 한다.
- 크로스서버 채팅은 기본적으로 모든 서버에 방송된다. 특정 서버 그룹만 채팅을 공유하려면 그룹 필터 로직을 추가해야 한다.

---

## 탭 리스트 동기화

### 개요

5초마다 전체 네트워크의 플레이어 수를 집계해 탭 리스트 상단(header)과 하단(footer)에 표시한다.

### 활성화

```yaml
# arc-network.yml
network:
  tablist:
    enabled: true
    update-interval: 5    # 갱신 주기 (초)
    header: "&b&l네트워크 &r&7| 전체 플레이어: &b{total}명"
    footer: "&7현재 서버: &f{server} &7| &f{players}&7/&f{max}&7명 &7| TPS: &a{tps}"
    # 사용 가능한 변수:
    # {total}   - 전체 네트워크 플레이어 수
    # {server}  - 현재 서버 ID
    # {players} - 현재 서버 플레이어 수
    # {max}     - 현재 서버 최대 인원
    # {tps}     - 현재 서버 TPS
    # {mspt}    - 현재 서버 MSPT
```

### 작동 원리

```
[5초마다]
  └─▶ arc:servers:online SMEMBERS → 온라인 서버 목록
  └─▶ 각 서버의 arc:server:<id> HGET players → 플레이어 수 합산
  └─▶ setPlayerListHeaderFooter() 호출 (NMS 없이 Bukkit API 사용)
```

**NMS를 사용하지 않는다.** `Player.setPlayerListHeaderFooter(Component, Component)`를 사용하므로 버전 호환성이 높다.

### 출력 예시

```
┌─────────────────────────────────┐
│  네트워크  |  전체 플레이어: 127명  │  ← Header
├─────────────────────────────────┤
│  [탭 리스트 플레이어 목록]       │
├─────────────────────────────────┤
│ 현재 서버: game-1 | 42/100명 | TPS: 19.8  │  ← Footer
└─────────────────────────────────┘
```

### 서버별 플레이어 목록 표시 (선택)

탭 리스트에 현재 서버 플레이어만 표시하거나, 전체 네트워크 플레이어를 표시하는 것을 선택할 수 있다.

```yaml
tablist:
  show-all-servers: false   # true: 전체 네트워크 플레이어 표시 (가상 플레이어 추가)
  show-server-prefix: true  # 플레이어 이름 앞에 서버 접두사 표시
  server-prefix-format: "&8[{server}] "
```

`show-all-servers: true`를 설정하면 다른 서버 플레이어가 가상 플레이어(Fake Player)로 탭 리스트에 추가된다. 이 기능은 NMS 의존성이 없지만 플레이어 수가 많으면 탭 리스트가 복잡해질 수 있다.

### 장단점

**장점:**
- 탭 리스트 상단/하단에 전체 네트워크 현황을 실시간으로 표시 가능
- NMS 없이 `setPlayerListHeaderFooter()` 사용 → 버전 호환성 높음
- 갱신 주기 조절로 Redis 부하 최소화 가능

**단점:**
- 갱신 주기(기본 5초)로 인해 실시간 정확도가 떨어진다. 서버 간 플레이어 이동이 빈번한 경우 오차가 발생할 수 있다.
- `show-all-servers: true` 모드에서 플레이어가 많으면 탭 리스트가 매우 길어진다.

---

## 로컬 채팅 vs 크로스서버 채팅 분리

특정 접두사를 사용해 로컬 채팅과 크로스서버 채팅을 분리할 수 있다.

```yaml
chat:
  local-prefix: "!"    # "!메시지"는 현재 서버에만 표시
  global-prefix: null  # null이면 기본이 글로벌 채팅
```

예시:
- `안녕하세요` → 전체 서버에 방송
- `!안녕하세요` → 현재 서버만 표시

---

## 채팅 이벤트 훅

```java
// 크로스서버 채팅 메시지 수신 이벤트 (다른 서버에서 온 메시지)
@EventHandler
public void onCrossChat(ArcNetworkChatEvent event) {
    String sourceServer = event.getSourceServer();
    String playerName = event.getPlayerName();
    String message = event.getMessage();
    String formatted = event.getFormattedMessage();

    // 특정 서버의 채팅 필터링
    if (sourceServer.equals("staff-only") && !isStaffServer()) {
        event.setCancelled(true);
        return;
    }

    // 채팅 로깅
    chatLogger.log(sourceServer, playerName, message);
}
```
