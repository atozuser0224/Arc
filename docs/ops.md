---
title: Ops Suite
nav_order: 2
---

# Layer 2 — Ops Suite

`/arc` 명령어 체계. `arc-ops.yml`로 개별 기능을 켜고 끈다.  
모든 위험 작업은 confirm 토큰을 요구한다.

---

## 플러그인 관리 `/arc plugin *`

| 커맨드 | 설명 |
|--------|------|
| `list` | 로드된 플러그인 전체 목록 (상태·버전 포함) |
| `info <plugin>` | 플러그인 상세 정보 (의존성·권한·이벤트 수) |
| `check <plugin>` | 리로드 안전 등급 평가 (SAFE/WARNING/UNSAFE/UNKNOWN) |
| `enable <plugin>` | 플러그인 활성화 (의존 플러그인 자동 감지) |
| `disable <plugin>` | 비활성화 (의존 플러그인 포함) |
| `reload <plugin> [--force]` | 안전 등급 확인 후 핫리로드 |
| `restart <plugin> [--force]` | disable → enable 재시작 |
| `dependents <plugin>` | 의존 중인 플러그인 목록 |
| `reload-chain <plugin> [--dry-run\|--apply\|--force]` | 의존 그래프 전체 리로드 계획·실행 |
| `leaks <plugin>` | classloader·스레드·listener 잔류 확인 |
| `report` | 전체 플러그인 상태 리포트 |
| `outdated` | 버전 업데이트 확인 |
| `rollback <plugin>` | 현재 상태 스냅샷 캡처 |
| `rollback <plugin> --list` | 사용 가능한 롤백 목록 |
| `rollback <plugin> --restore <id>` | 특정 버전으로 복구 후 핫리로드 |
| `confirm <token>` | 위험 작업 confirm 토큰 입력 |

### 마켓플레이스 `/arc plugin marketplace`

| 커맨드 | 설명 |
|--------|------|
| `search <query>` | Modrinth에서 Paper 플러그인 검색 |
| `install <slug>` | JAR 다운로드 → 샌드박스 체크 → 설치·핫로드 |
| `info <slug>` | Modrinth 플러그인 상세 정보 |

**설치 흐름**: 다운로드 완료 후 `PluginSandboxCheck`가 NMS·네이티브·스레드 패턴을 정적 분석한다.  
- `SAFE` → 즉시 설치  
- `CAUTION` → 결과 출력 후 confirm 토큰 요구  
- `DANGEROUS` → 설치 차단

### 샌드박스 분석 `/arc sandbox check <jar>`

플러그인 JAR을 로드하기 전에 오프라인 정적 분석한다.

```
/arc sandbox check WorldEdit-7.3.0.jar
```

분석 항목:
- NMS / CraftBukkit 직접 접근
- 네이티브 라이브러리 (`.so` / `.dll` / `.dylib`)
- `api-version` 누락 또는 구버전
- 스레드 풀 / Kotlin coroutine 사용
- Shaded 라이브러리 수
- Vault 의존성

---

## 서버 진단

| 커맨드 | 설명 |
|--------|------|
| `/arc doctor [--paste]` | 서버 전체 건강 리포트 |
| `/arc lagspike list\|last` | 렉 스파이크 기록 조회 |
| `/arc profiler start\|stop\|report` | 메인 스레드 프로파일러 |
| `/arc memory` | JVM 힙·GC 상태 |
| `/arc startup-profile [detail <plugin>]` | 부팅 시간 분석 |
| `/arc plugin-cost top\|<plugin>` | 플러그인별 메인스레드 점유 비용 |

**StallWatchdog**: 항상 실행되는 오프스레드 감시자. 메인 스레드가 N초 이상 응답 없으면 스택 트레이스를 파일로 덤프한다.

---

## 설정 관리 `/arc config *`

| 커맨드 | 설명 |
|--------|------|
| `get <path>` | 설정 값 읽기 |
| `set <path> <value>` | 실행 중 설정 변경 (이력 기록) |
| `reset <path>` | 기본값으로 복원 |
| `diff` | 현재 설정과 기본값 비교 |
| `search <keyword>` | 키·값 검색 |
| `history` | 변경 이력 (누가 언제 무엇을) |
| `exclude <plugin> <feature>` | 기능 제외 목록 관리 |

---

## 서버 도구

| 커맨드 | 설명 |
|--------|------|
| `/arc command search <query>` | 등록된 커맨드 검색 |
| `/arc permission search\|plugin\|player` | 권한 노드 검색 |
| `/arc world report\|gamerules` | 월드 상태·게임룰 |
| `/arc chunks report\|tickets\|backlog` | 청크 진단 |
| `/arc logs summary\|errors\|plugin` | 로그 검사 |
| `/arc snapshot create\|list\|compare` | 서버 스냅샷 |
| `/arc issue-bundle` | 진단 zip 생성 (지원용) |
| `/arc paste doctor\|config\|logs` | 민감 정보 마스킹 후 paste 업로드 |

---

## 보안·유지보수

| 커맨드 | 설명 |
|--------|------|
| `/arc security-audit` | 오프라인 모드·RCON·OP 수 확인 |
| `/arc proxy-check` | Velocity/BungeeCord 설정 검사 |
| `/arc safe-mode enable\|disable` | 다음 부팅 시 최소 플러그인만 로드 (재시작 후 유지) |
| `/arc maintenance on\|off\|allow <p>\|status` | 점검 모드 (재시작 후 유지) |
| `/arc audit last\|since\|search` | 감사 로그 조회 |
| `/arc pregen` | 청크 프리젠 (DEFAULT OFF) |
