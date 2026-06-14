---
title: 스케줄러 · 태스크
parent: Developer API
nav_order: 1
---

# 스케줄러 · 태스크

Arc API의 스케줄러는 Bukkit의 `BukkitRunnable` 방식을 람다 기반 확장 함수로 대체합니다. 익명 클래스를 작성하지 않아도 되고, 플러그인 비활성화 시 등록된 모든 태스크가 자동으로 취소됩니다.

---

## Bukkit 방식의 한계

Bukkit에서 주기적으로 실행되는 태스크를 작성하려면 다음과 같은 코드가 필요합니다.

```kotlin
// Bukkit 방식 — 익명 클래스, 취소 로직 수동 관리
object : BukkitRunnable() {
    var count = 0

    override fun run() {
        count++
        player.sendMessage("카운트: $count")
        if (count >= 5) {
            cancel()  // 내부에서 this를 참조
        }
    }
}.runTaskTimer(plugin, 0L, 20L)
```

이 방식의 문제점은 다음과 같습니다.

- `object : BukkitRunnable()` 선언이 매번 필요하여 코드가 장황해집니다.
- 반복 태스크 내에서 취소하려면 `cancel()`을 `this`에서 호출해야 합니다.
- 비동기 태스크에서 메인 스레드로 복귀하는 패턴이 중첩 클래스로 더 복잡해집니다.
- 플러그인 비활성화 시 태스크를 수동으로 취소해야 합니다(`onDisable`에서 등록된 태스크 추적 필요).

---

## Arc 방식

Arc는 `Plugin` 클래스에 확장 함수를 추가합니다. 모든 태스크는 플러그인 생명주기와 자동으로 연동됩니다.

### plugin.runLater — 지연 실행

지정한 틱 수 이후에 메인 스레드에서 한 번 실행합니다.

```kotlin
// 1초(20 ticks) 후 한 번 실행
plugin.runLater(20L) {
    player.sendMessage("§a1초가 지났습니다!")
}

// 0 ticks — 현재 틱 끝에 실행 (즉시 예약)
plugin.runLater(0L) {
    // 현재 이벤트 핸들러 컨텍스트를 벗어난 직후 실행
    world.save()
}
```

**사용 시나리오**: 이벤트 핸들러 직후에 실행해야 하는 작업, 딜레이 후 메시지 전송, 쿨다운 후 효과 발동.

### plugin.runRepeat — 반복 실행

일정 주기로 반복 실행합니다. 람다 내에서 `cancel()`을 호출하면 태스크가 취소됩니다.

```kotlin
// 즉시 시작, 1초(20 ticks)마다 반복
val task = plugin.runRepeat(delay = 0L, period = 20L) {
    broadcastServerStats()
}

// 나중에 외부에서 취소
task.cancel()
```

```kotlin
// 내부에서 조건에 따라 취소
var elapsed = 0
plugin.runRepeat(delay = 10L, period = 5L) {
    elapsed += 5
    spawnParticle(boss.location)
    if (elapsed >= 100) {
        cancel()  // 5초(100 ticks) 후 자동 취소
    }
}
```

**Bukkit 방식과 비교**: `BukkitRunnable`의 `object :` 선언 없이 간결하게 작성할 수 있습니다. 외부에서 태스크 참조를 통해 취소하거나, 내부에서 `cancel()`을 호출하는 두 가지 방법 모두 지원합니다.

### plugin.runAsync — 비동기 실행

파일 I/O, 네트워크, 데이터베이스 쿼리 등 무거운 작업을 비동기 스레드에서 실행합니다. 완료 후 메인 스레드로 복귀할 때는 `runNext`를 사용합니다.

```kotlin
plugin.runAsync {
    // 이 블록은 비동기 스레드에서 실행됩니다.
    // Bukkit API 직접 호출 금지!
    val playerData = database.loadPlayerData(player.uniqueId)

    // 결과를 메인 스레드에서 처리
    runNext {
        // 이 블록은 다시 메인 스레드에서 실행됩니다.
        player.sendMessage("§a코인: ${playerData.coins}")
        player.health = playerData.savedHealth
    }
}
```

{: .warning }
`runAsync` 블록 내에서 `player.sendMessage()`, `world.spawnEntity()` 등 Bukkit API를 직접 호출하면 스레드 안전 위반으로 서버가 불안정해질 수 있습니다. 반드시 `runNext { }` 안에서 Bukkit API를 호출하세요.

