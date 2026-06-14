---
title: 플러그인 관리
parent: Ops Suite
nav_order: 1
---

# 플러그인 관리

Arc의 플러그인 관리 명령어는 설치된 플러그인의 전체 수명 주기를 다룹니다. 단순한 목록 조회부터 안전 등급 평가, 롤백 스냅샷, Modrinth 마켓플레이스 연동 설치까지, 서버를 재시작하지 않고 플러그인을 안전하게 관리할 수 있는 도구를 제공합니다.

---

## 기본 관리 명령어

| 명령어 | 설명 |
|--------|------|
| `/arc plugin list` | 설치된 모든 플러그인 목록과 상태(활성/비활성) 표시 |
| `/arc plugin info <plugin>` | 플러그인 버전, 의존성, 설명, 작성자 등 상세 정보 표시 |
| `/arc plugin check <plugin>` | 플러그인의 안전 등급 평가 및 잠재적 문제 사전 감지 |
| `/arc plugin enable <plugin>` | 비활성화된 플러그인 활성화 (재시작 없이) |
| `/arc plugin disable <plugin>` | 플러그인 비활성화 (재시작 없이, 가능한 경우) |
| `/arc plugin reload <plugin>` | 특정 플러그인만 리로드 |
| `/arc plugin restart <plugin>` | 플러그인을 비활성화 후 다시 활성화 (완전 재초기화) |
| `/arc plugin dependents <plugin>` | 해당 플러그인에 의존하는 다른 플러그인 목록 표시 |
| `/arc plugin reload-chain <plugin>` | 플러그인과 그것에 의존하는 모든 플러그인을 순서에 맞게 리로드 |
| `/arc plugin leaks` | 플러그인 리로드/비활성화 후 잔존하는 메모리 누수 감지 |
| `/arc plugin report` | 현재 플러그인 전체 상태 리포트 생성 |
| `/arc plugin outdated` | 업데이트가 가능한 플러그인 목록 표시 (Modrinth 연동 필요) |

### 사용 예시

```
# 플러그인 상태 확인
/arc plugin list
> [ENABLED]  EssentialsX        v2.21.0
> [ENABLED]  LuckPerms          v5.4.102
> [DISABLED] SomeOldPlugin      v1.2.3
> [ENABLED]  SomeHeavyPlugin    v3.0.1

# 의존 관계 확인 후 안전하게 리로드
/arc plugin dependents EssentialsX
> EssentialsX에 의존하는 플러그인: EssentialsXChat, EssentialsXSpawn

/arc plugin reload-chain EssentialsX
> 리로드 순서: EssentialsX → EssentialsXChat → EssentialsXSpawn
> 리로드 완료 (1.24s)
```

---

## 안전 등급 시스템

플러그인을 리로드하거나 비활성화하기 전에 안전 등급을 확인하면, 서버 불안정이나 메모리 누수를 사전에 예방할 수 있습니다.

### 등급 기준

| 등급 | 의미 | 대처 방법 |
|------|------|-----------|
| **SAFE** | 리로드에 안전한 플러그인 | 자유롭게 리로드 가능 |
| **WARNING** | 주의가 필요하나 대부분의 경우 안전 | 리로드 전 백업 권장 |
| **UNSAFE** | 리로드 시 문제가 발생할 가능성이 높음 | 서버 재시작을 통해서만 교체 권장 |
| **UNKNOWN** | 분석 불가 (난독화, 네이티브 코드 등) | 직접 검토 필요 |

### 평가 항목

Arc는 다음 항목을 정적 분석하여 등급을 결정합니다.

- **정적 핸들러**: `Bukkit.getPluginManager().registerEvents()`로 등록한 리스너가 비활성화 시 자동 해제되는지 여부
- **NMS 직접 접근**: `net.minecraft.server.*` 또는 CraftBukkit 내부 클래스를 직접 참조하는 코드. 버전 간 호환성 문제와 리로드 시 충돌 위험이 높습니다.
- **Kotlin 코루틴 스레드 풀**: Kotlin 코루틴이 공유 스레드 풀을 사용하는 경우, 플러그인을 언로드해도 스레드가 남아 메모리 누수가 발생할 수 있습니다.
- **Vault 의존 여부**: Vault를 통해 경제·권한 기능을 사용하는 경우, 리로드 순서에 따라 Vault 서비스가 일시적으로 해제될 수 있습니다.

### 예시 출력

```
/arc plugin check EssentialsX
> 플러그인: EssentialsX v2.21.0
> 안전 등급: SAFE ✓
> - 정적 핸들러: 없음 (Listener 자동 해제 확인)
> - NMS 접근: 없음
> - 코루틴 스레드 풀: 없음
> - Vault 의존: 있음 (낮은 위험)

/arc plugin check SomeHeavyPlugin
> 플러그인: SomeHeavyPlugin v3.0.1
> 안전 등급: UNSAFE ✗
> - 정적 핸들러: 감지됨 (PluginStaticListener.class)
> - NMS 접근: 감지됨 (net.minecraft.server.level.ServerPlayer 직접 참조)
> - 코루틴 스레드 풀: 감지됨 (kotlinx.coroutines.DefaultDispatcher)
> ⚠ 이 플러그인은 리로드 시 메모리 누수 또는 서버 불안정이 발생할 수 있습니다.
> ⚠ 교체/업데이트 시 서버 재시작을 권장합니다.
```

