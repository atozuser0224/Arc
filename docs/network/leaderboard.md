---
title: 글로벌 리더보드
parent: Network Layer
nav_order: 8
---

# 글로벌 리더보드

Redis Sorted Set을 기반으로 여러 독립 리더보드를 관리한다. 모든 서버에서 동일한 순위를 조회하고 업데이트할 수 있다.

## 활성화

```yaml
# arc-network.yml
network:
  leaderboard:
    enabled: true
```

---

## Redis 키 구조

```
arc:lb:<name>  (ZSet)
  member: "<uuid>"   score: 1500.0    ← 킬 수 또는 잔액
  member: "<uuid>"   score: 2300.0
  member: "<uuid>"   score: 450.0
```

Redis ZSet은 score 오름차순으로 정렬된다. 높은 점수가 "1등"인 경우(킬, 잔액 등)는 `ZREVRANK`를 사용한다.

---

## API: ArcLeaderboard

```java
import dev.arc.network.leaderboard.ArcLeaderboard;

ArcLeaderboard lb = ArcLeaderboard.getInstance();
UUID uuid = player.getUniqueId();
```

### 점수 설정

```java
// 절대값 설정 (기존 값 덮어쓰기)
lb.set("kills", uuid, 1500L);
lb.set("coins", uuid, 25000L);
lb.set("playtime", uuid, 3600L); // 초 단위
```

### 점수 증감

```java
// 킬 1 증가
lb.increment("kills", uuid, 1L);

// 플레이타임 60초 증가
lb.increment("playtime", uuid, 60L);

// 점수 감소 (음수 increment)
lb.increment("deaths", uuid, 1L);
```

### 점수 삭제

```java
// 리더보드에서 플레이어 제거
lb.remove("kills", uuid);
```

### 순위 조회

```java
// 0-based 순위 (높은 점수가 1위: ZREVRANK 사용)
long rank = lb.getRank("kills", uuid);
// rank == 0  → 1위
// rank == 9  → 10위
// rank == -1 → 리더보드에 없음

// 1-based로 표시
long displayRank = rank + 1;
player.sendMessage("킬 순위: " + displayRank + "위");
```

### 점수 조회

```java
Long score = lb.getScore("kills", uuid);
if (score == null) {
    player.sendMessage("리더보드에 기록이 없습니다.");
} else {
    player.sendMessage("킬 수: " + score);
}
```

### 리더보드 크기

```java
long totalEntries = lb.getSize("kills");
player.sendMessage("전체 등록 플레이어: " + totalEntries + "명");
```

### 상위 N명 조회

```java
// 상위 10명 조회 (score 내림차순)
List<ArcLeaderboard.Entry> top10 = lb.getTop("kills", 10);

StringBuilder sb = new StringBuilder("&6=== 킬 리더보드 (상위 10) ===\n");
for (int i = 0; i < top10.size(); i++) {
    ArcLeaderboard.Entry entry = top10.get(i);
    UUID entryUuid = entry.getUuid();
    long entryScore = entry.getScore();

    // UUID → 이름 변환 (별도 조회 필요)
    String name = Bukkit.getOfflinePlayer(entryUuid).getName();
    if (name == null) name = entryUuid.toString().substring(0, 8) + "...";

    sb.append(String.format("&e%d위 &f%s &7- &a%d킬\n",
        i + 1, name, entryScore));
}

player.sendMessage(sb.toString());
```

### 특정 범위 조회

```java
// 11위~20위 조회 (페이지네이션)
int page = 2;
int pageSize = 10;
int offset = (page - 1) * pageSize;

List<ArcLeaderboard.Entry> entries = lb.getRange("kills", offset, offset + pageSize - 1);
```

---

## 경제 자동 동기화

글로벌 경제 잔액을 자동으로 리더보드에 동기화할 수 있다.

```java
// 잔액을 "coins" 리더보드에 동기화
lb.syncEconomy(uuid);

// 내부 동작:
// long balance = ArcGlobalEconomy.getInstance().getBalance(uuid);
// lb.set("coins", uuid, balance);
```

경제 잔액이 변경될 때마다 동기화를 호출해 리더보드를 최신 상태로 유지한다.

```java
@EventHandler
public void onEconomyChange(ArcEconomyChangeEvent event) {
    // 입금/출금 시 자동으로 리더보드 동기화
    ArcLeaderboard.getInstance().syncEconomy(event.getPlayerUuid());
}
```

또는 설정으로 자동 동기화를 활성화한다:

