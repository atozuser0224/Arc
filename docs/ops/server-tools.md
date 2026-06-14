---
title: 서버 도구
parent: Ops Suite
nav_order: 5
---

# 서버 도구

서버 도구는 월드·청크 분석, 커맨드·권한 검색, 로그 관리, 진단 번들 생성, 청크 사전 생성 등 운영에 필요한 다양한 유틸리티 명령어를 모아 놓은 섹션입니다.

---

## 커맨드 · 권한 검색

서버에 등록된 커맨드가 많아지면 어떤 플러그인이 어떤 커맨드를 등록했는지 파악하기 어려울 수 있습니다. Arc는 커맨드와 권한을 빠르게 검색하는 기능을 제공합니다.

### 커맨드 검색

| 명령어 | 설명 |
|--------|------|
| `/arc command search <query>` | 이름이나 설명에 키워드가 포함된 커맨드 검색 |
| `/arc command info <command>` | 특정 커맨드의 출처 플러그인, 권한, 별칭 표시 |
| `/arc command conflicts` | 동일한 이름으로 등록된 커맨드 충돌 목록 |

**사용 예시:**

```
/arc command search home
> 'home' 검색 결과:
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
> /home          EssentialsX    권한: essentials.home
> /sethome       EssentialsX    권한: essentials.sethome
> /delhome       EssentialsX    권한: essentials.delhome
> /homelist      EssentialsX    권한: essentials.home.list
> /phome         PlayerHomes    권한: playerhomes.home
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/arc command conflicts
> 커맨드 충돌 목록:
> /back   EssentialsX (우선), CMI (충돌)
> /msg    EssentialsX (우선), BetterChat (충돌)
```

### 권한 검색

| 명령어 | 설명 |
|--------|------|
| `/arc permission search <query>` | 권한 노드 검색 |
| `/arc permission plugin <plugin>` | 특정 플러그인이 등록한 권한 목록 전체 표시 |
| `/arc permission player <player>` | 특정 플레이어가 가진 권한 목록 (LuckPerms 연동) |

**사용 예시:**

```
/arc permission plugin EssentialsX
> EssentialsX 권한 목록 (총 47개):
> essentials.home          홈 포인트 사용
> essentials.sethome       홈 포인트 설정
> essentials.tpa           텔레포트 요청
> essentials.spawn         스폰 이동
> ... (43개 더)

/arc permission player Steve
> Steve의 권한 (LuckPerms 그룹: default):
> ✓ essentials.home
> ✓ essentials.tpa
> ✗ essentials.fly  (없음)
```

---

## 월드 · 청크 분석

### 월드 리포트

```
/arc world report
```

현재 로드된 모든 월드의 상태를 요약합니다.

**출력 예시:**

```
/arc world report
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
>   월드 리포트
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
> world (오버월드)
>   로드된 청크: 1,842개
>   엔티티 수:   4,231개 (몹: 2,100, 아이템: 412, 기타: 1,719)
>   플레이어:    28명
>
> world_nether (네더)
>   로드된 청크: 214개
>   엔티티 수:   892개
>   플레이어:    4명
>
> world_the_end (엔드)
>   로드된 청크: 38개
>   엔티티 수:   127개
>   플레이어:    0명
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
> 전체 로드된 청크: 2,094개
> 전체 엔티티: 5,250개
```

### 게임룰 조회

```
/arc world gamerules
```

현재 월드의 모든 게임룰 값을 기본값과 비교하여 표시합니다. 기본값과 다른 항목은 강조 표시됩니다.

```
/arc world gamerules
> 월드: world 게임룰
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
> keepInventory:          true    (기본: false) ⚠ 변경됨
> doDaylightCycle:        true    (기본: true)  ✓
> doMobSpawning:          true    (기본: true)  ✓
> randomTickSpeed:        3       (기본: 3)     ✓
> mobGriefing:            false   (기본: true)  ⚠ 변경됨
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 청크 분석

### 청크 리포트

```
/arc chunks report
```

각 월드별로 현재 로드된 청크의 수와 로드 이유를 분류합니다.

```
/arc chunks report
> world 청크 로드 현황 (1,842개)
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
> 플레이어 뷰 거리:        1,204개  (65.4%)
> 강제 로드 (플러그인):      412개  (22.4%)
> 스폰 청크:                 196개  (10.6%)
> 틱 티켓 (레드스톤 등):      30개   (1.6%)
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### 청크 티켓 분석

