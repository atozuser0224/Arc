---
title: Developer API
nav_order: 4
has_children: true
---

# Developer API

`arc-api.jar`는 Bukkit/Paper 위에 올라가는 Kotlin-first 라이브러리입니다. Arc 서버 없이도 일반 Paper 서버에서 의존성으로 추가하여 사용할 수 있으며, 서버를 재시작하지 않고 런타임에 기능을 등록·해제할 수 있습니다.

## arc-api란?

기존 Bukkit API는 Java 중심으로 설계되어 Kotlin에서 사용할 때 보일러플레이트 코드가 상당히 많습니다. 예를 들어, 아이템 하나를 만들 때도 `ItemMeta` 캐스팅, null 처리, `setItemMeta()` 호출이 모두 필요합니다. 커맨드를 하나 추가하려면 `plugin.yml` 선언, `CommandExecutor` 구현, `TabCompleter` 구현 등 세 군데를 수정해야 합니다.

`arc-api`는 다음 원칙으로 이 문제를 해결합니다.

- **람다 기반 DSL**: 익명 클래스 대신 람다로 동작을 정의합니다.
- **타입 안전**: 제네릭과 인라인 함수를 활용해 캐스팅을 최소화합니다.
- **생명주기 연동**: 플러그인 비활성화 시 등록된 이벤트·태스크·GUI가 자동으로 해제됩니다.
- **서버 없는 테스트**: `arc-test` 모듈을 통해 CI에서 JVM만으로 플러그인 로직을 검증할 수 있습니다.

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

Paper 서버에 `arc-api.jar`를 `plugins/` 폴더에 넣거나, shaded jar로 빌드하여 자체 배포할 수 있습니다. Arc 서버에는 이미 내장되어 있으므로 별도 설치가 필요하지 않습니다.

## 서브섹션

### [스케줄러 · 태스크](api/scheduler/)

람다 기반 태스크 스케줄링 API입니다. `BukkitRunnable`의 익명 클래스 패턴을 대체하며, 지연 실행·반복·비동기·조건부 반복을 간결하게 표현할 수 있습니다. 플러그인 생명주기와 자동 연동되어 플러그인이 비활성화되면 등록된 태스크가 자동으로 취소됩니다.

### [아이템 시스템](api/items/)

`item { }` DSL로 아이템을 선언적으로 생성합니다. `ItemBehavior`로 특정 아이템에 클릭·드랍 동작을 바인딩하고, `ItemAuthenticator`로 HMAC-SHA256 기반 위변조 감지를 제공합니다. 서버 외부에서 생성된 아이템을 효과적으로 차단할 수 있습니다.

### [GUI 시스템](api/gui/)

인벤토리 GUI를 이벤트 등록 없이 구성합니다. `InventoryHolder`, `Listener`, `HandlerList` 등록을 직접 다룰 필요가 없습니다. 단일 페이지 메뉴, 페이지네이션 메뉴, 채팅 입력 대기(`ChatInput`)를 지원합니다.

### [엔티티 · AI](api/entities/)

타입 안전 엔티티 스폰, 근처 엔티티 필터링, 체력 초기화 등 엔티티 조작 유틸리티를 제공합니다. 제네릭 기반으로 불필요한 캐스팅 없이 원하는 엔티티 타입을 직접 다룰 수 있습니다.

### [플레이어 데이터 · PDC](api/pdc/)

`NamespacedKey`와 `PersistentDataType` 토큰을 감추는 타입 안전 PDC 접근자와, 버전 마이그레이션을 지원하는 `PdcSchema`를 제공합니다. 플러그인 업데이트 시 기존 플레이어 데이터를 안전하게 마이그레이션할 수 있습니다.

### [커맨드 프레임워크](api/commands/)

`plugin.yml` 커맨드 선언 없이 런타임에 커맨드를 등록합니다. 서브커맨드와 탭 완성을 DSL로 선언하여, 기존의 if/else 체인 방식보다 훨씬 간결하고 유지보수하기 쉬운 커맨드 구조를 만들 수 있습니다.

### [월드 · 청크 유틸리티](api/world/)

블록 영역 스냅샷/복원(`WorldSnapshot`), 레이트레이스(`RayTrace`), 청크 강제 로드 유틸리티를 제공합니다. 미니게임 맵 리셋, 조준 아이템, 청크 기반 시스템 구축에 활용합니다.

### [유틸리티 패키지](api/utilities/)

텍스트 포맷, 시간 DSL, 영역(`Cuboid`), 스플라인 경로(`Spline`), 아이템 직렬화(`ItemSerializer`) 등 범용 유틸리티 모음입니다. 플러그인 개발에서 반복적으로 필요한 기능을 한 패키지에서 제공합니다.

### [arc-test — 서버 없는 테스트](api/testing/)

실제 서버 없이 JVM에서 Arc API 로직을 테스트합니다. CI 피드백 루프를 수 분에서 수 초로 단축합니다. 아이템 로직, GUI 행동, PDC 읽기/쓰기, 커맨드 파싱, 패킷 전송 등을 단위 테스트로 검증할 수 있습니다.

### [Java 호환 레이어](api/java-interop/)

arc-api의 핵심 함수(`inline reified listen<T>`, Kotlin DSL 빌더)를 Java에서 직접 호출하기 위한 브릿지 클래스 모음입니다. `ArcEvents`, `ArcItems`, `ArcCommands`, `ArcContentDsl`, `ArcCustomEffects` 다섯 가지 static 진입점을 제공하며, 기존 Java 플러그인을 코드베이스 변경 없이 Arc로 이전하거나 혼용할 수 있습니다.
