---
title: 서버 설정 (File Packs)
parent: ArcSync Content
nav_order: 1
---

# 서버 설정 — File Packs

File Pack은 플러그인 코드 없이 서버 파일시스템에 콘텐츠를 정의하는 방법이다. `arc-content/` 디렉토리 아래 팩 디렉토리를 만들고 `pack.json`과 에셋 파일을 배치하면, 서버 시작 시 자동으로 로드되어 content revision에 포함된다.

---

## 디렉토리 구조

```
arc-content/
  magic/                          ← 팩 디렉토리 (namespace와 일치 권장)
    pack.json                     ← 팩 메타 + 콘텐츠 정의
    assets/
      magic/
        models/
          item/
            ruby_wand.json        ← 아이템 모델 정의
        textures/
          item/
            ruby_wand.png         ← 아이템 텍스처
        items/
          ruby_wand.json          ← vanilla items/ 정의 (1.21.4+)
        lang/
          ko_kr.json              ← 한국어 번역
  showcase/
    pack.json
    assets/
      showcase/
        ...
```

팩 디렉토리 이름과 `pack.json`의 `namespace` 필드가 반드시 일치할 필요는 없지만, 일치시키는 것이 관리 측면에서 강력히 권장된다.

---

## pack.json 형식

### 전체 예시

```json
{
  "namespace": "magic",
  "version": "1.2.0",
  "items": [
    {
      "id": "ruby_wand",
      "fallback": "minecraft:blaze_rod",
      "maxStackSize": 1,
      "durability": 512,
      "order": 20
    },
    {
      "id": "mana_crystal",
      "fallback": "minecraft:amethyst_shard",
      "maxStackSize": 64,
      "order": 10
    },
    {
      "id": "arcane_tome",
      "fallback": "minecraft:book",
      "maxStackSize": 1,
      "order": 30
    }
  ],
  "blocks": [
    {
      "id": "ruby_altar",
      "fallback": "minecraft:redstone_block",
      "hardness": 4.0,
      "blastResistance": 8.0,
      "order": 10
    },
    {
      "id": "mana_ore",
      "fallback": "minecraft:amethyst_block",
      "hardness": 3.0,
      "blastResistance": 3.0,
      "order": 20
    }
  ],
  "furniture": [
    {
      "id": "crystal_lantern",
      "fallback": "minecraft:lantern",
      "order": 10
    }
  ],
  "recipes": [
    {
      "id": "mana_crystal_recipe",
      "result": "magic:mana_crystal",
      "ingredients": [
        "minecraft:amethyst_shard",
        "minecraft:amethyst_shard",
        "minecraft:glowstone_dust"
      ]
    },
    {
      "id": "ruby_wand_recipe",
      "result": "magic:ruby_wand",
      "ingredients": [
        "magic:mana_crystal",
        "minecraft:blaze_rod"
      ]
    }
  ]
}
```

### 필드 설명

**최상위 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `namespace` | string | ✅ | 이 팩의 콘텐츠 ID 앞부분. `[a-z0-9_.-]+` 패턴. 예: `"magic"` → `"magic:ruby_wand"` |
| `version` | string | ✅ | 팩 버전. SemVer 형식 권장. content revision 변경 감지에 사용 |
| `items` | array | ❌ | 아이템 정의 목록 |
| `blocks` | array | ❌ | 블록 정의 목록 |
| `furniture` | array | ❌ | 가구 정의 목록 |
| `recipes` | array | ❌ | 레시피 정의 목록 |

**아이템 정의 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `id` | string | ✅ | 아이템 path. `namespace`와 결합해 `namespace:id` content ID 생성 |
| `fallback` | string | ✅ | Arc 클라이언트 없는 플레이어에게 표시되는 vanilla 아이템 ID |
| `maxStackSize` | int | ❌ | 최대 스택 수량 (기본값: 64) |
| `durability` | int | ❌ | 내구도 (도구/무기류, 기본값: null = 내구도 없음) |
| `order` | int | ❌ | Arc Content creative tab 내 표시 순서 (낮을수록 앞) |

**블록 정의 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `id` | string | ✅ | 블록 path |
| `fallback` | string | ✅ | 클라이언트에 전송되는 vanilla 블록 상태 |
| `hardness` | float | ❌ | 채굴 시간 계수 (기본값: 1.0) |
| `blastResistance` | float | ❌ | 폭발 저항 (기본값: 1.0) |
| `order` | int | ❌ | creative tab 표시 순서 |

