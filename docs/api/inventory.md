---
title: 인벤토리 트랜잭션
parent: Developer API
nav_order: 11
---

# 인벤토리 트랜잭션

`ArcInventoryTransactionEvent`는 Bukkit이 2~5개로 쪼개 발생시키는 인벤토리 이벤트를 플레이어의 **단일 논리 행동** 하나로 합쳐 준다. "플레이어가 무엇을 하려 했는가"를 이벤트 하나로 알 수 있다.

---

## Bukkit 방식의 문제

플레이어가 인벤토리에서 아이템 하나를 옮기는 동작은 내부적으로 여러 이벤트로 쪼개진다.

```kotlin
// ❌ Bukkit — InventoryClickEvent 하나에서 의도를 파악해야 함
listen<InventoryClickEvent> { e ->
    val player = e.whoClicked as? Player ?: return@listen

    // 드롭인지, 픽업인지, 이동인지 직접 판별해야 함
    when {
        e.click == ClickType.DROP -> handleDrop(e)
        e.click == ClickType.SHIFT_LEFT -> handleMove(e)
        e.action == InventoryAction.PICKUP_ALL -> handlePickup(e)
        e.action == InventoryAction.PLACE_ALL -> handlePlace(e)
        e.action == InventoryAction.HOTBAR_SWAP -> handleHotbar(e)
        // ... 더 많은 케이스
        else -> { /* 처리 안 된 케이스 */ }
    }
}
```

이 방식의 문제:
- `InventoryClickEvent`, `InventoryDragEvent`를 모두 따로 구독해야 한다.
- `ClickType`과 `InventoryAction`의 조합이 복잡해 빠뜨린 케이스가 생기기 쉽다.
- "플레이어가 다이아몬드를 드롭했는가"를 판단하는 코드가 길어진다.
- 이벤트를 취소할 때 어느 이벤트를 취소해야 하는지 헷갈린다.

---

## 활성화

플러그인 `onEnable()`에서 한 번 호출하면 된다.

```kotlin
override fun onEnable() {
    enableInventoryTransactions()

    listen<ArcInventoryTransactionEvent> { e ->
        // 여기서 트랜잭션 처리
    }
}
```

### Java

```java
@Override
public void onEnable() {
    InventoryTransactions.enableInventoryTransactions(this);

    getServer().getPluginManager().registerEvents(new Listener() {
        @EventHandler
        public void onTransaction(ArcInventoryTransactionEvent e) {
            // 처리
        }
    }, this);
}
```

---

## TransactionKind — 행동 종류

| 값 | 설명 |
|----|------|
| `PICKUP` | 슬롯에서 커서로 아이템을 집음 |
| `PLACE` | 커서에서 슬롯으로 아이템을 놓음 |
| `MOVE` | Shift+클릭 또는 더블클릭 수집: 인벤토리 반대편으로 이동 |
| `HOTBAR_SWAP` | 숫자 키: 현재 슬롯과 핫바 슬롯 교환 |
| `OFF_HAND_SWAP` | F키: 현재 슬롯과 오프핸드 교환 |
| `DROP` | Q키 또는 인벤토리 밖 클릭: 슬롯에서 드롭 |
| `CRAFT` | 제작 결과 슬롯 클릭 |
| `DRAG` | 드래그로 여러 슬롯에 배분 |
| `CLONE` | 크리에이티브 중간 클릭: 아이템 복제 |
| `UNKNOWN` | 위 어느 것도 아닌 동작 — `rawEvent`를 직접 확인 |

---

## 기본 사용 예시

### 특정 아이템 드롭 방지

```kotlin
listen<ArcInventoryTransactionEvent> { e ->
    if (e.transaction.kind == TransactionKind.DROP &&
        e.transaction.item?.type == Material.NETHERITE_INGOT) {
        e.isCancelled = true
        e.transaction.player.sendMessage("§c네더라이트는 버릴 수 없습니다.")
    }
}
```

### 아이템 이동 로그

