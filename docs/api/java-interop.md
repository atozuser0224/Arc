---
title: Java 호환 레이어
parent: Developer API
nav_order: 9
---

# Java 호환 레이어

arc-api의 핵심 함수(`inline reified listen<T>`, Kotlin DSL 빌더)는 Java에서 직접 호출할 수 없다. `inline reified` 함수는 컴파일 타임에 타입이 인라인 치환되어 바이트코드에 제네릭 진입점이 존재하지 않기 때문이다.

Java 호환 레이어는 이 간극을 메우는 다섯 개의 `object` 싱글턴과 `Consumer`/`Function` 오버로드 모음이다. 기존 Java 플러그인을 코드베이스 변경 없이 Arc API에 연결하거나, Java/Kotlin 혼용 프로젝트에서 사용한다.

---

## ArcEvents

`dev.arc.api.event.ArcEvents`

Kotlin의 `inline reified listen<T>` 대신 `Class<T>` + `Consumer<T>`로 이벤트를 구독한다.

```java
import dev.arc.api.event.ArcEvents;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.EventPriority;

// 기본 등록 (NORMAL 우선순위, cancelledIgnore = false)
Listener joinListener = ArcEvents.listen(plugin, PlayerJoinEvent.class, event -> {
    event.getPlayer().sendMessage("환영합니다!");
});

// 우선순위 지정
Listener highPriority = ArcEvents.listen(
    plugin,
    PlayerJoinEvent.class,
    EventPriority.HIGH,
    false,
    event -> handleJoin(event)
);

// 해제
ArcEvents.unregister(joinListener);
```

| 메서드 | 설명 |
|--------|------|
| `listen(plugin, eventClass, handler)` | NORMAL 우선순위로 이벤트 구독 |
| `listen(plugin, eventClass, priority, ignoreCancelled, handler)` | 우선순위·취소 이벤트 필터 지정 |
| `unregister(listener)` | 이벤트 리스너 해제 |

Kotlin에서는 `plugin.listen<PlayerJoinEvent> { ... }`을 사용한다.

---

## ArcItems

`dev.arc.api.item.ArcItems`

`item { }` DSL, `defineItem`, `withBehavior`의 Java 진입점.

```java
import dev.arc.api.item.ArcItems;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

// 아이템 생성
ItemStack sword = ArcItems.item(Material.DIAMOND_SWORD, 1, b -> {
    b.setDisplayName("§bMana Blade");
    b.setLore(java.util.List.of("§7마나의 힘이 깃든 검"));
});

// ItemBehavior 등록
NamespacedKey bladeKey = new NamespacedKey(plugin, "mana_blade");
ArcItems.defineItem(plugin, bladeKey, b -> {
    b.onRightClick(ctx -> {
        ctx.getPlayer().sendMessage("Mana Blade activated!");
    });
});

// 아이템에 behavior 바인딩
ItemStack taggedSword = ArcItems.withBehavior(sword, bladeKey, plugin);

// 아이템의 behavior key 조회 (null이면 Arc 아이템 아님)
NamespacedKey key = ArcItems.behaviorKey(player.getInventory().getItemInMainHand());
```

| 메서드 | 설명 |
|--------|------|
| `item(material, amount, consumer)` | DSL 빌더로 ItemStack 생성 |
| `defineItem(plugin, key, consumer)` | NamespacedKey로 ItemBehavior 등록 |
| `withBehavior(stack, key, plugin)` | ItemStack에 behavior 태그 추가 |
| `behaviorKey(stack)` | ItemStack에 등록된 behavior key 반환 |

---

## ArcCommands

`dev.arc.api.command.ArcCommands`

`plugin.yml` 선언 없이 런타임에 커맨드를 등록한다.

```java
import dev.arc.api.command.ArcCommands;

ArcCommands.register(plugin, "shop", cmd -> {
    cmd.description("서버 상점");
    cmd.permission("myplugin.shop");

    cmd.sub("buy", sub -> {
        sub.playerOnly();
        sub.execute(ctx -> {
            String item = ctx.arg(0);
            if (item == null) {
                ctx.getSender().sendMessage("사용법: /shop buy <item>");
                return;
            }
            openBuyMenu(ctx.getPlayer(), item);
        });
        sub.complete(ctx -> getShopItems());
    });

    cmd.sub("sell", sub -> {
        sub.playerOnly();
        sub.execute(ctx -> openSellMenu(ctx.getPlayer()));
    });

    cmd.execute(ctx ->
        ctx.getSender().sendMessage("§e사용법: /shop <buy|sell>")
    );
});
```

Kotlin에서는 `plugin.command("shop") { ... }`을 사용한다.

---

## ArcContentDsl

`dev.arc.api.content.ArcContentDsl`