`/arc chunks tickets` 명령어는 어떤 플러그인 또는 원인이 청크를 강제로 로드 상태로 유지하는지 파악합니다. 일부 플러그인이 청크를 해제하지 않고 과도하게 유지하면 메모리 사용량이 급증할 수 있습니다.

```
/arc chunks tickets
> 청크 티켓 분석 (강제 로드 412개)
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
> ShopPlugin:           284개  (머천트 NPC 위치 로드)
> SomeHeavyPlugin:      112개  (이유 불명)  ⚠
> 바닐라 강제 청크:       16개
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
> ⚠ SomeHeavyPlugin이 112개 청크를 강제 로드 중입니다.
>   이유가 명확하지 않습니다. 플러그인 설정을 확인하세요.
```

### 청크 백로그 분석

```
/arc chunks backlog
```

청크 생성/로드 대기 큐 상태를 표시합니다. 백로그가 크면 플레이어 이동 시 청크 로딩 지연(청크 팝인)이 발생합니다.

---

## 로그 관리

### 명령어

| 명령어 | 설명 |
|--------|------|
| `/arc logs summary` | 최근 로그 요약 (오류 수, 경고 수, 주요 이벤트) |
| `/arc logs errors` | 최근 오류 및 예외 메시지 목록 |
| `/arc logs plugin <plugin>` | 특정 플러그인 관련 로그만 필터링하여 표시 |

**사용 예시:**

```
/arc logs errors
> 최근 오류 (최근 1시간, 총 7건)
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
> [14:23:01] SomeHeavyPlugin  NullPointerException in onPlayerMove
>            at com.example.heavy.MoveListener.onMove(MoveListener.java:42)
>
> [13:55:12] DatabasePlugin   Connection timeout (retry 3/3)
>            Unable to connect to database: connection refused
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/arc logs plugin SomeHeavyPlugin
> SomeHeavyPlugin 로그 (최근 1시간)
> [14:23:01] ERROR NullPointerException in onPlayerMove
> [14:10:44] WARN  캐시 미스 비율 높음: 78% (권장: 20% 이하)
> [14:00:00] INFO  플러그인 초기화 완료 (18.3s)
```

---

## 스냅샷

플러그인 스냅샷(롤백 시스템)과 별개로, Arc는 서버 설정 파일의 스냅샷을 생성하고 비교하는 기능도 제공합니다.

### 명령어

| 명령어 | 설명 |
|--------|------|
| `/arc snapshot create` | 현재 서버 설정 스냅샷 생성 |
| `/arc snapshot list` | 저장된 스냅샷 목록 조회 |
| `/arc snapshot compare <id1> <id2>` | 두 스냅샷 간의 설정 차이 비교 |

**사용 예시:**

