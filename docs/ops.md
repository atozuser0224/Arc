---
title: Ops Suite
nav_order: 3
has_children: true
---

# Ops Suite

Ops Suite는 Arc 서버 운영자를 위한 종합 관리 도구 모음입니다. 약 100개의 `/arc` 서브커맨드를 통해 플러그인 관리, 서버 진단, 설정 관리, 보안 점검, 유지보수 작업을 모두 처리할 수 있습니다.

## 개요

전통적인 Minecraft 서버 운영에서는 서버를 재시작하지 않으면 설정 변경이나 플러그인 교체가 어렵고, 문제 발생 시 원인을 파악하기 위해 로그를 직접 분석해야 했습니다. Ops Suite는 이러한 불편함을 해소하고, 운영 중 서버를 안전하게 관리할 수 있도록 설계되었습니다.

모든 Ops Suite 기능은 `arc-ops.yml` 설정 파일을 통해 개별적으로 활성화 또는 비활성화할 수 있습니다. 위험한 작업(예: 플러그인 강제 언로드, 설정 초기화)은 실행 전 **confirm 토큰**을 요구하며, 실수로 인한 장애를 방지합니다.

## 권한 요구 사항

대부분의 Ops Suite 명령어는 `arc.ops` 권한 또는 OP 레벨 4가 필요합니다. 일부 읽기 전용 명령어(`/arc doctor`, `/arc plugin list` 등)는 더 낮은 권한으로도 사용 가능합니다. 권한 체계는 `arc-ops.yml`의 `permissions` 섹션에서 세부 조정할 수 있습니다.

## 섹션 구성

### [플러그인 관리](ops/plugins)

플러그인의 전체 수명 주기를 관리합니다. 설치된 플러그인 목록 조회, 활성화/비활성화, 안전 등급 평가, 롤백 스냅샷 생성, Modrinth 마켓플레이스 연동 설치까지 지원합니다. 리로드 전 안전 등급을 확인하면 정적 핸들러나 NMS 직접 접근으로 인한 서버 불안정을 사전에 예방할 수 있습니다.

### [서버 진단](ops/diagnostics)

서버의 전반적인 상태를 진단하고 성능 병목을 파악합니다. `/arc doctor`로 TPS·MSPT·메모리·플러그인 오류를 한 번에 확인하고, 렉 스파이크 이력 분석, 내장 프로파일러, 플러그인별 틱 비용 분석을 통해 성능 문제의 근본 원인을 신속하게 찾을 수 있습니다.

### [설정 관리](ops/config)

서버를 재시작하지 않고 Arc 설정을 런타임에 변경하고 조회합니다. 변경 이력이 자동으로 기록되어 누가 언제 어떤 값을 변경했는지 추적할 수 있으며, 특정 플러그인을 Arc 기능의 영향에서 제외하는 것도 가능합니다.

### [보안 · 유지보수](ops/security)

서버의 보안 취약점을 점검하고 안전한 운영 환경을 유지합니다. 오프라인 모드·RCON 활성화 등 일반적인 보안 오류를 자동으로 감지하고, 문제 발생 시 안전 모드로 전환하거나 점검 모드로 플레이어 접속을 차단할 수 있습니다.

### [서버 도구](ops/server-tools)

월드·청크 분석, 커맨드·권한 검색, 로그 관리, 진단 번들 생성 등 운영에 필요한 다양한 유틸리티 명령어를 제공합니다. 청크 사전 생성(pregen) 기능도 포함되어 있어 신규 서버 오픈 전 지역을 미리 로드할 수 있습니다.

## arc-ops.yml 기본 구조

```yaml
arc-ops:
  enabled: true
  confirm-timeout: 30          # confirm 토큰 유효 시간 (초)
  lag-spike-capture:
    enabled: true
    mspt-threshold: 100        # 이 값 이상이면 스파이크로 기록 (ms)
  sandbox:
    enabled: true
  marketplace:
    enabled: true
    source: modrinth
  safe-mode:
    plugins-whitelist: []      # 안전 모드에서 로드할 플러그인 목록
  audit-log:
    enabled: true
    retain-days: 30
```

## confirm 토큰 시스템

위험한 작업을 실행하면 Arc는 임시 토큰을 발급합니다.

```
[Arc] ⚠ 위험한 작업입니다: 플러그인 'SomePlugin'을 강제 언로드합니다.
[Arc] 계속하려면 30초 내에 다음 명령어를 입력하세요:
[Arc]   /arc confirm a3f9k2
```

30초 내에 `/arc confirm a3f9k2`를 입력해야만 작업이 실행됩니다. 토큰은 1회용이며, 시간이 초과되면 자동으로 만료됩니다.