### 장단점

**장점:**
- 리로드 전 위험을 사전에 감지하여 예기치 않은 서버 다운을 방지합니다.
- 안전한 플러그인과 위험한 플러그인을 명확히 구분하여 운영 전략을 수립할 수 있습니다.

**단점:**
- 정적 분석이므로 런타임에서만 나타나는 동작(예: 특정 조건에서만 실행되는 코드 경로)은 감지하지 못할 수 있습니다.
- 난독화된 플러그인은 UNKNOWN으로 분류되며, 수동 검토가 필요합니다.

---

## 롤백 시스템

플러그인을 업데이트하기 전에 스냅샷을 저장해두면, 문제가 발생했을 때 서버를 재시작하지 않고도 이전 버전으로 복구할 수 있습니다.

### 명령어

| 명령어 | 설명 |
|--------|------|
| `/arc plugin snapshot create <plugin>` | 현재 플러그인 파일 스냅샷 저장 |
| `/arc plugin snapshot list <plugin>` | 저장된 스냅샷 목록 조회 |
| `/arc plugin snapshot restore <plugin> <id>` | 특정 스냅샷으로 복구 |
| `/arc plugin snapshot delete <plugin> <id>` | 스냅샷 삭제 |

### 사용 흐름

```
# 업데이트 전 스냅샷 저장
/arc plugin snapshot create LuckPerms
> 스냅샷 저장 완료: LuckPerms_snapshot_20260615_142300 (2.3 MB)

# 업데이트 진행 (문제 발생 시)
# 이전 버전으로 복구
/arc plugin snapshot restore LuckPerms LuckPerms_snapshot_20260615_142300
> 경고: 현재 실행 중인 LuckPerms를 교체합니다. 30초 내에 확인하세요.
> /arc confirm x7m3p2

/arc confirm x7m3p2
> 복구 완료: LuckPerms v5.4.100으로 롤백되었습니다.

# 스냅샷 목록 확인
/arc plugin snapshot list LuckPerms
> 1. LuckPerms_snapshot_20260615_142300  (v5.4.100, 2.3 MB)
> 2. LuckPerms_snapshot_20260610_090000  (v5.4.98,  2.2 MB)
```

### 언제 사용해야 하는가

- **플러그인 업데이트 전**: 새 버전에 버그가 있을 경우 즉시 롤백할 수 있습니다.
- **대규모 설정 변경 전**: 플러그인의 동작이 크게 바뀌는 설정 변경 전 안전망으로 활용합니다.
- **새 플러그인 도입 전**: 기존 플러그인의 스냅샷을 저장해 상호작용 문제 발생 시 대비합니다.

### 장단점

**장점:**
- 재시작 없이 이전 버전으로 복구할 수 있어 운영 중단 시간을 최소화합니다.
- 스냅샷 이력을 통해 언제 어떤 버전이 사용되었는지 추적할 수 있습니다.

**단점:**
- 스냅샷은 플러그인 jar 파일만 저장합니다. 데이터베이스 마이그레이션 등으로 변경된 데이터는 복구되지 않습니다.
- 스냅샷 파일이 서버 디스크 공간을 차지하므로, 오래된 스냅샷은 정기적으로 정리해야 합니다.
- 플러그인 API가 변경된 경우, 이전 버전으로 롤백해도 다른 플러그인과의 호환성 문제가 남을 수 있습니다.

---

## 마켓플레이스

Arc는 Modrinth API를 통해 플러그인을 검색하고 설치할 수 있는 내장 마켓플레이스 기능을 제공합니다. 의존성을 자동으로 해결하고 설치 전 샌드박스 분석을 수행합니다.

### 명령어

| 명령어 | 설명 |
|--------|------|
| `/arc plugin marketplace search <query>` | Modrinth에서 플러그인 검색 |
| `/arc plugin marketplace info <plugin-id>` | 플러그인 상세 정보 및 최신 버전 조회 |
| `/arc plugin marketplace install <plugin-id>` | 플러그인 설치 (의존성 자동 처리) |

### 설치 흐름 (4단계)

플러그인을 마켓플레이스에서 설치할 때 Arc는 다음 단계를 자동으로 진행합니다.

**1단계: 다운로드**
```
/arc plugin marketplace install luckperms
> Modrinth에서 LuckPerms v5.4.102 정보를 가져오는 중...
> 다운로드 중: LuckPerms-Bukkit-5.4.102.jar (2.4 MB)
> 다운로드 완료 ✓
```

**2단계: 의존성 자동 설치**
```
> 의존성 확인 중...
> 필요한 의존성: Vault (미설치)
> Vault v1.7.3 자동 다운로드 및 설치 중...
> 의존성 설치 완료 ✓
```

