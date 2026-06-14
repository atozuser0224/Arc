---
title: GUI 시스템
parent: Developer API
nav_order: 3
---

# GUI 시스템

Arc API의 GUI 시스템은 인벤토리 기반 메뉴를 이벤트 등록 없이 구성합니다. `InventoryHolder` 구현, `Listener` 등록, `HandlerList` 관리 등 Bukkit GUI 개발의 반복적인 보일러플레이트를 모두 제거합니다.

---

## Bukkit 방식의 문제

Bukkit에서 클릭 가능한 인벤토리 GUI를 만들려면 다음이 필요합니다.

1. `InventoryHolder`를 구현하는 클래스 작성.
2. `Listener`를 구현하는 클래스 작성.
3. `InventoryClickEvent`에서 GUI 인스턴스를 구분하는 로직 작성.
4. `HandlerList`에 리스너 등록 및 `onDisable`에서 해제.

```kotlin
// Bukkit 방식 — 최소 구현도 이 정도 필요
class ShopHolder : InventoryHolder {
    override fun getInventory(): Inventory = inv
    val inv = Bukkit.createInventory(this, 27, "상점")
}

class ShopListener(val plugin: JavaPlugin) : Listener {
    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? ShopHolder ?: return
        event.isCancelled = true
        when (event.slot) {
            13 -> buyDiamond(event.whoClicked as Player)
        }
    }
}

// 사용 시
val holder = ShopHolder()
holder.inv.setItem(13, ItemStack(Material.DIAMOND))
plugin.server.pluginManager.registerEvents(ShopListener(plugin), plugin)
player.openInventory(holder.inv)
```

---

## 단일 페이지 메뉴

`plugin.menu()`는 인벤토리 GUI를 선언적으로 구성합니다. 각 슬롯에 아이템과 클릭 핸들러를 함께 등록합니다.

```kotlin
val shop = plugin.menu(title = Component.text("§e상점"), rows = 3) {
    // 슬롯 13에 다이아몬드 배치, 클릭 시 구매 처리
    item(13, item(Material.DIAMOND) {
        name("§bDiamond — §a$100")
        lore("§7클릭하여 구매")
    }) { event ->
        event.isCancelled = true
        val buyer = event.whoClicked as? Player ?: return@item
        if (ArcEconomy.withdraw(buyer.uniqueId, 100L)) {
            buyer.inventory.addItem(ItemStack(Material.DIAMOND))
            buyer.sendMessage("§a다이아몬드를 구매했습니다!")
        } else {
            buyer.sendMessage("§c잔액이 부족합니다.")
        }
    }

    // 슬롯 22에 닫기 버튼
    item(22, item(Material.BARRIER) {
        name("§c닫기")
    }) { event ->
        event.whoClicked.closeInventory()
    }

    // GUI가 닫힐 때 처리
    onClose { viewer ->
        viewer.sendMessage("§7상점을 닫았습니다.")
    }
}

// 플레이어에게 GUI 열기
shop.open(player)
```

### 자주 쓰는 패턴 — 장식 아이템으로 빈 슬롯 채우기

```kotlin
val menu = plugin.menu(Component.text("§8메인 메뉴"), rows = 3) {
    // 빈 슬롯을 검은 유리로 채우기
    fill(item(Material.BLACK_STAINED_GLASS_PANE) { name("§r") })

    // 주요 버튼 배치
    item(11, item(Material.CHEST) { name("§e인벤토리") }) { event ->
        openInventoryMenu(event.whoClicked as Player)
    }
    item(13, item(Material.EMERALD) { name("§a상점") }) { event ->
        openShopMenu(event.whoClicked as Player)
    }
    item(15, item(Material.BOOK) { name("§b퀘스트") }) { event ->
        openQuestMenu(event.whoClicked as Player)
    }
}
```

---

## 페이지네이션 메뉴

아이템 목록이 많을 때 자동으로 이전/다음 버튼을 생성합니다.

```kotlin
// 서버의 모든 플레이어 목록을 페이지로 표시
val playerList: List<Player> = Bukkit.getOnlinePlayers().toList()
val playerItems = playerList.map { p ->
    item(Material.PLAYER_HEAD) {
        name("§a${p.name}")
        lore("§7클릭하여 정보 보기", "§7위치: ${p.world.name}")
        // SkullMeta는 meta<SkullMeta> { ... } 로 설정
    }
}

val pagedMenu = plugin.paginatedMenu(
    title = Component.text("§8온라인 플레이어"),
    rows = 6,  // 6행 중 마지막 행은 이전/다음 버튼에 사용
    items = playerItems
) { itemStack, slot ->
    // slot은 현재 페이지의 슬롯 인덱스
    slot.item = itemStack
    slot.onClick = { event ->
        val idx = event.slot + (currentPage * pageSize)
        val target = playerList.getOrNull(idx) ?: return@onClick
        (event.whoClicked as? Player)?.sendMessage("§e${target.name}의 정보...")
    }
}

pagedMenu.open(player)
```

**이전/다음 버튼**: 기본적으로 마지막 행의 좌측에 이전 버튼, 우측에 다음 버튼이 자동으로 배치됩니다. 버튼 슬롯과 아이템은 커스터마이징할 수 있습니다.

