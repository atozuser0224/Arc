---
title: arc-test — 서버 없는 테스트
parent: Developer API
nav_order: 9
---

# arc-test — 서버 없는 테스트

`arc-test`는 Arc API 플러그인 로직을 실제 Minecraft 서버 없이 JVM에서 실행하는 테스트 환경을 제공합니다. CI(지속적 통합) 파이프라인에서 수 분이 걸리던 서버 시작 단계를 수 초의 JUnit 테스트로 대체할 수 있습니다.

---

## 기존 테스트의 문제

Bukkit/Paper 플러그인을 테스트하는 데는 두 가지 주요 장벽이 있습니다.

**1. JVM 실행 불가**: Bukkit API 클래스들은 실제 서버 없이 인스턴스화할 수 없습니다. `Bukkit.getServer()`는 서버가 없으면 `null`을 반환하고, `Material.DIRT`조차 올바르게 초기화되지 않습니다.

**2. CI 비용**: 실제 Paper 서버를 CI에서 실행하면 서버 시작(30초~2분)이 필요하고, 월드 생성, 플레이어 접속 시뮬레이션까지 포함하면 테스트 한 번에 수 분이 걸립니다. 결과적으로 개발자들이 테스트를 작성하지 않게 됩니다.

**MockBukkit의 한계**: MockBukkit 같은 기존 목(mock) 라이브러리는 Bukkit API의 일부만 구현되어 있어, Arc API와 같은 래퍼 라이브러리와 완전히 호환되지 않습니다.

---

## arc-test가 제공하는 것

### 의존성 추가

```kotlin
// build.gradle.kts
dependencies {
    compileOnly("dev.arc:arc-api:1.21.4-SNAPSHOT")
    testImplementation("dev.arc:arc-test:1.21.4-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

tasks.test {
    useJUnitPlatform()
}
```

### VirtualConsole — 가상 서버 환경

`VirtualConsole`은 Arc API가 동작하는 데 필요한 최소한의 서버 환경을 JVM에서 시뮬레이션합니다.

```kotlin
import dev.arc.test.VirtualConsole
import dev.arc.test.VirtualPlayer

class MyPluginTest {
    private lateinit var console: VirtualConsole

    @BeforeEach
    fun setUp() {
        console = VirtualConsole.start()  // 가상 서버 환경 초기화
    }

    @AfterEach
    fun tearDown() {
        console.shutdown()  // 리소스 해제
    }

    @Test
    fun `플레이어 생성`() {
        val player: VirtualPlayer = console.createPlayer("TestPlayer")
        assertEquals("TestPlayer", player.name)
        assertNotNull(player.uniqueId)
    }
}
```

---

## 테스트 가능한 항목

### 아이템 빌더 결과 검증

```kotlin
@Test
fun `item DSL이 올바른 아이템을 생성한다`() {
    val sword = item(Material.DIAMOND_SWORD) {
        name("§bFrost Blade")
        lore("§7서리 피해", "§7속도 +20%")
        enchant(Enchantment.SHARPNESS, 5)
        unbreakable()
        modelData(1001)
    }

    val meta = sword.itemMeta!!
    assertEquals("§bFrost Blade", meta.displayName())
    assertTrue(meta.isUnbreakable)
    assertEquals(5, meta.getEnchantLevel(Enchantment.SHARPNESS))
    assertEquals(1001, meta.customModelData)
    assertEquals(2, meta.lore()?.size)
}

@Test
fun `glowing 효과가 설정된다`() {
    val glowingItem = item(Material.STICK) {
        name("§aMagic Stick")
        glowing()
    }
    assertTrue(glowingItem.itemMeta!!.hasEnchants() || glowingItem.hasItemFlag(ItemFlag.HIDE_ENCHANTS))
}

@Test
fun `amount 설정이 적용된다`() {
    val stackOf64 = item(Material.ARROW) { amount(64) }
    assertEquals(64, stackOf64.amount)
}
```

### PDC 읽기/쓰기 라운드트립

