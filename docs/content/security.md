---
title: 보안 · 제한
parent: ArcSync Content
nav_order: 4
---

# 보안 · 제한

ArcSync Content는 처음부터 보안을 핵심 설계 원칙으로 삼아 구현되었다. 이 문서는 보안 설계, 전송 제한, 키 관리, 예산 시스템, 모니터링 지표를 설명한다.

---

## 보안 설계 원칙

### 선언적 에셋과 메타데이터만 전송

ArcSync Content는 **결코 실행 가능한 코드를 전송하거나 실행하지 않는다**.

| 전송 가능 | 전송 불가 |
|-----------|-----------|
| PNG 텍스처 | JVM 바이트코드 (`.jar`, `.class`) |
| OGG 사운드 | 네이티브 라이브러리 (`.dll`, `.so`) |
| JSON 모델·정의 | 스크립트 파일 (`.js`, `.lua`, `.py`) |
| MCMETA 애니메이션 | 임의 실행 파일 |

클라이언트는 받은 파일을 resource pack으로만 사용한다. 파일을 실행하거나 JVM에 로드하는 경로가 존재하지 않는다.

### 모든 크기와 카운트를 Bounded 처리

악의적 서버가 클라이언트 메모리를 고갈시키거나 디스크를 채우는 것을 방지하기 위해 모든 경계에 제한이 적용된다.

| 경계 | 제한 |
|------|------|
| 단일 에셋 크기 | 8 MiB (기본값) |
| 팩 전체 에셋 크기 | 128 MiB (기본값) |
| 클라이언트 총 blob 수신 한도 | 64 MiB (기본값) |
| 패킷 크기 | bounded |
| 문자열 길이 | bounded |
| 컬렉션 크기 | bounded |
| in-flight 버퍼 | bounded |

### 요청 범위 제한

서버는 클라이언트에게 **해당 플레이어와 협상된 해시 목록에 있는 blob만** 응답한다. 클라이언트가 협상되지 않은 해시를 요청하면 요청이 거부된다. 이를 통해 한 플레이어의 세션이 다른 플레이어의 캐시나 에셋에 접근하는 것을 방지한다.

---

## Ed25519 서명

### 서명 생성 (서버)

서버는 arc-sync 초기화 시 Ed25519 키 쌍을 생성한다. content revision이 컴파일될 때마다 개인 키로 manifest에 서명한다.

```
manifest = {
    revision_id: <UUID>,
    blobs: [{ path: "...", sha256: "...", size: 1234 }, ...],
    compiled_at: <timestamp>
}
signature = Ed25519.sign(private_key, canonical_json(manifest))
```

### 서명 검증 (클라이언트)

클라이언트는 서버 공개 키로 서명을 검증한다. 검증에 실패하면 manifest를 거부하고 콘텐츠 동기화를 중단한다. 공격자가 네트워크 경로에서 manifest를 조작해도 서명이 맞지 않아 거부된다.

```
is_valid = Ed25519.verify(public_key, manifest_bytes, signature)
if (!is_valid) {
    throw ContentVerificationException("manifest signature invalid")
}
```

---

## 서버 키 관리

### 개인 키 보안

서버 개인 키는 `arc-sync/` 디렉토리에 저장된다. 이 키는 서버의 identity 자료와 동등하게 취급해야 한다.

**필수 조치:**
- `arc-sync/` 디렉토리를 다른 서버 identity 자료(SSL 인증서, RCON 비밀번호 등)와 함께 안전하게 백업한다.
- 파일 시스템 권한을 서버 프로세스 계정만 읽을 수 있도록 설정한다 (Linux: `chmod 600`).
- 버전 관리 시스템(Git 등)에 커밋하지 않는다.

**키 유출 시 대응:**
1. 즉시 새 키를 생성한다 (`/arc content key-rotate`).
2. 접속 중인 모든 클라이언트에게 키 변경을 공지한다.
3. 클라이언트가 `replaceTrust()` 또는 Pin 파일 삭제로 새 키를 수락하도록 안내한다.

### 클라이언트 Trust Pin

| 동작 | 설명 |
|------|------|
| 첫 접속 | 서버 공개 키를 `trust/<서버 주소>.key`에 자동 저장 |
| 재접속 | 저장된 키와 서버 키 비교 |
| 키 일치 | 정상 진행 |
| 키 불일치 | `ServerTrustException` → 동기화 거부 |

### replaceTrust() 사용 시나리오

```kotlin
// 서버 운영자 공지 후 합법적 키 교체를 수락하는 클라이언트 코드 예시
ArcClientApi.replaceTrust(serverAddress, newPublicKey)
// → trust/<서버 주소>.key 파일을 새 키로 덮어씀
// → 이후 접속에서 새 키를 신뢰
```

---

## 경로 Traversal 방지

### blob 경로 검증

모든 blob 경로는 `assets/` 디렉토리 아래로만 허용된다.

```kotlin
// 서버 측 검증 (개념 코드)
val normalized = Paths.get("assets").resolve(blob.path).normalize()
if (!normalized.startsWith(Paths.get("assets"))) {
    throw SecurityException("path traversal detected: ${blob.path}")
}
```

`..` 세그먼트가 포함된 경로는 normalize 후 `assets/` 밖으로 벗어나게 되어 즉시 거부된다.

**예시:**

| 입력 경로 | 정규화 결과 | 허용 여부 |
|-----------|------------|----------|
| `assets/magic/textures/ruby.png` | `assets/magic/textures/ruby.png` | ✅ |
| `assets/../secret.txt` | `secret.txt` | ❌ 거부 |
| `assets/magic/../../etc/passwd` | `etc/passwd` | ❌ 거부 |
| `assets/magic//textures/ruby.png` | `assets/magic/textures/ruby.png` | ✅ |

