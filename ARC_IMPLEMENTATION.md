# Arc 1.21.4 — Feature Map

Arc는 Leaf(Paper/Purpur) 기반 Minecraft 서버 포크로, 세 가지 가치를 동시에 제공한다: 운영자용 관리 도구(Ops Suite), 플러그인 개발자용 Kotlin-first API(Developer API), 멀티서버 네트워크 레이어(Network). 이 문서는 세 레이어를 기준으로 현재 구현된 기능 전체를 정리한다.

---

## 아키텍처 원칙

```
READ-ONLY DIAGNOSTICS  → DEFAULT ON
BEHAVIOR-CHANGING      → DEFAULT OFF
DANGEROUS OPERATIONS   → CONFIRM REQUIRED
ALL FEATURES           → CONFIG CONTROLLABLE
```

세 레이어는 독립적이다. Developer API만 써도 되고, Ops Suite 없이 Network만 구성해도 된다.

---

## Layer 1 — Fork Core (항상 활성)

서버가 뜨는 순간부터 작동하는 최하위 레이어. 플러그인 없이 포크 자체가 제공한다.

**성능 거버너** (`perf/`): `AdaptiveGovernor`가 MSPT를 기준으로 시뮬/뷰 거리를 자동 조정한다. `TickBudget`은 tick당 작업량을 제한하고, `EntityThrottler`는 비중요 엔티티의 AI 빈도를 낮춘다. `ServerLoad`는 `PerfBackend` SPI를 통해 arc-server의 순간 MSPT와 Bukkit의 평균 MSPT를 둘 다 사용할 수 있도록 추상화한다.

**NMS 브리지** (`nms/NmsRef.kt`): 리플렉션 래퍼. 플러그인이 NMS 필드를 안전하게 읽고 쓰도록 타입 안전한 접근자를 제공한다.

**커스텀 이벤트** (`event/`): `ServerLoadLevelChangeEvent`, `ServerLagSpikeEvent` — Paper가 제공하지 않는 서버 상태 이벤트.

---

## Layer 2 — Ops Suite (`/arc` 명령어)

서버 운영자가 쓰는 진단·관리 도구 전체. `arc-ops.yml`로 켜고 끈다.

### 플러그인 관리

`/arc plugin *` 서브커맨드 전체는 단일 명령어 체계로 묶인다. 안전 등급(SAFE/WARNING/UNSAFE/UNKNOWN)을 평가한 뒤 위험 작업은 반드시 confirm 토큰을 요구한다.

| 기능 | 커맨드 |
|------|--------|
| 목록·정보·호환성 확인 | `list`, `info`, `check`, `outdated` |
| 활성화/비활성화 | `enable`, `disable` (의존 플러그인 자동 감지) |
| 재로드 | `reload`, `restart`, `reload-chain --dry-run\|--apply` |
| 누수 감지 | `leaks` (classloader 스레드·listener 잔류 확인) |
| JAR 분석 | `/arc sandbox check <jar>` — 로드 전 NMS·네이티브 라이브러리·스레드 패턴 정적 분석 |
| 롤백 | `rollback --list\|--restore <id>` — 이전 버전 복구 후 자동 hot-reload |
| 정책 | `reload-policy set <plugin> allow\|warn\|block` |
| 비용 프로파일링 | `/arc plugin-cost top\|<plugin>` |

### 서버 진단

| 기능 | 커맨드 |
|------|--------|
| 전체 건강 리포트 | `/arc doctor [--paste]` |
| 렉 스파이크 기록 | `/arc lagspike list\|last` |
| 메인 스레드 프로파일러 | `/arc profiler start\|stop\|report` |
| 메모리 | `/arc memory` |
| 부팅 시간 분석 | `/arc startup-profile [detail <plugin>]` |
| 스톨 감시 | 자동 (StallWatchdog) |

### 설정 관리

`/arc config *`: 실행 중 설정 읽기·쓰기·비교·검색, 변경 이력 추적(누가 언제 무엇을 바꿨는지), 플러그인별 기능 제외 목록 관리.

### 서버 도구

| 기능 | 커맨드 |
|------|--------|
| 커맨드·퍼미션 검색 | `/arc command search`, `/arc permission search\|plugin\|player` |
| 월드 상태 | `/arc world report\|gamerules` |
| 청크 진단 | `/arc chunks report\|tickets\|backlog` |
| 로그 검사 | `/arc logs summary\|errors\|plugin` |
| 스냅샷 | `/arc snapshot create\|list\|compare` |
| 이슈 번들 | `/arc issue-bundle` (진단 zip 생성) |
| Paste 업로드 | `/arc paste doctor\|config\|logs` (민감 정보 마스킹 후 업로드) |

