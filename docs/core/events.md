---
title: 커스텀 이벤트
parent: Fork Core
nav_order: 3
---

# 커스텀 이벤트

Arc는 바닐라 Paper/Purpur가 제공하지 않는 추가 Bukkit 이벤트를 제공한다. 이 이벤트들은 모두 `dev.arc.api.event` 패키지에 위치하며, 일반 Bukkit 이벤트와 동일하게 `@EventHandler`로 구독할 수 있다.

---

## 서버 상태 이벤트

### ServerLoadLevelChangeEvent

**발생 시점**: 서버의 `LoadLevel`이 변경될 때 (LOW → NORMAL, NORMAL → HIGH 등)

서버 부하 단계가 바뀌는 순간 발생한다. 적응형 뷰 거버너가 조정을 수행하기 직전에 발생하므로, 이 이벤트를 이용해 플러그인 자체 최적화 로직을 거버너와 동기화할 수 있다.

**필드:**
- `getPreviousLevel(): LoadLevel` — 이전 부하 단계
- `getNewLevel(): LoadLevel` — 새 부하 단계
- `getCurrentMspt(): Double` — 이벤트 발생 시점의 MSPT

**활용 예시:**
```kotlin
@EventHandler
fun onLoadChange(event: ServerLoadLevelChangeEvent) {
    when (event.newLevel) {
        LoadLevel.CRITICAL -> {
            // 부하 심각: 비필수 자동 저장 주기 늘리기
            autoSaveTask.period = 12000L // 10분으로 연장
            Bukkit.broadcast(
                Component.text("[서버] 일시적으로 성능 최적화 모드로 전환합니다.")
                    .color(NamedTextColor.YELLOW)
            )
        }
        LoadLevel.LOW -> {
            // 부하 정상: 자동 저장 원래 주기로 복원
            autoSaveTask.period = 6000L // 5분으로 복원
        }
        else -> { /* 별도 조치 없음 */ }
    }
}
```

**주의사항**: 이 이벤트는 메인 스레드에서 발생한다. 이벤트 핸들러에서 무거운 동기 작업을 수행하면 해당 틱이 지연된다.

---

### ServerLagSpikeEvent

**발생 시점**: 단일 틱의 처리 시간이 임계값(`lag-spike-threshold-ms`, 기본 100ms)을 초과할 때

렉 스파이크가 발생했음을 플러그인에 알린다. 알림 발송, 로그 기록, Discord 경고 등에 활용할 수 있다.

**필드:**
- `getTickDurationMs(): Long` — 해당 틱의 처리 시간 (ms)
- `getThresholdMs(): Long` — 설정된 임계값
- `getLoadLevel(): LoadLevel` — 발생 시점의 부하 단계

**활용 예시 (Discord 웹훅 알림):**
```kotlin
@EventHandler
fun onLagSpike(event: ServerLagSpikeEvent) {
    if (event.tickDurationMs < 200) return // 200ms 미만은 무시

    // 비동기로 Discord 알림 발송 (메인 스레드 차단 방지)
    ArcAsync.runAsync {
        discordWebhook.send(
            "⚠️ 렉 스파이크 감지: ${event.tickDurationMs}ms " +
            "(부하: ${event.loadLevel})"
        )
    }
}
```

**주의사항**: 이 이벤트 자체도 렉이 발생한 틱의 처리 과정에서 발생한다. 핸들러가 추가 지연을 일으키지 않도록 무거운 작업은 반드시 비동기로 처리해야 한다.

---

## 플레이어 행동 이벤트

### PlayerChangeChunkEvent

**발생 시점**: 플레이어가 청크 경계를 넘어 다른 청크로 이동할 때

바닐라 Bukkit의 `PlayerMoveEvent`는 모든 위치 이동마다 발생해 핸들러 수가 많으면 부하가 크다. `PlayerChangeChunkEvent`는 청크 경계를 넘을 때만 발생하므로 지역 기반 로직에 훨씬 효율적이다.

**필드:**
- `getFromChunk(): Chunk` — 이전 청크
- `getToChunk(): Chunk` — 새 청크
- `getFromChunkX(): Int`, `getFromChunkZ(): Int` — 이전 청크 좌표
- `getToChunkX(): Int`, `getToChunkZ(): Int` — 새 청크 좌표

