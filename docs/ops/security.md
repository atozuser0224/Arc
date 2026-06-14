---
title: 보안 · 유지보수
parent: Ops Suite
nav_order: 4
---

# 보안 · 유지보수

Arc의 보안 및 유지보수 도구는 서버의 취약점을 점검하고, 문제 발생 시 서버를 안전하게 보호하는 기능을 제공합니다. 보안 감사, 안전 모드, 점검 모드, 감사 로그를 통해 서버를 체계적으로 관리할 수 있습니다.

---

## 보안 감사

Arc는 일반적인 Minecraft 서버 보안 취약점을 자동으로 점검하는 보안 감사 기능을 제공합니다.

### /arc security-audit

서버 전반의 보안 설정을 종합적으로 점검합니다.

**점검 항목:**

| 항목 | 설명 | 위험도 |
|------|------|--------|
| `online-mode` | 오프라인 모드 활성화 여부. 오프라인 모드는 사용자 인증을 건너뛰어 계정 도용 위험이 있습니다. | 높음 |
| RCON 활성화 | RCON 포트가 열려 있는 경우 외부 공격 표면이 넓어집니다. | 높음 |
| RCON 비밀번호 강도 | 약한 RCON 비밀번호 사용 여부 | 높음 |
| OP 계정 수 | OP 계정이 많을수록 계정 탈취 시 피해가 큽니다. | 중간 |
| 기본 `ops.json` 항목 | 테스트 또는 개발 시 추가된 OP 계정이 남아있는 경우 | 중간 |
| 방화벽 노출 | 25565 이외의 포트가 불필요하게 열려 있는 경우 | 중간 |
| 플러그인 보안 경고 | 알려진 보안 취약점이 있는 플러그인 버전 사용 | 낮음~높음 |

**예시 출력:**

```
/arc security-audit
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
>   Arc 보안 감사 리포트
>   2026-06-15 14:30:00
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
>
> [심각]
>   ✗ online-mode: false
>     오프라인 모드가 활성화되어 있습니다.
>     Velocity/BungeeCord 없이 오프라인 모드를 사용하면 계정 도용 위험이 있습니다.
>
> [경고]
>   ⚠ OP 계정: 5개 (권장: 2개 이하)
>     등록된 OP: Steve, Alex, testadmin, dev_account, backup_op
>
>   ⚠ RCON 활성화됨
>     포트: 25575 (방화벽으로 차단 여부를 확인하세요)
>
> [정상]
>   ✓ RCON 비밀번호: 강도 충분 (16자 이상)
>   ✓ 알려진 취약점이 있는 플러그인 없음
>
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
>   점수: 62 / 100   (개선 필요)
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### /arc proxy-check

Velocity 또는 BungeeCord를 사용하는 네트워크 환경에서 프록시 포워딩 설정 오류를 감지합니다. 잘못된 프록시 설정은 플레이어 IP 스푸핑 취약점으로 이어질 수 있습니다.

**점검 항목:**

- Velocity Modern Forwarding 비밀 키 설정 여부
- BungeeCord `bungeecord: true` 설정과 `online-mode: false` 조합 검사
- 프록시 없이 25565 포트가 직접 노출되는 경우 (프록시 우회 가능)
- IP 포워딩 헤더 신뢰 범위 설정

```
/arc proxy-check
> 프록시 설정 검사
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
> 감지된 프록시 유형: Velocity
>
> ✓ Velocity Modern Forwarding: 활성화됨
> ✓ 포워딩 시크릿: 설정됨 (32자)
> ⚠ 25565 포트 직접 노출: 확인 필요
>     백엔드 서버는 Velocity 프록시에서만 접근 가능해야 합니다.
>     방화벽에서 25565 포트를 Velocity 서버 IP로만 제한하세요.
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 안전 모드

서버가 특정 플러그인 충돌이나 오류로 인해 정상적으로 시작되지 않을 때, 안전 모드를 사용하면 최소한의 플러그인만 로드하여 문제 원인을 격리할 수 있습니다.

