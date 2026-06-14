---
title: Discord Webhook
parent: Network Layer
nav_order: 7
---

# Discord Webhook

Arc Network 이벤트(밴, 서버 다운/업, 방송 등)가 발생하면 Discord 채널에 자동으로 메시지를 전송한다. JDK 내장 `HttpClient`를 사용하므로 추가 라이브러리가 필요 없다.

## 활성화

```yaml
# arc-network.yml
network:
  discord:
    webhook:
      url: "https://discord.com/api/webhooks/1234567890/AbCdEfGhIjKlMnOpQrStUvWxYz"
      events:
        ban: true           # 밴/언밴 발생 시 알림
        server-down: true   # 서버 오프라인 감지 시 알림
        server-up: true     # 서버 온라인 감지 시 알림
        broadcast: false    # 전체 방송 시 알림 (기본 OFF, 너무 많을 수 있음)
```

`url`이 비어 있으면 Discord Webhook 기능은 완전히 비활성화된다.

---

## Runtime 설정 변경

서버 재시작 없이 Webhook URL을 변경할 수 있다.

```java
import dev.arc.api.config.ArcGlobalConfig;

// Webhook URL 변경 (즉시 적용)
ArcGlobalConfig.set("discord.webhook.url",
    "https://discord.com/api/webhooks/new-webhook-id/new-token");

// 특정 이벤트 토글
ArcGlobalConfig.set("discord.webhook.events.ban", "true");
ArcGlobalConfig.set("discord.webhook.events.broadcast", "false");
```

명령어로도 변경 가능:

```
/arc config set discord.webhook.url <url>
/arc config set discord.webhook.events.ban true
```

---

## API: ArcDiscordWebhook

직접 Webhook 메시지를 보낼 수도 있다.

### 밴 알림

```java
import dev.arc.network.discord.ArcDiscordWebhook;

ArcDiscordWebhook webhook = ArcDiscordWebhook.getInstance();

// 밴 알림 (자동으로 호출됨, 수동 호출도 가능)
webhook.sendBan(
    "PlayerName",           // 밴된 플레이어 이름
    "핵 프로그램 사용",      // 이유
    "AdminPlayer",          // 처리한 관리자
    true,                   // 영구 밴 여부
    null                    // 만료 시각 (영구이면 null)
);
```

Discord 전송 내용:

```
🔨 플레이어 밴
플레이어: PlayerName
이유: 핵 프로그램 사용
처리: AdminPlayer
종류: 영구 밴
시각: 2026-06-15 14:30:00 KST
```

### 방송 알림

```java
// 전체 서버 방송 알림
webhook.sendBroadcast(
    "서버 점검이 1시간 후 시작됩니다.",
    "AdminPlayer",   // 방송한 관리자
    "ALL"            // 대상 (서버 ID 또는 "ALL")
);
```

### 서버 다운 알림

```java
// 서버 오프라인 감지 시 자동 호출
webhook.sendServerDown(
    "game-1",                    // 서버 ID
    "game.example.com",          // 서버 주소 (설정에 있는 경우)
    System.currentTimeMillis()   // 감지 시각
);
```

Discord 전송 내용:

```
🔴 서버 오프라인
서버: game-1
감지 시각: 2026-06-15 14:35:12 KST
```

### 서버 업 알림

```java
// 서버 온라인 감지 시 자동 호출
webhook.sendServerUp(
    "game-1",
    System.currentTimeMillis()
);
```

Discord 전송 내용:

```
🟢 서버 온라인
서버: game-1
복구 시각: 2026-06-15 14:38:00 KST
다운 시간: 2분 48초
```

### 커스텀 메시지

```java
// 원하는 메시지 직접 전송
webhook.send("커스텀 메시지 내용");

// JSON Embed 형식으로 전송
webhook.sendRaw("{\"content\": \"커스텀 메시지\", \"embeds\": []}");
```

---

## 내부 구현

JDK 11+의 `java.net.http.HttpClient`를 사용한다.

```java
// 내부 구현 참고 (실제 코드 요약)
HttpClient client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(5))
    .build();

HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create(webhookUrl))
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
    .timeout(Duration.ofSeconds(10))
    .build();

// 비동기 전송 (서버 tick에 영향 없음)
client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
    .exceptionally(ex -> {
        logger.warning("[Arc-Discord] Webhook 전송 실패: " + ex.getMessage());
        return null;
    });
```

모든 Webhook 전송은 비동기로 처리되므로 서버 tick에 영향을 주지 않는다.

---

## 장단점

### 장점

- **재시작 없이 URL 변경**: `ArcGlobalConfig.set()`으로 즉시 적용된다. Webhook URL이 만료되거나 채널이 바뀌어도 서버를 재시작할 필요가 없다.
- **추가 라이브러리 불필요**: JDK 내장 `HttpClient`를 사용하므로 Discord4J, JDA 같은 무거운 라이브러리를 추가하지 않아도 된다.
- **비동기 처리**: 서버 tick을 차단하지 않는다.

### 단점

- **고급 Discord 기능 제한**: 복잡한 Embed 포맷(다중 필드, 이미지, 썸네일, 타임스탬프 색상 등), 파일 첨부, 버튼/컴포넌트 등은 직접 구현해야 한다. `sendRaw()`를 통해 JSON을 직접 전송하는 방식으로 우회 가능하다.
- **속도 제한(Rate Limit)**: Discord Webhook의 기본 속도 제한은 30req/min이다. 서버가 많거나 이벤트가 빈번한 경우 `429 Too Many Requests`가 발생할 수 있다. 현재 Arc는 자동 재시도 로직이 없으므로 주의가 필요하다.
- **단방향 통신**: Discord → Minecraft 방향의 명령 전달은 지원하지 않는다. Bot 기반 양방향 연동이 필요하면 별도 구현이 필요하다.

---

## 여러 채널로 라우팅

현재 단일 Webhook URL만 지원하지만, 이벤트 유형별로 다른 채널을 사용하고 싶은 경우 이벤트 훅을 통해 구현할 수 있다.

```java
// 이벤트별 채널 분리 예시
@EventHandler
public void onPlayerBan(ArcNetworkPlayerBanEvent event) {
    // 밴 전용 채널 Webhook
    String banWebhookUrl = "https://discord.com/api/webhooks/ban-channel/...";
    ArcDiscordWebhook.sendCustom(banWebhookUrl, buildBanEmbed(event));
}

private String buildBanEmbed(ArcNetworkPlayerBanEvent event) {
    return String.format(
        "{\"embeds\":[{\"title\":\"플레이어 밴\",\"color\":16711680," +
        "\"fields\":[{\"name\":\"플레이어\",\"value\":\"%s\"},{\"name\":\"이유\",\"value\":\"%s\"}]}]}",
        event.getPlayerName(), event.getReason()
    );
}
```

---

## 보안 주의사항

{: .warning }
Webhook URL은 공개하면 안 된다. Webhook URL을 아는 사람은 누구나 해당 채널에 메시지를 보낼 수 있다. `arc-network.yml`의 접근 권한을 제한하고, Webhook URL을 환경 변수로 관리하는 것을 권장한다.

```yaml
# 환경 변수로 URL 설정 (arc-network.yml)
webhook:
  url: "${DISCORD_WEBHOOK_URL}"  # 환경 변수에서 읽기
```

Webhook URL이 유출됐다면 Discord 채널 설정에서 즉시 재생성하라.
