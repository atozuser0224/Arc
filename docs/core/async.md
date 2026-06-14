---
title: 코루틴 · 비동기
parent: Fork Core
nav_order: 5
---

# 코루틴 · 비동기

Arc는 Bukkit의 전통적인 콜백 기반 비동기 API를 대체하는 현대적인 비동기 처리 인터페이스를 제공한다. Kotlin 코루틴 스타일의 API와 CompletableFuture 체인을 통해, 비동기 플러그인 코드를 더 읽기 쉽고 에러가 적게 발생하도록 작성할 수 있다.

---

## Bukkit 기존 방식의 한계

Bukkit의 `BukkitScheduler.runTaskAsynchronously()`는 단순한 비동기 실행에는 충분하지만, 복잡한 비동기 로직에서는 심각한 문제를 낳는다.

**콜백 지옥 예시:**
```kotlin
// ❌ Bukkit 방식 — 콜백 중첩이 깊어짐
scheduler.runTaskAsynchronously(plugin) {
    val data = database.loadPlayer(uuid)           // IO 스레드에서 실행
    scheduler.runTask(plugin) {                    // 메인 스레드로 복귀
        player.sendMessage("데이터 로드: ${data.name}")
        scheduler.runTaskAsynchronously(plugin) {  // 다시 비동기
            database.updateLastSeen(uuid)
            scheduler.runTask(plugin) {            // 또 메인 스레드
                player.sendMessage("업데이트 완료")
                // 여기에 또 다른 비동기가 필요하다면?
            }
        }
    }
}
```

이 패턴의 문제점:
- 중첩이 깊어질수록 코드 가독성이 급격히 떨어진다 (콜백 지옥)
- 각 비동기 블록에서 별도의 예외 처리가 필요하다
- 비동기 블록에서 Bukkit API를 실수로 직접 호출하기 쉽다 (스레드 안전 위반)
- 취소나 타임아웃 처리가 매우 번거롭다

---

## ArcAsync API

`ArcAsync`는 CompletableFuture 체인을 간결하게 작성할 수 있는 헬퍼 API다. 동일한 로직을 다음과 같이 작성할 수 있다.

**ArcAsync 방식:**
```kotlin
import dev.arc.api.coroutine.ArcAsync

// ✅ ArcAsync 방식 — 선형으로 읽히는 체인
ArcAsync.runBlockingIO {
    database.loadPlayer(uuid)                      // IO 스레드에서 실행
}.thenSync { data ->
    player.sendMessage("데이터 로드: ${data.name}") // 메인 스레드에서 실행
}.thenBlockingIO { _ ->
    database.updateLastSeen(uuid)                   // 다시 IO 스레드
}.thenSync { _ ->
    player.sendMessage("업데이트 완료")              // 메인 스레드
}.exceptionallySync { error ->
    // 체인 어디서 예외가 발생해도 이 블록에서 처리
    logger.severe("플레이어 데이터 처리 실패", error)
    player.sendMessage("오류가 발생했습니다. 관리자에게 문의하세요.")
}
```

### 주요 메서드

| 메서드 | 실행 스레드 | 설명 |
|--------|------------|------|
| `runBlockingIO { }` | IO 스레드 풀 | 최초 비동기 진입점 |
| `thenSync { result -> }` | 메인 스레드 | 결과를 메인 스레드에서 처리 |
| `thenBlockingIO { result -> }` | IO 스레드 풀 | 결과를 IO 스레드에서 처리 |
| `thenAsync { result -> }` | 공용 풀 | 결과를 공용 스레드에서 처리 |
| `exceptionallySync { error -> }` | 메인 스레드 | 체인 전체의 예외를 메인 스레드에서 처리 |
| `exceptionallyAsync { error -> }` | 공용 풀 | 예외를 비동기로 처리 |

**Java 예시:**
```java
ArcAsync.runBlockingIO(() -> {
    return database.loadPlayer(uuid);
}).thenSync(data -> {
    player.sendMessage("데이터 로드: " + data.getName());
    return null;
}).exceptionallySync(error -> {
    logger.severe("오류: " + error.getMessage());
    return null;
});
```

---

## ArcDispatchers