### 명령어

| 명령어 | 설명 |
|--------|------|
| `/arc safe-mode enable` | 다음 재시작 시 안전 모드로 부팅하도록 설정 |
| `/arc safe-mode disable` | 안전 모드 설정 해제 (다음 재시작 시 정상 모드로 복귀) |
| `/arc safe-mode status` | 안전 모드 설정 상태 확인 |

### 안전 모드에서 로드되는 플러그인

안전 모드에서는 `arc-ops.yml`의 `safe-mode.plugins-whitelist`에 명시된 플러그인만 로드됩니다.

```yaml
arc-ops:
  safe-mode:
    plugins-whitelist:
      - LuckPerms      # 권한 관리 (필수)
      - Vault          # 의존성
      - EssentialsX    # 기본 명령어
```

화이트리스트가 비어 있는 경우, Arc 자체와 API 필수 플러그인만 로드됩니다.

### 사용 시나리오

**시나리오 1: 플러그인 충돌로 서버가 시작되지 않는 경우**

```
# 서버 콘솔 또는 RCON을 통해 실행
/arc safe-mode enable
> [Arc] 안전 모드가 활성화되었습니다.
>   다음 재시작 시 화이트리스트에 있는 플러그인만 로드됩니다.
> [Arc] 서버를 재시작하면 안전 모드로 부팅됩니다.

# 서버 재시작 후 안전 모드에서 문제 플러그인 비활성화
/arc plugin disable SomeProblematicPlugin

# 안전 모드 해제 후 정상 재시작
/arc safe-mode disable
```

**시나리오 2: 업데이트 후 플러그인 충돌**

새 플러그인 버전을 설치한 후 서버가 충돌하는 경우, 안전 모드로 부팅하여 해당 플러그인을 롤백(`/arc plugin snapshot restore`)한 후 정상 모드로 복귀합니다.

### 장단점

**장점:**
- 문제 플러그인이 비활성화된 상태에서 서버에 접근하여 설정을 수정하거나 플러그인을 교체할 수 있습니다.
- 여러 플러그인 중 어떤 것이 문제인지 격리하는 데 유용합니다.

**단점:**
- 안전 모드로 전환하려면 서버 재시작이 필요합니다. 재시작 없이 실시간으로 전환할 수는 없습니다.
- 화이트리스트에 없는 플러그인이 비활성화되므로, 플레이어가 접속해도 대부분의 기능을 사용할 수 없습니다.

---

## 점검 모드

서버 업데이트나 유지보수 작업 중 일반 플레이어의 접속을 차단하면서, 관리자는 정상적으로 접속할 수 있는 모드입니다.

### 명령어

| 명령어 | 설명 |
|--------|------|
| `/arc maintenance on` | 점검 모드 활성화 |
| `/arc maintenance off` | 점검 모드 비활성화 |
| `/arc maintenance allow <player>` | 특정 플레이어를 점검 모드 중 임시 허용 |
| `/arc maintenance allow remove <player>` | 임시 허용 해제 |
| `/arc maintenance status` | 현재 점검 모드 상태 및 허용 플레이어 목록 확인 |

### 접속 거부 메시지 설정

점검 모드에서 접속을 시도하는 플레이어에게 표시될 메시지를 설정할 수 있습니다.

```
/arc config set ops.maintenance.message "서버 점검 중입니다. 예상 완료 시간: 오후 3시"
```

또는 `arc-ops.yml`에서 직접 설정:

```yaml
arc-ops:
  maintenance:
    message: "서버 점검 중입니다.\n예상 완료 시간: 오후 3시"
    allowed-players: []     # 영구 허용 플레이어 (관리자 계정 등)
```

### 사용 예시

