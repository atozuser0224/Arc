---
title: 커맨드 프레임워크
parent: Developer API
nav_order: 6
---

# 커맨드 프레임워크

Arc API의 커맨드 프레임워크는 `plugin.yml` 선언 없이 런타임에 커맨드를 등록합니다. 서브커맨드, 탭 완성, 권한 검사, 플레이어 전용 제한을 하나의 DSL 블록으로 선언합니다.

---

## 기존 방식의 한계

Bukkit에서 커맨드를 하나 추가하려면 세 군데를 수정해야 합니다.

**1. plugin.yml — 커맨드 선언**

```yaml
commands:
  shop:
    description: 상점 명령어
    usage: /shop <buy|sell|balance> [args]
    permission: myplugin.shop
```

**2. CommandExecutor 구현 — if/else 서브커맨드 체인**

```kotlin
class ShopCommand(val plugin: JavaPlugin) : CommandExecutor {
    override fun onCommand(
        sender: CommandSender, command: Command, label: String, args: Array<String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("플레이어만 사용 가능합니다.")
            return true
        }
        if (!sender.hasPermission("myplugin.shop")) {
            sender.sendMessage("권한이 없습니다.")
            return true
        }
        when (args.getOrNull(0)?.lowercase()) {
            "buy" -> {
                val item = args.getOrNull(1) ?: run {
                    sender.sendMessage("사용법: /shop buy <아이템>"); return true
                }
                buy(sender, item)
            }
            "sell" -> sell(sender)
            "balance" -> showBalance(sender)
            else -> sender.sendMessage("사용법: /shop <buy|sell|balance>")
        }
        return true
    }
}
```

**3. TabCompleter 구현 — 별도 클래스**

```kotlin
class ShopTabCompleter : TabCompleter {
    override fun onTabComplete(
        sender: CommandSender, command: Command, alias: String, args: Array<String>
    ): List<String> {
        return when (args.size) {
            1 -> listOf("buy", "sell", "balance").filter { it.startsWith(args[0]) }
            2 -> if (args[0] == "buy") listOf("sword", "bow") else emptyList()
            else -> emptyList()
        }
    }
}
```

**4. onEnable에서 등록**

```kotlin
getCommand("shop")?.also {
    it.setExecutor(ShopCommand(this))
    it.tabCompleter = ShopTabCompleter()
}
```

총 4개 파일, 50줄 이상의 코드가 필요합니다.

---

## Arc 커맨드 DSL

`plugin.registerCommand()`로 위의 모든 과정을 하나의 블록으로 처리합니다.

```kotlin
plugin.registerCommand("shop") {
    description("상점 명령어")
    permission("myplugin.shop")
    playerOnly()  // 플레이어 전용 (콘솔 차단 + 자동 메시지)

    subcommand("buy") {
        description("아이템 구매")
        permission("myplugin.shop.buy")

        execute { ctx ->
            val itemName = ctx.arg(0) ?: run {
                ctx.sender.sendMessage("§c사용법: /shop buy <아이템>")
                return@execute
            }
            buy(ctx.player, itemName)
        }

        complete { ctx ->
            listOf("sword", "bow", "armor", "helmet", "boots")
                .filter { it.startsWith(ctx.arg(0) ?: "") }
        }
    }

    subcommand("sell") {
        description("손에 든 아이템 판매")
        execute { ctx ->
            val heldItem = ctx.player.inventory.itemInMainHand
            if (heldItem.type == Material.AIR) {
                ctx.player.sendMessage("§c판매할 아이템을 손에 드세요.")
                return@execute
            }
            sell(ctx.player, heldItem)
        }
    }

    subcommand("balance") {
        description("잔액 조회")
        execute { ctx ->
            val bal = ArcEconomy.getBalance(ctx.player.uniqueId)
            ctx.sender.sendMessage("§e현재 잔액: §a${bal}코인")
        }
    }

    // 서브커맨드 없이 직접 실행
    execute { ctx ->
        ctx.sender.sendMessage("§e사용법: /shop <buy|sell|balance>")
    }
}
```

plugin.yml에 커맨드 선언이 전혀 없어도 됩니다. `registerCommand()`가 런타임에 커맨드를 등록합니다.

---

## CommandContext

`execute` 블록에서 받는 `ctx` 객체가 제공하는 정보입니다.

```kotlin
execute { ctx ->
    ctx.sender          // CommandSender — 항상 사용 가능
    ctx.player          // Player — playerOnly()가 아니라면 null일 수 있음
    ctx.isPlayer        // Boolean
    ctx.arg(0)          // 첫 번째 아규먼트 (null-safe)
    ctx.arg(0, "기본값")  // 기본값 포함
    ctx.args            // List<String> — 전체 아규먼트
    ctx.rawInput        // 원본 입력 문자열
}
```

**플레이어 전용 커맨드에서 안전한 `ctx.player` 접근**:

```kotlin
// playerOnly() 선언 시 ctx.player는 절대 null이 아님
plugin.registerCommand("warp") {
    playerOnly()
    execute { ctx ->
        // 이 블록은 오직 플레이어만 실행 가능
        val player = ctx.player  // !! 없이 사용 가능
        player.teleport(warpLocation)
    }
}
```

---

## command() — 경량 등록 방식

서브커맨드가 없는 단순한 커맨드는 `command()` 함수를 사용합니다.

```kotlin
// 단순 커맨드 — 서브커맨드 없음
command("heal") {
    permission("myplugin.heal")
    playerOnly()

    execute { ctx ->
        ctx.player.healFully()
        ctx.player.sendMessage("§a체력이 회복되었습니다.")
    }

    complete { ctx ->
        // 아규먼트 없으면 빈 목록
        emptyList()
    }
}
```

