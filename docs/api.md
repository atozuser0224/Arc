---
title: Developer API
nav_order: 3
---

# Layer 3 — Developer API

Bukkit을 대체하지 않고 보완하는 Kotlin-first API. `arc-api` 모듈.

---

## 스케줄러·태스크

```kotlin
// Kotlin DSL 스케줄러
Scheduler.runLater(20L) { /* 1초 후 실행 */ }
Scheduler.runTimer(0L, 20L) { /* 매 1초 반복 */ }
Scheduler.runAsync { /* 비동기 */ }

// Pipeline: 순차 태스크 체인
Pipeline.create(plugin)
    .then { loadData() }
    .thenSync { renderResult() }
    .start()

// ArcAsync (Ops Suite 전용)
ArcAsync.runBlockingIO { heavyIoWork() }
    .thenSync { result -> sender.sendMessage(result) }
    .exceptionallySync { e -> sender.sendMessage("Error: ${e.message}") }
```

---

## 아이템

```kotlin
val sword = itemBuilder(Material.DIAMOND_SWORD) {
    name("§bFrost Blade")
    lore("§7Deals frost damage", "§7+20% speed")
    enchant(Enchantment.SHARPNESS, 5)
    unbreakable()
    customModelData(1001)
}

// 아이템 행동 바인딩
ItemBehavior.on(plugin, Material.BLAZE_ROD) {
    rightClick { player, _ -> player.sendMessage("Activated!") }
}

// 서버 서명 기반 위변조 감지
val verified = ItemAuthenticator.verify(item)
```

---

## GUI

```kotlin
// 인벤토리 메뉴
val menu = Menu.create(plugin, "Shop", 3) {
    slot(13) {
        item = itemBuilder(Material.DIAMOND) { name("§bDiamond — $100") }
        onClick { player, _ -> purchase(player) }
    }
}
menu.open(player)

// 페이지네이션
PaginatedMenu.create(plugin, "Items", items) { item, slot ->
    slot.item = item.toItemStack()
}

// 채팅 입력 흐름
ChatInput.await(player, "Enter amount:") { input ->
    withdraw(player, input.toIntOrNull() ?: 0)
}
```

---

## 엔티티·AI

```kotlin
// 스폰 DSL
val zombie = Entities.spawn<Zombie>(location) {
    customName("§cBoss")
    health(200.0)
    equipment { helmet = itemBuilder(Material.DIAMOND_HELMET) {} }
}

// AI 목표 등록
MobGoalRegistry.register(plugin, Zombie::class.java) { mob ->
    object : Goal<Zombie>(mob) {
        override fun canStart() = mob.target != null
        override fun tick() { /* custom behavior */ }
    }
}

// AI 일시 중단
MobAi.disable(zombie)
```

---

## 효과·입자·사운드

```kotlin
// 가상 포션 효과 (플러그인 스코프)
CustomEffectEngine.apply(player, CustomEffect(
    id = NamespacedKey(plugin, "frost"),
    duration = 200,
    amplifier = 1,
    policy = MergePolicy.KEEP_STRONGER,
    onTick = { p -> p.velocity = p.velocity.multiply(0.9) }
))

// 입자
Particles.spawn(location, Particle.FLAME) {
    count(20)
    offset(0.3, 0.3, 0.3)
    speed(0.05)
}

// 사운드
Sounds.play(player, Sound.ENTITY_ENDER_DRAGON_GROWL) {
    volume(1.0f)
    pitch(0.8f)
}
```

---

## 플레이어 데이터

```kotlin
// 타입 안전 PDC 스키마
val COINS = PlayerStore.key<Int>(plugin, "coins")

val coins = COINS.get(player) ?: 0
COINS.set(player, coins + 100)

// 크로스서버 쿨다운
Cooldowns.set(player, "ability", 60L) // 60초
val remaining = Cooldowns.remaining(player, "ability")
```

---

## 커맨드

```kotlin
Commands.register(plugin, "shop") {
    description("Open the shop")
    permission("myplugin.shop")

    subcommand("buy") {
        argument<String>("item")
        execute { sender, args ->
            val item = args[0]
            buy(sender as Player, item)
        }
        tabComplete { _ -> listOf("sword", "bow", "armor") }
    }
}
```

---

## 월드·청크

```kotlin
// 블록 영역 스냅샷
val snapshot = WorldSnapshot.capture(location1, location2)
WorldSnapshot.restore(snapshot, location1)

// 청크 강제 로드
WorldChunk.forceLoad(chunk)

// RayTrace
val hit = RayTrace.cast(player.eyeLocation, player.eyeDirection, 10.0)
```

---

## 패킷·채널

```kotlin
// Netty 레벨 패킷 후킹
PacketInterceptor.on<ClientboundSetEntityDataPacket>(plugin) { packet, player ->
    // intercept or modify
}

// 클라이언트 모드 패킷 채널
ModChannel.register(plugin, "myplugin:data") { player, buf ->
    val value = buf.readInt()
    handleClientData(player, value)
}
```

---

## PDC · 직렬화

```kotlin
// 스키마 정의 (마이그레이션·검증 포함)
val schema = PdcSchema.define(plugin) {
    field("level", DataType.INTEGER, default = 1)
    field("xp", DataType.LONG, default = 0L)
    migrate(from = 1, to = 2) { data ->
        data["xp"] = (data["xp"] as Long) * 100
    }
}

val level = schema.get(entity, "level") as Int
```

---

## 유틸리티

```kotlin
// 텍스트 포맷
Format.colorize("&aHello &b{name}", "name" to player.name)

// 시간 DSL
val duration = 2.hours + 30.minutes
Time.format(duration) // "2h 30m"

// 영역
val region = Cuboid(location1, location2)
region.forEachBlock { block -> block.type = Material.AIR }

// 스플라인 (수학)
val path = Spline.catmullRom(points)
val pos = path.at(t = 0.5)
```

---

## arc-test

서버 없이 arc-api 플러그인을 단위 테스트한다.

```kotlin
class MyPluginTest {
    @Test
    fun `item builder creates correct item`() {
        val console = VirtualConsole()
        val item = itemBuilder(Material.DIAMOND_SWORD) {
            name("Test")
        }
        assertEquals("Test", item.itemMeta?.displayName)
    }
}
```

제공 컴포넌트: `PacketCapture`, `PDC assertions`, `VirtualConsole`.
