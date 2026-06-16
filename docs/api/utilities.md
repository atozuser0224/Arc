---
title: 유틸리티 패키지
parent: Developer API
nav_order: 8
---

# 유틸리티 패키지

Arc API는 플러그인을 만들다 보면 늘 다시 짜게 되는 유틸리티를 모아 제공한다. 텍스트 포맷과 시간 DSL, 영역(Cuboid), 스플라인 경로(Spline), 아이템 직렬화(ItemSerializer)로 매번 손수 쓰던 헬퍼 코드를 줄여 준다.

---

## 텍스트 포맷 (Format)

### & 컬러 코드 + 플레이스홀더

`Format.colorize()`는 `&` 코드로 쓴 색상 코드를 Minecraft 색상으로 바꾸고, 플레이스홀더도 함께 치환한다.

```kotlin
// 단순 색상 코드 변환
val msg = Format.colorize("&a환영합니다, &b{name}님!", "name" to player.name)
player.sendMessage(msg)
// 결과: "환영합니다, §aPlayerName§b님!"

// 여러 플레이스홀더
val announcement = Format.colorize(
    "&e[공지] &f{player}님이 &c{level}레벨&f을 달성했습니다!",
    "player" to player.name,
    "level" to level.toString()
)
Bukkit.broadcastMessage(announcement)
```

### MiniMessage 지원

Adventure API의 MiniMessage 형식도 쓸 수 있다.

```kotlin
// MiniMessage 형식
val component = Format.miniMessage("<gradient:red:gold>Epic Boss Defeated!</gradient>")
player.sendMessage(component)

// & 코드와 MiniMessage를 혼용
val mixed = Format.colorize("&e&l[이벤트] ")
    .append(Format.miniMessage("<rainbow>축하합니다!</rainbow>"))
player.sendMessage(mixed)
```

### 서버 공지 포맷

```kotlin
// 제목 + 부제목 포맷
fun broadcastTitle(title: String, subtitle: String) {
    Bukkit.getOnlinePlayers().forEach { p ->
        p.sendTitle(
            Format.colorize(title),
            Format.colorize(subtitle),
            fadeIn = 10, stay = 70, fadeOut = 20
        )
    }
}

broadcastTitle("&c&l보스 등장!", "&7어둠의 군주가 나타났습니다.")
```

---

## 시간 DSL

Kotlin 확장 프로퍼티로 시간 단위를 자연스럽게 적는다.

### Duration 생성

```kotlin
// 직관적인 시간 표현
val cooldown = 30.seconds
val sessionTimeout = 2.hours + 30.minutes
val tickDelay = 5.ticks        // 5 ticks = 0.25초

// 복합 표현
val respawnDelay = 1.minutes + 30.seconds  // 90초

// Duration 산술
val remaining = sessionTimeout - 45.minutes  // 1시간 45분
```

### 시간 포맷

`Time.format()`은 Duration을 한국어 문구로 바꿔 준다.

```kotlin
Time.format(2.hours + 30.minutes)  // "2시간 30분"
Time.format(90.seconds)            // "1분 30초"
Time.format(3600.seconds)          // "1시간"
Time.format(45.seconds)            // "45초"
Time.format(500.milliseconds)      // "0.5초"

// 쿨다운 표시 예시
val remaining = cooldownEnd - System.currentTimeMillis()
if (remaining > 0) {
    player.sendMessage("§c쿨다운: §e${Time.format(remaining.milliseconds)}")
} else {
    player.sendMessage("§a사용 가능합니다.")
}
```

### 틱 변환

Bukkit 스케줄러는 틱 단위로 움직인다. `Time.toTicks()`로 Duration을 틱으로 바꾸면 된다.

```kotlin
val ticks: Long = Time.toTicks(30.seconds)  // 600L
plugin.runLater(Time.toTicks(5.seconds)) {
    player.sendMessage("5초가 지났습니다.")
}

plugin.runRepeat(0L, Time.toTicks(1.minutes)) {
    saveAllPlayerData()
}
```