---

## 실전 예시 — /shop buy/sell/balance 전체

```kotlin
class ShopPlugin : JavaPlugin() {
    
    private val prices = mapOf(
        "sword" to 500L,
        "bow" to 300L,
        "armor" to 800L,
        "helmet" to 200L,
        "boots" to 150L
    )

    private val shopItems = mapOf(
        "sword" to item(Material.IRON_SWORD) {
            name("§7Iron Sword")
            enchant(Enchantment.SHARPNESS, 2)
        },
        "bow" to item(Material.BOW) {
            name("§7Iron Bow")
            enchant(Enchantment.POWER, 2)
        },
        "armor" to item(Material.IRON_CHESTPLATE) { name("§7Iron Armor") },
        "helmet" to item(Material.IRON_HELMET) { name("§7Iron Helmet") },
        "boots" to item(Material.IRON_BOOTS) { name("§7Iron Boots") }
    )

    override fun onEnable() {
        registerShopCommand()
    }

    private fun registerShopCommand() {
        registerCommand("shop") {
            description("상점 명령어")
            permission("shop.use")
            playerOnly()

            subcommand("buy") {
                description("아이템 구매 — /shop buy <아이템명>")
                permission("shop.buy")

                complete { ctx ->
                    val prefix = ctx.arg(0) ?: ""
                    prices.keys.filter { it.startsWith(prefix) }
                }

                execute { ctx ->
                    val itemName = ctx.arg(0)?.lowercase() ?: run {
                        ctx.player.sendMessage("§c사용법: /shop buy <${prices.keys.joinToString("|")}>")
                        return@execute
                    }
                    val price = prices[itemName] ?: run {
                        ctx.player.sendMessage("§c'$itemName' 아이템을 찾을 수 없습니다.")
                        return@execute
                    }
                    val itemStack = shopItems[itemName] ?: return@execute

                    if (!ArcEconomy.has(ctx.player.uniqueId, price)) {
                        ctx.player.sendMessage("§c잔액이 부족합니다. 필요: §e${price}코인")
                        return@execute
                    }
                    ArcEconomy.withdraw(ctx.player.uniqueId, price)
                    ctx.player.inventory.addItem(itemStack.clone())
                    ctx.player.sendMessage("§a$itemName 구매 완료! (§e-${price}코인§a)")
                }
            }

            subcommand("sell") {
                description("손에 든 아이템 판매 — /shop sell")

                execute { ctx ->
                    val held = ctx.player.inventory.itemInMainHand
                    if (held.type == Material.AIR) {
                        ctx.player.sendMessage("§c판매할 아이템을 손에 드세요.")
                        return@execute
                    }

                    val sellPrice = when (held.type) {
                        Material.DIAMOND -> 1000L
                        Material.GOLD_INGOT -> 150L
                        Material.IRON_INGOT -> 50L
                        Material.EMERALD -> 500L
                        else -> {
                            ctx.player.sendMessage("§c이 아이템은 판매할 수 없습니다.")
                            return@execute
                        }
                    } * held.amount

                    ctx.player.inventory.setItemInMainHand(null)
                    ArcEconomy.deposit(ctx.player.uniqueId, sellPrice)
                    ctx.player.sendMessage("§a판매 완료! (§e+${sellPrice}코인§a)")
                }
            }

            subcommand("balance") {
                description("현재 잔액 조회 — /shop balance")

                execute { ctx ->
                    val bal = ArcEconomy.getBalance(ctx.player.uniqueId)
                    ctx.player.sendMessage("§e현재 잔액: §a${bal}코인")
                }
            }

            // 루트 — 서브커맨드 없이 /shop 입력
            execute { ctx ->
                ctx.player.sendMessage("""
                    §e=== 상점 명령어 ===
                    §7/shop buy <아이템> §f- 아이템 구매
                    §7/shop sell §f- 손에 든 아이템 판매
                    §7/shop balance §f- 잔액 조회
                """.trimIndent())
            }
        }
    }
}
```

---

## 장점과 단점

### 장점

- **plugin.yml 불필요**: `plugin.yml`에 커맨드 블록을 선언하지 않아도 됩니다. 런타임 등록이므로 조건에 따라 커맨드를 동적으로 등록/해제할 수 있습니다.
- **3 파일 → 1 블록**: `CommandExecutor`, `TabCompleter`, `plugin.yml` 선언이 하나의 `registerCommand` 블록으로 통합됩니다.
- **서브커맨드 선언적**: if/else 체인 대신 `subcommand("name") { }` 블록으로 명확하게 분리됩니다.
- **자동 권한 처리**: `permission("...")`을 선언하면 권한 체크와 실패 메시지를 자동으로 처리합니다.
- **자동 플레이어 체크**: `playerOnly()` 선언 시 콘솔에서 실행하면 자동으로 차단됩니다.

### 단점

- **복잡한 아규먼트 파서 없음**: IntRange, 엔티티 선택자(`@a`, `@p`), Location 파싱 등 복잡한 아규먼트 타입은 직접 구현해야 합니다. 이 경우 [CloudCommands](https://github.com/Incendo/cloud) 같은 전문 커맨드 프레임워크를 고려하세요.
- **런타임 등록 제한**: 서버 시작 후 등록된 커맨드는 `/reload` 시 재등록이 필요할 수 있습니다. 플러그인 `onEnable`에서 반드시 등록하세요.
- **명령어 충돌**: 다른 플러그인과 동일한 이름의 커맨드를 등록하면 충돌이 발생합니다. 플러그인 고유 접두사를 사용하는 것을 권장합니다.