### 보안·유지보수

| 기능 | 커맨드 |
|------|--------|
| 보안 감사 | `/arc security-audit` — 오프라인 모드·RCON·OP 수 확인 |
| 프록시 검증 | `/arc proxy-check` — Velocity/BungeeCord 설정 검사 |
| 세이프 모드 | `/arc safe-mode enable\|disable` — 다음 부팅 시 최소 플러그인만 로드 |
| 점검 모드 | `/arc maintenance on\|off\|allow\|status` — 재시작 후에도 상태 유지됨 |
| 감사 로그 | `/arc audit last\|since\|search` |
| 엔티티 최적화 | `EntityAiOptimizer`, `EntityDensityGuard` — DEFAULT OFF |
| 청크 프리젠 | `/arc pregen` — DEFAULT OFF |
| 상태 HTTP 서버 | `StatusHttpServer` — DEFAULT OFF |

---

## Layer 3 — Developer API (`arc-api`)

플러그인 개발자가 쓰는 Kotlin-first API. Bukkit을 대체하지 않고 보완한다.

### 스케줄러·태스크

`Scheduler`: 메인/비동기 디스패처, 지연·반복 DSL. `Pipeline`: 순차 태스크 체인. 코루틴 SPI(`arc-api/coroutine`)와 연동된다.

### GUI·인터페이스

`Menu` / `PaginatedMenu`: 인벤토리 기반 GUI 빌더, 클릭 핸들러 포함. `Merchants`: 커스텀 상인 GUI. `ChatInput` / `Form`: 채팅 입력 흐름.

### 아이템

`ItemBuilder`: Kotlin DSL 아이템 생성. `ItemBehavior`: 아이템에 행동 바인딩. `ItemAuthenticator`: 서버 서명 기반 아이템 위·변조 감지. `DataComponents`: 1.21 데이터 컴포넌트 접근자. `Enchants`: 커스텀 인챈트 등록.

### 엔티티·디스플레이

`Entities`: 스폰·조회 DSL. `EntityEffects`: 시각 효과 적용. `Equipment`: 장비 관리. `Displays`: Display 엔티티 빌더. `Holograms` / `Hologram`: 텍스트 홀로그램. `Npc`: 스킨 적용 NPC. `Fireworks`: 불꽃놀이 빌더.

### AI

`MobGoalRegistry` / `GoalDsl`: 바닐라 AI 목표를 코드로 추가·제거. `MobAi`: 엔티티 AI 일시 중단·재개.

### 효과·입자·사운드

`CustomEffect` / `CustomEffectEngine`: 플러그인 스코프 가상 포션 효과 (KEEP_STRONGER/REPLACE/EXTEND/IGNORE 정책). `Effects`: 바닐라 포션 DSL. `Particles` / `ParticleEffects`: 입자 DSL. `Sounds`: 사운드 DSL.

### 플레이어

`Cooldowns` / `ServerCooldown`: 단일/크로스서버 쿨다운. `PlayerPersistentData` / `PlayerData` / `PlayerStore`: 타입 안전 PDC 스키마 (마이그레이션·검증 포함). `PlayerControl`: 이동·능력 조작. `ClientWorldState`: 클라이언트에게만 보이는 월드 상태.

### 월드

`WorldOps`: 복잡한 월드 작업 DSL. `WorldChunk`: 청크 강제 로드·언로드. `WorldSnapshot`: 블록 영역 스냅샷. `Explosions`, `Gamerules`, `WorldBorders`, `Structures`, `LootTables`, `RayTrace` — 각 기능별 DSL.

### 커맨드

`Commands`: Kotlin DSL 커맨드 등록. 탭 완성, 퍼미션, 서브커맨드 중첩 지원.

### 채팅

`Chat`: 채팅 파이프라인 진입점. `ChatPipeline`: 필터·변환 체인.

### UI 요소

`Titles`, `Tablist`, `BossBars`, `Sidebar`, `Teams` — 각각 단순한 Kotlin DSL로 제공.

### 퍼미션·레시피

`PermissionRegistry`: 코드에서 퍼미션 노드 선언. `Recipes`: 커스텀 제작법 등록.

### PDC·직렬화

`PdcSchema` / `PersistentData`: 타입 토큰 기반 PDC, 중복 키 거부, 검증, 마이그레이션. `Serialization`: 공통 직렬화 어댑터.

### 레지스트리·어트리뷰트

