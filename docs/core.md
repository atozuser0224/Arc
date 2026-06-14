---
title: Fork Core
nav_order: 2
has_children: true
---

# Fork Core

Fork Core는 Arc의 기반 레이어다. Leaf(Paper/Purpur 계열) 포크 위에서 동작하며, 서버가 시작되는 순간부터 별도의 설정 없이 자동으로 활성화된다. 플러그인 개발자와 서버 운영자 모두를 위한 핵심 인프라를 제공한다.

Fork Core는 다섯 가지 서브시스템으로 구성된다.

---

## 서브시스템 목록

### [성능 거버너](core/performance)

서버 부하를 실시간으로 감시하고 자동으로 대응하는 세 가지 메커니즘을 제공한다. 적응형 뷰 거버너(뷰/시뮬레이션 거리 자동 조절), 엔티티 밀도 가드(TPS 저하 시 스폰 억제), 틱 버짓 스프레더(메인 스레드 작업 분산)가 포함된다.

### [서버 모니터링](core/monitoring)

ServerLoad API, StallWatchdog, 메모리 가드, 충돌 분석기, Prometheus 메트릭 엔드포인트를 통해 서버 상태를 실시간으로 진단하고 외부 모니터링 시스템과 연동할 수 있다.

### [커스텀 이벤트](core/events)

Arc가 제공하는 추가 Bukkit 이벤트 목록이다. 서버 부하 단계 변경, 렉 스파이크 감지, 플레이어 청크 이동, 네트워크 전송 훅 등 바닐라 Paper가 제공하지 않는 이벤트를 플러그인에서 구독할 수 있다.

### [NMS 브리지](core/nms)

버전 간 호환성을 유지하면서 Minecraft 내부(NMS) API에 안전하게 접근하는 추상화 레이어다. 패킷 직접 전송, 플레이어별 날씨/시간 분리, NPC 제어, 레지스트리 접근 등 바닐라 Bukkit API로는 불가능한 기능을 제공한다.

### [코루틴 · 비동기](core/async)

Bukkit의 콜백 기반 비동기 API를 대체하는 현대적인 비동기 처리 인터페이스다. Kotlin 코루틴 스타일의 `launchEveryTicks`, CompletableFuture 체인, 메인 스레드 복귀 헬퍼를 제공해 비동기 플러그인 코드를 간결하고 안전하게 작성할 수 있다.
