---
title: Ops Suite
nav_order: 2
---

# Layer 2 — Ops Suite

Ops Suite는 Arc의 운영자 도구 모음이다. 약 100개의 `/arc` 서브커맨드로 서버 관리, 플러그인 관리, 진단, 보안을 처리한다. 모든 기능은 `arc-ops.yml`에서 개별적으로 켜고 끌 수 있으며, 위험한 작업은 confirm 토큰을 입력해야 실행된다.

---

## 플러그인 관리 `/arc plugin *`

기존에는 플러그인 리로드가 필요하면 서버를 재시작하거나 안전성이 검증되지 않은 PluginMan 계열 도구를 써야 했다. Arc는 플러그인의 의존성 그래프를 분석하고, 안전하게 리로드할 수 있는지 사전에 평가한 뒤 작업한다.

### 기본 관리

| 커맨드 | 설명 |
|--------|------|
| `list` | 로드된 모든 플러그인 목록 (상태, 버전 포함) |
| `info <plugin>` | 플러그인 상세 정보 — 의존성, 등록된 권한, 이벤트 리스너 수 |
| `check <plugin>` | 리로드 안전 등급 평가 — SAFE / WARNING / UNSAFE / UNKNOWN |
| `enable <plugin>` | 플러그인 활성화. 의존하는 플러그인도 함께 감지 |
| `disable <plugin>` | 비활성화. 이 플러그인에 의존하는 플러그인까지 함께 비활성화 |
| `reload <plugin>` | 안전 등급 확인 후 핫리로드. `--force`로 경고 무시 가능 |
| `restart <plugin>` | disable → enable 순서로 재시작 |
| `dependents <plugin>` | 이 플러그인을 의존하는 플러그인 목록 |
| `reload-chain <plugin>` | 의존 그래프 전체 리로드 계획 및 실행. `--dry-run`으로 미리 보기 가능 |
| `leaks <plugin>` | 비활성화 후에도 남아있는 classloader, 스레드, 이벤트 리스너 확인 |
| `report` | 전체 플러그인 상태 요약 리포트 |
| `outdated` | Modrinth 기준 업데이트 가능한 플러그인 목록 |

**안전 등급 예시:**
```
/arc plugin check EssentialsX
→ SAFE — 정적 핸들러 없음, NMS 직접 접근 없음, 리로드 안전
   의존 플러그인: Vault, EssentialsXChat, EssentialsXSpawn

/arc plugin check SomeHeavyPlugin
→ UNSAFE — Kotlin 코루틴 스레드 풀 사용 감지 (리로드 시 스레드 누수 가능)
   해결: 서버 재시작 또는 --force로 강제 리로드 (권장하지 않음)
```

### 롤백

플러그인을 업데이트하기 전에 현재 상태를 스냅샷으로 저장할 수 있다. 문제가 생기면 스냅샷으로 즉시 되돌린다.

```
/arc plugin rollback EssentialsX                  — 현재 상태 스냅샷 저장
/arc plugin rollback EssentialsX --list           — 저장된 스냅샷 목록
/arc plugin rollback EssentialsX --restore 3      — 스냅샷 #3으로 복구 후 핫리로드
```

서버 재시작 없이 이전 버전으로 복구할 수 있어 운영 중단 시간을 크게 줄인다.

### 마켓플레이스 `/arc plugin marketplace`

Modrinth에서 플러그인을 검색하고 바로 설치한다. 설치 전 정적 분석(PluginSandboxCheck)을 통해 위험 요소를 사전에 확인한다.

```
/arc plugin marketplace search economy            — Modrinth Paper 플러그인 검색
/arc plugin marketplace install vault             — 다운로드 → 의존성 자동 설치 → 샌드박스 분석 → 설치
/arc plugin marketplace info essentialsx          — Modrinth 플러그인 상세 정보
```

**설치 흐름:**
1. JAR 다운로드
2. `plugin.yml`에서 의존성 목록 확인 → 미설치 의존성 자동 설치 (재귀)
3. PluginSandboxCheck 분석
   - `SAFE` → 즉시 설치 및 활성화
   - `CAUTION` → 분석 결과 출력 후 confirm 토큰 요구
   - `DANGEROUS` → 설치 차단
