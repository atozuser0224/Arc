---
title: Developer API
nav_order: 3
---

# Layer 3 — Developer API

Arc Developer API는 플러그인 개발자를 위한 Kotlin-first 라이브러리다. Bukkit을 대체하지 않고 그 위에 쌓인다. `arc-api.jar`를 의존성에 추가하는 것만으로 사용 가능하며, Arc 서버 없이도 Paper 서버에서 동작한다.

---

## 스케줄러 · 태스크

Bukkit의 `BukkitRunnable`은 반복·취소·체인이 번거롭다. Arc는 플러그인 확장 함수로 간결한 스케줄러 DSL을 제공한다.

```kotlin
// 1초 후 한 번 실행
plugin.runLater(20L) {
    player.sendMessage("1초 경과!")
}

// 매 1초 반복 (5회 후 자동 취소)
var count = 0
plugin.runRepeat(delayTicks = 0, periodTicks = 20L) {
    count++
    player.sendMessage("카운트: $count")
    if (count >= 5) cancel()
}

// 비동기 실행 (파일 I/O, 네트워크 등)
plugin.runAsync {
    val data = heavyFileRead()
    plugin.runNext { player.sendMessage(data) }
}

// 조건부 반복 — false 반환 시 자동 취소
plugin.runWhile(periodTicks = 5L) {
    if (boss.isDead) {
        showVictoryEffect()
        return@runWhile false
    }
    pulseEffect(boss.location)
    true
}
```

---

## 아이템 빌더

`item()` DSL은 아이템 생성에 필요한 모든 작업을 하나의 블록으로 처리한다. Bukkit의 `ItemMeta` 캐스팅, null 처리, `setItemMeta()` 호출이 사라진다.

```kotlin
// Bukkit 방식 (기존)
val item = ItemStack(Material.DIAMOND_SWORD)
val meta = item.itemMeta as? org.bukkit.inventory.meta.Damageable ?: return
meta.displayName(Component.text("§bFrost Blade"))
meta.lore(listOf(Component.text("§7Deals frost damage")))
meta.addEnchant(Enchantment.SHARPNESS, 5, true)
meta.isUnbreakable = true
meta.setCustomModelData(1001)
item.itemMeta = meta

// Arc 방식
val sword = item(Material.DIAMOND_SWORD) {
    name(Component.text("§bFrost Blade"))
    lore(Component.text("§7Deals frost damage"), Component.text("§7+20% speed"))
    enchant(Enchantment.SHARPNESS, 5)
    unbreakable()
    modelData(1001)
    glowing()           // 인챈트 없이 빛 효과
    flags(ItemFlag.HIDE_ATTRIBUTES)
}
```

`ItemBehavior`는 특정 아이템 타입에 행동을 바인딩한다. 플러그인 비활성화 시 자동 해제된다.

```kotlin
ItemBehavior.on(plugin, Material.BLAZE_ROD) {
    rightClick { player, item ->
        player.sendMessage("마법봉 발동!")
        castFireball(player)
    }
}
```

`ItemAuthenticator`는 서버 서명으로 외부에서 주입된 아이템을 감지한다.

```kotlin
// 아이템에 서버 서명 삽입
val signedItem = ItemAuthenticator.sign(plugin, item)

// 검증 — 위변조 or 외부 /give 아이템이면 false
if (!ItemAuthenticator.verify(signedItem)) {
    player.sendMessage("허가되지 않은 아이템입니다.")
    player.inventory.remove(signedItem)
}
```

---

## GUI 시스템

인벤토리 GUI 개발의 모든 보일러플레이트를 제거한다. `InventoryHolder`, `Listener`, `HandlerList` 등록을 직접 다룰 필요가 없다.

```kotlin
// 단일 페이지 메뉴
val shop = plugin.menu(Component.text("§eShop"), rows = 3) {
    item(13, item(Material.DIAMOND) {
        name(Component.text("§bDiamond — $100"))
    }) { event ->
        val buyer = event.whoClicked as? Player ?: return@item
        if (ArcGlobalEconomy.withdraw(buyer.uniqueId, 100L)) {
            buyer.inventory.addItem(ItemStack(Material.DIAMOND))
            buyer.sendMessage("구매 완료!")
        } else {
            buyer.sendMessage("잔액 부족.")
        }
    }

    onClose { viewer ->
        viewer.sendMessage("상점을 닫았습니다.")
    }
}
shop.open(player)
```