**레시피 정의 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `id` | string | ✅ | 레시피 고유 path |
| `result` | string | ✅ | 결과물 content ID. `namespace:path` 형식 |
| `ingredients` | array | ✅ | 재료 content ID 목록. vanilla ID 혼용 가능 |

---

## 에셋 파일 구조

### 아이템 모델 (1.21.4 방식)

Minecraft 1.21.4부터 아이템 시각화는 `assets/<namespace>/items/<id>.json`으로 정의한다.

```json
// assets/magic/items/ruby_wand.json
{
  "model": {
    "type": "minecraft:model",
    "model": "magic:item/ruby_wand"
  }
}
```

```json
// assets/magic/models/item/ruby_wand.json
{
  "parent": "minecraft:item/handheld",
  "textures": {
    "layer0": "magic:item/ruby_wand"
  }
}
```

### 블록 모델

```json
// assets/magic/models/block/ruby_altar.json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "magic:block/ruby_altar"
  }
}
```

### 언어 파일

```json
// assets/magic/lang/ko_kr.json
{
  "item.magic.ruby_wand": "루비 지팡이",
  "item.magic.mana_crystal": "마나 결정",
  "block.magic.ruby_altar": "루비 제단"
}
```

---

## 지원 파일 형식

| 형식 | 용도 | 비고 |
|------|------|------|
| `.png` | 텍스처 | RGBA, 최대 8MiB |
| `.json` | 모델, 아이템 정의, lang | UTF-8 |
| `.mcmeta` | 애니메이션 텍스처 메타 | |
| `.ogg` | 사운드 | |

**허용되지 않는 파일**:
- 실행 파일 (`.exe`, `.jar`, `.sh`, `.py` 등)
- 스크립트 파일
- 심볼릭 링크 (보안상 거부)
- 경로에 `..` 세그먼트가 포함된 파일 (경로 traversal 방지)

---

## 크기 제한 (기본값)

| 제한 항목 | 기본값 | 설명 |
|-----------|--------|------|
| 에셋 당 최대 크기 | 8 MiB | 단일 파일 (PNG, OGG 등) |
| 팩 당 최대 크기 | 128 MiB | 팩 전체 에셋 합산 |

이 값은 `arc-content.yml`에서 조정할 수 있다. 너무 크게 설정하면 초기 접속 시 다운로드 대기 시간이 늘어나므로 주의한다. 텍스처는 최소 필요한 해상도로 유지하고, 큰 사운드 파일은 가능하면 분리 서빙을 고려한다.

---

## 블록과 청크 팔레트

### 팔레트 저장 방식

커스텀 블록 ID는 청크의 `arc:palette` PDC(Persistent Data Container) 항목에 저장된다. 형식은 다음과 같다.

```
arc:palette → { "magic:ruby_altar" → 1, "magic:mana_ore" → 2, ... }
```

vanilla 블록 상태는 fallback으로 등록된 바닐라 블록으로 저장되므로, 바닐라 클라이언트가 접속해도 청크를 정상적으로 볼 수 있다. Arc 클라이언트는 팔레트를 읽어 커스텀 모델로 렌더링한다.

### 팩 임시 제거 시 동작

팩이 서버에서 일시적으로 제거되어도 청크 팔레트에 저장된 ID는 삭제되지 않는다. 알 수 없는(unknown) ID와 상태 바이트는 그대로 유지된다. 팩을 다시 추가하면 ID가 자동으로 연결되어 기존 배치된 블록이 복원된다.

### 영구 삭제 전 마이그레이션 권장

동일한 namespace:id를 재사용하면 기존 팔레트 항목이 새 콘텐츠와 매핑된다. 영구 삭제가 목적이라면 서버 플러그인 또는 별도 스크립트로 해당 블록이 배치된 청크를 명시적으로 마이그레이션(fallback으로 교체)한 후 팩을 제거한다.

```
콘텐츠 제거 시 권장 절차:
1. 배치된 커스텀 블록 위치 목록 수집 (/arc content blocks list <id>)
2. 각 블록을 적절한 vanilla 블록으로 교체
3. pack.json 또는 Plugin API에서 콘텐츠 제거
4. 서버 재시작 또는 /arc content reload
```

### 성능 노트

팔레트 인코딩 시간은 `ContentMetrics`에서 측정한다. 청크당 고유 커스텀 블록 종류가 많을수록 팔레트 직렬화 비용이 증가한다. 일반적인 사용 규모(팩 당 블록 50개 미만, 청크당 팔레트 항목 20개 미만)에서는 성능 영향이 미미하다.
