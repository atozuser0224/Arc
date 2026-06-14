---
title: Plugin API
parent: ArcSync Content
nav_order: 2
---

# Plugin API

`arcContent` DSL은 플러그인 코드에서 Kotlin으로 아이템·블록·가구·레시피를 선언하는 방법이다. 선언은 **트랜잭션**으로 처리되며, 플러그인 생명주기와 자동으로 연동된다.

File Pack 방식과 달리, Plugin API는 플러그인 로직과 콘텐츠 정의를 하나의 코드베이스에서 관리하고 런타임에 동적으로 등록·해제할 수 있어 복잡한 플러그인 시스템에 적합하다.

---

## 기본 사용법

### 의존성 추가

```kotlin
// build.gradle.kts
dependencies {
    compileOnly("dev.arc:arc-api:1.21.4-SNAPSHOT")
}
```

### arcContent DSL 전체 예시

```kotlin
import dev.arc.api.content.*

class ShowcasePlugin : JavaPlugin() {

    override fun onEnable() {
        val result = arcContent {
            // 아이템 정의
            item("showcase/mana_crystal") {
                fallback = "minecraft:amethyst_shard"
                maxStackSize = 64
                order = 10
            }
            item("showcase/arc_blade") {
                fallback = "minecraft:diamond_sword"
                durability = 1561
                maxStackSize = 1
                order = 20
            }
            item("showcase/ancient_tome") {
                fallback = "minecraft:book"
                maxStackSize = 1
                order = 30
            }

            // 블록 정의
            block("showcase/mana_ore") {
                fallback = "minecraft:amethyst_block"
                hardness = 3.0f
                blastResistance = 3.0f
                order = 40
            }
            block("showcase/arcane_furnace") {
                fallback = "minecraft:furnace"
                hardness = 5.0f
                blastResistance = 10.0f
                order = 50
            }

            // 레시피 정의
            recipe("showcase/mana_crystal_recipe") {
                result = ContentId("showcase", "mana_crystal")
                ingredients += ContentId("minecraft", "amethyst_shard")
                ingredients += ContentId("minecraft", "amethyst_shard")
                ingredients += ContentId("minecraft", "glowstone_dust")
            }
            recipe("showcase/arc_blade_recipe") {
                result = ContentId("showcase", "arc_blade")
                ingredients += ContentId("showcase", "mana_crystal")
                ingredients += ContentId("minecraft", "diamond_sword")
            }
        }

        // 등록 결과 확인
        if (!result.accepted) {
            logger.warning("Content pack rejected:")
            result.diagnostics.forEach { diag ->
                logger.warning("  [${diag.severity}] ${diag.message}")
            }
            return
        }

        logger.info("Content registered. Revision: ${result.revision?.id}")
    }

    override fun onDisable() {
        removeArcContent()
    }
}
```

---

## ContentId 형식

`ContentId`는 namespace와 path로 구성된다.

```kotlin
// 생성 방법 1: 생성자
val id = ContentId("magic", "ruby_wand")

// 생성 방법 2: 문자열 파싱
val id = ContentId.parse("magic:ruby_wand")

// 경로가 있는 경우
val id = ContentId.parse("magic:weapons/ruby_wand")

println(id.toString())  // "magic:ruby_wand"
```

### 유효성 규칙

| 부분 | 패턴 | 예시 | 비고 |
|------|------|------|------|
| namespace | `[a-z0-9_.-]+` | `magic`, `my-plugin` | 대문자, 공백 불가 |
| path | `[a-z0-9_.-]+(/[a-z0-9_.-]+)*` | `ruby_wand`, `weapons/ruby_wand` | 슬래시로 계층 구분 가능 |
| traversal | `..` 세그먼트 | `weapons/../ruby_wand` | **거부됨** |

namespace는 서버 전체에서 고유해야 한다. 동일 namespace를 다른 플러그인이 이미 등록하면 설치가 거부된다.

---

## 트랜잭션 보장

`arcContent {}` 블록은 원자적으로 처리된다. 블록 내 항목 중 하나라도 유효하지 않으면 **전체 등록이 거부**되고 이전 revision이 유지된다.

```kotlin
val result = arcContent {
    item("mana_crystal") { fallback = "minecraft:amethyst_shard" }
    item("mana_crystal") { fallback = "minecraft:amethyst_shard" }  // 중복 ID!
    // → 전체 거부, 이전 revision 유지
}

if (!result.accepted) {
    // 반드시 처리할 것
    result.diagnostics.forEach { logger.warning(it.message) }
}
```

**거부되는 경우:**

- 동일 namespace에 중복 content ID
- 유효하지 않은 `fallback` vanilla ID
- namespace 형식 위반 (`..`, 대문자 등)
- 다른 플러그인이 이미 같은 namespace를 등록한 경우
- `ContentId`가 존재하지 않는 다른 팩 항목을 참조

