---
title: 설정 관리
parent: Ops Suite
nav_order: 3
---

# 설정 관리

Arc의 설정 관리 명령어는 서버를 재시작하지 않고 Arc 관련 설정을 런타임에 변경하고 조회할 수 있는 기능을 제공합니다. 모든 변경 사항은 이력에 자동으로 기록되어 누가 언제 무엇을 바꿨는지 추적할 수 있습니다.

---

## 런타임 설정 변경

### 명령어 목록

| 명령어 | 설명 |
|--------|------|
| `/arc config get <key>` | 특정 설정 값 조회 |
| `/arc config set <key> <value>` | 설정 값을 런타임에 변경 |
| `/arc config reset <key>` | 설정 값을 기본값으로 초기화 |
| `/arc config diff` | 현재 런타임 값과 yml 파일 값이 다른 항목 표시 |
| `/arc config search <query>` | 설정 키 검색 |
| `/arc config history` | 최근 설정 변경 이력 조회 |
| `/arc config history <key>` | 특정 키의 변경 이력 조회 |
| `/arc config exclude <plugin>` | 플러그인을 Arc 기능 적용에서 제외 |
| `/arc config exclude list` | 제외된 플러그인 목록 조회 |
| `/arc config exclude remove <plugin>` | 플러그인 제외 해제 |

---

## 재시작 없이 변경 가능한 항목 vs 재시작이 필요한 항목

모든 설정이 런타임에 즉시 반영되는 것은 아닙니다. Arc는 설정 변경 시 해당 항목이 즉시 적용 가능한지 여부를 자동으로 알려줍니다.

### 즉시 적용 가능한 항목 (재시작 불필요)

| 설정 키 | 설명 |
|---------|------|
| `perf.mspt-threshold` | MSPT 경보 임계값 |
| `perf.tps-threshold` | TPS 경보 임계값 |
| `ops.lag-spike-capture.mspt-threshold` | 렉 스파이크 감지 임계값 |
| `ops.confirm-timeout` | confirm 토큰 유효 시간 |
| `ops.audit-log.enabled` | 감사 로그 활성화 여부 |
| `ops.maintenance.message` | 점검 모드 접속 거부 메시지 |
| `network.rate-limit.*` | 네트워크 속도 제한 설정 |
| `perf.view-distance` | 뷰 거리 (어댑티브 거버너와 별개로 수동 설정) |

### 재시작이 필요한 항목

| 설정 키 | 이유 |
|---------|------|
| `ops.safe-mode.plugins-whitelist` | 플러그인 로드 순서는 시작 시에만 결정 |
| `network.proxy.enabled` | 네트워크 스택 초기화 필요 |
| `content.runtime.enabled` | Arc Content Runtime 초기화 필요 |
| `perf.tick-budget.enabled` | 틱 예산 시스템은 서버 시작 시 초기화 |

### 사용 예시

```
# 즉시 적용 예시
/arc config set perf.mspt-threshold 60
> [Arc] 설정 변경: perf.mspt-threshold
>   이전 값: 50
>   새 값:   60
>   적용: 즉시 ✓

# 재시작 필요 예시
/arc config set network.proxy.enabled true
> [Arc] 설정 변경: network.proxy.enabled
>   이전 값: false
>   새 값:   true
>   ⚠ 이 설정은 서버 재시작 후 적용됩니다.
>   현재 세션에서는 이전 값(false)이 유지됩니다.
```

---

## 변경 이력 자동 기록

모든 `/arc config set` 명령어 실행은 자동으로 이력에 기록됩니다. 이력에는 변경한 운영자, 변경 시각, 이전 값, 새 값이 모두 포함됩니다.

### 사용 예시