**활용 예시 (지역 진입/퇴장 시스템):**
```kotlin
@EventHandler
fun onChunkChange(event: PlayerChangeChunkEvent) {
    val player = event.player
    val newChunk = event.toChunk

    // 청크에 연결된 지역 확인
    val region = regionManager.getRegionForChunk(newChunk) ?: return

    if (!regionManager.isPlayerInRegion(player, region)) {
        regionManager.onPlayerEnter(player, region)
        player.sendActionBar(Component.text("${region.displayName} 진입"))
    }
}
```

**주의사항**: 텔레포트, 워프, 엔더 포탈 이동도 이 이벤트를 발생시킨다. 이동 방향이나 속도 기반 로직이 필요하다면 `getFromChunk`와 `getToChunk`를 비교해야 한다.

---

### PlayerDoubleSneakEvent

**발생 시점**: 플레이어가 웅크리기(shift)를 빠르게 두 번 연속 입력할 때

더블 클릭과 유사한 "더블 스니크" 입력 패턴을 감지한다. 능력 발동, 메뉴 열기, 특수 모드 전환 등의 트리거로 활용할 수 있다.

**필드:**
- `getPlayer(): Player` — 이벤트를 발생시킨 플레이어
- `getIntervalMs(): Long` — 두 번의 스니크 사이 간격 (ms)
- `isCancellable(): Boolean` — 항상 false (행동 자체를 취소할 수 없음)

**활용 예시 (능력 발동):**
```kotlin
@EventHandler
fun onDoubleSneak(event: PlayerDoubleSneakEvent) {
    val player = event.player
    val ability = abilityManager.getActiveAbility(player) ?: return

    if (ability.canActivate(player)) {
        ability.activate(player)
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f)
    }
}
```

**설정 (arc-ops.yml):**
```yaml
events:
  double-sneak:
    max-interval-ms: 400  # 이 시간 내 두 번 스니크 시 이벤트 발생
```

---

## GUI 이벤트

### MenuOpenEvent / MenuCloseEvent

**발생 시점**: Arc GUI 시스템으로 만들어진 메뉴(인벤토리 UI)가 열리거나 닫힐 때

Arc의 내장 GUI 빌더(`ArcMenu`)로 생성된 UI에 한정된다. 바닐라 `InventoryOpenEvent`와 달리 Arc 메뉴임이 보장되므로 타입 확인 없이 바로 메뉴 객체에 접근할 수 있다.

**MenuOpenEvent 필드:**
- `getPlayer(): Player` — 메뉴를 연 플레이어
- `getMenu(): ArcMenu` — 열린 메뉴 객체
- `isCancellable(): Boolean` — true (열기 취소 가능)

**MenuCloseEvent 필드:**
- `getPlayer(): Player` — 메뉴를 닫은 플레이어
- `getMenu(): ArcMenu` — 닫힌 메뉴 객체
- `getCloseReason(): CloseReason` — PLAYER_CLOSED, PLUGIN_CLOSED, SERVER_CLOSED

**활용 예시 (메뉴 접근 로그):**
```kotlin
@EventHandler
fun onMenuOpen(event: MenuOpenEvent) {
    analytics.trackMenuOpen(event.player.uniqueId, event.menu.menuId)
}

@EventHandler
fun onMenuClose(event: MenuCloseEvent) {
    if (event.closeReason == CloseReason.PLAYER_CLOSED) {
        // 플레이어가 직접 닫은 경우 임시 데이터 정리
        sessionCache.clearPendingChanges(event.player.uniqueId)
    }
}
```

---

## Arc 초기화 이벤트

### ArcInitializeEvent

**발생 시점**: Arc의 모든 서브시스템(성능 거버너, 모니터링, NMS 브리지 등)이 초기화 완료된 후

Arc가 제공하는 API를 플러그인에서 사용하려면 Arc가 완전히 초기화된 이후여야 한다. 이 이벤트 이전에 Arc API를 호출하면 예외가 발생할 수 있다. `onEnable()`에서 Arc API를 즉시 사용하는 대신 이 이벤트를 기다리는 것이 안전하다.

**필드:**
- `getArcVersion(): String` — 초기화된 Arc 버전
- `getInitializationTimeMs(): Long` — Arc 초기화 소요 시간 (ms)

**활용 예시:**
```kotlin
class MyPlugin : JavaPlugin() {
    override fun onEnable() {
        // Arc 초기화 대기
        server.pluginManager.registerEvents(object : Listener {
            @EventHandler
            fun onArcInit(event: ArcInitializeEvent) {
                // 이 시점부터 Arc API 안전 사용 가능
                initializeWithArc()
                logger.info("Arc ${event.arcVersion} 확인 완료")
            }
        }, this@MyPlugin)
    }
}
```