```kotlin
// 페이지네이션 — 아이템이 많을 때 자동으로 이전/다음 버튼 생성
val items: List<ItemStack> = getShopItems()  // 예: 100개
val pagedShop = plugin.paginatedMenu(Component.text("§eShop"), rows = 6, items) { itemStack, slot ->
    slot.item = itemStack
    slot.onClick = { event ->
        purchase(event.whoClicked as Player, itemStack)
    }
}
pagedShop.open(player)
```

```kotlin
// ChatInput — GUI에서 수량 입력
fun openWithdrawMenu(player: Player) {
    ChatInput.await(player, prompt = "§e출금할 금액을 입력하세요:") { input ->
        val amount = input.toLongOrNull()
        if (amount == null || amount <= 0) {
            player.sendMessage("올바른 금액을 입력하세요.")
            return@await
        }
        if (ArcGlobalEconomy.withdraw(player.uniqueId, amount)) {
            player.sendMessage("§a${amount}코인 출금 완료.")
        } else {
            player.sendMessage("§c잔액이 부족합니다.")
        }
    }
}
```

---

## 엔티티 · AI

```kotlin
// 타입 안전 스폰 — 캐스팅 없음
val boss = world.spawnEntity<Zombie>(spawnLoc) { zombie ->
    zombie.customName(Component.text("§4[BOSS] Dark King"))
    zombie.health = 200.0
    zombie.equipment?.helmet = item(Material.NETHERITE_HELMET) { unbreakable() }
}

// 근처 엔티티 필터링 — filterIsInstance 없음
val nearbyPlayers: List<Player> = location.nearbyPlayers(radius = 16.0)
val nearbyMonsters: List<Monster> = location.nearbyOfType<Monster>(radius = 32.0)
val nearest: Zombie? = location.nearestOfType<Zombie>(radius = 10.0)

// 체력 초기화
boss.healFully()
```

---

## 플레이어 데이터 · PDC

타입 안전 PDC — 컴파일 시점에 타입이 검증된다. 런타임 `ClassCastException`이 사라진다.

```kotlin
// Bukkit 방식 (기존) — 타입 토큰 직접 지정, null 처리 필수
val key = NamespacedKey(plugin, "coins")
val current = player.persistentDataContainer.get(key, PersistentDataType.INTEGER) ?: 0
player.persistentDataContainer.set(key, PersistentDataType.INTEGER, current + 100)

// Arc 방식 — 타입 추론, 기본값 내장
val coinsKey = plugin.key("coins")
val current = player.getInt(coinsKey, default = 0)
player.setInt(coinsKey, current + 100)

// 지원 타입
player.setString(key, "value")
player.getString(key, default = "")
player.setLong(key, 9999L)
player.getLong(key, default = 0L)
player.setBoolean(key, true)        // 내부적으로 Byte로 저장 (PDC는 Boolean 미지원)
player.getBoolean(key, default = false)
player.hasKey(key)
player.removeKey(key)
```

`PdcSchema`는 스키마 버전 관리와 마이그레이션을 지원한다.

```kotlin
val schema = PdcSchema.define(plugin) {
    version(2)
    field("level", DataType.INTEGER, default = 1)
    field("xp", DataType.LONG, default = 0L)

    // v1 → v2 마이그레이션: xp 단위를 100배로 변환
    migrate(from = 1, to = 2) { data ->
        data["xp"] = ((data["xp"] as? Long) ?: 0L) * 100L
    }
}

val level = schema.get(player, "level") as Int
schema.set(player, "xp", schema.get(player, "xp") as Long + 500L)
```

---

## 커맨드 프레임워크

서브커맨드, 아규먼트, 탭 완성, 권한 검사를 하나의 선언적 블록으로 작성한다. `plugin.yml` 등록 + `CommandExecutor` + `TabCompleter` 3개 파일이 하나로 합쳐진다.

