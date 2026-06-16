---
title: 엔티티 뷰 레이어
parent: Developer API
nav_order: 14
---

# 엔티티 뷰 레이어 (EntityViewLayer)

`EntityViewLayer`는 플레이어마다 엔티티의 표시 이름, 발광 효과, 투명도를 다르게 보여주는 레이어다. 엔티티의 실제 상태를 건드리지 않고 `ClientboundSetEntityDataPacket`을 플레이어별로 개별 전송해 클라이언트 표시만 달라지게 만든다.

---

## 기존 방식의 문제

Bukkit 엔티티 메타데이터는 서버 전역 상태다. `entity.customName()`을 바꾸면 모든 플레이어가 같은 이름을 본다.

```kotlin
// ❌ Bukkit — 모든 플레이어에게 같은 이름이 보임
zombie.customName(Component.text("Boss Zombie"))
zombie.isCustomNameVisible = true
zombie.isGlowing = true

// 플레이어마다 다르게 보여주려면 개별 패킷을 써야 하는데
// Bukkit API에는 그 방법이 없다
```

특정 플레이어에게만 엔티티를 빛나게 보여주거나, 플레이어가 표적 지정한 몹의 이름을 HUD처럼 표시하거나, VIP에게만 희귀 엔티티를 다르게 보여줄 때 기존 방법으로는 별도 엔티티를 생성하거나 패킷 인터셉션을 구현해야 했다.

---

## EntityViewLayer 사용법

`Arc.isReady`가 `true`인 상태여야 패킷 전송이 작동한다. NMS 브리지가 필요한 기능이다.

```kotlin
val viewLayer = EntityViewLayer(plugin)
// 또는: plugin.entityViewLayer()  (확장 함수 미제공 시 생성자 직접 사용)
```

---

## EntityViewConfig — 재정의 설정

모든 필드가 nullable이다. `null`인 필드는 패킷에 포함되지 않아 클라이언트의 기존 값이 그대로 유지된다.

```kotlin
data class EntityViewConfig(
    val displayName: Component? = null,      // 표시 이름 재정의
    val customNameVisible: Boolean? = null,  // 이름표 항상 표시 여부
    val glowing: Boolean? = null,            // 발광 윤곽선
    val invisible: Boolean? = null,          // 투명 (클라이언트에서만)
)
```

---

## 기본 사용 예시

### 플레이어별 다른 이름 표시

```kotlin
// PlayerA에게는 보스 이름, 다른 플레이어에게는 기본 이름
viewLayer.set(playerA, zombie, EntityViewConfig(
    displayName = Component.text("[BOSS] 어둠의 왕").color(NamedTextColor.RED),
    customNameVisible = true,
    glowing = true,
))

// 나중에 초기화 (vanilla 상태로 복귀)
viewLayer.clear(playerA, zombie)
```

### 특정 플레이어에게만 엔티티 투명 처리

```kotlin
// 스텔스 미션: playerB에게는 경비원이 투명으로 보임
viewLayer.set(playerB, guard, EntityViewConfig(invisible = true))

// 미션 종료 후 원래대로
viewLayer.clear(playerB, guard)
```

### 조준 중인 몹 이름 HUD

```kotlin
plugin.runRepeat(period = 4L) {  // 0.2초마다 갱신
    onlinePlayers.forEach { player ->
        val target = player.getTargetEntity(8) as? LivingEntity

        // 이전 타깃 초기화
        previousTarget[player.uniqueId]?.let { prev ->
            viewLayer.clear(player, prev)
        }

        if (target != null) {
            val hpBar = "♥".repeat((target.health / target.maxHealth * 10).toInt())
            viewLayer.set(player, target, EntityViewConfig(
                displayName = Component.text()
                    .append(Component.text(target.name).color(NamedTextColor.WHITE))
                    .append(Component.text(" §c$hpBar").color(NamedTextColor.RED))
                    .build(),
                customNameVisible = true,
            ))
            previousTarget[player.uniqueId] = target
        } else {
            previousTarget.remove(player.uniqueId)
        }
    }
}
```

---

## set · clear · refresh

