---
title: 플레이어 데이터 · PDC
parent: Developer API
nav_order: 5
---

# 플레이어 데이터 · PDC

Arc API는 Bukkit의 `PersistentDataContainer(PDC)`를 세 가지 레이어로 감쌉니다. 타입 안전 접근자, 버전 관리 스키마(`PdcSchema`), 선언적 플레이어 데이터 DSL(`playerDataSchema`)을 통해 `NamespacedKey`와 `PersistentDataType` 토큰을 직접 다루지 않아도 됩니다.

---

## Bukkit 방식의 문제

Bukkit PDC를 사용하려면 키와 타입 토큰을 매번 지정해야 합니다.

```kotlin
// Bukkit 방식 — 세 가지 문제
val key = NamespacedKey(plugin, "coins")

// 1. 타입 토큰 직접 지정
val current = player.persistentDataContainer.get(key, PersistentDataType.INTEGER) ?: 0

// 2. null 처리 필수
player.persistentDataContainer.set(key, PersistentDataType.INTEGER, current + 100)

// 3. Boolean은 PersistentDataType에 없어서 Byte로 변환해야 함
val boolKey = NamespacedKey(plugin, "premium")
val isPremium = (player.persistentDataContainer.get(boolKey, PersistentDataType.BYTE) ?: 0) != 0.toByte()
player.persistentDataContainer.set(boolKey, PersistentDataType.BYTE, if (isPremium) 1 else 0)
```

`PersistentDataType` 토큰을 잘못 지정하면 `ClassCastException`이 런타임에 발생합니다. 컴파일 타임에 잡을 수 없습니다.

---

## 타입 안전 PDC 접근자

`plugin.key()` + 타입 접근자 조합으로 이 문제를 해결합니다.

```kotlin
// 키 생성 — 재사용을 위해 companion object 또는 상수로 선언 권장
val coinsKey = plugin.key("coins")
val levelKey = plugin.key("level")
val nameKey = plugin.key("display_name")
val premiumKey = plugin.key("premium")

// 정수
val coins = player.getInt(coinsKey, default = 0)
player.setInt(coinsKey, coins + 100)

// Long (대용량 숫자)
val xp = player.getLong(levelKey, default = 0L)
player.setLong(levelKey, xp + 500L)

// String
val displayName = player.getString(nameKey, default = player.name)
player.setString(nameKey, "§a${player.name}")

// Boolean — 내부적으로 Byte로 저장 (PDC는 Boolean 타입 미지원)
val isPremium = player.getBoolean(premiumKey, default = false)
player.setBoolean(premiumKey, true)

// 키 존재 확인 및 삭제
if (player.hasKey(coinsKey)) {
    player.removeKey(coinsKey)
}
```

**지원 타입과 내부 저장 방식**:

| Arc 메서드 | 내부 PersistentDataType | 비고 |
|-----------|------------------------|------|
| `getInt` / `setInt` | `INTEGER` | |
| `getLong` / `setLong` | `LONG` | |
| `getString` / `setString` | `STRING` | |
| `getBoolean` / `setBoolean` | `BYTE` | 0/1 변환 |
| `getDouble` / `setDouble` | `DOUBLE` | |
| `getFloat` / `setFloat` | `FLOAT` | |

**컴파일 타임 타입 안전**: 잘못된 타입으로 읽으면 컴파일 오류가 발생합니다. 런타임 `ClassCastException`이 없습니다.

---

## PdcSchema — 버전 관리 PDC

플러그인을 업데이트할 때 기존 플레이어 데이터의 형식이 바뀌는 경우가 있습니다. `PdcSchema`는 스키마 버전을 PDC에 함께 저장하여, 오래된 데이터를 자동으로 마이그레이션합니다.

### 기본 사용법

```kotlin
val schema = PdcSchema.define(plugin) {
    version(2)  // 현재 스키마 버전

    // 필드 선언
    field("level", DataType.INTEGER, default = 1)
    field("xp", DataType.LONG, default = 0L)
    field("title", DataType.STRING, default = "신입")

    // 검증 — 유효하지 않은 데이터는 기본값으로 리셋
    validate("level") { value -> (value as? Int ?: 0) in 1..100 }
    validate("xp") { value -> (value as? Long ?: 0L) >= 0L }
}

// 데이터 읽기
val level = schema.get(player, "level") as Int
val xp = schema.get(player, "xp") as Long

// 데이터 쓰기
schema.set(player, "xp", xp + 500L)
schema.set(player, "level", level + 1)
```

### 마이그레이션

버전이 올라갈 때 기존 데이터를 변환합니다.

```kotlin
val schema = PdcSchema.define(plugin) {
    version(3)

    field("level", DataType.INTEGER, default = 1)
    field("xp", DataType.LONG, default = 0L)
    field("rank", DataType.STRING, default = "Bronze")  // v3에서 추가된 필드

    // v1 → v2: xp를 100배 (단위 변경)
    migrate(from = 1, to = 2) { data ->
        val oldXp = data["xp"] as? Int ?: 0
        data["xp"] = oldXp.toLong() * 100L
    }

    // v2 → v3: level에 따라 rank 자동 할당
    migrate(from = 2, to = 3) { data ->
        val level = data["level"] as? Int ?: 1
        data["rank"] = when {
            level >= 50 -> "Diamond"
            level >= 30 -> "Gold"
            level >= 10 -> "Silver"
            else -> "Bronze"
        }
    }
}
```