```kotlin
@Test
fun `PDC Int 읽기 쓰기 라운드트립`() {
    val console = VirtualConsole.start()
    val player = console.createPlayer("PDCTestPlayer")
    val key = console.plugin.key("coins")

    // 초기값 없으면 기본값 반환
    assertEquals(0, player.getInt(key, default = 0))

    // 값 저장 후 읽기
    player.setInt(key, 500)
    assertEquals(500, player.getInt(key, default = 0))

    // 업데이트
    player.setInt(key, player.getInt(key) + 100)
    assertEquals(600, player.getInt(key))

    console.shutdown()
}

@Test
fun `PDC Boolean 내부 Byte 변환이 올바르다`() {
    val console = VirtualConsole.start()
    val player = console.createPlayer("BoolTest")
    val key = console.plugin.key("premium")

    player.setBoolean(key, true)
    assertTrue(player.getBoolean(key, default = false))

    player.setBoolean(key, false)
    assertFalse(player.getBoolean(key, default = true))

    console.shutdown()
}

@Test
fun `hasKey와 removeKey가 동작한다`() {
    val console = VirtualConsole.start()
    val player = console.createPlayer("KeyTest")
    val key = console.plugin.key("temp")

    assertFalse(player.hasKey(key))
    player.setString(key, "hello")
    assertTrue(player.hasKey(key))
    player.removeKey(key)
    assertFalse(player.hasKey(key))

    console.shutdown()
}
```

### PdcSchema 마이그레이션 검증

```kotlin
@Test
fun `PdcSchema가 v1 데이터를 v2로 마이그레이션한다`() {
    val console = VirtualConsole.start()
    val player = console.createPlayer("MigrationTest")

    // v1 데이터를 직접 PDC에 쓰기 (이전 버전 플러그인이 저장한 데이터 시뮬레이션)
    val rawKey = console.plugin.key("xp")
    player.setInt(rawKey, 999)  // v1: Int로 저장
    // 스키마 버전 키도 v1로 설정
    player.setInt(console.plugin.key("__schema_version"), 1)

    // v2 스키마 로드 — 마이그레이션 실행
    val schema = PdcSchema.define(console.plugin) {
        version(2)
        field("xp", DataType.LONG, default = 0L)
        migrate(from = 1, to = 2) { data ->
            data["xp"] = (data["xp"] as? Int ?: 0).toLong()
        }
    }
    schema.load(player)

    // 마이그레이션 결과 확인
    val xp = schema.get(player, "xp")
    assertInstanceOf(Long::class.java, xp)
    assertEquals(999L, xp)

    console.shutdown()
}
```

### GUI 슬롯 클릭 시뮬레이션

```kotlin
@Test
fun `GUI 슬롯 클릭이 올바르게 처리된다`() {
    val console = VirtualConsole.start()
    val player = console.createPlayer("GUITest")
    var clicked = false

    val menu = console.plugin.menu(Component.text("Test"), rows = 1) {
        item(4, item(Material.DIAMOND) { name("Click Me") }) {
            clicked = true
        }
    }

    menu.open(player)
    // 슬롯 4 클릭 시뮬레이션
    console.simulateInventoryClick(player, slot = 4)

    assertTrue(clicked, "슬롯 4 클릭 핸들러가 실행되어야 합니다.")
    console.shutdown()
}

@Test
fun `onClose 핸들러가 호출된다`() {
    val console = VirtualConsole.start()
    val player = console.createPlayer("CloseTest")
    var closedBy: String? = null

    val menu = console.plugin.menu(Component.text("Test"), rows = 1) {
        onClose { viewer -> closedBy = viewer.name }
    }

    menu.open(player)
    console.simulateInventoryClose(player)

    assertEquals("CloseTest", closedBy)
    console.shutdown()
}
```

### 커맨드 파싱 검증

