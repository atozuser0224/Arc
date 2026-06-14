---
title: 엔티티 · AI
parent: Developer API
nav_order: 4
---

# 엔티티 · AI

Arc API는 엔티티 스폰, 근처 엔티티 탐색, 체력 조작에 제네릭 기반 유틸리티를 제공합니다. 캐스팅 없이 원하는 엔티티 타입을 직접 다룰 수 있으며, 초기화 블록으로 스폰과 설정을 한 번에 처리합니다.

---

## Bukkit 방식의 문제

Bukkit에서 엔티티를 스폰하고 설정하려면 캐스팅이 필요합니다.

```kotlin
// Bukkit 방식 — 스폰 후 캐스팅
val entity = world.spawnEntity(location, EntityType.ZOMBIE)
val zombie = entity as? Zombie ?: return  // 캐스팅 실패 가능

// 초기화 작업
zombie.customName(Component.text("§4Dark King"))
zombie.isCustomNameVisible = true
zombie.health = 200.0
zombie.equipment?.helmet = ItemStack(Material.NETHERITE_HELMET)
zombie.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, Int.MAX_VALUE, 2))
```

근처 엔티티 탐색도 타입 필터링이 번거롭습니다.

```kotlin
// Bukkit 방식 — 타입 필터링
val nearby = location.getNearbyEntities(16.0, 16.0, 16.0)
    .filterIsInstance<Player>()

// 특정 타입 하나만 얻기
val nearestZombie = location.getNearbyEntities(10.0, 10.0, 10.0)
    .filterIsInstance<Zombie>()
    .minByOrNull { it.location.distance(location) }
```

---

## 타입 안전 스폰

`world.spawnEntity<T>(location) { }` 는 스폰과 초기화를 하나의 블록으로 처리합니다. 반환 타입이 제네릭으로 결정되므로 캐스팅이 필요하지 않습니다.

```kotlin
// Arc 방식 — 캐스팅 없음, 초기화 블록
val boss: Zombie = world.spawnEntity<Zombie>(spawnLocation) { zombie ->
    zombie.customName(Component.text("§4[BOSS] Dark King"))
    zombie.isCustomNameVisible = true
    zombie.health = 200.0
    zombie.isInvulnerable = false
    zombie.equipment?.apply {
        helmet = item(Material.NETHERITE_HELMET) { unbreakable() }
        chestplate = item(Material.NETHERITE_CHESTPLATE) { unbreakable() }
        itemInMainHand = item(Material.NETHERITE_SWORD) {
            enchant(Enchantment.SHARPNESS, 5)
            unbreakable()
        }
    }
    zombie.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, Int.MAX_VALUE, 2))
    zombie.addPotionEffect(PotionEffect(PotionEffectType.SPEED, Int.MAX_VALUE, 1))
}

// boss는 이미 Zombie 타입 — 추가 캐스팅 불필요
boss.target = player
```

다양한 엔티티 타입에 동일하게 적용됩니다.

```kotlin
// 위더 보스
val wither: Wither = world.spawnEntity<Wither>(spawnLocation) { w ->
    w.customName(Component.text("§4이벤트 위더"))
    w.health = 600.0
}

// 아머 스탠드 (장식용)
val stand: ArmorStand = world.spawnEntity<ArmorStand>(location) { s ->
    s.isVisible = false
    s.isGravity = false
    s.equipment?.helmet = item(Material.CARVED_PUMPKIN) {}
}

// 빛나는 파티클 효과를 위한 Slime
val slime: Slime = world.spawnEntity<Slime>(location) { s ->
    s.size = 0  // 크기 0 — 보이지 않음
    s.isGlowing = true
}
```

---

## 근처 엔티티 필터링

Arc는 `Location` 확장 함수로 타입 안전한 엔티티 탐색을 제공합니다.

### nearbyPlayers — 근처 플레이어

```kotlin
// 반경 16블록 내 모든 플레이어
val players: List<Player> = location.nearbyPlayers(radius = 16.0)

// 활용 예 — 폭발 범위 피해
fun applyExplosionDamage(center: Location, radius: Double, damage: Double) {
    center.nearbyPlayers(radius).forEach { player ->
        val dist = player.location.distance(center)
        val actualDamage = damage * (1.0 - dist / radius)  // 거리에 따라 감소
        player.damage(actualDamage)
        val knockback = center.toVector().subtract(player.location.toVector())
            .normalize().multiply(-1.5)
        player.velocity = knockback
    }
}
```

### nearbyOfType — 타입별 필터링

```kotlin
// 반경 32블록 내 모든 몬스터
val monsters: List<Monster> = location.nearbyOfType<Monster>(radius = 32.0)

// 반경 10블록 내 모든 Zombie
val zombies: List<Zombie> = location.nearbyOfType<Zombie>(radius = 10.0)

// 활용 예 — 주변 몬스터 처치
fun clearMobs(center: Location) {
    center.nearbyOfType<Monster>(radius = 20.0).forEach { mob ->
        mob.remove()
        center.world?.spawnParticle(Particle.EXPLOSION_LARGE, mob.location, 1)
    }
}
```

### nearestOfType — 가장 가까운 엔티티

