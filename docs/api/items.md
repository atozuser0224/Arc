---
title: 아이템 시스템
parent: Developer API
nav_order: 2
---

# 아이템 시스템

Arc API의 아이템 시스템은 세 가지 핵심 기능으로 구성됩니다. `item { }` DSL로 아이템을 선언적으로 생성하고, `ItemBehavior`로 아이템에 동작을 바인딩하며, `ItemAuthenticator`로 위변조를 감지합니다.

---

## 아이템 빌더 (item DSL)

### Bukkit 방식의 문제

Bukkit에서 커스텀 아이템을 만들려면 다음과 같은 보일러플레이트 코드가 필요합니다.

```kotlin
// Bukkit 방식 — 6단계, 캐스팅, null 처리
val item = ItemStack(Material.DIAMOND_SWORD)
val meta = item.itemMeta ?: return  // null 체크 필수
meta.displayName(Component.text("§bFrost Blade"))
meta.lore(listOf(
    Component.text("§7서리 피해를 입힙니다"),
    Component.text("§7이동 속도 +20%")
))
meta.addEnchant(Enchantment.SHARPNESS, 5, true)
meta.isUnbreakable = true
meta.setCustomModelData(1001)
item.itemMeta = meta  // setItemMeta 호출 필수
```

특수 아이템의 경우 `ItemMeta` 캐스팅까지 추가됩니다.

```kotlin
// LeatherArmor 색상 설정 예
val meta = item.itemMeta as? LeatherArmorMeta ?: return
meta.setColor(Color.fromRGB(0x1A, 0x2B, 0x3C))
item.itemMeta = meta
```

### Arc 방식

`item()` 함수는 이 모든 과정을 단일 블록으로 처리합니다.

```kotlin
// Arc 방식 — 선언적, 한 블록
val sword = item(Material.DIAMOND_SWORD) {
    name("§bFrost Blade")                         // 문자열 컬러 코드 지원
    lore(
        "§7서리 피해를 입힙니다",
        "§7이동 속도 +20%"
    )
    enchant(Enchantment.SHARPNESS, 5)
    unbreakable()
    modelData(1001)
    glowing()                                      // 인챈트 없이 빛 효과만 추가
    flags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
}
```

Component API(MiniMessage)를 선호한다면 그대로 사용할 수 있습니다.

```kotlin
val sword = item(Material.DIAMOND_SWORD) {
    name(Component.text("Frost Blade").color(NamedTextColor.AQUA))
    lore(
        Component.text("서리 피해를 입힙니다").color(NamedTextColor.GRAY),
        Component.text("이동 속도 +20%").color(NamedTextColor.GRAY)
    )
    enchant(Enchantment.SHARPNESS, 5)
    unbreakable()
}
```

### 지원 설정 항목

| 메서드 | 설명 | 비고 |
|--------|------|------|
| `name(text)` | 표시 이름 | `String` 또는 `Component` |
| `lore(vararg lines)` | 설명 줄 | 여러 줄 가변 인자 |
| `enchant(enchant, level)` | 인챈트 추가 | 레벨 제한 무시 |
| `unbreakable()` | 내구도 무한 | |
| `modelData(int)` | 커스텀 모델 데이터 | 리소스팩 연동 |
| `glowing()` | 인챈트 효과 없이 빛남 | |
| `flags(vararg flags)` | ItemFlag 설정 | 속성 숨기기 등 |
| `amount(int)` | 수량 설정 | |
| `damage(int)` | 내구도 설정 | |

### 단점

`ItemMeta`의 일부 특수 기능(예: `LeatherArmorMeta.setColor()`, `MapMeta`, `FireworkMeta` 등)은 아직 DSL에서 직접 지원하지 않습니다. 이 경우 `meta { }` 블록으로 직접 접근할 수 있습니다.

```kotlin
val armor = item(Material.LEATHER_CHESTPLATE) {
    name("§e황금 갑옷")
    meta<LeatherArmorMeta> {
        // 특수 meta에 직접 접근
        setColor(Color.fromRGB(255, 215, 0))
    }
}
```

---

## ItemBehavior

`ItemBehavior`는 특정 `Material`을 가진 아이템에 동작을 바인딩합니다. 별도의 이벤트 리스너를 작성하지 않아도 됩니다.

### 기본 사용법

```kotlin
// 블레이즈 막대를 우클릭하면 파이어볼 발사
ItemBehavior.on(plugin, Material.BLAZE_ROD) {
    rightClick { player, item ->
        player.sendMessage("§c파이어볼!")
        castFireball(player)
    }
}
```

### 지원 이벤트 훅

```kotlin
ItemBehavior.on(plugin, Material.STICK) {
    // 우클릭 (공기/블록 모두)
    rightClick { player, item ->
        player.sendMessage("우클릭: ${item.type}")
    }

    // 좌클릭 (공기/블록 모두)
    leftClick { player, item ->
        player.sendMessage("좌클릭")
    }

    // 블록에 사용 (우클릭 블록)
    useOn { player, item, block ->
        block.type = Material.DIAMOND_ORE
    }

    // 드랍 (Q 키)
    drop { player, item ->
        player.sendMessage("아이템을 버렸습니다.")
    }

    // 인벤토리에서 선택 (스크롤)
    select { player, item ->
        player.sendMessage("아이템을 선택했습니다.")
    }

    // 인벤토리에서 해제
    deselect { player, item ->
        // 선택 해제 처리
    }
}
```

