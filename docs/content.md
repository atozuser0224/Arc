---
title: ArcSync Content
nav_order: 6
has_children: true
---

# ArcSync Content

ArcSync Content는 Arc 서버에서 커스텀 아이템, 블록, 가구, 레시피를 **새로운 vanilla 숫자 ID 없이** 정의하는 콘텐츠 플랫폼이다. Fabric 클라이언트 모드가 서버에서 서명된 content revision을 다운로드하고, resource pack을 자동 활성화해 `Arc Content` creative tab을 자동으로 채운다.

기존 모드 프레임워크(Forge, Fabric 서버 사이드)와 달리, ArcSync Content는 **바닐라 클라이언트 호환성을 유지**하면서 Arc 클라이언트를 설치한 플레이어에게만 풍부한 비주얼 경험을 제공한다. 클라이언트 모드 없이 접속하는 플레이어도 `fallback` 바닐라 아이템으로 게임플레이에 제약 없이 참여할 수 있다.

---

## 핵심 개념

### 콘텐츠 정의 방식

ArcSync Content는 두 가지 방법으로 콘텐츠를 정의할 수 있다.

- **File Pack**: 서버 파일시스템의 `arc-content/` 디렉토리 아래 JSON과 에셋을 배치하는 방식. 플러그인 없이도 콘텐츠를 추가할 수 있어 리소스팩 제작자나 서버 운영자에게 적합하다.
- **Plugin API**: 플러그인 코드에서 Kotlin DSL로 아이템·블록을 선언하는 방식. 플러그인 생명주기와 자동으로 연동되어 플러그인 언로드 시 콘텐츠가 자동 제거된다.

두 방식은 동시에 사용할 수 있으며 namespace가 다르면 충돌하지 않는다.

### 새로운 숫자 ID 없음

기존 Minecraft 모딩에서 새 아이템·블록을 추가하면 서버와 클라이언트가 일치하는 숫자 레지스트리 ID가 필요하다. 이 ID가 불일치하면 청크 손상이나 아이템 오작동이 발생한다. ArcSync Content는 이 문제를 **namespaced content ID 문자열**로 해결한다.

- 아이템은 `CUSTOM_DATA["arc:id"]`와 `item_model` 컴포넌트로 식별한다.
- 블록은 청크의 `arc:palette` PDC 항목에 namespaced ID로 기록한다.
- 숫자 ID가 없으므로 팩이 임시로 제거되어도 청크 데이터가 손상되지 않는다.

### 서명된 Content Revision

서버는 등록된 모든 콘텐츠를 컴파일해 하나의 **content revision**으로 묶고, Ed25519 개인 키로 서명한다. 클라이언트는 접속 시 이 서명을 검증해 위변조된 콘텐츠를 자동으로 거부한다.

---

## 하위 섹션

| 문서 | 설명 |
|------|------|
| [서버 설정 (File Packs)](./content/server.md) | `arc-content/` 디렉토리 구조, `pack.json` 형식, 에셋 제한, 블록 팔레트 |
| [Plugin API](./content/plugin-api.md) | Kotlin DSL로 콘텐츠 등록, `ContentId` 형식, 트랜잭션 보장, 여러 플러그인 동시 등록 |
| [클라이언트 모드 (Fabric)](./content/client.md) | 설치 요구사항, 연결 흐름, creative tab, 서버 키 Pin, 바닐라 클라이언트 호환성 |
| [보안 · 제한](./content/security.md) | 보안 설계 원칙, 전송 제한, 서버 키 관리, ContentBudget, ContentMetrics |

---

## 빠른 흐름 요약

```
[서버] pack.json 또는 arcContent DSL
          ↓ compile
      ContentRevision (Ed25519 서명)
          ↓ arc:sync 채널
[클라이언트] 서명 검증 → SHA-256 blob 선택 다운로드
          ↓
      서버별 resource pack 활성화
          ↓
      Arc Content creative tab 자동 생성
```

---

## 바닐라 클라이언트와의 공존

ArcSync Content는 아이템 정의에 `fallback` 필드를 요구한다. Arc 클라이언트 없이 접속한 플레이어는 이 fallback 아이템으로 콘텐츠를 받는다. 게임플레이 로직(인벤토리, 상자, 드롭)은 동일하게 작동하며, 비주얼만 차이가 난다.

이 설계를 통해 서버 운영자는 클라이언트 모드 설치를 **권장은 하되 강제하지 않는** 운영 방침을 취할 수 있다.