---

## 영역 (Cuboid)

`Cuboid`는 두 모서리 위치로 잡는 직육면체 영역이다. 특정 위치가 안에 들어오는지 확인하거나, 블록을 순회하거나, 경계 정보를 가져올 수 있다.

### 기본 사용법

```kotlin
val arena = Cuboid(
    Location(world, -50.0, 60.0, -50.0),
    Location(world, 50.0, 120.0, 50.0)
)

// in 연산자로 포함 여부 확인
if (player.location in arena) {
    player.sendMessage("§a경기장 안에 있습니다.")
} else {
    player.sendMessage("§c경기장 밖에 있습니다.")
}
```

### 블록 순회

```kotlin
// 경기장 내 모든 TNT 제거
arena.forEachBlock { block ->
    if (block.type == Material.TNT) {
        block.type = Material.AIR
    }
}

// 경기장 바닥 유리로 변경
arena.forEachBlock { block ->
    if (block.y == arena.minY) {
        block.type = Material.GLASS
    }
}
```

### 경계 정보

```kotlin
arena.minX   // 최소 X
arena.maxX   // 최대 X
arena.minY   // 최소 Y
arena.maxY   // 최대 Y
arena.minZ   // 최소 Z
arena.maxZ   // 최대 Z
arena.center // 중앙 Location
arena.volume // 총 블록 수 (Int)

// 영역 크기
val sizeX = arena.maxX - arena.minX + 1
val sizeY = arena.maxY - arena.minY + 1
val sizeZ = arena.maxZ - arena.minZ + 1
```

### 사용 시나리오 — 경기장 경계 이탈 감지

```kotlin
val arena = Cuboid(corner1, corner2)

plugin.runRepeat(0L, 10L) {  // 0.5초마다 체크
    Bukkit.getOnlinePlayers()
        .filter { it.world == world && it !in arena }
        .forEach { p ->
            p.sendMessage("§c경기장을 벗어났습니다! 텔레포트됩니다.")
            p.teleport(arena.center)
        }
}
```

---

## 스플라인 경로 (Spline)

`Spline`은 제어점을 부드럽게 잇는 곡선 경로를 만든다. 발사체 이동이나 파티클 효과 경로, 카메라 이동 경로에 쓰면 좋다.

### 카트뮬-롬 스플라인

카트뮬-롬(Catmull-Rom) 스플라인은 제어점을 모두 지나는 부드러운 곡선이다.

```kotlin
// 4개 이상의 제어점이 필요
val path = Spline.catmullRom(listOf(
    Location(world, 0.0, 70.0, 0.0),
    Location(world, 10.0, 80.0, 5.0),
    Location(world, 20.0, 75.0, -5.0),
    Location(world, 30.0, 85.0, 0.0),
    Location(world, 40.0, 70.0, 10.0)
))

// 파라미터 t (0.0 ~ 1.0)로 위치 얻기
val midPoint = path.at(0.5)  // 경로 중간 지점
```

### 파티클 경로 효과

```kotlin
// 주문 발사 효과 — 스플라인 경로를 따라 파티클 발사
fun castSpellEffect(from: Location, to: Location) {
    // 중간 제어점을 곡선으로 설정
    val mid1 = from.clone().add(5.0, 10.0, 0.0)
    val mid2 = to.clone().add(-5.0, 10.0, 0.0)

    val path = Spline.catmullRom(listOf(
        from.clone().subtract(1.0, 0.0, 0.0),  // 카트뮬-롬은 첫/끝 점이 접선
        from,
        mid1,
        mid2,
        to,
        to.clone().add(1.0, 0.0, 0.0)
    ))

    var step = 0
    plugin.runRepeat(0L, 1L) {  // 매 틱
        if (step > 40) { cancel(); return@runRepeat }
        val t = step / 40.0
        val pos = path.at(t)
        pos.world?.spawnParticle(Particle.SPELL_WITCH, pos, 2, 0.1, 0.1, 0.1, 0.0)
        step++
    }
}
```