```kotlin
// 가장 가까운 Zombie (없으면 null)
val nearest: Zombie? = location.nearestOfType<Zombie>(radius = 10.0)

// 활용 예 — 가장 가까운 적에게 유도
fun guideMissile(projectile: Projectile, radius: Double) {
    val target = projectile.location.nearestOfType<Monster>(radius)
        ?: return  // 범위 내 몬스터 없음

    val direction = target.location.subtract(projectile.location).toVector().normalize()
    projectile.velocity = projectile.velocity.lerp(direction.multiply(2.0), 0.3)
}
```

**Bukkit 방식과 비교**:

| 작업 | Bukkit | Arc |
|------|--------|-----|
| 근처 플레이어 | `getNearbyEntities().filterIsInstance<Player>()` | `nearbyPlayers(radius)` |
| 타입 필터 | `getNearbyEntitiesByType(cls, x, y, z)` | `nearbyOfType<T>(radius)` |
| 가장 가까운 것 | `filterIsInstance<T>().minByOrNull { dist }` | `nearestOfType<T>(radius)` |

Arc의 `nearbyOfType<T>(radius)`는 구(球) 범위로 탐색합니다. Bukkit의 `getNearbyEntities(x, y, z)`는 직육면체 범위입니다. 원형 범위가 필요하다면 Arc 방식이 더 직관적입니다.

---

## 체력 초기화

`entity.healFully()`는 엔티티의 체력과 관련 상태를 초기화합니다.

```kotlin
// 보스 리셋
boss.healFully()   // 최대 체력으로 회복 + 포션 효과 제거

// 게임 시작 시 모든 플레이어 체력 초기화
players.forEach { player ->
    player.healFully()
    player.foodLevel = 20
    player.saturation = 20f
}
```

내부적으로 `entity.health = entity.maxHealth`를 설정하고, `ActivePotionEffect`를 제거하며, 화염/익사 상태를 초기화합니다.

---

## 커스텀 AI 설정

Arc API는 NMS 수준의 커스텀 AI 설정을 직접 지원하지 않습니다. 커스텀 패스파인더 목표, 커스텀 AI 트리 등은 별도의 NMS 접근이 필요합니다. Arc 서버의 NMS 브리지를 사용하거나, PathfinderGoal API를 직접 활용하는 것을 권장합니다.

```kotlin
// Arc NMS 브리지를 통한 커스텀 AI 설정 예
val nmsEntity = boss.nms<net.minecraft.world.entity.monster.Zombie>()
nmsEntity.goalSelector.addGoal(0, CustomAttackGoal(nmsEntity, player))
```

---

## 장점과 단점

### 장점

- **캐스팅 불필요**: 제네릭 타입 파라미터로 반환 타입이 결정됩니다.
- **초기화 블록**: 스폰과 설정이 한 블록에서 원자적으로 실행됩니다. 스폰된 직후 엔티티가 AI를 시작하기 전에 속성을 설정할 수 있습니다.
- **구형 탐색 범위**: `nearbyOfType<T>(radius)`는 단일 radius 값으로 구형 범위를 정의합니다.
- **코드 간결성**: `filterIsInstance` 체인 없이 원하는 타입을 바로 얻습니다.

### 단점

- **NMS 의존 기능 제외**: 커스텀 패스파인더, 브레인(Brain) AI, 감각(Sensing) 시스템 등 NMS 수준 조작은 직접 구현해야 합니다.
- **엔티티 타입 오류**: 잘못된 타입 파라미터(예: `spawnEntity<Player>`)는 컴파일 타임에는 통과하지만 런타임에 예외가 발생할 수 있습니다.

---

## 전체 예시 — 미니보스 소환 시스템

```kotlin
fun spawnMiniBoss(plugin: JavaPlugin, location: Location): Zombie {
    val boss = location.world!!.spawnEntity<Zombie>(location) { z ->
        z.customName(Component.text("§4[미니보스] Shadow Zombie"))
        z.isCustomNameVisible = true
        z.health = 100.0
        z.equipment?.apply {
            helmet = item(Material.IRON_HELMET) { enchant(Enchantment.PROTECTION, 4) }
            chestplate = item(Material.DIAMOND_CHESTPLATE) { enchant(Enchantment.PROTECTION, 4) }
            itemInMainHand = item(Material.IRON_SWORD) { enchant(Enchantment.SHARPNESS, 3) }
        }
        z.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, Int.MAX_VALUE, 1))
    }

    // 소환 효과
    location.world!!.spawnParticle(Particle.SMOKE_LARGE, location, 30, 0.5, 1.0, 0.5, 0.05)
    location.world!!.playSound(location, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.5f)

    // 보스 사망 감지
    plugin.runWhile(period = 10L) {
        if (!boss.isValid || boss.isDead) {
            location.nearbyPlayers(30.0).forEach {
                it.sendMessage("§a미니보스를 처치했습니다!")
            }
            dropBossLoot(location)
            return@runWhile false
        }
        // 체력 30% 이하 — 분노 모드
        if (boss.health < 30.0 && !boss.hasPotionEffect(PotionEffectType.SPEED)) {
            boss.addPotionEffect(PotionEffect(PotionEffectType.SPEED, Int.MAX_VALUE, 2))
            location.nearbyPlayers(30.0).forEach {
                it.sendMessage("§c보스가 분노했습니다!")
            }
        }
        true
    }

    return boss
}
```