`ArcDispatchers`는 Arc의 코루틴 컨텍스트 및 스레드 풀을 정의한다. 어떤 코드가 어느 스레드에서 실행될지를 명시적으로 제어할 수 있다.

### 디스패처 종류

| 디스패처 | 스레드 | 용도 |
|----------|--------|------|
| `ArcDispatchers.Main` | Bukkit 메인 스레드 | Bukkit API 호출, 플레이어 상태 변경 |
| `ArcDispatchers.IO` | IO 전용 스레드 풀 (기본 8개) | 데이터베이스, 파일, 네트워크 요청 |
| `ArcDispatchers.Default` | 공용 코루틴 스레드 풀 | CPU 집약적 계산, 일반 비동기 |

```kotlin
import dev.arc.api.coroutine.ArcDispatchers
import kotlinx.coroutines.*

// 코루틴에서 디스패처 전환
plugin.launch(ArcDispatchers.IO) {
    val result = database.heavyQuery()       // IO 스레드에서 실행

    withContext(ArcDispatchers.Main) {
        player.sendMessage(result.toString()) // 메인 스레드로 복귀
    }
}
```

### IO 스레드 풀 크기 설정

```yaml
# arc-ops.yml
async:
  io-pool-size: 8        # IO 스레드 풀 크기 (기본 8)
  default-pool-size: 4   # 공용 풀 크기 (기본 CPU 코어 수)
```

대규모 서버에서 동시 DB 쿼리가 많다면 `io-pool-size`를 16~32로 늘리는 것을 고려한다. 단, 스레드 수를 너무 늘리면 컨텍스트 전환 비용이 증가한다.

---

## ServiceLoader SPI — 구현체 교체

Arc의 비동기 레이어는 `ServiceLoader` SPI(서비스 공급자 인터페이스)로 구현되어 있어, 내부 구현을 교체할 수 있다. 예를 들어, 기본 Java CompletableFuture 기반 구현 대신 Kotlin 코루틴 기반 구현을 주입할 수 있다.

```kotlin
// META-INF/services/dev.arc.api.coroutine.ArcAsyncProvider에 클래스 이름 등록
class MyCustomAsyncProvider : ArcAsyncProvider {
    override fun <T> runBlockingIO(block: () -> T): ArcFuture<T> {
        // 커스텀 구현
    }
}
```

대부분의 서버에서는 기본 구현으로 충분하다. SPI 교체는 Arc의 비동기 레이어를 기존 사내 라이브러리와 통합해야 하는 경우에 활용한다.

---

## Kotlin 코루틴 스타일 API

Arc는 Bukkit 스케줄러를 코루틴으로 감싼 확장 함수도 제공한다. 일정 주기로 반복되는 작업을 코루틴으로 간결하게 작성할 수 있다.

### launchEveryTicks

```kotlin
import dev.arc.api.coroutine.launchEveryTicks
import dev.arc.api.coroutine.launchAfterTicks

// 20틱(1초)마다 실행
val job = plugin.launchEveryTicks(20) {
    // 이 블록은 메인 스레드에서 실행됨
    updateScoreboard()
}

// 나중에 취소
job.cancel()

// 100틱 후 한 번 실행
plugin.launchAfterTicks(100) {
    player.sendMessage("5초가 지났습니다!")
}
```

### 코루틴 내 스레드 전환

```kotlin
import dev.arc.api.coroutine.ArcDispatchers
import dev.arc.api.coroutine.switchToMain
import dev.arc.api.coroutine.switchToIO

plugin.launch(ArcDispatchers.IO) {
    // IO 스레드에서 DB 조회
    val playerData = database.loadPlayerData(uuid)

    // 메인 스레드로 전환
    switchToMain()
    player.sendMessage("안녕하세요, ${playerData.displayName}!")
    player.gameMode = playerData.savedGameMode

    // 다시 IO 스레드로
    switchToIO()
    database.updateLastLogin(uuid, System.currentTimeMillis())
}
```

### 완전한 실용 예시: 로그인 플로우