```yaml
leaderboard:
  auto-sync-economy: true    # 경제 변경 시 자동으로 coins 리더보드 동기화
  sync-interval: 60          # 주기적 동기화 주기 (초, 0이면 비활성화)
```

---

## 다중 리더보드 예시

여러 독립 리더보드를 동시에 관리할 수 있다.

```java
// 리더보드 이름 상수 정의
public static final String LB_KILLS = "kills";
public static final String LB_DEATHS = "deaths";
public static final String LB_KDR = "kdr";        // Kill/Death Ratio
public static final String LB_COINS = "coins";
public static final String LB_PLAYTIME = "playtime";
public static final String LB_WINS = "wins";
public static final String LB_BLOCKS_PLACED = "blocks_placed";

ArcLeaderboard lb = ArcLeaderboard.getInstance();

// 킬 기록
lb.increment(LB_KILLS, killerUuid, 1L);

// 데스 기록
lb.increment(LB_DEATHS, victimUuid, 1L);

// KDR 업데이트 (킬/데스 비율, 소수점은 *1000으로 저장)
long kills = lb.getScore(LB_KILLS, killerUuid);
long deaths = lb.getScore(LB_DEATHS, killerUuid);
long kdr = deaths > 0 ? (kills * 1000) / deaths : kills * 1000;
lb.set(LB_KDR, killerUuid, kdr);
```

---

## 채팅 출력 예시

```java
// 상위 10명을 채팅에 출력하는 명령어 처리
public void showLeaderboard(CommandSender sender, String lbName) {
    List<ArcLeaderboard.Entry> top = ArcLeaderboard.getInstance().getTop(lbName, 10);

    sender.sendMessage("&6&l=== " + lbName.toUpperCase() + " 리더보드 ===");

    if (top.isEmpty()) {
        sender.sendMessage("&7아직 기록이 없습니다.");
        return;
    }

    String[] medals = {"&6①", "&7②", "&c③"};

    for (int i = 0; i < top.size(); i++) {
        ArcLeaderboard.Entry entry = top.get(i);
        String name = Bukkit.getOfflinePlayer(entry.getUuid()).getName();
        String prefix = i < 3 ? medals[i] : "&7" + (i + 1) + ".";

        sender.sendMessage(String.format(
            "%s &f%s &8| &e%,d",
            prefix,
            name != null ? name : "알 수 없음",
            entry.getScore()
        ));
    }

    // 자신의 순위 표시 (CommandSender가 Player인 경우)
    if (sender instanceof Player p) {
        long myRank = ArcLeaderboard.getInstance().getRank(lbName, p.getUniqueId());
        if (myRank >= 0) {
            Long myScore = ArcLeaderboard.getInstance().getScore(lbName, p.getUniqueId());
            sender.sendMessage(String.format(
                "&7내 순위: &e%d위 &8| &e%,d",
                myRank + 1, myScore
            ));
        }
    }
}
```

---

## 성능

| 연산 | Redis 명령 | 시간 복잡도 |
|------|-----------|------------|
| `set` | ZADD | O(log N) |
| `increment` | ZINCRBY | O(log N) |
| `getRank` | ZREVRANK | O(log N) |
| `getScore` | ZSCORE | O(1) |
| `getTop(N)` | ZREVRANGE 0 N-1 | O(log N + N) |
| `getSize` | ZCARD | O(1) |
| `remove` | ZREM | O(log N) |

N = 리더보드 전체 항목 수. Redis Sorted Set은 대규모 데이터에도 O(log N)을 보장하므로 수만 명의 플레이어가 등록된 경우에도 빠르다.

---

## 장단점

### 장점

- **빠른 순위 조회**: `ZREVRANK` O(log N)으로 대규모 리더보드도 즉시 응답한다.
- **다중 리더보드**: 이름 기반으로 독립 관리되므로 원하는 만큼 리더보드를 만들 수 있다.
- **즉시 반영**: 어느 서버에서 점수를 업데이트해도 모든 서버에서 동일한 순위를 조회한다.

### 단점

- **플레이어 이름 별도 조회 필요**: ZSet에는 UUID만 저장된다. 이름 표시를 위해 `Bukkit.getOfflinePlayer(uuid).getName()`이 필요하며, 이는 오프라인 플레이어의 경우 캐시 미스가 발생할 수 있다. 별도의 이름 캐시(예: `arc:playername:<uuid>`) 운영을 권장한다.
- **소수점 미지원**: score가 `double`이지만 API는 `long`을 사용한다. KDR처럼 소수점이 필요한 경우 ×1000 등의 단위 변환이 필요하다.
- **Redis 의존**: Redis 장애 시 리더보드 업데이트 및 조회가 불가능하다.