**사용 시나리오**: 데이터베이스 쿼리, 파일 읽기/쓰기, HTTP 요청, 대용량 월드 데이터 처리.

### plugin.runWhile — 조건부 반복

람다가 `false`를 반환할 때까지 반복합니다. 별도의 `cancel()` 호출 없이 종료 조건을 람다 안에서 자연스럽게 표현할 수 있습니다.

```kotlin
// 보스가 살아있는 동안 매 5틱마다 파티클 효과
plugin.runWhile(period = 5L) {
    if (!boss.isValid || boss.isDead) {
        showDeathEffect(boss.location)
        return@runWhile false  // false 반환 → 태스크 자동 취소
    }
    pulseParticle(boss.location)
    true  // 계속 반복
}
```

```kotlin
// 플레이어가 경기장을 벗어날 때까지 위치 추적
plugin.runWhile(period = 10L) {
    if (player !in arena) {
        player.sendMessage("§c경기장을 벗어났습니다!")
        return@runWhile false
    }
    trackerEffect(player.location)
    true
}
```

**Bukkit 방식과 비교**: `BukkitRunnable` 내에서 조건 체크 후 `cancel()`을 호출하는 패턴보다 의도가 명확합니다. `false`를 반환하는 것이 곧 "종료"를 의미하므로 코드 가독성이 높습니다.

---

## 생명주기 자동 연동

Arc의 태스크는 플러그인이 비활성화(`onDisable`)될 때 자동으로 취소됩니다. `onDisable`에서 태스크를 수동으로 추적하고 취소하는 코드를 작성할 필요가 없습니다.

```kotlin
class MyPlugin : JavaPlugin() {
    override fun onEnable() {
        // 등록된 태스크는 플러그인 비활성화 시 자동 취소
        plugin.runRepeat(0L, 20L) { broadcastTips() }
        plugin.runRepeat(0L, 100L) { saveAllData() }
    }

    // onDisable에서 태스크 취소 코드 불필요
    override fun onDisable() {
        // 아무것도 안 해도 됩니다 — Arc가 처리합니다.
    }
}
```

---

## 장점과 단점

| 항목 | Arc 방식 | Bukkit 방식 |
|------|----------|-------------|
| 코드 길이 | 짧음 (람다) | 길음 (익명 클래스) |
| 취소 관리 | 자동 (플러그인 생명주기) | 수동 (onDisable 추적) |
| 가독성 | 높음 | 낮음 |
| 조건부 반복 | `runWhile` (return false) | cancel() 내부 호출 |
| 학습 비용 | Kotlin 람다 필요 | Java 익명 클래스 패턴 |

**단점**: Kotlin 람다와 클로저 캡처(capture) 개념에 익숙하지 않으면 초반에 혼란스러울 수 있습니다. 특히 람다 내에서 외부 변수를 변경하는 경우(`var` 캡처) 스레드 안전을 직접 보장해야 합니다.

---

## 전체 예시 — 카운트다운 보스 전투 시스템

```kotlin
fun startBossEvent(plugin: JavaPlugin, world: World) {
    val boss = world.spawnEntity<Wither>(bossSpawnLoc) { wither ->
        wither.customName(Component.text("§4[이벤트 보스] Dark Wither"))
        wither.health = 600.0
    }

    // 1. 카운트다운 안내 (비동기 DB에서 참가자 로드 후 알림)
    plugin.runAsync {
        val participants = database.getEventParticipants()
        runNext {
            participants.forEach { uuid ->
                world.getPlayerByUUID(uuid)?.sendMessage("§e보스 이벤트가 시작됩니다!")
            }
        }
    }

    // 2. 보스 생존 중 주기적 공격 패턴
    plugin.runWhile(period = 40L) {
        if (!boss.isValid || boss.isDead) {
            world.players.forEach { it.sendMessage("§a보스를 처치했습니다!") }
            return@runWhile false
        }
        val nearby = boss.location.nearbyPlayers(radius = 20.0)
        nearby.forEach { it.damage(3.0, boss) }
        true
    }

    // 3. 체력 50% 미만 — 분노 모드 시작 (30초 후 체크)
    plugin.runLater(600L) {
        if (boss.isValid && boss.health < 300.0) {
            boss.addPotionEffect(PotionEffect(PotionEffectType.SPEED, Int.MAX_VALUE, 2))
            world.players.forEach { it.sendMessage("§c보스가 분노했습니다!") }
        }
    }
}
```