---

## ContentPublishResult

```kotlin
data class ContentPublishResult(
    val accepted: Boolean,
    val revision: CompiledContentRevision?,
    val diagnostics: List<ContentDiagnostic>
)

data class ContentDiagnostic(
    val severity: DiagnosticSeverity,  // ERROR, WARNING, INFO
    val message: String,
    val contentId: ContentId?          // 문제가 된 항목 (있는 경우)
)
```

`accepted == false`이면 `revision`은 `null`이다. `diagnostics`에는 실패 원인이 상세히 기록된다. 운영 환경에서는 ERROR 이상 진단을 로그로 남기고 관리자에게 알리는 것을 권장한다.

---

## onDisable에서 반드시 해제

```kotlin
override fun onDisable() {
    removeArcContent()
}
```

`removeArcContent()`는 이 플러그인이 등록한 모든 콘텐츠를 content revision에서 제거하고 새 revision을 publish한다. 호출하지 않으면 플러그인이 비활성화된 후에도 콘텐츠 정의가 남아, 접속한 클라이언트가 실제로 존재하지 않는 콘텐츠를 받게 된다.

플러그인 핫리로드(`/arc plugin reload`) 시에도 onDisable → onEnable 순서로 호출되므로, 이 패턴을 따르면 핫리로드 후 콘텐츠가 자동으로 갱신된다.

---

## 여러 플러그인 동시 등록

각 플러그인은 독립적인 namespace로 콘텐츠를 등록한다. 한 플러그인이 비활성화되어도 다른 플러그인의 콘텐츠는 영향을 받지 않는다.

```
[Plugin A] namespace: "economy"  → economy:gold_coin, economy:silver_coin
[Plugin B] namespace: "magic"    → magic:ruby_wand, magic:mana_crystal
[Plugin C] namespace: "pvp"      → pvp:arc_blade, pvp:shield_rune

→ content revision에 세 namespace 모두 포함
→ Plugin B 비활성화 → magic:* 제거, economy/pvp 콘텐츠 유지
```

### namespace 충돌 시 동작

```kotlin
// Plugin A가 이미 "magic"으로 등록한 상태에서 Plugin B가 같은 namespace 시도
val result = arcContent {
    item("magic/some_item") { ... }  // namespace "magic"
}
// result.accepted == false
// result.diagnostics: "namespace 'magic' already registered by plugin 'PluginA'"
```

namespace 충돌을 방지하기 위해 플러그인 이름과 namespace를 동일하게 유지하거나, 팀 접두사를 사용하는 것을 권장한다 (예: `myteam_magic`).

---

## 아이템 스택 접근

플러그인 코드에서 content ID로 아이템을 생성하거나 확인할 수 있다.

```kotlin
// content ID로 아이템 생성
val crystal = ContentItems.createStack(ContentId.parse("showcase/mana_crystal"), amount = 5)

// 아이템이 특정 content ID인지 확인
val item: ItemStack = player.inventory.itemInMainHand
val contentId: ContentId? = ContentItems.getContentId(item)
if (contentId == ContentId.parse("showcase/arc_blade")) {
    // 아크 블레이드 처리
}
```

Arc 클라이언트가 없는 플레이어도 동일한 `ItemStack`을 받는다. Arc 클라이언트는 `CUSTOM_DATA["arc:id"]`를 읽어 커스텀 모델을 적용하고, 일반 클라이언트는 `fallback` 아이템으로 표시한다.

---

## 장점과 단점

### 장점

- **선언적 API**: 코드 구조가 콘텐츠 구조와 일치해 읽기 쉽다.
- **트랜잭션 보장**: 부분 등록이 없어 서버 상태가 항상 일관된다.
- **플러그인 생명주기 자동 연동**: `removeArcContent()` 한 줄로 완전 정리.
- **핫리로드 지원**: `/arc plugin reload` 시 콘텐츠 자동 갱신.
- **다중 플러그인 격리**: namespace로 완전히 분리되어 플러그인 간 간섭 없음.

### 단점

- **런타임 vanilla ID 등록 불가**: fallback은 기존 vanilla 아이템만 지정할 수 있다. 새 숫자 ID를 서버 레지스트리에 추가하는 것은 지원하지 않는다.
- **namespace 충돌 시 전체 거부**: 동일 namespace가 충돌하면 나중에 등록하는 플러그인이 완전히 거부된다. 플러그인 배포 시 namespace 고유성을 사전에 확인해야 한다.
- **클라이언트 에셋은 별도 포함 필요**: 텍스처, 모델 파일은 Plugin API가 자동으로 생성하지 않는다. File Pack의 `assets/` 디렉토리에 직접 배치하거나, 플러그인 JAR 안에서 추출해 `arc-content/`에 복사하는 로직을 별도로 구현해야 한다.