### 발사체 유도 경로

```kotlin
// 유도 미사일 — 타겟 방향으로 스플라인 경로 생성
fun guidedMissile(plugin: JavaPlugin, from: Location, target: LivingEntity) {
    val controlPoints = buildList {
        add(from.clone().subtract(from.direction))  // 접선 시작
        add(from)
        add(from.clone().add(0.0, 5.0, 0.0))        // 위쪽으로 호 그리기
        add(target.location.clone().add(0.0, 3.0, 0.0))
        add(target.location)
        add(target.location.clone().subtract(0.0, 1.0, 0.0))  // 접선 끝
    }

    val path = Spline.catmullRom(controlPoints)
    val armorStand = from.world!!.spawnEntity<ArmorStand>(from) { s ->
        s.isVisible = false
        s.isGravity = false
    }

    var step = 0
    plugin.runRepeat(0L, 1L) {
        if (step > 60 || !armorStand.isValid) {
            armorStand.remove()
            target.damage(20.0)
            from.world!!.spawnParticle(Particle.EXPLOSION_HUGE, target.location, 1)
            cancel()
            return@runRepeat
        }
        val pos = path.at(step / 60.0)
        armorStand.teleport(pos)
        pos.world?.spawnParticle(Particle.FLAME, pos, 3, 0.1, 0.1, 0.1, 0.0)
        step++
    }
}
```

---

## 직렬화 (ItemSerializer)

`ItemSerializer`는 `ItemStack`을 Base64 문자열로 직렬화하고 다시 되돌린다. Redis나 데이터베이스, 파일, 서버 간 전송에 쓰면 된다.

### 기본 사용법

```kotlin
// ItemStack → Base64 String
val encoded: String = ItemSerializer.toBase64(itemStack)

// Base64 String → ItemStack
val decoded: ItemStack = ItemSerializer.fromBase64(encoded)

// 인벤토리 전체 직렬화
val inventoryEncoded: String = ItemSerializer.inventoryToBase64(player.inventory)
val restoredInv: Array<ItemStack?> = ItemSerializer.inventoryFromBase64(inventoryEncoded)
```

### 데이터베이스 저장

```kotlin
// MySQL에 아이템 저장
suspend fun savePlayerItems(player: Player) = withContext(Dispatchers.IO) {
    val encoded = ItemSerializer.inventoryToBase64(player.inventory)
    database.execute(
        "INSERT INTO player_items (uuid, inventory) VALUES (?, ?) ON DUPLICATE KEY UPDATE inventory = ?",
        player.uniqueId.toString(), encoded, encoded
    )
}

// MySQL에서 로드
suspend fun loadPlayerItems(player: Player) = withContext(Dispatchers.IO) {
    val row = database.query("SELECT inventory FROM player_items WHERE uuid = ?", player.uniqueId.toString())
    val encoded = row?.getString("inventory") ?: return@withContext
    val items = ItemSerializer.inventoryFromBase64(encoded)
    withContext(Dispatchers.Main) {  // 메인 스레드로 복귀
        items.forEachIndexed { slot, item ->
            if (item != null) player.inventory.setItem(slot, item)
        }
    }
}
```

### Redis 저장 (서버 간 전송)

```kotlin
// 서버 A → Redis → 서버 B 아이템 전송
fun transferPlayerToServer(player: Player, targetServer: String) {
    val inventoryData = ItemSerializer.inventoryToBase64(player.inventory)
    redis.set("transfer:${player.uniqueId}", inventoryData, expireSeconds = 60)
    // BungeeCord 플러그인 채널로 서버 전환
    sendToServer(player, targetServer)
}

// 서버 B에서 수신
@EventHandler
fun onPlayerJoin(event: PlayerJoinEvent) {
    val data = redis.get("transfer:${event.player.uniqueId}") ?: return
    redis.del("transfer:${event.player.uniqueId}")
    val items = ItemSerializer.inventoryFromBase64(data)
    items.forEachIndexed { slot, item ->
        if (item != null) event.player.inventory.setItem(slot, item)
    }
}
```

