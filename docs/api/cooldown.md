---
title: 플레이어 쿨다운
parent: Developer API
nav_order: 10
---

# 플레이어 쿨다운

`PlayerCooldown`은 플레이어별, 키별로 능력·명령어·아이템의 재사용 대기 시간을 추적한다. Bukkit 내장 `Player.setCooldown(Material, ticks)`은 아이템 타입 단위라 임의 문자열 키를 쓸 수 없다. `PlayerCooldown`은 그 공백을 채운다.

---

## Bukkit 방식의 문제

Bukkit은 `Material` 단위 쿨다운만 지원한다. "폭발 능력"이나 "키트 수령" 같은 논리적 행동에는 쓸 수 없어, 대부분의 플러그인이 `HashMap<UUID, Long>`을 직접 관리한다.

```kotlin
// ❌ 직접 구현 — 잊기 쉬운 정리 코드, 만료 계산 분산
private val cooldowns = HashMap<UUID, Long>()

fun isOnCooldown(player: Player): Boolean {
    val expires = cooldowns[player.uniqueId] ?: return false
    return System.currentTimeMillis() < expires
}

fun setCooldown(player: Player, seconds: Long) {
    cooldowns[player.uniqueId] = System.currentTimeMillis() + seconds * 1000
}

// onDisable, PlayerQuitEvent 에서 정리 코드 별도 작성 필요
```

이 패턴의 문제:
- 만료된 항목이 Map에 계속 쌓인다.
- 플레이어 퇴장 시 정리 코드를 빠뜨리면 메모리 누수가 생긴다.
- 남은 시간 계산, 진행도 계산을 매번 직접 짜야 한다.
- 플러그인마다 비슷한 코드가 반복된다.

---

## PlayerCooldown 사용법

```kotlin
// 플러그인 생명주기에 묶인 쿨다운 트래커 생성
val cooldowns = plugin.playerCooldown()
// 또는: val cooldowns = PlayerCooldown(plugin)
```

플레이어가 퇴장하면 그 플레이어의 쿨다운 데이터가 자동으로 지워진다. `close()`를 부르면 전체 상태가 초기화된다.

---

## 기본 쿨다운 설정 · 확인

### Kotlin

```kotlin
plugin.listen<PlayerInteractEvent> { e ->
    val player = e.player
    val key = "fireball"

    if (cooldowns.isOnCooldown(player, key)) {
        val left = cooldowns.remaining(player, key)
        player.sendMessage("§c${left.inWholeSeconds}초 후 다시 쓸 수 있습니다.")
        e.isCancelled = true
        return@listen
    }

    // 능력 발동
    castFireball(player)
    cooldowns.set(player, key, 10.seconds)
}
```

### Java

```java
import java.util.concurrent.TimeUnit;

@EventHandler
public void onInteract(PlayerInteractEvent e) {
    Player player = e.getPlayer();
    String key = "fireball";

    if (cooldowns.isOnCooldown(player, key)) {
        long left = cooldowns.remainingMillis(player, key) / 1000;
        player.sendMessage("§c" + left + "초 후 다시 쓸 수 있습니다.");
        e.setCancelled(true);
        return;
    }

    castFireball(player);
    cooldowns.set(player, key, 10, TimeUnit.SECONDS);
}
```

---

## tryUse — 원자적 사용 시도

쿨다운 확인 → 능력 발동 → 쿨다운 설정을 한 번에 처리한다.

### Kotlin

```kotlin
// 쿨다운이 끝났으면 블록을 실행하고 새 쿨다운을 건다. 이미 대기 중이면 false 반환.
val used = cooldowns.tryUse(player, "kit", 5.minutes) {
    giveKit(player)
    player.sendMessage("§a키트를 받았습니다!")
}

if (!used) {
    val left = cooldowns.remaining(player, "kit")
    player.sendMessage("§c키트는 ${left.inWholeMinutes}분 후에 다시 받을 수 있습니다.")
}
```

### Java

```java
boolean used = cooldowns.tryUse(player, "kit", 5, TimeUnit.MINUTES, () -> {
    giveKit(player);
    player.sendMessage("§a키트를 받았습니다!");
});

if (!used) {
    long leftSec = cooldowns.remainingMillis(player, "kit") / 1000;
    player.sendMessage("§c키트는 " + leftSec / 60 + "분 후에 다시 받을 수 있습니다.");
}
```