```
# 점검 모드 시작
/arc maintenance on
> [Arc] 점검 모드가 활성화되었습니다.
>   일반 플레이어는 접속할 수 없습니다.
>   현재 접속 중인 플레이어: 12명 (즉시 퇴장되지는 않습니다)

# 특정 플레이어를 임시로 허용 (예: 테스터)
/arc maintenance allow TestPlayer
> TestPlayer가 점검 모드 중 접속 허용되었습니다.

# 상태 확인
/arc maintenance status
> 점검 모드: 활성화 중
> 허용된 플레이어: Admin1, Admin2, TestPlayer
> 시작 시각: 2026-06-15 14:00:00 (경과: 32분)

# 점검 완료 후 해제
/arc maintenance off
> [Arc] 점검 모드가 해제되었습니다. 플레이어 접속이 허용됩니다.
```

---

## 감사 로그

서버에서 발생한 모든 관리자 작업은 감사 로그에 자동으로 기록됩니다. 이를 통해 누가 어떤 명령을 실행했는지 언제든지 확인할 수 있습니다.

### 명령어

| 명령어 | 설명 |
|--------|------|
| `/arc audit last` | 가장 최근 관리자 작업 20건 조회 |
| `/arc audit last <n>` | 최근 n건 조회 |
| `/arc audit since <time>` | 특정 시간 이후의 작업 조회 (예: `1h`, `30m`, `2026-06-15`) |
| `/arc audit search <keyword>` | 키워드로 감사 로그 검색 |
| `/arc audit player <player>` | 특정 플레이어의 관리자 작업 이력 조회 |

### 기록되는 작업 유형

- Arc 명령어 실행 (`/arc *`)
- 플러그인 활성화/비활성화/리로드
- 설정 변경 (`/arc config set`)
- 점검 모드 활성화/비활성화
- 보안 감사 실행
- 스냅샷 생성/복구

### 예시 출력

```
/arc audit last 10
> 감사 로그 (최근 10건)
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
> 2026-06-15 14:30:00  Admin1   /arc config set perf.mspt-threshold 60
> 2026-06-15 14:25:10  Admin1   /arc maintenance on
> 2026-06-15 14:24:55  Admin2   /arc plugin check SomeHeavyPlugin
> 2026-06-15 14:20:00  Admin2   /arc plugin reload EssentialsX
> 2026-06-15 13:55:33  Admin1   /arc security-audit
> 2026-06-15 13:30:00  Admin1   /arc plugin snapshot create LuckPerms
> 2026-06-15 12:15:42  Admin2   /arc plugin disable SomeOldPlugin  [확인됨]
> 2026-06-15 11:20:33  Admin2   /arc config set ops.confirm-timeout 60
> 2026-06-15 10:05:11  Admin1   /arc maintenance off
> 2026-06-15 09:00:02  Admin1   /arc doctor
> ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/arc audit search "plugin disable"
> 'plugin disable' 검색 결과 (최근 30일)
> 2026-06-15 12:15:42  Admin2  /arc plugin disable SomeOldPlugin
> 2026-06-10 09:30:00  Admin1  /arc plugin disable LegacyPlugin
```

### 감사 로그 보관 설정

```yaml
arc-ops:
  audit-log:
    enabled: true
    retain-days: 30      # 30일 경과 후 자동 삭제
    log-file: logs/arc-audit.log  # 파일 경로 (선택)
```

### 장단점

**장점:**
- 여러 관리자가 함께 서버를 운영할 때 변경 사항의 책임 소재를 명확히 할 수 있습니다.
- 보안 사고나 설정 오류 발생 시 원인을 추적하는 데 유용합니다.
- 지원 요청 시 최근 관리자 활동 이력을 제공하여 문제 재현을 도울 수 있습니다.

**단점:**
- 감사 로그는 `arc.ops` 권한이 있는 관리자도 직접 삭제하거나 수정할 수 없으므로(의도적 설계), 중요한 이력을 보존합니다. 단, 서버 파일 시스템에 직접 접근하면 우회가 가능합니다.
- 장기간 많은 관리자가 활동하는 서버에서는 로그 파일이 커질 수 있으므로 `retain-days`를 적절히 설정해야 합니다.
