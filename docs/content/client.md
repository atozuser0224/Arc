---
title: 클라이언트 모드 (Fabric)
parent: ArcSync Content
nav_order: 3
---

# 클라이언트 모드 (Fabric)

Arc 클라이언트 모드는 Fabric 기반 Minecraft 클라이언트 모드로, ArcSync Content 플랫폼의 클라이언트 측 구현이다. 서버에서 서명된 content revision을 검증·다운로드하고, 서버별로 격리된 resource pack을 자동으로 활성화해 `Arc Content` creative tab을 채운다.

설치는 선택사항이다. Arc 클라이언트 없이도 Arc 서버에 접속할 수 있으며, 이 경우 아이템은 `fallback` vanilla 아이템으로 표시된다.

---

## 설치 요구사항

| 요소 | 버전 |
|------|------|
| Minecraft | 1.21.4 |
| Fabric Loader | 최신 권장 |
| Fabric API | 최신 권장 |
| Fabric Language Kotlin | 최신 권장 |
| arc-client mod | arc-client 모듈 빌드 결과물 |

### 설치 절차

1. [Fabric 설치 프로그램](https://fabricmc.net/use/)으로 Fabric Loader를 설치한다.
2. `.minecraft/mods/` 폴더에 다음 파일을 복사한다.
   - `fabric-api-*.jar`
   - `fabric-language-kotlin-*.jar`
   - `arc-client-*.jar`
3. Minecraft 런처에서 Fabric 프로파일로 게임을 시작한다.
4. Arc 서버에 접속하면 자동으로 content sync가 시작된다.

---

## 연결 시 동작 흐름

Arc 서버에 접속할 때 클라이언트는 다음 순서로 콘텐츠를 동기화한다.

```
1. 서버 핸드쉐이크 (arc:sync 채널)
        ↓
2. Ed25519 서명 manifest 수신 및 검증
        ↓
3. 서버 키 Pin 확인
   - 첫 접속: 키 자동 저장 (trust/)
   - 재접속: 저장된 키와 비교 → 불일치 시 ServerTrustException
        ↓
4. SHA-256 기반 blob 목록 비교
   - 캐시에 있는 blob: 재사용
   - 누락된 blob만 서버에서 선택적 다운로드
        ↓
5. 서버별 격리된 resource pack 생성 및 활성화
        ↓
6. 리소스 리로드 → Arc Content creative tab 자동 생성
```

### 단계별 설명

**1단계: arc:sync 협상**

클라이언트는 서버 접속 직후 `arc:sync` 플러그인 채널로 ArcSync 지원 여부를 협상한다. 서버가 ArcSync를 지원하지 않으면 이후 단계가 실행되지 않고 일반 접속으로 처리된다.

**2단계: Ed25519 서명 검증**

서버는 content revision manifest를 Ed25519 개인 키로 서명해 전송한다. 클라이언트는 서버의 공개 키로 서명을 검증한다. 서명이 유효하지 않으면 manifest를 거부하고 콘텐츠 동기화를 중단한다.

**3단계: 서버 키 Pin**

첫 접속 시 서버 공개 키를 `arc config dir / trust / <서버 주소>.key` 파일에 저장한다. 이후 접속에서 서버가 다른 키를 제시하면 `ServerTrustException`을 발생시키고 동기화를 거부한다. 이는 중간자 공격(MITM)을 방어하는 핵심 메커니즘이다.

**4단계: SHA-256 기반 선택적 다운로드**

manifest에는 각 에셋 blob의 SHA-256 해시 목록이 포함된다. 클라이언트는 로컬 캐시와 비교해 누락된 blob만 서버에서 요청한다. 이미 캐시된 텍스처나 모델은 다시 다운로드하지 않으므로, 재접속 시 대역폭이 크게 절약된다.

**5단계: 서버별 resource pack 격리**

다운로드한 blob으로 구성된 resource pack은 서버 주소 기반으로 격리된 디렉토리에 저장된다. 여러 Arc 서버에 접속해도 각 서버의 콘텐츠가 혼합되지 않는다.

**6단계: Arc Content creative tab**

resource pack 활성화 후 클라이언트가 리소스를 리로드하면 `Arc Content` creative tab이 나타난다. 서버 접속 중에만 표시되며, 연결이 끊어지면 자동으로 숨겨진다.

---

## Arc Content Creative Tab

`Arc Content` tab은 서버가 등록한 모든 아이템·가구를 `order` 필드 순서로 나열한다.

### 아이템 스택 구조

```
ItemStack {
    vanilla type = <fallback>          ← 기본 아이템 (예: AMETHYST_SHARD)
    item_model = "magic:ruby_wand"     ← Arc 클라이언트가 렌더링에 사용
    CUSTOM_DATA["arc:id"] = "magic:ruby_wand"  ← 서버 로직 식별자
}
```

- **Arc 클라이언트**: `item_model` 컴포넌트를 읽어 서버에서 받은 모델·텍스처로 렌더링.
- **vanilla 클라이언트**: `item_model`을 무시하고 fallback 아이템으로 표시.
- **서버 로직**: `CUSTOM_DATA["arc:id"]`로 아이템 종류를 식별. fallback 아이템 타입에 의존하지 않음.

### 여러 서버 간 tab 동작

Arc 클라이언트는 접속 중인 서버의 revision만 활성화한다. 서버 A에서 서버 B로 이동하면 서버 A의 resource pack이 비활성화되고 서버 B의 resource pack이 활성화된다. 각 서버의 creative tab 내용이 완전히 교체된다.

---

## 서버 키 Pin 관리

### Pin 파일 위치

```
<Arc config dir>/
  trust/
    play.example.com.key       ← 서버 주소별 공개 키
    192.168.1.100_25565.key
```

### 합법적인 서버 키 교체

서버 운영자가 개인 키를 교체하면 기존 클라이언트에서 `ServerTrustException`이 발생한다. 이는 보안상 의도된 동작이다.

합법적 키 교체를 클라이언트에 적용하는 방법은 두 가지다.

**방법 1: replaceTrust() 호출 (권장)**

서버 운영자가 클라이언트에게 특별한 인증 코드나 절차로 키 교체를 통보하고, 클라이언트가 명시적으로 신뢰를 갱신한다. 이 방법은 키 교체가 합법적임을 플레이어가 인지한다.

**방법 2: Pin 파일 수동 삭제**

해당 서버의 `.key` 파일을 삭제하면 다음 접속 시 새 키를 첫 접속처럼 저장한다. 파일을 삭제하기 전 서버 운영자에게 키 교체를 확인하는 것을 강력히 권장한다.

### ServerTrustException 발생 시

```
[ArcSync] 서버 키가 변경되었습니다.
기대 키: ed25519:abc123...
수신 키: ed25519:xyz789...

이 변경이 합법적이라면 /arc trust accept 명령어를 실행하거나
trust 디렉토리에서 해당 서버의 키 파일을 삭제하세요.
의심스러운 경우 서버 운영자에게 확인하세요.
```

---

## 바닐라 클라이언트 호환성

Arc 클라이언트 없이 접속한 플레이어의 경험:

| 기능 | Arc 클라이언트 | 바닐라 클라이언트 |
|------|---------------|-----------------|
| 아이템 시각화 | 커스텀 모델·텍스처 | fallback 아이템 |
| Arc Content creative tab | 표시됨 | 없음 |
| 서버 게임플레이 로직 | 정상 | 정상 (동일) |
| 인벤토리·상자·드롭 | 정상 | 정상 (동일) |
| 블록 시각화 | 커스텀 모델 | fallback 블록 |

서버 게임플레이 로직은 `CUSTOM_DATA["arc:id"]`로 아이템을 식별하므로, 클라이언트 종류에 관계없이 동일하게 동작한다. 바닐라 클라이언트 플레이어도 제한 없이 게임에 참여할 수 있다.

---

## 성능 특성

- **대역폭 절약**: SHA-256 기반 선택적 다운로드로 재접속 시 이미 캐시된 에셋은 전송하지 않음.
- **서버별 격리**: 여러 Arc 서버를 오가며 접속해도 캐시가 독립적으로 관리됨.
- **리소스 리로드 시간**: content revision 규모에 따라 처음 접속 시 수 초의 리소스 리로드가 발생할 수 있음. 이후 접속에서는 캐시 덕분에 크게 단축됨.
- **메모리**: 활성화된 resource pack은 바닐라 resource pack과 함께 메모리에 로드됨. 에셋이 많을수록 클라이언트 메모리 사용량이 늘어남. 팩 총 크기 128MiB 제한이 클라이언트 메모리 영향도 간접적으로 제한함.

---

## 장점과 단점

### 장점

- **서버별 격리**: 각 Arc 서버의 콘텐츠가 독립적으로 캐시되어 혼합되지 않음.
- **SHA-256 기반 선택적 다운로드**: 변경된 에셋만 다운로드해 대역폭을 최소화.
- **Ed25519 서명으로 위변조 차단**: 악의적 서버가 클라이언트에 임의 에셋을 주입할 수 없음.
- **서버 키 Pin**: MITM 공격 방어.
- **자동 creative tab**: 별도 설정 없이 접속만으로 Arc Content tab 활성화.

### 단점

- **클라이언트 모드 설치 필요**: 바닐라 런처로는 사용 불가. Fabric 설치가 필수.
- **에셋은 별도 포함 필요**: 서버가 텍스처·모델을 에셋으로 제공해야 함. 클라이언트가 자동 생성하지 않음.
- **ServerTrustException 혼란 가능**: 서버 키 교체 시 적절한 안내 없이 플레이어가 당황할 수 있음. 키 교체 전 공지가 중요.
- **리소스 리로드 딜레이**: 처음 접속 시 에셋 다운로드와 리소스 리로드로 수 초가 소요될 수 있음.
