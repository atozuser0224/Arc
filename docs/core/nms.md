---
title: NMS 브리지
parent: Fork Core
nav_order: 4
---

# NMS 브리지

NMS(net.minecraft.server) 브리지는 Minecraft 내부 API에 안전하게 접근할 수 있는 추상화 레이어다. Bukkit/Paper API만으로는 구현 불가능한 저수준 기능(패킷 직접 전송, 플레이어별 날씨/시간 분리, NPC 제어 등)을 버전 호환성을 유지하며 사용할 수 있게 한다.

---

## 왜 NMS 브리지가 필요한가

Minecraft는 업데이트마다 내부 클래스 이름과 메서드 시그니처가 변경된다(난독화 해제 이후에도 구조 자체가 바뀐다). 플러그인이 NMS 클래스를 직접 import하면 매 Minecraft 버전마다 플러그인을 수정·재컴파일해야 하며, 버전이 맞지 않으면 `ClassNotFoundException`이나 `NoSuchMethodError`로 서버가 크래시된다.

Arc NMS 브리지는 이 문제를 두 가지 방식으로 해결한다:

1. **리플렉션 기반 접근**: 클래스·메서드를 런타임에 동적으로 조회하므로 컴파일 타임 의존성이 없다.
2. **캐싱**: 조회 결과를 내부에 캐싱해 반복 호출의 리플렉션 비용을 제거한다.

플러그인이 Arc NMS 브리지를 사용하면, Minecraft 버전이 바뀌더라도 **Arc만 업데이트하면 된다**. 플러그인 코드는 Arc의 안정적인 API를 호출하므로 수정이 필요 없다.

---

## 1. NmsRef — 리플렉션 래퍼

### 개요

`NmsRef`는 NMS 클래스, 메서드, 필드에 접근하기 위한 저수준 리플렉션 유틸리티다. 내부적으로 `MethodHandle`을 캐싱하므로 첫 번째 접근 이후의 호출은 직접 메서드 호출 대비 약 2~5배 느린 수준(JIT 최적화 후)으로, 게임 로직에서 허용 가능한 범위다.

### 사용 예시

```kotlin
import dev.arc.api.nms.NmsRef

// NMS 클래스 참조 (버전별 패키지 경로를 Arc가 추상화)
val entityRef = NmsRef.ofClass("Entity")
val playerRef = NmsRef.ofClass("ServerPlayer")

// 메서드 호출
val nmsPlayer = NmsRef.getNmsPlayer(player) // CraftPlayer → NMS ServerPlayer
val connection = NmsRef.getField(nmsPlayer, "connection")

// 필드 값 조회
val entityId: Int = NmsRef.getField<Int>(nmsPlayer, "id")
```

**Java:**
```java
Object nmsPlayer = NmsRef.getNmsPlayer(player);
int entityId = NmsRef.<Integer>getField(nmsPlayer, "id");
```

### 성능 특성

| 접근 방식 | 상대적 비용 |
|-----------|------------|
| 직접 Java 호출 | 1× |
| NmsRef (캐싱 후) | 2~5× |
| 매번 `Class.forName()` | 100~500× |

NmsRef는 첫 호출 시에만 리플렉션을 수행하고 이후 `MethodHandle`을 캐싱한다. 틱당 수십 번 호출되는 경우에도 무시할 수 있는 수준의 오버헤드다.

---

## 2. 패킷 직접 전송

### 개요

Bukkit API는 특정 플레이어에게만 특정 패킷을 보내는 기능을 제공하지 않는다. NMS 브리지의 패킷 전송 API를 사용하면 원하는 플레이어에게 원하는 NMS 패킷을 직접 전송할 수 있다.

### 사용 예시

```kotlin
import dev.arc.api.nms.NmsPackets

// 특정 플레이어에게 타이틀 패킷 직접 전송 (더 세밀한 제어)
NmsPackets.sendTo(player) {
    titlePacket(
        title = "환영합니다",
        subtitle = "서버에 오신 것을 환영합니다",
        fadeIn = 10,
        stay = 70,
        fadeOut = 20
    )
}

// 임의의 NMS 패킷 객체 전송
val packet = NmsRef.createInstance("ClientboundSetTimePacket", worldTime, dayTime, doDayNightCycle)
NmsPackets.sendRaw(player, packet)
```

### 주의사항

- 패킷 클래스 이름과 생성자 시그니처는 Minecraft 버전마다 다를 수 있다. `NmsRef.createInstance`를 직접 사용하는 경우 Arc 버전 업데이트 시 호환성을 확인해야 한다.
- 가능하면 `NmsPackets`의 고수준 헬퍼 메서드(`titlePacket`, `soundPacket` 등)를 사용하고, 직접 `sendRaw`는 헬퍼가 없을 때만 사용한다.

---

## 3. NmsClientWorldStateBackend — 플레이어별 날씨/시간 분리

### 개요

바닐라 Minecraft에서 날씨와 시간은 월드 단위로 관리된다. `NmsClientWorldStateBackend`를 사용하면 **플레이어마다 독립적인 날씨와 시간**을 설정할 수 있다. 네트워크 패킷 레벨에서 처리되므로 서버 월드 상태는 그대로 유지되고 각 플레이어의 클라이언트만 다르게 보인다.

### 사용 예시

```kotlin
import dev.arc.api.nms.NmsClientWorldStateBackend

val worldState = NmsClientWorldStateBackend.get()

// 특정 플레이어에게만 비가 내리는 것처럼 보이게
worldState.setWeather(player, WeatherType.DOWNFALL)

// 특정 플레이어에게 항상 정오(6000틱)로 보이게
worldState.setTime(player, 6000L)

// 원래 월드 날씨/시간으로 복원
worldState.resetWeather(player)
worldState.resetTime(player)
```