---

## progress — 진행도 (경험치바 / 보스바 활용)

0.0(방금 설정) → 1.0(만료)으로 단조 증가하는 진행률을 돌려준다. 경험치바나 보스바 쿨다운 표시에 바로 쓸 수 있다.

### Kotlin

```kotlin
// 불덩이 쿨다운 10초, 진행도 경험치바로 표시
plugin.runRepeat(period = 2L) {  // 0.1초마다 갱신
    onlinePlayers.forEach { player ->
        val progress = cooldowns.progress(player, "fireball", 10.seconds)
        player.exp = progress.toFloat()
        player.level = ((1.0 - progress) * 10).toInt()  // 남은 초 표시
    }
}
```

### Java

```java
// totalMillis를 ms 단위로 전달
double progress = cooldowns.progress(player, "fireball", 10_000L);
player.setExp((float) progress);
```

---

## 수동 초기화

```kotlin
// 특정 키만 초기화 (어드민 명령어 등)
cooldowns.clear(player, "fireball")

// 플레이어의 모든 쿨다운 초기화
cooldowns.clearAll(player)
```

---

## API 레퍼런스

| 메서드 | 설명 |
|--------|------|
| `set(player, key, duration)` | 쿨다운 설정 (Kotlin `Duration`) |
| `set(player, key, amount, unit)` | 쿨다운 설정 (Java `TimeUnit`) |
| `isOnCooldown(player, key)` | 쿨다운 중이면 `true`, 만료된 항목은 자동 제거 |
| `remaining(player, key)` | 남은 시간 (`Duration`), 없으면 `ZERO` |
| `remainingMillis(player, key)` | 남은 시간 (ms, Java 친화적) |
| `tryUse(player, key, duration) { }` | 쿨다운이 없으면 블록 실행 후 쿨다운 설정 |
| `tryUse(player, key, amount, unit, Runnable)` | Java 버전 |
| `progress(player, key, total)` | 0.0~1.0 진행률 |
| `progress(player, key, totalMillis)` | Java 버전 |
| `clear(player, key)` | 특정 키 초기화 |
| `clearAll(player)` | 플레이어 전체 초기화 |
| `close()` | 전체 상태 비우기 |

---

## 전체 예시 — 능력 시스템

```kotlin
class AbilityPlugin : JavaPlugin() {

    private val cooldowns = PlayerCooldown(this)

    override fun onEnable() {
        // 더블 스니크로 대시
        listen<PlayerDoubleSneakEvent> { e ->
            val player = e.player

            cooldowns.tryUse(player, "dash", 8.seconds) {
                val dir = player.location.direction.normalize().multiply(2.0)
                player.velocity = dir
                player.world.spawnParticle(Particle.SWEEP_ATTACK, player.location, 10)
                player.playSound(player.location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.5f)
            } || player.sendActionBar(
                Component.text("§c대시 쿨다운: ${cooldowns.remaining(player, "dash").inWholeSeconds}초")
            )
        }

        // 쿨다운 진행도 액션바 표시
        runRepeat(period = 4L) {  // 0.2초마다
            onlinePlayers.forEach { p ->
                val prog = cooldowns.progress(p, "dash", 8.seconds)
                if (prog < 1.0) {
                    val bar = "█".repeat((prog * 10).toInt()) + "░".repeat(10 - (prog * 10).toInt())
                    p.sendActionBar(Component.text("§b대시 [$bar]"))
                }
            }
        }
    }
}
```

---

## 이점과 한계

**이점**
- 임의 문자열 키 → 능력·명령어·키트 등 어떤 행동에도 적용 가능
- 만료 검사가 O(1): `TimeSource.Monotonic` 타임마크 비교
- 만료된 항목을 접근 시 자동 제거해 Map이 무한정 늘어나지 않음
- 플레이어 퇴장 시 자동 정리, 플러그인 종료 시 `close()` 한 줄로 완전 해제
- Kotlin `Duration` + Java `TimeUnit` 양쪽 지원

**한계**
- 서버 재시작 시 쿨다운이 초기화된다. 재시작 후에도 유지해야 하면 PDC나 DB에 직접 저장해야 한다.
- 여러 서버 인스턴스에 걸친 글로벌 쿨다운은 지원하지 않는다. 네트워크 쿨다운이 필요하면 Redis 키와 TTL을 직접 써야 한다.
