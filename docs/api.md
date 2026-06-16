---
title: Developer API
nav_order: 4
has_children: true
---

# Developer API

`arc-api.jar`는 Bukkit/Paper 위에서 동작하는 Kotlin 우선 라이브러리입니다. Arc 서버가 없는 일반 Paper 서버에서도 의존성으로 추가해 쓸 수 있고, 서버를 다시 시작하지 않고도 런타임에 기능을 등록하거나 해제할 수 있습니다.

## arc-api란?

기존 Bukkit API는 Java를 중심으로 설계된 탓에 Kotlin에서 쓰려면 보일러플레이트가 꽤 많이 따라붙습니다. 아이템 하나를 만들 때만 해도 `ItemMeta` 캐스팅과 null 처리, `setItemMeta()` 호출이 전부 필요합니다. 커맨드 하나를 추가하려면 `plugin.yml` 선언, `CommandExecutor` 구현, `TabCompleter` 구현까지 세 군데를 손봐야 합니다.

`arc-api`는 이 문제를 다음 원칙으로 풀어냅니다.

- **람다 기반 DSL**: 익명 클래스 대신 람다로 동작을 정의합니다.
- **타입 안전**: 제네릭과 인라인 함수를 활용해 캐스팅을 최소화합니다.
- **생명주기 연동**: 플러그인이 비활성화되면 등록해 둔 이벤트·태스크·GUI가 자동으로 해제됩니다.
- **서버 없는 테스트**: `arc-test` 모듈로 CI에서 JVM만 가지고 플러그인 로직을 검증할 수 있습니다.

## 의존성 추가

```kotlin
// build.gradle.kts
repositories {
    maven("https://repo.arc.dev/releases")
}

dependencies {
    // 플러그인 서버에 arc-api.jar가 설치된 경우
    compileOnly("dev.arc:arc-api:1.21.4-SNAPSHOT")

    // 독립 배포 (arc-api를 shaded jar에 포함)
    // implementation("dev.arc:arc-api:1.21.4-SNAPSHOT")

    // 서버 없는 테스트용
    testImplementation("dev.arc:arc-test:1.21.4-SNAPSHOT")
}
```

`arc-api.jar`를 Paper 서버의 `plugins/` 폴더에 넣거나, shaded jar로 빌드해 직접 배포하면 됩니다. Arc 서버에는 이미 들어 있어 따로 설치할 필요가 없습니다.

## 서브섹션

### [스케줄러 · 태스크](api/scheduler/)

람다로 태스크를 예약하는 API입니다. `BukkitRunnable`의 익명 클래스 패턴을 대신하며, 지연 실행과 반복, 비동기, 조건부 반복을 간결하게 표현합니다. 플러그인 생명주기와 맞물려 있어 플러그인이 꺼지면 등록한 태스크도 함께 취소됩니다.

### [아이템 시스템](api/items/)

`item { }` DSL로 아이템을 선언적으로 만듭니다. `ItemBehavior`로 특정 아이템에 클릭·드랍 동작을 묶고, `ItemAuthenticator`로 HMAC-SHA256 기반 위변조 감지를 제공합니다. 서버 밖에서 들어온 아이템을 차단할 수 있습니다.

### [GUI 시스템](api/gui/)

인벤토리 GUI를 이벤트 등록 없이 구성합니다. `InventoryHolder`나 `Listener`, `HandlerList` 등록을 직접 다룰 필요가 없습니다. 단일 페이지 메뉴와 페이지네이션 메뉴, 채팅 입력 대기(`ChatInput`)를 지원합니다.

### [엔티티 · AI](api/entities/)

타입 안전 엔티티 스폰, 근처 엔티티 필터링, 체력 초기화처럼 엔티티를 다루는 유틸리티를 제공합니다. 제네릭을 기반으로 해서 불필요한 캐스팅 없이 원하는 엔티티 타입을 곧장 다룰 수 있습니다.

### [플레이어 데이터 · PDC](api/pdc/)

`NamespacedKey`와 `PersistentDataType` 토큰을 감춘 타입 안전 PDC 접근자, 그리고 버전 마이그레이션을 지원하는 `PdcSchema`를 제공합니다. 플러그인을 업데이트할 때 기존 플레이어 데이터를 안전하게 옮길 수 있습니다.

### [커맨드 프레임워크](api/commands/)

`plugin.yml`에 커맨드를 선언하지 않고 런타임에 등록합니다. 서브커맨드와 탭 완성을 DSL로 선언하므로, 기존 if/else 체인 방식보다 훨씬 간결하고 손보기 쉬운 커맨드 구조를 만들 수 있습니다.

### [월드 · 청크 유틸리티](api/world/)

블록 영역 스냅샷과 복원(`WorldSnapshot`), 레이트레이스(`RayTrace`), 청크 강제 로드 유틸리티를 제공합니다. 미니게임 맵 리셋이나 조준 아이템, 청크 기반 시스템을 만들 때 씁니다.

### [유틸리티 패키지](api/utilities/)

텍스트 포맷, 시간 DSL, 영역(`Cuboid`), 스플라인 경로(`Spline`), 아이템 직렬화(`ItemSerializer`) 같은 범용 유틸리티 모음입니다. 플러그인을 개발하다 보면 반복해서 필요해지는 기능을 한 패키지에 담았습니다.

### [arc-test — 서버 없는 테스트](api/testing/)

실제 서버 없이 JVM에서 Arc API 로직을 테스트합니다. 몇 분 걸리던 CI 피드백을 몇 초로 줄여 줍니다. 아이템 로직, GUI 동작, PDC 읽기/쓰기, 커맨드 파싱, 패킷 전송 등을 단위 테스트로 검증할 수 있습니다.

### [플레이어 쿨다운](api/cooldown/)

`PlayerCooldown`은 플레이어별·키별 쿨다운 트래커입니다. Bukkit 내장 `setCooldown(Material, ticks)`이 커버하지 못하는 임의 문자열 키를 지원하며, 만료 검사 O(1), 플레이어 퇴장 시 자동 정리, `tryUse` · `progress` 같은 편의 API를 제공합니다.

### [인벤토리 트랜잭션](api/inventory/)

`ArcInventoryTransactionEvent`는 Bukkit의 복잡한 인벤토리 이벤트(Click + Drag + Craft)를 `TransactionKind` 열거형 하나로 합칩니다. 플레이어가 무엇을 하려 했는지를 이벤트 하나로 알 수 있으며, 취소 시 rawEvent도 자동으로 함께 취소됩니다.

### [블록 감시](api/block-watch/)

`BlockWatchRegistry`는 특정 블록 위치에 변경 감지 구독을 겁니다. 청크 단위 인덱싱으로 전체 구독자를 순회하지 않아 감시 위치가 수백 개여도 성능에 거의 영향이 없습니다. BREAK · EXPLODE · PISTON 등 10가지 원인을 `BlockChangeCause` 하나로 구분합니다.

### [영역 감시](api/area-watch/)

`AreaWatchRegistry`는 `Cuboid` 영역의 플레이어 진입·이탈을 감지합니다. `PlayerMoveEvent`를 직접 구독하는 것보다 코드가 간결하며, 텔레포트·월드 이동·로그아웃 같은 엣지 케이스를 자동으로 처리합니다.

### [엔티티 뷰 레이어](api/entity-view/)

`EntityViewLayer`는 플레이어마다 엔티티의 표시 이름, 발광 효과, 투명도를 다르게 보여줍니다. 엔티티 실제 상태를 건드리지 않고 `ClientboundSetEntityDataPacket`을 개별 전송하므로 다른 플러그인과 충돌이 없습니다.