4. 기존 플러그인이면 롤백 스냅샷 자동 생성

### 샌드박스 분석 `/arc sandbox check <jar>`

서버에 올리기 전에 JAR 파일을 오프라인으로 분석한다.

```
/arc sandbox check /tmp/SuspiciousPlugin.jar

분석 결과:
  [WARNING] NMS 직접 접근 감지 (버전 고정 위험): net/minecraft/server/v1_18_R2/...
  [WARNING] Shaded 라이브러리 47개 (JAR 크기: 38MB)
  [OK] api-version: 1.21
  [OK] 네이티브 라이브러리 없음
  등급: CAUTION
```

**분석 항목:**
- NMS / CraftBukkit 직접 접근 (버전 고정 위험)
- 네이티브 라이브러리 (.so / .dll / .dylib) 포함 여부
- `api-version` 누락 또는 구버전
- 스레드 풀 / Kotlin 코루틴 사용 (리로드 시 스레드 누수 위험)
- Shaded 라이브러리 수 (JAR 크기 과다 여부)
- Vault 의존성 (Vault가 없는 환경에서의 실패 가능성)

---

## 서버 진단

서버에 문제가 생겼을 때 원인을 빠르게 찾기 위한 도구들이다. 대부분은 읽기 전용이라 실행해도 서버 상태가 바뀌지 않는다.

| 커맨드 | 설명 |
|--------|------|
| `/arc doctor` | 서버 전체 건강 리포트. TPS, 메모리, 플러그인 오류, 최근 크래시, 보안 설정을 한 번에 출력 |
| `/arc doctor --paste` | 민감 정보를 마스킹한 뒤 paste 서비스에 업로드. 지원 요청 시 유용 |
| `/arc lagspike list` | 기록된 렉 스파이크 목록 (시각, 지속 시간, 당시 MSPT) |
| `/arc lagspike last` | 가장 최근 렉 스파이크 상세 정보 |
| `/arc profiler start` | 메인 스레드 프로파일러 시작 |
| `/arc profiler stop` | 프로파일러 중지 |
| `/arc profiler report` | 어떤 코드 경로가 틱 시간을 가장 많이 차지했는지 분석 결과 출력 |
| `/arc memory` | JVM 힙 사용량, GC 횟수, 각 메모리 영역 상태 |
| `/arc startup-profile` | 서버 부팅 시 각 단계가 얼마나 걸렸는지 분석 |
| `/arc startup-profile detail <plugin>` | 특정 플러그인의 onEnable 소요 시간 상세 |
| `/arc plugin-cost top` | 메인 스레드 점유 비용 상위 플러그인 목록 |
| `/arc plugin-cost <plugin>` | 특정 플러그인의 이벤트·틱 비용 세부 내역 |

**`/arc doctor` 출력 예시:**
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Arc Doctor — game-1 서버 상태 리포트
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TPS:     ✅ 19.8 (5분 평균: 19.5)
MSPT:    ✅ 12.4ms
메모리:  ✅ 4.2GB / 8GB (52%)
플레이어: 47명 / 100명

플러그인 오류:  ⚠️  SomePlugin — 최근 1시간 23개 오류
크래시 기록:   ✅  없음
보안:          ⚠️  오프라인 모드 활성화됨
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**`/arc plugin-cost top` 출력 예시:**
```
플러그인 메인 스레드 점유 비용 (최근 60초)
  1. SomeHeavyPlugin     28.4ms/s  (18.2%)
  2. EssentialsX          8.1ms/s  ( 5.2%)
  3. WorldGuard           4.7ms/s  ( 3.0%)
  ...
```

### StallWatchdog

항상 실행되는 오프스레드 감시자로, 메인 스레드가 설정된 시간(기본 5초) 이상 응답하지 않으면 스택 트레이스를 파일로 덤프한다.

---

## 설정 관리 `/arc config *`

실행 중인 서버의 설정값을 재시작 없이 변경하고, 변경 이력을 추적한다.