```kotlin
// 재정의 등록 (플레이어가 이미 엔티티를 추적 중이면 즉시 패킷 전송)
viewLayer.set(player, entity, config)

// 특정 플레이어/엔티티 재정의 제거
viewLayer.clear(player, entity)

// 해당 플레이어의 모든 재정의 제거 (예: 로그아웃 시)
viewLayer.clearAll(player)

// 재정의 패킷 재전송 (서버가 entity.customName() 등을 변경한 뒤 호출)
viewLayer.refresh(player, entity)

// 해당 플레이어의 모든 재정의 재전송 (월드 재진입 후 등)
viewLayer.refreshAll(player)
```

---

## Java

```java
EntityViewConfig config = new EntityViewConfig(
    Component.text("Boss").color(NamedTextColor.RED),  // displayName
    true,   // customNameVisible
    true,   // glowing
    null    // invisible — 변경 없음
);

viewLayer.set(player, zombie, config);
viewLayer.clear(player, zombie);
```

---

## 전체 예시 — 파티 기반 아군 표시

```kotlin
class PartyViewPlugin : JavaPlugin() {

    private lateinit var viewLayer: EntityViewLayer
    private val partyMembers = mutableMapOf<UUID, UUID>() // player → partyId

    override fun onEnable() {
        viewLayer = EntityViewLayer(this)

        // 플레이어가 엔티티를 추적하기 시작할 때마다
        // 같은 파티인지 확인해 이름 색상 변경
        listen<PlayerTrackEntityEvent> { e ->
            val viewer = e.player
            val target = e.entity as? Player ?: return@listen

            val sameParty = partyMembers[viewer.uniqueId] != null &&
                partyMembers[viewer.uniqueId] == partyMembers[target.uniqueId]

            if (sameParty) {
                viewLayer.set(viewer, target, EntityViewConfig(
                    displayName = Component.text("§a[아군] ${target.name}"),
                    glowing = true,
                ))
            }
        }

        // 파티 변경 시 재정의 갱신
        listen<PlayerQuitEvent> { e ->
            viewLayer.clearAll(e.player)
        }
    }

    fun refreshPartyView(player: Player) {
        viewLayer.refreshAll(player)
    }

    override fun onDisable() {
        viewLayer.close()
    }
}
```

---

## API 레퍼런스

| 메서드 | 설명 |
|--------|------|
| `EntityViewLayer(plugin)` | 레이어 생성 (PlayerTrackEntityEvent·PlayerQuitEvent 자동 등록) |
| `set(player, entity, config)` | 재정의 등록. 이미 추적 중이면 즉시 패킷 전송 |
| `clear(player, entity)` | 특정 재정의 제거 (vanilla 복귀는 클라이언트 재추적 시) |
| `clearAll(player)` | 해당 플레이어의 모든 재정의 제거 |
| `get(player, entity)` | 현재 설정된 `EntityViewConfig` 조회 |
| `refresh(player, entity)` | 재정의 패킷 재전송 |
| `refreshAll(player)` | 해당 플레이어의 모든 재정의 패킷 재전송 |
| `close()` | 전체 상태 비우기 |

---

## 이점과 한계

**이점**
- 엔티티 실제 상태를 변경하지 않아 다른 플러그인과 충돌이 없음
- 가짜 엔티티를 생성할 필요 없이 기존 엔티티에 바로 적용
- 플레이어 퇴장 시 자동 정리
- NMS를 직접 다루지 않고 Arc API 레벨에서 사용 가능

**한계**
- 재정의는 `PlayerTrackEntityEvent` 시점에 한 번 전송된다. 그 이후 Bukkit(또는 다른 플러그인)이 같은 엔티티의 메타데이터를 변경하면 클라이언트가 vanilla 값으로 덮어쓴다. 그 뒤에는 `refresh()`를 직접 불러야 재정의가 다시 적용된다.
- `invisible = true`는 클라이언트 표시만 바꾼다. 서버에서는 엔티티가 투명하지 않아 히트박스가 남아 있고 공격이 가능하다.
- NMS 브리지(`Arc.isReady`)가 필요하다. Arc 없는 일반 Paper 서버에서는 패킷 전송이 무시된다.
- `displayName`에 `Component.empty()`를 넣으면 이름이 지워지는 것처럼 보이지만, vanilla 업데이트 패킷이 오면 원래 이름이 복원된다.
