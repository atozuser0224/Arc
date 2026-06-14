---
title: Arc Documentation
layout: home
nav_order: 1
---

# Arc — Minecraft Server Fork

Arc는 [Leaf](https://github.com/Winds-Studio/Leaf)(Paper/Purpur) 기반 Minecraft 1.21.4 서버 포크다.  
세 가지 가치를 동시에 제공한다: **운영자용 관리 도구(Ops Suite)**, **플러그인 개발자용 Kotlin-first API(Developer API)**, **멀티서버 네트워크 레이어(Network)**.

## 레이어 구조

| 레이어 | 설명 | 기본 상태 |
|--------|------|-----------|
| [Fork Core](./core.md) | 성능 거버너·NMS 브리지·커스텀 이벤트 | 항상 활성 |
| [Ops Suite](./ops.md) | `/arc` 관리 명령어 전체 (~80개) | `arc-ops.yml` 제어 |
| [Developer API](./api.md) | Kotlin-first plugin API (160+ 파일) | 라이브러리로 사용 |
| [Network](./network.md) | 멀티서버 레이어 (Redis 필요) | `arc-network.yml` 제어 |

## 빠른 시작

```bash
# 1. plugins/ 폴더에 Arc JAR 배치
# 2. 서버 시작 — arc-ops.yml / arc-network.yml 자동 생성
# 3. /arc doctor  — 서버 상태 전체 진단
```

## 링크

- [GitHub](https://github.com/atozuser0224/Arc)
- [Ops Suite 레퍼런스](./ops.md)
- [Network 레퍼런스](./network.md)
- [Developer API 레퍼런스](./api.md)
- [설정 레퍼런스](./configuration.md)