### 성능 고려사항

- Base64 인코딩은 CPU 비용이 거의 들지 않는다.
- 직렬화된 문자열 크기: 인챈트가 없는 단순 아이템은 약 100~300B, 인챈트와 PDC가 많은 아이템은 1~5KB다.
- 인벤토리 전체(36슬롯)는 약 10~50KB다.
- 매 틱 직렬화하는 방식은 피하고, quit·death·transfer 같은 이벤트 시점에만 저장하자.

---

## 어트리뷰트 모디파이어 (AttributeModifier 헬퍼)

Paper 1.21+의 `AttributeModifier` API는 `NamespacedKey` 기반으로 바뀌었다. 같은 키로 모디파이어를 두 번 추가하면 중복이 쌓여 속성 값이 예상보다 커지는 문제가 생기기 쉽다. Arc의 헬퍼는 이 중복을 자동으로 제거한다.

### 기본 사용법

```kotlin
// 플레이어 이동 속도를 +20% 증가 (키 기반 누적 방지)
val key = NamespacedKey(plugin, "speed_boost")
player.applyModifier(Attribute.MOVEMENT_SPEED, key, 0.2, AttributeModifier.Operation.ADD_SCALAR)

// 같은 키로 다시 호출하면 기존 모디파이어를 먼저 제거 후 새 값 적용
player.applyModifier(Attribute.MOVEMENT_SPEED, key, 0.4, AttributeModifier.Operation.ADD_SCALAR)

// 특정 키 모디파이어 제거
player.removeModifier(Attribute.MOVEMENT_SPEED, key)

// 해당 키 모디파이어가 있는지 확인
val hasBoost: Boolean = player.hasModifier(Attribute.MOVEMENT_SPEED, key)

// 해당 키의 현재 값 조회
val amount: Double? = player.modifierValue(Attribute.MOVEMENT_SPEED, key)
```

### applyModifier — 누적 없는 적용

`@JvmOverloads`가 붙어 있어 Java에서도 `operation` 인수를 생략할 수 있다.

```kotlin
// operation 생략 시 기본값: ADD_NUMBER
player.applyModifier(Attribute.MAX_HEALTH, NamespacedKey(plugin, "bonus_hp"), 4.0)

// operation 명시
player.applyModifier(
    attribute = Attribute.MOVEMENT_SPEED,
    key = NamespacedKey(plugin, "sprint_bonus"),
    amount = 0.1,
    operation = AttributeModifier.Operation.ADD_NUMBER  // 기본값
)
```

### Java

```java
NamespacedKey key = new NamespacedKey(plugin, "attack_boost");

// 공격력 +2.0 추가 (ADD_NUMBER)
AttributeUtil.applyModifier(player, Attribute.ATTACK_DAMAGE, key, 2.0);

// 제거
AttributeUtil.removeModifier(player, Attribute.ATTACK_DAMAGE, key);

// 확인
boolean has = AttributeUtil.hasModifier(player, Attribute.ATTACK_DAMAGE, key);

// 값 조회 (없으면 null)
Double value = AttributeUtil.modifierValue(player, Attribute.ATTACK_DAMAGE, key);
```

### 전체 예시 — 장비 세트 보너스