**3단계: 샌드박스 분석**
```
> 보안 분석 중: LuckPerms-Bukkit-5.4.102.jar
> NMS 직접 접근: 없음
> 네이티브 라이브러리: 없음
> 분석 결과: SAFE ✓
```

**4단계: 설치 또는 차단**
```
> 설치를 진행합니다. 서버 재시작 없이 활성화하시겠습니까? [Y/n]
> LuckPerms 설치 및 활성화 완료 ✓
```

분석 결과가 DANGEROUS인 경우, Arc는 설치를 차단하고 상세 사유를 표시합니다.

```
> 보안 분석 중: SuspiciousPlugin-1.0.jar
> ✗ 네이티브 라이브러리 감지: libmalicious.so
> ✗ 분석 결과: DANGEROUS
> 이 플러그인의 설치가 차단되었습니다.
```

### 장단점

**장점:**
- 의존성을 수동으로 찾아서 설치할 필요가 없습니다.
- 설치 전 보안 분석으로 명백히 위험한 플러그인을 차단합니다.
- Modrinth의 버전 정보와 연동되어 최신 버전을 쉽게 확인할 수 있습니다.

**단점:**
- Modrinth API에 접근할 수 없는 환경(방화벽, 오프라인 서버)에서는 사용할 수 없습니다.
- 샌드박스 분석은 정적 분석이므로, 런타임에서 악의적인 동작을 하는 플러그인은 감지하지 못할 수 있습니다.
- Spigot Plugin Repository(SpigotMC) 등 Modrinth 이외의 플랫폼에서는 직접 jar를 다운로드해야 합니다.

---

## 샌드박스 분석

마켓플레이스 설치 외에도, 이미 다운로드한 jar 파일을 독립적으로 분석할 수 있습니다.

### 명령어

```
/arc sandbox check <jar-path>
```

서버의 `plugins/` 디렉터리 또는 절대 경로를 지정할 수 있습니다.

### 분석 항목

| 항목 | 설명 |
|------|------|
| NMS 직접 접근 | `net.minecraft.server.*`, CraftBukkit 내부 클래스 참조 여부 |
| 네이티브 라이브러리 | `.so`, `.dll`, `.dylib` 등 네이티브 파일 포함 여부 |
| api-version 누락 | `plugin.yml`에 `api-version` 선언이 없어 구버전 동작을 가정하는 경우 |
| 스레드 풀 / 코루틴 | 비관리형 스레드 풀이나 Kotlin 코루틴 사용으로 인한 누수 가능성 |
| Shaded 라이브러리 수 | jar 내에 번들된 외부 라이브러리 수 (많을수록 충돌 가능성 증가) |
| Vault 의존 여부 | Vault 경제·권한 API 사용 여부 |

### 분석 결과 등급

| 등급 | 의미 |
|------|------|
| **SAFE** | 위험 요소가 발견되지 않음 |
| **CAUTION** | 주의가 필요한 요소가 있으나 치명적이지 않음 |
| **DANGEROUS** | 심각한 위험 요소가 발견됨 |

### 예시 출력

```
/arc sandbox check plugins/SomePlugin-2.0.jar
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
>   샌드박스 분석: SomePlugin-2.0.jar
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
> NMS 직접 접근:        감지됨 (3개 클래스) ⚠
> 네이티브 라이브러리:   없음 ✓
> api-version:          선언됨 (1.21) ✓
> 비관리형 스레드 풀:    감지됨 (Executors.newFixedThreadPool) ⚠
> Shaded 라이브러리:    4개 (GSON, Netty, slf4j, HikariCP)
> Vault 의존:           있음
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
>   최종 등급: CAUTION
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
> 리로드 전 /arc plugin check SomePlugin 명령어로 안전 등급을 재확인하세요.
```

---

## confirm 토큰 시스템

플러그인 비활성화, 강제 언로드, 롤백 복구 등 돌이키기 어려운 작업은 실수를 방지하기 위해 confirm 토큰을 요구합니다.

### 동작 방식

1. 위험한 명령어를 입력합니다.
2. Arc가 임시 토큰을 발급하고 30초 타이머를 시작합니다.
3. 운영자가 `/arc confirm <token>`을 입력합니다.
4. 시간 내에 입력하지 않으면 작업이 취소됩니다.

### 예시 흐름

```
# 위험한 작업 시도
/arc plugin disable EssentialsX

> [Arc] ⚠ 주의: EssentialsX를 비활성화하면 다음 플러그인에 영향을 줍니다:
>   - EssentialsXChat (의존)
>   - EssentialsXSpawn (의존)
> [Arc] 계속하려면 30초 내에 입력하세요:
>   /arc confirm b8q1m5

/arc confirm b8q1m5
> [Arc] EssentialsX 및 의존 플러그인을 비활성화합니다...
> [Arc] 완료: EssentialsX, EssentialsXChat, EssentialsXSpawn 비활성화됨

# 시간 초과 시
/arc confirm b8q1m5 (31초 후)
> [Arc] 토큰이 만료되었습니다. 작업이 취소되었습니다.
```

토큰 유효 시간은 `arc-ops.yml`의 `confirm-timeout` 값으로 조정할 수 있습니다 (기본값: 30초).