```
# MSPT 임계값을 변경하고 이력 확인
/arc config set perf.mspt-threshold 60
> 설정 변경 완료.

/arc config history perf.mspt-threshold
> perf.mspt-threshold 변경 이력
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
> 2026-06-15 14:30:00  Admin1   50 → 60
> 2026-06-10 09:15:22  Admin2   45 → 50
> 2026-06-01 12:00:00  Admin1   50 → 45  (초기 설정)
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

# 최근 모든 설정 변경 이력
/arc config history
> 최근 설정 변경 이력 (최근 20건)
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
> 2026-06-15 14:30:00  Admin1  perf.mspt-threshold: 50 → 60
> 2026-06-15 11:20:33  Admin2  ops.confirm-timeout: 30 → 60
> 2026-06-14 18:45:10  Admin1  network.rate-limit.packets: 500 → 400
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## diff — 런타임 값과 파일 값 비교

런타임에서 변경한 값은 yml 파일에 자동으로 저장되지 않습니다. 서버를 재시작하면 yml 파일의 값이 다시 적용됩니다. `diff` 명령어로 현재 런타임과 파일 사이의 차이를 확인할 수 있습니다.

```
/arc config diff
> 런타임 값과 yml 파일 값이 다른 항목:
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
> perf.mspt-threshold
>   런타임: 60
>   파일:   50   (재시작 후 파일 값으로 복원됨)
>
> ops.confirm-timeout
>   런타임: 60
>   파일:   30   (재시작 후 파일 값으로 복원됨)
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
> 런타임 변경을 영구 적용하려면 yml 파일을 직접 수정하세요.
```

---

## 설정 검색

키 이름이 정확히 기억나지 않을 때 키워드로 검색할 수 있습니다.

```
/arc config search mspt
> 'mspt' 검색 결과:
> - perf.mspt-threshold          현재: 60       (기본값: 50)
> - ops.lag-spike-capture.mspt-threshold  현재: 100  (기본값: 100)
```

---

## 설정 초기화

특정 설정을 기본값으로 되돌리려면 `reset` 명령어를 사용합니다.

```
/arc config reset perf.mspt-threshold
> [Arc] perf.mspt-threshold를 기본값으로 초기화합니다.
>   현재 값: 60
>   기본값:  50
> [Arc] 계속하려면 /arc confirm k9p2m1

/arc confirm k9p2m1
> perf.mspt-threshold = 50 (기본값으로 복원됨)
```

---

## exclude — 특정 플러그인을 Arc 기능에서 제외

Arc의 일부 기능(어댑티브 뷰 거리 조정, 틱 예산 분배 등)이 특정 플러그인과 호환되지 않거나, 해당 플러그인에는 Arc가 개입하지 않기를 원하는 경우 `exclude` 기능으로 제외할 수 있습니다.

### 사용 예시

```
# 특정 플러그인을 Arc 어댑티브 기능에서 제외
/arc config exclude SomePlugin
> SomePlugin이 Arc 기능 적용에서 제외되었습니다.
> 적용된 제외 항목:
>   - 어댑티브 뷰 거리 조정
>   - 틱 예산 분배
>   - 플러그인 비용 추적

# 제외된 플러그인 목록 확인
/arc config exclude list
> Arc 기능에서 제외된 플러그인:
> 1. SomePlugin  (제외일: 2026-06-15 14:30:00, 제외한 관리자: Admin1)

# 제외 해제
/arc config exclude remove SomePlugin
> SomePlugin의 제외가 해제되었습니다. Arc 기능이 다시 적용됩니다.
```

### 제외 기능이 필요한 경우

- 플러그인이 자체적으로 뷰 거리를 관리하는 경우 (Arc의 어댑티브 조정과 충돌)
- 특정 플러그인이 틱 예산 추적에서 오탐(false positive)을 보이는 경우
- 레거시 플러그인이 Arc의 이벤트 처리 방식과 호환되지 않는 경우

---

## 장단점

### 장점

- **운영 중 즉각 반응**: 재시작 없이 임계값이나 설정을 변경할 수 있어, 피크 시간대에 빠르게 대응할 수 있습니다.
- **감사 추적**: 모든 변경 이력이 자동으로 기록되므로, 설정 변경으로 인한 문제 발생 시 원인을 빠르게 파악할 수 있습니다.
- **팀 운영 지원**: 여러 관리자가 함께 서버를 운영할 때, 누가 어떤 설정을 변경했는지 투명하게 파악할 수 있습니다.

### 단점

- **휘발성**: 런타임 변경은 서버 재시작 후 yml 파일 값으로 복원됩니다. 영구 적용이 필요하면 yml 파일을 직접 수정해야 합니다.
- **일부 설정은 재시작 필요**: 네트워크 스택, 콘텐츠 런타임 등 핵심 시스템과 연관된 설정은 런타임 변경이 불가합니다.
- **이력 보관 기간 제한**: 감사 로그 이력은 `audit-log.retain-days` 설정에 따라 일정 기간이 지나면 자동으로 삭제됩니다.