| 커맨드 | 설명 |
|--------|------|
| `get <path>` | 현재 설정값 조회 |
| `set <path> <value>` | 실행 중 설정 변경. 변경자, 시각, 이전값이 이력에 기록됨 |
| `reset <path>` | 기본값으로 복원 |
| `diff` | 현재 설정과 기본값의 차이점 출력 |
| `search <keyword>` | 키 또는 값으로 설정 항목 검색 |
| `history` | 누가 언제 무엇을 바꿨는지 변경 이력 조회 |
| `exclude <plugin> <feature>` | 특정 플러그인을 Arc 기능 영향에서 제외 |

```
/arc config set lag-spike-capture.mspt-threshold 50.0
→ 변경됨: lag-spike-capture.mspt-threshold
   이전값: 100.0 → 새 값: 50.0
   변경자: Admin | 시각: 2026-06-14 15:32:11

/arc config history
→ [15:32:11] Admin: lag-spike-capture.mspt-threshold 100.0 → 50.0
   [14:21:05] Operator: entity-density-guard.enabled false → true
   [12:00:00] SYSTEM: 서버 시작, 설정 로드
```

---

## 서버 도구

| 커맨드 | 설명 |
|--------|------|
| `/arc command search <query>` | 서버에 등록된 커맨드 검색. 출처 플러그인까지 표시 |
| `/arc permission search <query>` | 권한 노드 검색 |
| `/arc permission plugin <plugin>` | 특정 플러그인이 등록한 권한 목록 |
| `/arc permission player <player>` | 플레이어의 현재 권한 상태 |
| `/arc world report` | 월드 상태 (청크 수, 엔티티 수, 게임룰) |
| `/arc world gamerules` | 현재 게임룰 전체 목록 |
| `/arc chunks report` | 청크 로드 현황 |
| `/arc chunks tickets` | 청크 로드 티켓 목록 (어떤 이유로 청크가 로드됐는지) |
| `/arc chunks backlog` | 청크 생성 백로그 크기 |
| `/arc logs summary` | 최근 로그 요약 |
| `/arc logs errors` | 오류 로그만 필터링 |
| `/arc logs plugin <plugin>` | 특정 플러그인 관련 로그만 표시 |
| `/arc snapshot create` | 서버 현재 상태 스냅샷 저장 |
| `/arc snapshot list` | 저장된 스냅샷 목록 |
| `/arc snapshot compare` | 두 스냅샷 간 차이 비교 |
| `/arc issue-bundle` | 진단 정보 zip 파일 생성. 지원 티켓 제출 시 첨부 |

---

## 보안 · 유지보수

| 커맨드 | 설명 |
|--------|------|
| `/arc security-audit` | 오프라인 모드, RCON 활성화, OP 계정 수 등 보안 취약점 점검 |
| `/arc proxy-check` | Velocity / BungeeCord 설정 검사. 포워딩 설정 오류 감지 |
| `/arc safe-mode enable` | 다음 재시작 시 최소한의 플러그인만 로드 (문제 플러그인 격리) |
| `/arc safe-mode disable` | 안전 모드 해제 |
| `/arc maintenance on` | 점검 모드 활성화. 화이트리스트 외 플레이어 입장 차단 |
| `/arc maintenance off` | 점검 모드 해제 |
| `/arc maintenance allow <player>` | 점검 중 특정 플레이어 입장 허용 |
| `/arc maintenance status` | 현재 점검 모드 상태 확인 |
| `/arc audit last` | 최근 관리자 작업 이력 |
| `/arc audit since <time>` | 특정 시점 이후 감사 로그 |
| `/arc audit search <keyword>` | 감사 로그 검색 |
| `/arc pregen` | 청크 미리 생성 (DEFAULT OFF) |

---

## 위험 작업 confirm 시스템

플러그인 강제 리로드, 서버 상태 변경, 롤백 등 되돌리기 어려운 작업은 실행 직전에 임시 토큰을 발급한다.

```
/arc plugin reload SomePlugin --force
→ ⚠️ 경고: 이 플러그인은 UNSAFE 등급입니다. 강제 리로드 시 스레드 누수 가능.
   확인하려면 30초 안에: /arc confirm a3f9bx
   취소: 아무것도 하지 않으면 30초 후 자동 취소

/arc confirm a3f9bx
→ ✅ SomePlugin 강제 리로드 실행 중...
```

30초 안에 `/arc confirm <token>`을 입력해야 실행된다. 실수로 위험한 명령을 날리는 것을 방지한다.