```kotlin
plugin.paginatedMenu(
    title = Component.text("§8아이템 목록"),
    rows = 6,
    items = shopItems,
    prevButton = item(Material.ARROW) { name("§7이전 페이지") },
    nextButton = item(Material.ARROW) { name("§7다음 페이지") },
    prevSlot = 45,
    nextSlot = 53
) { itemStack, slot -> ... }
```

**사용 시나리오**: 100개 이상의 아이템 상점, 플레이어 목록, 로그 조회, 경매장.

---

## ChatInput

`ChatInput`은 GUI를 닫고 채팅 입력을 대기한 후, 입력된 값을 콜백으로 처리합니다. 수량 입력, 이름 입력 등 텍스트가 필요한 상황에서 사용합니다.

```kotlin
fun openWithdrawMenu(player: Player) {
    // 1. 먼저 안내 GUI를 닫고
    player.closeInventory()

    // 2. 채팅 입력 대기
    ChatInput.await(
        player = player,
        prompt = "§e출금할 금액을 채팅창에 입력하세요: (취소: §ccancel§e)",
        timeout = 30.seconds  // 30초 내 입력 없으면 자동 취소
    ) { input ->
        if (input.equals("cancel", ignoreCase = true)) {
            player.sendMessage("§7취소했습니다.")
            return@await
        }
        val amount = input.toLongOrNull()
        if (amount == null || amount <= 0) {
            player.sendMessage("§c올바른 금액을 입력하세요.")
            return@await
        }
        if (ArcEconomy.withdraw(player.uniqueId, amount)) {
            player.sendMessage("§a${amount}코인을 출금했습니다.")
        } else {
            player.sendMessage("§c잔액이 부족합니다.")
        }
    }
}
```

```kotlin
// GUI → ChatInput 연계 예시
val bankMenu = plugin.menu(Component.text("§6은행"), rows = 3) {
    item(11, item(Material.EMERALD) { name("§a입금") }) { event ->
        val p = event.whoClicked as? Player ?: return@item
        ChatInput.await(p, "§e입금할 금액:") { input ->
            val amount = input.toLongOrNull() ?: return@await
            ArcEconomy.deposit(p.uniqueId, amount)
            p.sendMessage("§a${amount}코인 입금 완료.")
            bankMenu.open(p)  // GUI 재오픈
        }
    }

    item(15, item(Material.GOLD_INGOT) { name("§6출금") }) { event ->
        val p = event.whoClicked as? Player ?: return@item
        ChatInput.await(p, "§e출금할 금액:") { input ->
            val amount = input.toLongOrNull() ?: return@await
            if (ArcEconomy.withdraw(p.uniqueId, amount)) {
                p.sendMessage("§a${amount}코인 출금 완료.")
            } else {
                p.sendMessage("§c잔액 부족.")
            }
            bankMenu.open(p)  // GUI 재오픈
        }
    }
}
```

**timeout 미설정 시**: 플레이어가 서버에 있는 한 계속 입력을 대기합니다. 무한 대기를 방지하기 위해 `timeout`을 설정하는 것을 권장합니다.

---

## 장점과 단점

### 장점

- **이벤트 등록 불필요**: `Listener`를 직접 구현할 필요가 없습니다.
- **슬롯별 onClick**: 슬롯에 아이템을 배치할 때 클릭 핸들러를 함께 등록합니다. 클릭 이벤트에서 슬롯 번호로 분기하는 when/if 체인이 사라집니다.
- **자동 이벤트 취소**: 등록된 슬롯을 클릭하면 `isCancelled = true`가 자동으로 적용됩니다.
- **자동 해제**: 플러그인이 비활성화되면 이벤트 리스너가 자동으로 해제됩니다.
- **페이지네이션 내장**: 이전/다음 버튼 로직을 직접 구현하지 않아도 됩니다.

### 단점

- **드래그 앤 드롭**: 아이템을 드래그하여 GUI 슬롯에 배치하는 복잡한 인터랙션은 아직 공식 지원이 제한적입니다.
- **Anvil GUI**: 이름 입력을 위한 Anvil 인터페이스는 별도 NMS 처리가 필요합니다. `ChatInput`을 대안으로 사용하는 것을 권장합니다.
- **동적 업데이트**: 열려 있는 GUI의 아이템을 실시간으로 업데이트하려면 `menu.refresh(player)` 또는 `player.updateInventory()`를 수동으로 호출해야 합니다.

---

## 동적 업데이트 예시

카운트다운이 표시되는 GUI:

```kotlin
var secondsLeft = 30
val countdownMenu = plugin.menu(Component.text("§c카운트다운"), rows = 1) {
    item(4, item(Material.CLOCK) {
        name("§e남은 시간: §c${secondsLeft}초")
    }) { /* 클릭 무시 */ }
}

countdownMenu.open(player)

plugin.runRepeat(delay = 0L, period = 20L) {
    secondsLeft--
    if (secondsLeft <= 0) {
        player.closeInventory()
        player.sendMessage("§c시간이 초과되었습니다!")
        cancel()
        return@runRepeat
    }

    // 아이템 업데이트 후 인벤토리 갱신
    countdownMenu.updateItem(4, item(Material.CLOCK) {
        name("§e남은 시간: §c${secondsLeft}초")
    })
    player.updateInventory()
}
```