```kotlin
// 플레이어 로그인 시 데이터 로드 → 메인 스레드에서 적용 → 로그 저장
@EventHandler
fun onPlayerJoin(event: PlayerJoinEvent) {
    val player = event.player

    plugin.launch(ArcDispatchers.IO) {
        // 1. DB에서 플레이어 데이터 비동기 로드
        val data = try {
            database.loadPlayerData(player.uniqueId)
        } catch (e: DatabaseException) {
            logger.severe("플레이어 데이터 로드 실패: ${player.name}", e)
            null
        }

        // 2. 메인 스레드로 전환해 플레이어 상태 적용
        switchToMain()

        if (!player.isOnline) return@launch // 로드 중 로그아웃한 경우

        if (data == null) {
            // 신규 플레이어
            player.sendMessage("처음 오셨군요! 환영합니다.")
            player.teleport(spawnLocation)
        } else {
            // 기존 플레이어
            player.inventory.contents = data.inventory
            player.health = data.health
            player.sendMessage("환영합니다, ${data.displayName}!")
            player.teleport(data.lastLocation)
        }

        // 3. 다시 IO 스레드로 전환해 접속 로그 저장
        switchToIO()
        database.logLogin(player.uniqueId, System.currentTimeMillis())
    }
}
```

---

## 가장 중요한 규칙: 비동기 블록에서 Bukkit API 호출 금지

Bukkit API(플레이어 이동, 인벤토리 조작, 블록 변경 등)는 **반드시 메인 스레드에서만 호출**해야 한다. 비동기 블록에서 Bukkit API를 직접 호출하면 다음 문제가 발생한다:

- **크래시**: 내부 데이터 구조에 동시 접근으로 인한 `ConcurrentModificationException`
- **데이터 손상**: 경쟁 조건으로 인한 플레이어 데이터 또는 청크 데이터 손상
- **무작위 버그**: 재현이 어려운 간헐적 버그

**잘못된 코드:**
```kotlin
// ❌ 비동기 블록에서 Bukkit API 직접 호출
ArcAsync.runBlockingIO {
    val result = database.loadData(uuid)
    player.sendMessage(result.toString()) // 위험! IO 스레드에서 Bukkit API 호출
}
```

**올바른 코드:**
```kotlin
// ✅ thenSync로 메인 스레드에서 Bukkit API 호출
ArcAsync.runBlockingIO {
    database.loadData(uuid)
}.thenSync { result ->
    player.sendMessage(result.toString()) // 안전! 메인 스레드에서 실행
}
```

`NmsThreadGuard`가 활성화되어 있으면 비동기 스레드에서의 NMS 접근을 감지해 경고 로그를 출력한다. Bukkit API 호출은 Arc가 직접 감지할 수 없으므로 개발자가 항상 주의해야 한다.

---

## 장단점 요약

**장점:**
- 콜백 중첩 없이 선형으로 읽히는 비동기 코드 작성 가능
- 체인 전체의 예외를 단일 `exceptionallySync`로 처리
- `switchToMain()` / `switchToIO()`로 스레드 전환 의도가 코드에 명시됨
- `launchEveryTicks`로 스케줄러 관리 간소화
- SPI로 구현체 교체 가능 (테스트, 커스텀 통합)

**단점:**
- 코루틴과 CompletableFuture 개념에 익숙하지 않은 개발자에게는 초기 학습 비용이 있다
- `switchToMain()` / `thenSync()` 없이 Bukkit API를 호출하는 실수를 Arc가 컴파일 타임에 막지는 못한다 (`NmsThreadGuard`는 NMS에만 적용)
- 기존 Bukkit 스케줄러 코드에서 마이그레이션 시 리팩터링 작업이 필요하다

---

## 빠른 참조

```kotlin
// 비동기 실행 후 메인 스레드에서 결과 처리
ArcAsync.runBlockingIO { /* IO 작업 */ }
    .thenSync { result -> /* Bukkit API 안전 */ }
    .exceptionallySync { e -> /* 에러 처리 */ }

// 코루틴 스타일
plugin.launch(ArcDispatchers.IO) {
    val data = fetchData()    // IO 스레드
    switchToMain()
    applyToPlayer(data)       // 메인 스레드
}

// 반복 작업
val job = plugin.launchEveryTicks(20) { updateScoreboard() }
job.cancel() // 취소
```
