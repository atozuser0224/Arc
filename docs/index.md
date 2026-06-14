---
title: Arc 소개
nav_order: 1
---

# Arc — Minecraft 서버 포크

Arc는 [Leaf](https://github.com/Winds-Studio/Leaf)(Paper/Purpur 기반) 위에 구축된 Minecraft 1.21.4 서버 포크다. 단순 성능 패치 모음이 아니라, **운영 자동화 · 플러그인 개발 생산성 · 멀티서버 네트워크 관리**를 하나의 포크로 통합하는 것을 목표로 한다.

---

## 기존 서버와의 차이

일반적인 Paper 서버 운영에는 여러 독립 플러그인이 필요하다 — 상태 모니터링, 플러그인 핫리로드, 멀티서버 전송, 네트워크 경제, 글로벌 밴. 이 플러그인들은 서로 충돌하거나 버전 호환성 문제를 일으킨다.

Arc는 이 모든 기능을 **서버 코어에 내장**한다.

| 항목 | Paper / Purpur | Leaf | **Arc** |
|------|----------------|------|---------|
| 성능 최적화 | ✅ | ✅✅ | ✅✅ Leaf 계승 |
| 서버 관리 도구 | ❌ | ❌ | ✅ Ops Suite 내장 |
| Kotlin-first API | ❌ | ❌ | ✅ 160+ 클래스 |
| 멀티서버 네트워크 | ❌ | ❌ | ✅ Redis 기반 |
| 플러그인 핫리로드 | ❌ | ❌ | ✅ 안전 등급 평가 후 |
| 글로벌 밴 / 경제 | ❌ | ❌ | ✅ |
| 플러그인 없는 단위 테스트 | ❌ | ❌ | ✅ arc-test |

---

## 퍼포먼스 수치

Arc의 적응형 거버너(Adaptive Governor)는 TPS 하락을 감지하면 뷰/시뮬레이션 거리를 자동으로 낮춘다. 부하가 회복되면 즉시 원래 값으로 복원한다.

- **개입 기준**: MSPT ≥ 45ms (LoadLevel.HIGH)
- **긴급 기준**: MSPT ≥ 50ms (LoadLevel.CRITICAL)
- **복구 기준**: MSPT ≤ 25ms (LoadLevel.LOW)
- **점검 주기**: 매 100틱 (5초)
- **뷰 거리 하한**: 설정 가능, 기본 4청크

StallWatchdog은 메인 스레드가 **5초** 이상 응답이 없으면 스택 트레이스를 자동 덤프한다. 메모리 가드는 힙 **85%** 도달 시 경고, **95%** 도달 시 조치를 취한다.

---

## 4레이어 구조

| 레이어 | 역할 | 기본 상태 |
|--------|------|-----------|
| [Fork Core](./core.md) | 성능 거버너, NMS 브리지, 커스텀 이벤트, 서버 모니터링 | 항상 활성 |
| [Ops Suite](./ops.md) | `/arc` 관리 명령어 ~100개, 진단·롤백·마켓플레이스 | `arc-ops.yml`로 제어 |
| [Developer API](./api.md) | Kotlin-first 플러그인 API, 160+ 유틸리티 클래스 | 라이브러리로 사용 |
| [Network](./network.md) | 멀티서버 관리, 글로벌 경제·밴·채팅·볼트, Redis 필요 | `arc-network.yml`로 제어 |

---

## 누가 사용하면 좋은가

**서버 운영자** — 플러그인 핫리로드, 실시간 성능 진단, 서버 간 플레이어 이동·관리, 글로벌 밴을 하나의 `/arc` 명령어 체계로 처리한다.

**플러그인 개발자** — Bukkit API의 반복 코드를 제거하는 Kotlin DSL, 타입 안전 PDC, 서버 없는 단위 테스트(`arc-test`)를 제공한다.

**네트워크 관리자** — Redis 하나로 전 서버 플레이어 전송, 대기열, 경제, 밴, 채팅, 리더보드를 통합 관리한다.

---

## 빠른 시작

1. `plugins/` 폴더에 Arc JAR 파일을 넣는다.
2. 서버를 시작한다. `arc-ops.yml`과 `arc-network.yml`이 자동 생성된다.
3. `/arc doctor`로 서버 전체 상태를 진단한다.
4. 멀티서버가 필요하면 `arc-network.yml`에서 `enabled: true`로 설정하고 Redis 정보를 입력한다.

---

## GitHub

- [소스 코드](https://github.com/atozuser0224/Arc)
- [이슈 / 버그 리포트](https://github.com/atozuser0224/Arc/issues)
- [설정 레퍼런스](./configuration.md)