```
/arc snapshot create
> 설정 스냅샷 생성: snap-20260615-1430
>   포함된 파일: server.properties, arc-perf.yml, arc-ops.yml, arc-network.yml

/arc snapshot compare snap-20260610-0900 snap-20260615-1430
> 스냅샷 비교: snap-20260610 → snap-20260615
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
> arc-perf.yml:
>   perf.mspt-threshold: 50 → 60
>   perf.view-distance.max: 12 → 10
>
> arc-ops.yml:
>   ops.confirm-timeout: 30 → 60
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 진단 번들

지원 요청 시 서버 상태를 종합하여 하나의 zip 파일로 묶는 기능입니다.

```
/arc issue-bundle
```

### 번들에 포함되는 정보

- 최근 서버 로그 (민감 정보 마스킹)
- `/arc doctor` 리포트
- 플러그인 목록 및 안전 등급
- 최근 렉 스파이크 이력
- JVM 메모리 상태
- Arc 설정 파일 (비밀번호 등 민감 항목 제외)
- 최근 크래시 보고서

```
/arc issue-bundle
> 진단 정보 수집 중...
>   ✓ 로그 수집 (민감 정보 마스킹 완료)
>   ✓ 성능 데이터 수집
>   ✓ 플러그인 리포트 생성
>   ✓ 설정 파일 포함 (비밀번호 제외)
>
> 번들 생성 완료: /server/arc-diagnostics-20260615-1435.zip (2.4 MB)
> 지원 티켓 제출 시 이 파일을 첨부하세요.
```

---

## 청크 미리 생성 (pregen)

신규 서버를 오픈하기 전이나, 플레이어가 탐험할 예정인 지역을 미리 생성해 두면 서버 운영 중 청크 생성으로 인한 TPS 저하를 크게 줄일 수 있습니다.

> **주의:** 청크 미리 생성 기능은 기본적으로 비활성화(DEFAULT OFF)되어 있습니다. 운영 중 서버에서 사용하면 매우 높은 CPU 및 디스크 I/O가 발생하여 TPS가 크게 저하될 수 있습니다.

### 명령어

| 명령어 | 설명 |
|--------|------|
| `/arc pregen start <world> <radius>` | 지정한 월드의 스폰을 중심으로 반경 내 청크 사전 생성 |
| `/arc pregen start <world> <x> <z> <radius>` | 특정 좌표를 중심으로 청크 사전 생성 |
| `/arc pregen stop` | 진행 중인 미리 생성 중지 |
| `/arc pregen status` | 현재 진행 상태 및 예상 완료 시간 확인 |
| `/arc pregen resume` | 중지된 미리 생성 재개 |

### 사용 예시

```
# 스폰 기준 반경 5000블록 청크 미리 생성
/arc pregen start world 5000
> ⚠ 경고: 청크 미리 생성은 높은 CPU/디스크 I/O를 유발합니다.
>   운영 중 서버에서 실행하면 TPS가 일시적으로 저하될 수 있습니다.
> 계속하려면: /arc confirm p2n8k3

/arc confirm p2n8k3
> 청크 미리 생성 시작
>   월드: world
>   범위: 스폰 기준 반경 5,000블록
>   예상 청크 수: 약 78,540개
>   예상 소요 시간: 약 2시간 30분 (서버 부하에 따라 변동)

/arc pregen status
> 청크 미리 생성 진행 중
>   완료: 12,450개 / 78,540개 (15.9%)
>   경과 시간: 23분
>   예상 남은 시간: 약 2시간 7분
>   현재 TPS: 16.2 (미리 생성으로 인한 부하)
```

### 사용 시나리오

- **신규 서버 오픈 전**: 오픈 전날 밤이나 서버 유지보수 시간에 미리 생성을 실행합니다.
- **새 탐험 지역 추가**: 맵 확장이나 이벤트 지역을 미리 생성하여 플레이어 경험을 개선합니다.
- **성능 민감 서버**: 많은 플레이어가 동시에 탐험하는 서버에서 청크 생성 렉을 사전에 방지합니다.

### 주의 사항

- 미리 생성 중에는 TPS가 저하되므로 플레이어 경험에 직접적인 영향을 줍니다. 가능하면 플레이어가 적은 시간대에 실행하세요.
- 대규모 지역(반경 10,000블록 이상)은 수 시간에서 수십 시간이 소요될 수 있으며, 디스크 공간도 상당히 필요합니다.
- `/arc pregen stop` 후 `/arc pregen resume`으로 중간부터 재개할 수 있으므로, 필요시 나누어서 진행할 수 있습니다.

### 장단점

**장점:**
- 서버 운영 중 청크 생성으로 인한 TPS 저하와 청크 팝인 현상을 크게 줄입니다.
- 플레이어가 새로운 지역을 탐험할 때 부드러운 경험을 제공합니다.

**단점:**
- 미리 생성 과정에서 매우 높은 CPU 및 디스크 I/O가 발생합니다.
- 대규모 지역은 많은 디스크 공간을 차지합니다 (반경 5,000블록 기준 약 4~8 GB).
- 운영 중 사용하면 TPS가 일시적으로 낮아져 플레이어 불만이 생길 수 있습니다. 점검 시간이나 새벽 시간대 사용을 권장합니다.