```kotlin
class SetBonusPlugin : JavaPlugin() {

    private val SET_SPEED_KEY = NamespacedKey(this, "set_speed")
    private val SET_HP_KEY = NamespacedKey(this, "set_hp")

    override fun onEnable() {
        listen<PlayerItemHeldEvent> { e -> updateSetBonus(e.player) }
        listen<PlayerArmorChangeEvent> { e -> updateSetBonus(e.player) }
        listen<PlayerJoinEvent> { e -> updateSetBonus(e.player) }
    }

    private fun updateSetBonus(player: Player) {
        val armorCount = listOf(
            player.inventory.helmet,
            player.inventory.chestplate,
            player.inventory.leggings,
            player.inventory.boots
        ).count { it?.type?.name?.startsWith("NETHERITE") == true }

        when {
            armorCount >= 4 -> {
                // 풀셋: 이동 속도 +15%, 최대 체력 +4
                player.applyModifier(Attribute.MOVEMENT_SPEED, SET_SPEED_KEY, 0.15, AttributeModifier.Operation.ADD_SCALAR)
                player.applyModifier(Attribute.MAX_HEALTH, SET_HP_KEY, 4.0)
                player.sendActionBar(Component.text("§d네더라이트 풀셋 보너스 활성"))
            }
            armorCount >= 2 -> {
                // 절반 세트: 이동 속도 +7%만
                player.applyModifier(Attribute.MOVEMENT_SPEED, SET_SPEED_KEY, 0.07, AttributeModifier.Operation.ADD_SCALAR)
                player.removeModifier(Attribute.MAX_HEALTH, SET_HP_KEY)
            }
            else -> {
                player.removeModifier(Attribute.MOVEMENT_SPEED, SET_SPEED_KEY)
                player.removeModifier(Attribute.MAX_HEALTH, SET_HP_KEY)
            }
        }
    }
}
```

### Operation 종류

| 값 | 계산 방식 | 예시 |
|----|----------|------|
| `ADD_NUMBER` | `기본값 + amount` | 체력 +4 (절댓값 추가) |
| `ADD_SCALAR` | `기본값 × (1 + amount)` | 속도 +20% (비율 추가) |
| `MULTIPLY_SCALAR_1` | `기본값 × amount` | 속도 × 1.5 (절대 배율) |

### 이점과 주의사항

**이점**
- 같은 키로 여러 번 호출해도 중복 누적 없이 최신 값 하나만 유지
- `hasModifier` · `modifierValue`로 현재 상태를 명확하게 조회 가능
- Java `@JvmOverloads`로 `operation` 기본값 생략 가능

**주의사항**
- `AttributeModifier`는 플레이어 재접속 후 일부 속성이 초기화될 수 있다. 영구 보너스는 `PlayerJoinEvent`에서 다시 적용해야 한다.
- `ADD_SCALAR`는 기본값 기준 배율이라 여러 개를 쌓으면 합산된다. 장비 세트 보너스처럼 하나만 유지해야 하는 경우에는 이 헬퍼의 누적 방지가 특히 중요하다.

---

## 전체 유틸리티 조합 예시

```kotlin
// 이벤트 안내 시스템 — Format + Time + Cuboid 조합
class EventNotifier(val plugin: JavaPlugin) {
    val eventArena = Cuboid(
        Location(world, -100.0, 60.0, -100.0),
        Location(world, 100.0, 150.0, 100.0)
    )

    fun startCountdown(durationSeconds: Int) {
        var remaining = durationSeconds
        plugin.runRepeat(0L, Time.toTicks(1.seconds)) {
            if (remaining <= 0) {
                cancel()
                val msg = Format.colorize("&a&l이벤트 시작!")
                Bukkit.broadcastMessage(msg)
                return@runRepeat
            }

            // 경기장 내 플레이어에게만 안내
            val playersInArena = Bukkit.getOnlinePlayers()
                .filter { it.location in eventArena }

            val countdown = Format.colorize(
                "&e이벤트까지 &c{time} &e남았습니다. ({count}명 대기)",
                "time" to Time.format(remaining.seconds),
                "count" to playersInArena.size.toString()
            )
            playersInArena.forEach { it.sendMessage(countdown) }
            remaining--
        }
    }
}
```