```kotlin
listen<ArcInventoryTransactionEvent> { e ->
    val tx = e.transaction
    when (tx.kind) {
        TransactionKind.PICKUP, TransactionKind.PLACE, TransactionKind.MOVE -> {
            logger.info(
                "${tx.player.name}: ${tx.kind} " +
                "${tx.item?.type} " +
                "from=${tx.fromSlot} to=${tx.toSlot}"
            )
        }
        else -> {}
    }
}
```

### 제작 결과 인터셉트

```kotlin
listen<ArcInventoryTransactionEvent> { e ->
    val tx = e.transaction
    if (tx.kind != TransactionKind.CRAFT) return@listen

    val result = tx.item ?: return@listen
    if (result.type == Material.DIAMOND_SWORD) {
        // 제작을 취소하고 강화 버전으로 교체
        e.isCancelled = true
        tx.player.inventory.addItem(
            item(Material.DIAMOND_SWORD) { enchant(Enchantment.SHARPNESS, 5) }
        )
        tx.player.sendMessage("§d강화된 다이아몬드 검을 얻었습니다!")
    }
}
```

---

## InventoryTransaction 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `player` | `Player` | 행동을 한 플레이어 |
| `kind` | `TransactionKind` | 논리적 행동 종류 |
| `item` | `ItemStack?` | 다뤄진 아이템 스냅샷 (라이브 참조 아님) |
| `fromSlot` | `Int` | 출발 슬롯 인덱스. 커서 출발이면 `-1` |
| `toSlot` | `Int?` | 도착 슬롯 인덱스. 드롭·픽업·드래그는 `null` |
| `rawEvent` | `InventoryInteractEvent?` | 원본 Bukkit 이벤트 |

---

## 취소 동작

`ArcInventoryTransactionEvent`를 취소하면 `rawEvent`도 자동으로 취소된다. 직접 `rawEvent.isCancelled = true`를 부를 필요가 없다.

```kotlin
listen<ArcInventoryTransactionEvent> { e ->
    if (shouldBlock(e.transaction)) {
        e.isCancelled = true  // rawEvent도 함께 취소됨
    }
}
```

---

## 전체 예시 — 잠긴 인벤토리 슬롯

```kotlin
class LockedSlotPlugin : JavaPlugin() {

    // 슬롯 0~3은 잠금 (퀘스트 아이템 보호)
    private val LOCKED_SLOTS = setOf(0, 1, 2, 3)

    override fun onEnable() {
        enableInventoryTransactions()

        listen<ArcInventoryTransactionEvent> { e ->
            val tx = e.transaction

            // 잠긴 슬롯을 건드리는 행동 차단
            val touchesLocked = tx.fromSlot in LOCKED_SLOTS ||
                (tx.toSlot != null && tx.toSlot in LOCKED_SLOTS)

            if (touchesLocked && tx.kind != TransactionKind.UNKNOWN) {
                e.isCancelled = true
                tx.player.sendActionBar(
                    Component.text("§c이 슬롯은 잠겨 있습니다.")
                )
            }
        }
    }
}
```

---

## 이점과 한계

**이점**
- `InventoryClickEvent` + `InventoryDragEvent`를 별도로 구독하지 않아도 됨
- `TransactionKind` 열거형으로 케이스가 명확 — 빠뜨린 케이스가 줄어듦
- 취소 시 rawEvent까지 자동으로 함께 취소
- `item`은 스냅샷이라 비동기에서 타입 확인이 안전

**한계**
- `UNKNOWN` 케이스는 해결 불가 — Bukkit이 명확하게 분류하지 않는 동작은 `rawEvent`를 직접 봐야 함
- 드래그(`DRAG`)는 어느 슬롯에 얼마만큼 분배됐는지 알려면 `rawEvent as InventoryDragEvent`를 캐스팅해야 함
- `enableInventoryTransactions()`는 플러그인당 한 번만 불러야 한다. 여러 번 부르면 리스너가 중복 등록된다.