```kotlin
@Test
fun `shop buy 커맨드가 올바르게 파싱된다`() {
    val console = VirtualConsole.start()
    val player = console.createPlayer("CmdTest")
    var purchasedItem: String? = null

    console.plugin.registerCommand("shop") {
        playerOnly()
        subcommand("buy") {
            execute { ctx ->
                purchasedItem = ctx.arg(0)
            }
        }
    }

    // 커맨드 실행 시뮬레이션
    console.executeCommand(player, "/shop buy sword")

    assertEquals("sword", purchasedItem)
    console.shutdown()
}

@Test
fun `권한 없으면 커맨드가 차단된다`() {
    val console = VirtualConsole.start()
    val player = console.createPlayer("NoPermTest")
    // player에게 권한 부여 안 함
    var executed = false

    console.plugin.registerCommand("admin") {
        permission("admin.use")
        execute { executed = true }
    }

    console.executeCommand(player, "/admin")

    assertFalse(executed, "권한 없는 플레이어는 커맨드를 실행할 수 없어야 합니다.")
    console.shutdown()
}
```

### PacketCapture — 패킷 전송 검증

`PacketCapture`는 실제로 네트워크로 전송되지 않고, 패킷을 캡처하여 단언합니다.

```kotlin
@Test
fun `타이틀 전송 패킷이 올바르게 생성된다`() {
    val console = VirtualConsole.start()
    val player = console.createPlayer("PacketTest")

    val capture = PacketCapture.start(player)
    player.sendTitle("§aVictory!", "§7Well played.", 10, 70, 20)
    val packets = capture.stop()

    val titlePacket = packets.find<ClientboundSetTitlesTextPacket>()
    assertNotNull(titlePacket, "타이틀 패킷이 전송되어야 합니다.")

    console.shutdown()
}

@Test
fun `ItemAuthenticator 서명 후 검증이 통과한다`() {
    val console = VirtualConsole.start()
    val item = item(Material.DIAMOND_SWORD) { name("Signed Sword") }

    val signed = ItemAuthenticator.sign(console.plugin, item)
    val result = ItemAuthenticator.verify(console.plugin, signed)

    assertEquals(ItemVerification.VALID, result)

    // 아이템 수정 후 검증 실패
    val modified = signed.clone()
    modified.editMeta { it.displayName(Component.text("Hacked Sword")) }
    val invalidResult = ItemAuthenticator.verify(console.plugin, modified)
    assertEquals(ItemVerification.INVALID, invalidResult)

    console.shutdown()
}
```

---

## 테스트할 수 없는 항목

`arc-test`는 Arc API 로직을 테스트하는 환경이지, 전체 Minecraft 서버를 시뮬레이션하지 않습니다. 다음 항목은 테스트할 수 없습니다.

- **NMS 수준 동작**: `nms<T>()` 접근, 커스텀 패스파인더, NMS 이벤트 시스템.
- **실제 월드 생성**: 청크 생성, 블록 물리, 중력 시뮬레이션.
- **플레이어 물리**: 이동, 충돌, 낙하 데미지.
- **네트워크 프로토콜**: 실제 클라이언트-서버 패킷 왕복.
- **멀티스레드 Bukkit 이벤트 흐름**: `AsyncPlayerChatEvent` 같은 실제 비동기 이벤트.

이런 항목은 실제 서버에서 E2E 테스트로 검증해야 합니다.

---

## 장점과 단점

### 장점

- **CI 피드백 루프 단축**: 서버 시작(30초~2분) 없이 JUnit 테스트로 수 초 내 결과를 얻습니다.
- **서버 불필요**: 로컬 개발 환경에 Paper 서버를 설치하지 않아도 테스트를 작성할 수 있습니다.
- **결정론적 테스트**: 실제 서버 환경의 타이밍 이슈, 월드 상태 등 비결정론적 요소 없이 테스트합니다.
- **빠른 반복**: 아이템 로직, PDC, 커맨드 파싱 등 순수 로직 버그를 빠르게 발견합니다.

### 단점

- **부분 시뮬레이션**: 전체 Minecraft 서버를 시뮬레이션하지 않으므로 NMS 의존 기능은 테스트 불가.
- **arc-api와 버전 동기화 필요**: `arc-test` 버전은 `arc-api` 버전과 일치해야 합니다.
- **플레이어 물리/월드 불가**: 실제 게임 플레이 시나리오(전투, 이동, 건물 붕괴 등)는 테스트 불가.