**주의사항**: Arc는 서버 시작 중에 초기화되므로, `ArcInitializeEvent`는 일반적으로 `onEnable()`보다 먼저 또는 직후에 발생한다. `onEnable()`에서 직접 등록하면 이벤트를 놓칠 수 있으므로, Arc 의존성을 선언한 플러그인은 `plugin.yml`에 `depend: [Arc]`를 명시하는 것을 권장한다.

---

## 네트워크 이벤트 (ArcSync 연동)

아래 이벤트들은 ArcSync(네트워크 레이어)와 연동될 때 발생한다. 단독 서버에서는 발생하지 않는다.

### ArcNetworkPlayerTransferEvent

**발생 시점**: 플레이어가 다른 서버로 전송되기 직전

플레이어 데이터를 전송 전에 저장하거나 전송 자체를 취소하는 데 활용한다.

**필드:**
- `getPlayer(): Player` — 전송될 플레이어
- `getTargetServer(): String` — 목적지 서버 ID
- `isCancellable(): Boolean` — true (전송 취소 가능)

**활용 예시:**
```kotlin
@EventHandler(priority = EventPriority.HIGH)
fun onTransfer(event: ArcNetworkPlayerTransferEvent) {
    val player = event.player

    // 미저장 데이터 동기 저장
    try {
        playerDataManager.saveNow(player)
    } catch (e: Exception) {
        // 저장 실패 시 전송 취소
        event.isCancelled = true
        player.sendMessage("데이터 저장 실패로 서버 이동이 취소되었습니다.")
        logger.severe("전송 전 저장 실패: ${player.name}", e)
    }
}
```

---

### ArcNetworkPlayerBanEvent

**발생 시점**: 네트워크 레벨에서 밴 명령이 이 서버로 전달될 때

특정 플레이어가 다른 서버에서 밴되었을 때 이 서버에도 동기화된다. 밴 처리 전 커스텀 로직(예: 인게임 공지, 아이템 회수)을 삽입할 수 있다.

**필드:**
- `getTargetUUID(): UUID` — 밴 대상 플레이어 UUID
- `getBanReason(): String` — 밴 사유
- `getBannerName(): String` — 밴을 집행한 관리자 이름

**활용 예시:**
```kotlin
@EventHandler
fun onNetworkBan(event: ArcNetworkPlayerBanEvent) {
    val player = Bukkit.getPlayer(event.targetUUID) ?: return

    // 밴 처리 전 아이템 회수 또는 로그 저장
    inventoryLogger.logAndClear(player, "BAN: ${event.banReason}")
}
```

---

### ArcNetworkQueueJoinEvent / ArcNetworkQueueLeaveEvent

**발생 시점**: 플레이어가 이 서버의 대기열에 진입하거나 이탈할 때

대기열 UI 표시, 대기 시간 추정, 우선순위 처리 등에 활용한다.

**ArcNetworkQueueJoinEvent 필드:**
- `getPlayerUUID(): UUID` — 대기 중인 플레이어 UUID
- `getQueuePosition(): Int` — 현재 대기 순번
- `getEstimatedWaitSeconds(): Int` — 예상 대기 시간 (초)
- `isPriority(): Boolean` — 우선순위 대기 여부 (VIP 등)

**ArcNetworkQueueLeaveEvent 필드:**
- `getPlayerUUID(): UUID`
- `getLeaveReason(): QueueLeaveReason` — JOINED, TIMEOUT, PLAYER_CANCELLED

**활용 예시:**
```kotlin
@EventHandler
fun onQueueJoin(event: ArcNetworkQueueJoinEvent) {
    // 대기 중인 플레이어에게 현재 위치 알림 (별도 메시지 채널로)
    networkMessenger.sendToPlayer(
        event.playerUUID,
        "대기열 ${event.queuePosition}번째 — 예상 대기: ${event.estimatedWaitSeconds}초"
    )
}

@EventHandler
fun onQueueLeave(event: ArcNetworkQueueLeaveEvent) {
    if (event.leaveReason == QueueLeaveReason.TIMEOUT) {
        // 타임아웃으로 이탈한 플레이어에게 재진입 안내
        networkMessenger.sendToPlayer(event.playerUUID, "대기열 시간 초과. 다시 시도해주세요.")
    }
}
```