플레이어가 접속할 때 `schema.load(player)`를 호출하면 PDC에 저장된 버전을 확인하고, 현재 버전보다 낮으면 순차적으로 마이그레이션을 적용합니다.

```kotlin
@EventHandler
fun onPlayerJoin(event: PlayerJoinEvent) {
    schema.load(event.player)  // 버전 확인 + 마이그레이션 자동 실행
}
```

**사용 시나리오**: 
- v1 → v2: 경험치 단위를 정수에서 Long으로 변경.
- v2 → v3: 새 필드(rank) 추가, 기존 level 데이터에서 자동 계산.
- v3 → v4: String 필드를 enum 인덱스로 교체.

---

## PlayerDataSchema (playerDataSchema DSL)

더 선언적인 방식으로 플레이어 데이터를 정의합니다. 타입 안전성과 기본값을 한 번에 선언합니다.

```kotlin
// 스키마 정의 (companion object 또는 최상위 선언)
val PlayerStats = playerDataSchema(plugin) {
    int("level", default = 1)
    long("xp", default = 0L)
    string("title", default = "신입")
    boolean("premium", default = false)
}

// 사용
val level = PlayerStats.level.get(player)             // Int (null 불가)
val xp = PlayerStats.xp.get(player)                  // Long
PlayerStats.xp.set(player, xp + 500L)

// increment 헬퍼 — Long/Int 필드에 값 더하기
PlayerStats.xp.increment(player, 500L)
PlayerStats.level.increment(player, 1)

// getOrDefault — 값이 없으면 기본값 반환하되 저장하지 않음
val title = PlayerStats.title.getOrDefault(player)
```

### PlayerStore

반복 접근이 많은 경우 `PlayerStore<V, D>`로 플레이어별 캐시를 구성합니다.

```kotlin
val coinStore: PlayerStore<Int, Int> = PlayerStats.coins.asStore(
    serialize = { it },
    deserialize = { it }
)

// 온라인 플레이어 전체 코인 조회 — 캐시 활용
Bukkit.getOnlinePlayers().forEach { player ->
    val coins = coinStore.get(player)
    // ...
}
```

---

## 장점과 단점

### 장점

- **컴파일 타임 타입 검증**: 잘못된 타입으로 읽으면 컴파일 오류 발생. 런타임 `ClassCastException` 없음.
- **기본값 내장**: null 체크 코드가 사라집니다.
- **Boolean 지원**: `PersistentDataType.BYTE`로의 변환을 자동 처리.
- **마이그레이션 지원**: 플러그인 업데이트 시 기존 데이터를 안전하게 변환.
- **버전 추적**: 스키마 버전이 PDC에 함께 저장되어 데이터 상태를 명확히 파악 가능.

### 단점

- **키 충돌 주의**: Arc 타입 안전 접근자와 Bukkit 직접 PDC 코드를 혼용하면 같은 키를 다른 타입으로 읽으려 할 때 예외가 발생할 수 있습니다. 하나의 키는 하나의 방식으로만 접근하세요.
- **스키마 변경 비용**: `PdcSchema` 필드 이름을 바꾸면 기존 데이터와 호환이 깨집니다. 마이그레이션을 작성해야 합니다.
- **직렬화 제한**: PDC는 기본 타입과 String만 저장 가능합니다. 복잡한 구조체는 JSON 직렬화 후 String으로 저장해야 합니다.

```kotlin
// 복잡한 구조체 — JSON 직렬화 후 String 저장
data class Inventory(val items: List<String>, val gold: Int)

val inventoryKey = plugin.key("saved_inventory")
val json = Json.encodeToString(inventory)
player.setString(inventoryKey, json)

val loaded = Json.decodeFromString<Inventory>(player.getString(inventoryKey, "{}"))
```

---

## 전체 예시 — 레벨 시스템

```kotlin
class LevelSystem(val plugin: JavaPlugin) {

    // 스키마 정의
    private val schema = PdcSchema.define(plugin) {
        version(2)
        field("level", DataType.INTEGER, default = 1)
        field("xp", DataType.LONG, default = 0L)
        field("total_kills", DataType.INTEGER, default = 0)

        // v1 → v2: xp를 Int에서 Long으로 마이그레이션
        migrate(from = 1, to = 2) { data ->
            data["xp"] = (data["xp"] as? Int ?: 0).toLong()
        }
    }

    fun onPlayerJoin(player: Player) {
        schema.load(player)  // 마이그레이션 자동 실행
    }

    fun addXp(player: Player, amount: Long) {
        val xp = schema.get(player, "xp") as Long + amount
        schema.set(player, "xp", xp)
        checkLevelUp(player, xp)
    }

    private fun checkLevelUp(player: Player, xp: Long) {
        val level = schema.get(player, "level") as Int
        val required = level * 1000L
        if (xp >= required) {
            schema.set(player, "level", level + 1)
            schema.set(player, "xp", xp - required)
            player.sendMessage("§a레벨 업! 현재 레벨: §e${level + 1}")
            player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
        }
    }

    fun getLevel(player: Player): Int = schema.get(player, "level") as Int
    fun getXp(player: Player): Long = schema.get(player, "xp") as Long
}
```
