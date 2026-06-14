---
title: 원격 배포 · 커맨드
parent: Network Layer
nav_order: 9
---

# 원격 배포 · 커맨드

{: .warning }
원격 배포와 원격 커맨드는 **기본값 OFF**다. 잘못 설정하면 전체 서버의 코드와 상태를 바꿀 수 있다. 충분한 보안 검토 후 활성화하라.

---

## 플러그인 원격 배포

### 개요

한 서버에서 JAR 파일을 다른 서버(또는 전체 서버)의 `plugins/` 폴더에 배포한다. 배포 후 서버는 자동으로 플러그인을 로드한다(핫 리로드를 지원하는 플러그인의 경우).

### 활성화

```yaml
# arc-network.yml
network:
  deploy:
    enabled: true
    sandbox-check: true     # DANGEROUS 클래스 차단 (기본 ON, 비활성화 비권장)
    allowed-sources:        # 배포 허용 소스 서버 목록 (비어 있으면 모든 서버 허용)
      - "admin-server"
    max-jar-size: 52428800  # 최대 JAR 크기 (bytes, 기본 50MB)
    deploy-dir: "plugins"   # 배포 대상 디렉토리 (서버 루트 기준)
```

### 명령어

```
/arc network deploy <jar-path> <server-id|all>
```

예시:

```
# 특정 서버에 배포
/arc network deploy /home/minecraft/MyPlugin-1.0.jar game-1

# 전체 서버에 배포
/arc network deploy /home/minecraft/MyPlugin-1.0.jar all

# 그룹에 배포
/arc network deploy /home/minecraft/MyPlugin-1.0.jar group:survival
```

### 배포 흐름

```
[배포 요청 (서버 A)]
  └─▶ JAR 파일 읽기 및 크기 확인
  └─▶ PluginSandboxCheck 실행:
      ├─ DANGEROUS 클래스 포함 시 → 배포 차단, 경고 출력
      └─ 통과 시 → 계속 진행
  └─▶ JAR을 Base64 인코딩
  └─▶ LPUSH arc:inbox:game-1 {type:"DEPLOY", filename:"MyPlugin-1.0.jar", data:"<base64>"}

[대상 서버 (game-1) 수신]
  └─▶ RPOP으로 DEPLOY 메시지 수신
  └─▶ Base64 디코딩 → JAR 바이트
  └─▶ PluginSandboxCheck 재실행 (수신 측에서도 검증)
  └─▶ plugins/MyPlugin-1.0.jar 에 저장
  └─▶ 플러그인 로드 시도 (PluginManager.loadPlugin())
  └─▶ 결과를 arc:inbox:server-A 에 응답
```

### PluginSandboxCheck

배포 전 JAR 바이트코드를 스캔해 위험한 클래스 사용을 차단한다.

```
차단 목록 (DANGEROUS):
  java.lang.Runtime.exec(...)            # 셸 명령 실행
  java.lang.ProcessBuilder               # 프로세스 생성
  java.io.FileOutputStream               # 임의 파일 쓰기
  java.net.Socket                        # 임의 소켓 연결
  sun.misc.Unsafe                        # 메모리 직접 조작
  java.lang.reflect.Method.invoke(...)   # 리플렉션 (조건부)
```

```
경고 목록 (WARNING):
  java.net.URL.openConnection()          # 외부 HTTP 요청
  java.lang.System.exit()               # JVM 종료 시도
```

```java
// 수동으로 샌드박스 체크 실행
import dev.arc.network.deploy.PluginSandboxCheck;

byte[] jarBytes = Files.readAllBytes(Paths.get("MyPlugin.jar"));
PluginSandboxCheck.Result result = PluginSandboxCheck.check(jarBytes);

if (result.isDangerous()) {
    logger.warning("위험 클래스 발견: " + result.getDangerousClasses());
} else if (result.hasWarnings()) {
    logger.info("경고: " + result.getWarnings());
} else {
    logger.info("샌드박스 체크 통과");
}
```

### 장단점

**장점:**
- 여러 서버에 동시 배포 가능. 릴리스 시간 단축
- 중앙에서 플러그인 버전 관리 가능
- 샌드박스 체크로 악의적 코드 1차 차단

**단점:**
- 샌드박스 체크는 정적 분석이다. 동적으로 생성된 코드나 리플렉션을 통한 우회는 탐지하지 못한다. 신뢰할 수 있는 소스에서만 배포하라.
- JAR 크기에 따라 Redis 메시지 크기가 커진다. 매우 큰 플러그인은 네트워크 부하를 줄이기 위해 공유 파일 시스템(NFS, S3 등) 경유를 고려하라.
- 기본 OFF 이유: 임의 JAR 배포는 서버 전체 코드 변조가 가능하다. CI/CD 파이프라인과 코드 서명을 준비한 뒤 활성화하라.

---

## 원격 커맨드

### 개요