### 사용 시나리오

**마법봉**: 우클릭 시 특수 효과 발동.

```kotlin
ItemBehavior.on(plugin, Material.BLAZE_ROD) {
    rightClick { player, item ->
        if (!cooldowns.isReady(player)) {
            player.sendMessage("§c쿨다운 중입니다.")
            return@rightClick
        }
        cooldowns.set(player, 3.seconds)
        val target = player.getTargetEntity(5)
        (target as? LivingEntity)?.damage(20.0, player)
        player.world.spawnParticle(Particle.FLASH, target?.location ?: player.eyeLocation, 1)
    }
}
```

**채취 도구**: 특정 블록을 좌클릭하면 즉시 수확.

```kotlin
ItemBehavior.on(plugin, Material.GOLDEN_HOE) {
    useOn { player, item, block ->
        if (block.type in crops) {
            val drops = block.drops
            block.type = replantType(block.type)
            drops.forEach { player.inventory.addItem(it) }
        }
    }
}
```

### 자동 해제

`ItemBehavior.on(plugin, ...)` 형태로 등록하면 플러그인이 비활성화될 때 자동으로 이벤트 리스너가 해제됩니다. `onDisable`에서 수동으로 핸들러를 제거할 필요가 없습니다.

---

## ItemAuthenticator

`ItemAuthenticator`는 HMAC-SHA256 서명을 아이템의 PDC(PersistentDataContainer)에 저장하여 서버 외부에서 생성된 아이템을 감지합니다.

### 기본 사용법

```kotlin
// 서명 삽입 — 아이템을 플레이어에게 지급하기 전에 서명
val signedSword = ItemAuthenticator.sign(plugin, sword)
player.inventory.addItem(signedSword)
```

```kotlin
// 검증 — PlayerInteractEvent 등에서 확인
@EventHandler
fun onItemUse(event: PlayerInteractEvent) {
    val item = event.item ?: return
    when (ItemAuthenticator.verify(plugin, item)) {
        ItemVerification.VALID    -> { /* 서버에서 발급한 아이템 */ }
        ItemVerification.INVALID  -> {
            event.isCancelled = true
            event.player.sendMessage("§c위조된 아이템입니다.")
            event.player.inventory.remove(item)
        }
        ItemVerification.UNSIGNED -> {
            // 서명이 없는 일반 아이템 — 필요에 따라 처리
        }
    }
}
```

### 키 관리

서버 시작 시 키를 설정합니다. 키는 외부(환경 변수, 설정 파일)에서 로드하여 플러그인 코드에 하드코딩하지 마세요.

```kotlin
override fun onEnable() {
    // 기본 키 설정
    val keyBytes = config.getString("auth-key")!!.toByteArray()
    ItemAuthenticator.primaryKey(plugin, "main", keyBytes)
}
```

키를 교체할 때는 `rotateKey()`를 사용합니다. 이전 키로 서명된 아이템도 일정 기간 검증을 통과하도록 전환 기간을 설정할 수 있습니다.

```kotlin
// 새 키로 교체 — 이전 키로 서명된 아이템은 grace 기간 동안 유효
val newKeyBytes = generateNewKey()
ItemAuthenticator.rotateKey(plugin, "main", newKeyBytes, gracePeriodHours = 24)
```

### 작동 원리

1. `sign(plugin, item)` 호출 시 아이템의 고유 속성(Material, 이름, 인챈트 등)을 직렬화합니다.
2. 이 데이터를 HMAC-SHA256으로 해시하여 PDC에 저장합니다.
3. `verify(plugin, item)` 호출 시 동일한 방법으로 해시를 재계산하여 PDC에 저장된 값과 비교합니다.
4. 일치하면 `VALID`, 불일치하면 `INVALID`, PDC에 서명이 없으면 `UNSIGNED`를 반환합니다.

### 장점과 단점

**장점**:
- 서버 외부 `/give` 명령으로 만든 아이템 차단 가능.
- 아이템 속성 변조(이름, 인챈트 수정) 감지.
- 키 교체로 유출된 키 무효화 가능.

**단점**:
- 아이템에 PDC 데이터가 추가되어 직렬화 크기가 소폭 증가합니다.
- 서버 재시작마다 동일한 키를 사용해야 이전 서명이 유효합니다 (키를 설정 파일에 영속적으로 저장해야 함).
- 서명 검증을 적용한 아이템은 플러그인 외부에서 수정하면 즉시 무효화됩니다(의도적인 설계이나, 운영 시 주의 필요).

---

## 성능 고려사항

- `item { }` DSL은 내부적으로 표준 `ItemStack` + `ItemMeta`를 생성합니다. 성능 비용은 Bukkit 방식과 동일합니다.
- `ItemBehavior`는 이벤트 리스너를 하나만 등록하고, 내부에서 Material별로 라우팅합니다. 여러 Material에 행동을 등록해도 리스너 수는 증가하지 않습니다.
- `ItemAuthenticator.verify()`는 HMAC 계산을 수행하므로, 매 틱마다 모든 아이템을 검증하는 방식은 피해야 합니다. `PlayerInteractEvent`, `InventoryClickEvent` 등 실제 사용 시점에 검증하는 것을 권장합니다.