### ContentId 검증

content ID의 namespace와 path는 엄격한 패턴으로 검증된다.

```kotlin
val NAMESPACE_PATTERN = Regex("[a-z0-9_.\\-]+")
val PATH_SEGMENT = Regex("[a-z0-9_.\\-]+")

// ".." 세그먼트는 path 파서에서 명시적으로 거부
if (pathSegment == "..") {
    throw IllegalArgumentException("traversal segment '..' not allowed in ContentId path")
}
```

---

## 지원 파일 형식 검증

파일 형식은 확장자뿐만 아니라 **파일 헤더(magic bytes)**로도 검증된다.

| 형식 | 확장자 | 헤더 검증 |
|------|--------|---------|
| PNG | `.png` | `89 50 4E 47 0D 0A 1A 0A` |
| OGG | `.ogg` | `4F 67 67 53` |
| JSON | `.json` | 유효한 UTF-8 JSON |
| MCMETA | `.mcmeta` | 유효한 UTF-8 JSON |

확장자를 위조해도 헤더가 맞지 않으면 거부된다. 예를 들어 `exploit.png` 이름의 PE 실행 파일은 헤더 검증에서 거부된다.

---

## ContentBudget — 예산 시스템

`ContentBudget`은 단일 content ID가 서버 자원을 과도하게 소비하는 것을 방지한다.

### 동작 원리

```
content ID별 독립 예산:
  - bounded 큐 permit: 동시 처리 요청 수 제한
  - 핸들러별 타이밍: 처리 시간이 예산을 초과하면 기록
  - 반복 위반: 해당 content ID만 throttle
  - cooldown 후: 자동 복구
```

### 분리 보장

한 content ID의 예산이 소진되어도 다른 content ID는 영향을 받지 않는다. 악의적이거나 비효율적인 단일 콘텐츠가 서버 전체 콘텐츠 처리를 막을 수 없다.

```
정상 상황:
  magic:ruby_wand      → 예산 정상
  magic:mana_crystal   → 예산 정상
  showcase:arc_blade   → 예산 위반 (throttled)
                            ↓
  magic:*는 정상 처리 계속
  showcase:arc_blade만 cooldown 대기 → 자동 복구
```

### 설정 예시

```yaml
# arc-content.yml
content-budget:
  max-queue-permits: 16          # content ID당 동시 처리 허용 수
  handler-timeout-ms: 500        # 핸들러 타임아웃
  violation-threshold: 3         # 이 횟수 초과 시 throttle
  cooldown-seconds: 30           # throttle 복구 시간
```

---

## ContentMetrics — 운영 지표

`ContentMetrics`는 콘텐츠 플랫폼의 성능과 활동을 기록한다.

### 기록 항목

| 지표 | 설명 |
|------|------|
| `compile_time_ms` | content revision 컴파일 소요 시간 |
| `revision_size_bytes` | 컴파일된 revision 총 크기 |
| `cache_hits` | 클라이언트 blob 캐시 적중 횟수 |
| `cache_misses` | 클라이언트 blob 캐시 미스 횟수 |
| `transferred_bytes` | 클라이언트에 실제 전송된 바이트 합계 |
| `palette_encode_time_ms` | 청크 팔레트 인코딩 소요 시간 |

### 활용 방법

```
# 운영 중 지표 조회
/arc content metrics

출력 예시:
  Revision: abc123 (컴파일 45ms, 크기 12.4 MiB)
  캐시: 적중 1,234회 / 미스 56회 (95.7%)
  전송: 3.2 MiB (이번 서버 재시작 이후 누적)
  팔레트 인코딩: avg 0.8ms / max 12ms
```

cache hit rate가 낮다면 서버 IP나 포트가 자주 변경되어 클라이언트 캐시가 재활용되지 않고 있을 가능성이 있다. 같은 에셋이 반복 전송되면 `revision_size_bytes` 대비 `transferred_bytes` 비율로 낭비를 확인할 수 있다.

---

## 전송 제한 설정

기본값은 대부분의 서버 환경에서 적합하도록 설계되었다. 필요에 따라 `arc-content.yml`에서 조정할 수 있다.

```yaml
# arc-content.yml
limits:
  max-asset-size-bytes: 8388608    # 8 MiB (에셋 당)
  max-pack-size-bytes: 134217728   # 128 MiB (팩 당)
  client-blob-budget-bytes: 67108864  # 64 MiB (클라이언트 총 수신 한도)
  allowed-extensions:
    - png
    - ogg
    - json
    - mcmeta
```

**주의**: 제한을 크게 올리면 악의적 클라이언트(또는 실수로 과도하게 큰 팩)가 서버 메모리와 대역폭을 소진할 수 있다. 제한을 변경하기 전에 실제 팩 크기와 서버 용량을 고려한다.

---

## 위협 모델 요약

| 위협 | 대응 |
|------|------|
| 악의적 서버가 클라이언트에 실행 코드 주입 | 선언적 에셋만 허용 (PNG/OGG/JSON/MCMETA) |
| manifest 위변조 (MITM) | Ed25519 서명 검증 |
| 서버 사칭 (MITM) | 서버 키 Pin (첫 접속 후 키 변경 거부) |
| 경로 traversal 공격 | normalize() + startsWith 검증 |
| 클라이언트 메모리/디스크 고갈 | 모든 크기 bounded (에셋/팩/클라이언트 총량) |
| 단일 콘텐츠로 서버 자원 독점 | ContentBudget per-ID throttle |
| 협상 외 blob 접근 | 플레이어별 hash 목록으로 응답 제한 |