한 서버에서 다른 서버로 명령어를 전송해 원격 실행한다.

### 활성화

```yaml
# arc-network.yml
network:
  remote-cmd:
    enabled: true
    require-confirm: true   # 실행 전 confirm 토큰 요구 (기본 ON)
    rate-limit: 3           # 분당 최대 실행 횟수 (기본 3)
    allowlist:              # 허용 명령어 목록 (비어 있으면 blocklist만 적용)
      - "broadcast"
      - "kick"
      - "arc ban"
    blocklist:              # 차단 명령어 목록 (allowlist보다 우선)
      - "stop"
      - "op"
      - "deop"
      - "reload"
      - "plugman"
```

### 명령어

```
# 기본 실행 (require-confirm: false인 경우)
/arc network remote-cmd game-1 broadcast 서버 점검 10분 후

# require-confirm: true인 경우 — 1단계: 요청
/arc network remote-cmd game-1 broadcast 서버 점검 10분 후
> [Arc] 원격 커맨드 대기 중. 확인하려면: /arc network confirm abc123

# 2단계: 확인
/arc network confirm abc123
> [Arc] 명령 실행됨: game-1 → broadcast 서버 점검 10분 후

# 전체 서버에 동시 실행
/arc network remote-cmd all broadcast 전체 서버 공지사항
```

### 실행 흐름

```
[원격 커맨드 요청 (서버 A)]
  └─▶ blocklist 검사 → 차단 명령이면 즉시 거부
  └─▶ allowlist 검사 → 목록이 있고 해당 명령이 없으면 거부
  └─▶ rate-limit 확인 (Redis: arc:ratelimit:remote-cmd:<actor>)
  └─▶ require-confirm이면 confirm 토큰 생성 (TTL 60초)
  └─▶ 확인 후 LPUSH arc:inbox:game-1 {type:"REMOTE_CMD", cmd:"broadcast ...", actor:"AdminPlayer"}

[대상 서버 (game-1) 수신]
  └─▶ blocklist 재검사 (수신 측에서도 검증)
  └─▶ Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
  └─▶ 실행 결과 로그 기록 (감사 로그)
  └─▶ 결과를 arc:inbox:server-A 에 응답
```

### rate-limit 동작

```
arc:ratelimit:remote-cmd:<actor>  (String, TTL 60s) = "3"

[분당 3회 초과 시]
> [Arc] 원격 커맨드 속도 제한 초과. 잠시 후 다시 시도하세요.
> [Arc] 남은 대기: 45초
```

### confirm 토큰

```
arc:remote-cmd:confirm:<token>  (Hash, TTL 60s)
  target  → "game-1"
  cmd     → "broadcast 서버 점검 10분 후"
  actor   → "AdminPlayer"
  created → "1718400000000"
```

60초 내에 `/arc network confirm <token>`을 입력하지 않으면 자동 만료된다.

### API를 통한 원격 커맨드

```java
import dev.arc.network.deploy.ArcRemoteCommand;

ArcRemoteCommand remoteCmd = ArcRemoteCommand.getInstance();

// 즉시 실행 (require-confirm 무시)
remoteCmd.execute("game-1", "broadcast 공지사항", "AdminPlugin", true /* bypass-confirm */);

// 일반 실행 (설정 준수)
remoteCmd.execute("game-1", "kick PlayerName 규칙 위반", "AdminPlugin", false);
```

### 장단점

**장점:**
- 여러 서버에 동시 명령 실행 가능. 전서버 공지, 전서버 킥 등에 유용
- allowlist/blocklist로 허용 범위 제한 가능
- require-confirm으로 실수 방지
- rate-limit으로 남용 차단

**단점:**
- 잘못된 명령이 다른 서버에 영향을 미친다. 예를 들어 `reload` 명령이 blocklist에 없으면 원격으로 서버를 리로드할 수 있다.
- 수신 서버의 콘솔 권한으로 실행되므로 allowlist를 엄격하게 유지해야 한다.
- 기본 OFF 이유: 임의 명령 원격 실행은 높은 보안 위험을 수반한다.

---

## 보안 권장사항

1. **allowlist 우선 사용**: blocklist만 사용하면 새로운 위험 명령을 놓칠 수 있다. 허용할 명령을 명시적으로 나열하는 allowlist 방식을 권장한다.

2. **require-confirm: true 유지**: 실수로 잘못된 서버에 명령을 보내는 것을 방지한다.

3. **rate-limit 유지**: 기본값 3회/분은 대부분의 운영 시나리오에 충분하다. 자동화 스크립트가 필요한 경우에만 늘려라.

4. **감사 로그 활성화**: 원격 커맨드의 모든 실행을 감사 로그에 기록해 추적 가능하게 하라.

5. **배포와 원격 커맨드 분리**: 권한이 있는 관리자만 접근할 수 있도록 별도 권한(`arc.network.deploy`, `arc.network.remote-cmd`)을 사용하라.