```kotlin
plugin.registerCommand("shop") {
    description("상점 명령어")
    permission("myplugin.shop")
    playerOnly()

    subcommand("buy") {
        description("아이템 구매")
        permission("myplugin.shop.buy")
        execute { ctx ->
            val itemName = ctx.arg(0) ?: run {
                ctx.sender.sendMessage("사용법: /shop buy <아이템>"); return@execute
            }
            buy(ctx.player, itemName)
        }
        complete { _ -> listOf("sword", "bow", "armor", "helmet") }
    }

    subcommand("sell") {
        description("아이템 판매")
        execute { ctx ->
            val heldItem = ctx.player.inventory.itemInMainHand
            sell(ctx.player, heldItem)
        }
    }

    subcommand("balance") {
        execute { ctx ->
            val bal = ArcGlobalEconomy.getBalance(ctx.player.uniqueId)
            ctx.sender.sendMessage("잔액: §a${bal}코인")
        }
    }
}
```

---

## 월드 · 청크 유틸리티

```kotlin
// 블록 영역 스냅샷 — 미니게임 맵 리셋에 유용
val snapshot = WorldSnapshot.capture(corner1, corner2)
// ... 게임 진행 ...
WorldSnapshot.restore(snapshot, corner1)  // 재시작 없이 복원

// 레이트레이스
val hit = RayTrace.cast(player.eyeLocation, player.eyeDirection, maxDistance = 10.0)
hit?.let { result ->
    when {
        result.hitBlock != null -> result.hitBlock.type = Material.GLASS
        result.hitEntity != null -> (result.hitEntity as? LivingEntity)?.damage(5.0)
    }
}

// 청크 작업
WorldChunk.forceLoad(chunk)
val entityCount = chunk.entities.size
```

---

## 유틸리티 패키지

Arc API는 플러그인 개발에서 자주 필요한 유틸리티를 160개 이상의 클래스로 제공한다.

```kotlin
// 텍스트 포맷 — & 컬러 코드 + 플레이스홀더
val msg = Format.colorize("&a안녕하세요, &b{name}님!", "name" to player.name)
player.sendMessage(msg)

// 시간 DSL
val cooldown = 2.hours + 30.minutes  // Duration
Time.format(cooldown)                // "2시간 30분"
Time.toTicks(30.seconds)             // 600L

// 영역(Cuboid)
val arena = Cuboid(corner1, corner2)
if (player.location in arena) {
    player.sendMessage("경기장 내에 있습니다.")
}
arena.forEachBlock { block ->
    if (block.type == Material.TNT) block.type = Material.AIR
}

// 스플라인 경로 — 발사체·연출 이동에 활용
val path = Spline.catmullRom(listOf(p1, p2, p3, p4))
for (t in 0..100) {
    val pos = path.at(t / 100.0)
    world.spawnParticle(Particle.FLAME, pos, 1)
}

// 직렬화 — ItemStack ↔ Base64
val encoded: String = ItemSerializer.toBase64(itemStack)
val decoded: ItemStack = ItemSerializer.fromBase64(encoded)
```

---

## arc-test — 서버 없는 단위 테스트

Bukkit 플러그인 테스트의 최대 장벽은 실제 서버 없이는 아무것도 실행이 안 된다는 점이다. `arc-test`는 Arc API를 서버 없이 JVM에서 실행할 수 있는 테스트 환경을 제공한다.

```kotlin
class ShopTest {
    private val console = VirtualConsole()

    @Test
    fun `item builder creates correct item`() {
        val sword = item(Material.DIAMOND_SWORD) {
            name(Component.text("Test Sword"))
            enchant(Enchantment.SHARPNESS, 5)
            unbreakable()
        }
        assertEquals(Component.text("Test Sword"), sword.itemMeta?.displayName())
        assertTrue(sword.itemMeta?.isUnbreakable == true)
        assertTrue(sword.containsEnchantment(Enchantment.SHARPNESS))
    }

    @Test
    fun `PDC read-write roundtrip`() {
        val player = console.createFakePlayer("TestPlayer")
        val key = console.plugin.key("score")
        player.setInt(key, 42)
        assertEquals(42, player.getInt(key))
    }

    @Test
    fun `packets are captured`() {
        val capture = PacketCapture.start()
        player.sendTitle("Title", "Subtitle", 10, 70, 20)
        val titlePacket = capture.find<ClientboundSetTitlesTextPacket>()
        assertNotNull(titlePacket)
        capture.stop()
    }
}
```

CI 파이프라인에서 서버를 띄우지 않고도 아이템 로직, GUI 행동, PDC 읽기/쓰기, 커맨드 파싱을 단위 테스트로 검증할 수 있다. 피드백 루프를 수 분에서 수 초로 단축한다.