콘텐츠 팩(아이템·블록·가구·레시피)을 Java에서 등록한다.

```java
import dev.arc.api.content.ArcContentDsl;
import dev.arc.api.content.ContentId;

// 등록
ContentPublishResult result = ArcContentDsl.register(plugin, pack -> {
    pack.item("showcase/ruby", item -> {
        item.setFallback("minecraft:redstone");
        item.setMaxStackSize(64);
        item.setOrder(10);
    });
    pack.block("showcase/ruby_ore", block -> {
        block.setFallback("minecraft:redstone_ore");
        block.setHardness(3.0f);
        block.setBlastResistance(3.0f);
        block.setOrder(20);
    });
    pack.recipe("showcase/ruby_recipe", recipe -> {
        recipe.setResult(new ContentId("showcase", "showcase/ruby"));
        recipe.getIngredients().add(new ContentId("minecraft", "redstone"));
    });
});

if (!result.getAccepted()) {
    result.getDiagnostics().forEach(d ->
        plugin.getLogger().warning(d.getMessage())
    );
}

// onDisable에서 해제
ArcContentDsl.unregister(plugin);
```

namespace는 플러그인 이름에서 자동으로 파생된다 (소문자·정규화). 명시적으로 지정하려면:

```java
ArcContentDsl.register(plugin, "my_namespace", pack -> { ... });
```

---

## ArcCustomEffects

`dev.arc.api.effect.ArcCustomEffects`

커스텀 포션 효과 레지스트리를 생성·등록·적용한다.

`kotlin.time.Duration`은 Java에서 value class로 노출되어 직접 사용하기 불편하다. `setDefaultDurationSeconds`와 `applySeconds`가 초 단위 `long`을 받는 브릿지를 제공한다.

```java
import dev.arc.api.effect.ArcCustomEffects;
import org.bukkit.potion.PotionEffectType;

// 레지스트리 생성 + 효과 등록
CustomEffectRegistry effects = ArcCustomEffects.createRegistry(plugin, registry -> {
    ArcCustomEffects.registerEffect(registry, "mana_surge", effect -> {
        effect.setVisualEffect(PotionEffectType.REGENERATION);
        ArcCustomEffects.setDefaultDurationSeconds(effect, 15L);
        effect.setReapplyPolicy(EffectReapplyPolicy.KEEP_STRONGER);

        effect.onApply(player ->
            player.sendMessage("§b마나 서지가 발동되었습니다!")
        );
        effect.onTick(20, (player, state) ->
            player.sendMessage("남은 틱: " + state.getRemainingTicks())
        );
        effect.onRemove((player, state, reason) ->
            player.sendMessage("§7마나 서지 종료: " + reason)
        );
    });
});

// 효과 적용 (20초)
ArcCustomEffects.applySeconds(effects, player, effects.get("mana_surge"), 20L, 0);

// 플러그인 종료 시
@Override
public void onDisable() {
    effects.close();
}
```

| 메서드 | 설명 |
|--------|------|
| `createRegistry(plugin)` | 빈 레지스트리 생성 |
| `createRegistry(plugin, consumer)` | 생성 + 즉시 구성 |
| `registerEffect(registry, id, consumer)` | 효과 등록 |
| `setDefaultDurationSeconds(builder, seconds)` | 기본 지속 시간 설정 (초 단위) |
| `applySeconds(registry, player, effect, seconds)` | 효과 적용 (초 단위) |
| `applySeconds(registry, player, effect, seconds, amplifier)` | 효과 적용 (증폭 지정) |

---

## Java 플러그인 이전 가이드

PICO_CORE3와 같은 기존 Java 플러그인을 Arc로 이전할 때는 플러그인 프레임워크(커맨드 라우터, 리스너 베이스 클래스)를 그대로 유지하면서 Arc의 추가 API만 점진적으로 사용한다.

**권장 순서:**

1. `arc-api.jar`를 `compileOnly` 의존성으로 추가한다.
2. 콘텐츠 등록이 필요한 곳에서 `ArcContentDsl.register`를 사용한다.
3. 커스텀 아이템 동작이 필요한 곳에서 `ArcItems`로 ItemBehavior를 정의한다.
4. 커스텀 효과가 필요한 곳에서 `ArcCustomEffects`로 레지스트리를 만든다.
5. 기존 `Listener` 등록 방식 대신 `ArcEvents.listen`으로 교체하면 플러그인 비활성화 시 자동 해제된다.

기존 커맨드 프레임워크(`AbstractCommand` 등)를 `ArcCommands`로 교체할 필요는 없다. 단, 신규 커맨드라면 `ArcCommands.register`가 plugin.yml 편집 없이 런타임 등록·해제를 지원해 더 편리하다.