### 활용 사례

- **롤플레잉 서버**: 지역마다 다른 날씨/시간 분위기 (다른 월드 없이)
- **공포 게임**: 특정 플레이어에게만 어두운 밤 연출
- **이벤트 시스템**: 이벤트 참가자에게만 특수 날씨 효과
- **포토 모드**: 플레이어가 자신의 날씨/시간을 선택

---

## 4. NmsNpcBackend — NPC 제어

### 개요

Arc의 NPC 시스템은 NMS 수준에서 가짜 플레이어 엔티티를 생성하고 제어한다. 커스텀 스킨, 이름, 이동, 상호작용을 지원하며, 다른 NPC 플러그인(Citizens, ZNPC 등)과 달리 Arc 내장 기능으로 제공된다.

### 사용 예시

```kotlin
import dev.arc.api.nms.NmsNpcBackend

val npcBackend = NmsNpcBackend.get()

// NPC 생성
val npc = npcBackend.createNpc(location) {
    name("§6상점 주인 김철수")
    skin(skinValue, skinSignature)  // Mineskin 등에서 획득한 값
    lookAt(player)                   // 가장 가까운 플레이어를 바라봄
}

// NPC 이동
npcBackend.moveNpc(npc, targetLocation, speed = 0.2f)

// NPC 제거
npcBackend.removeNpc(npc)
```

### 주의사항

NMS 기반 NPC는 서버 재시작 시 사라진다. 영구적인 NPC가 필요하다면 플러그인에서 데이터베이스에 NPC 정보를 저장하고 서버 시작 시 재생성해야 한다.

---

## 5. NmsRegistryBackend — Minecraft 레지스트리 접근

### 개요

`NmsRegistryBackend`는 Minecraft의 내부 레지스트리(아이템, 블록, 엔티티 타입, 인챈트먼트 등)에 직접 접근하는 인터페이스를 제공한다. Bukkit API에서 접근할 수 없는 레지스트리 항목이나 등록 시점에 개입이 필요한 경우에 사용한다.

### 사용 예시

```kotlin
import dev.arc.api.nms.NmsRegistryBackend

val registry = NmsRegistryBackend.get()

// 특정 레지스트리 항목 조회
val itemType = registry.getItem(NamespacedKey.minecraft("netherite_sword"))
val blockType = registry.getBlock(NamespacedKey.minecraft("ancient_debris"))

// 커스텀 아이템 레지스트리 등록 (고급 사용)
registry.registerCustomItem(customItemKey, customItemProperties)
```

### 주의사항

레지스트리 직접 수정은 매우 위험하다. 잘못된 등록은 서버 크래시나 청크 손상을 유발할 수 있다. 레지스트리 쓰기 작업은 서버 시작 직후(`ArcInitializeEvent` 이전)에만 수행해야 한다.

---

## 6. NmsThreadGuard — 메인 스레드 외 NMS 접근 감지

### 개요

NMS API의 대부분은 메인 스레드에서만 안전하게 호출할 수 있다. 비동기 컨텍스트에서 NMS를 호출하면 데이터 경쟁(race condition), 크래시, 청크 손상 등이 발생할 수 있다.

`NmsThreadGuard`는 개발 중에 이런 잘못된 사용을 감지해 경고 로그를 출력한다. 프로덕션에서는 오버헤드를 피하기 위해 비활성화할 수 있다.

### 동작

메인 스레드가 아닌 스레드에서 `NmsRef` 또는 `NmsPackets` 메서드를 호출하면:
- `debug` 모드: 스택 트레이스와 함께 경고 로그 출력
- `throw` 모드: `IllegalStateException` 발생 (개발 중 즉시 발견)

### 설정 (arc-ops.yml)

```yaml
nms:
  thread-guard:
    enabled: true
    mode: "warn"   # warn (로그만) | throw (예외 발생) | disabled
```

---

## 장단점 요약

**장점:**
- Minecraft 버전이 바뀌어도 플러그인 코드 수정 불필요 (Arc 업데이트로 해결)
- 리플렉션 캐싱으로 반복 호출 비용 최소화
- 고수준 헬퍼 API로 패킷/레지스트리 세부 사항을 몰라도 사용 가능
- `NmsThreadGuard`로 개발 중 잘못된 사용 조기 발견

**단점:**
- Minecraft 내부 API가 크게 바뀌면 Arc 업데이트가 필요하다 (플러그인 직접 의존보다는 빠른 대응)
- 리플렉션 기반이므로 직접 호출보다는 느리다 (캐싱 후 2~5×, 대부분 무시 가능)
- 레지스트리 쓰기 등 고급 기능은 잘못 사용 시 서버 안정성에 영향을 준다

## 언제 NMS 브리지를 사용해야 하나

다음 경우에 NMS 브리지를 사용한다:

- 특정 플레이어에게만 날씨나 시간을 다르게 보여줘야 할 때
- 패킷 레벨의 세밀한 제어가 필요할 때 (지연 최소화, 특정 클라이언트 전용 기능)
- Bukkit API에 존재하지 않는 NMS 필드나 메서드에 접근해야 할 때
- NPC 시스템을 외부 플러그인 없이 구현할 때

일반적인 게임 로직(블록 변경, 인벤토리 조작, 데미지 처리 등)에는 Bukkit/Paper API를 사용한다. NMS는 Bukkit API로 불가능한 경우에만 사용하는 것을 원칙으로 한다.