`Registries` / `RegistryDsl` / `DynamicRegistry`: 1.21 RegistryAccess 래퍼. `Attributes` / `CustomAttributes`: 커스텀 어트리뷰트 등록.

### 데이터팩

`DatapackContent` / `DatapackMaker`: 코드로 데이터팩 생성·배포. 바이옴, 루트 테이블, 태그, mcfunction 지원.

### 패킷·채널

`PacketBridge` / `PacketInterceptor`: Netty 레벨 패킷 후킹. `ModChannel` / `ModPacketBuffer`: NMS 타입 노출 없는 클라이언트 모드 패킷 채널.

### 프로필·스킨

`Profiles`, `Skins`: Mojang API 연동 없이 텍스처 적용.

### 유틸리티

`Format` (텍스트 포맷), `Time` (시간 DSL), `Spline` (수학), `Countdown` (카운트다운), `Cuboid` / `RegionEffects` (영역), `Combat` / `DamageSources` (전투), `Movement` (이동), `Stats` (통계).

### arc-test

서버 없이 arc-api 플러그인을 테스트하는 프레임워크. `PacketCapture`, `PDC assertions`, `VirtualConsole` 포함.

---

## Layer 4 — Network (선택, Redis 필요)

`arc-network.yml`에서 `enabled: false`가 기본값이다. Redis 없이는 아무것도 활성화되지 않는다.

### 핵심 컴포넌트

`ArcRelayClient`: Redis 추상화 (KV·Set·ZSet·Pub/Sub·분산 락·쿨다운). `ArcServerRegistry`: 2초 heartbeat로 서버 상태를 Redis에 등록. `ArcPlayerTransfer`: 서버 간 플레이어 이동 (그룹 권한·full 체크 포함). `ArcQueue`: Redis ZSet 기반 대기열 (VIP/Priority 오프셋). `ArcNetworkAudit`: 모든 네트워크 작업 감사 기록.

### 네트워크 커맨드 (`/arc network *`)

서버 대시보드, 플레이어 조회, 전송, 대기열 관리, 유지보수 모드, 강제 대피(evacuate), 네트워크 broadcast, 글로벌 쿨다운.

### 위험도별 기본값

| 기능 | 기본값 | 이유 |
|------|--------|------|
| 서버 등록·전송·대기열 | ON | 역방향 복구 가능 |
| Global Vault | OFF | 아이템 복제 위험 |
| Inventory Transfer | OFF | 높은 데이터 손실 위험 |
| Remote Command | OFF | 임의 명령 실행 위험 |
| Plugin Deploy | OFF | 서버 자체 변조 위험 |

---

## 이 프로젝트에서 제거하거나 분리를 권장하는 기능

Arc의 정체성은 *서버 포크*다. 아래 기능들은 현재 arc-api에 있지만, 별도 라이브러리 플러그인으로 분리하는 것이 더 자연스럽다.

**`http/Http.kt`**: HTTP 클라이언트 추상화. 서버 포크 레벨에서 제공할 이유가 없다. 플러그인이 직접 의존성을 가져오는 것이 더 명확하다.

**`db/Database.kt` / `data/Sql.kt`**: 데이터베이스 풀·쿼리 추상화. 동일한 이유. 플러그인 인프라 레이어가 필요하다면 별도 `arc-db` 모듈이 적절하다.

**`ArcItemMail`**: 아이템 우편함. 네트워크 레이어 안에 있지만 게임 기능에 가깝다. 별도 플러그인이나 arc-network의 optional extension으로 분리를 권장.

**이미 제거된 것들** (`584b76ff` 커밋에서 삭제됨): `NativeAntiCheat`, `SchematicPlacer`, `VirtualEntity`, `AnimationScheduler`, `GlowRegistry`, `FakeAdvancementProgress`, `FakeAdvancementTree`, `FakeWorldState`, `GameRuleOverride`, `ChunkGenerationModifier`, `LiveMapRenderer`, `NoiseAccessor`, `StructureBuilder` — 이 판단은 맞다. 해당 기능들은 구현 완성도 대비 유지보수 부담이 크고, 포크보다 플러그인에 더 어울린다.

---

## 현재 파일 수 (실제 기준)

| 모듈 | 파일 수 |
|------|---------|
| arc-api (Developer API + Ops + Network) | ~160 |
| arc-server (NMS 구현체) | 6 |
| arc-test | ~10 |
| leaf-api / leaf-server (업스트림) | 상속 |

**총 구현 커맨드**: ~80개  
**Minecraft**: 1.21.4  
**Base**: Leaf MC → Paper → Bukkit
