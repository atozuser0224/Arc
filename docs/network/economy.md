---
title: 글로벌 경제
parent: Network Layer
nav_order: 3
---

# 글로벌 경제

Redis에 플레이어 잔액을 저장해 모든 서버에서 즉시 동일한 잔액을 조회·변경할 수 있다. 서버 A에서 돈을 지불하면 서버 B에서 즉시 반영된다.

## 활성화

```yaml
# arc-network.yml
network:
  economy:
    enabled: true
```

---

## API: ArcGlobalEconomy

```java
import dev.arc.network.economy.ArcGlobalEconomy;
import dev.arc.network.economy.EconomyResult;
import java.util.UUID;

ArcGlobalEconomy eco = ArcGlobalEconomy.getInstance();
UUID uuid = player.getUniqueId();
```

### 잔액 조회

```java
// 잔액 조회 (존재하지 않으면 0 반환)
long balance = eco.getBalance(uuid);
player.sendMessage("잔액: " + balance + "코인");

// 잔액 존재 확인 (계정이 한 번이라도 생성됐는지)
boolean hasAccount = eco.hasBalance(uuid);
```

### 입금

```java
// 입금 (항상 성공, 음수 입금 시 예외)
eco.deposit(uuid, 1000L);

// 결과 확인
EconomyResult result = eco.deposit(uuid, 500L);
if (result == EconomyResult.SUCCESS) {
    player.sendMessage("§a500코인이 입금됐습니다.");
}
```

### 출금 (원자적)

출금은 Redis Lua 스크립트로 실행된다. 잔액 확인과 차감이 하나의 원자적 연산으로 처리되므로, 동시에 여러 서버에서 출금 요청이 들어와도 음수 잔액이 발생하지 않는다.

```java
// 출금 시도
EconomyResult result = eco.withdraw(uuid, 200L);

switch (result) {
    case SUCCESS:
        player.sendMessage("§a200코인이 출금됐습니다.");
        break;
    case INSUFFICIENT_FUNDS:
        player.sendMessage("§c잔액이 부족합니다.");
        long current = eco.getBalance(uuid);
        player.sendMessage("§7현재 잔액: " + current + "코인");
        break;
    case INVALID_AMOUNT:
        // 음수 또는 0 입력 시
        player.sendMessage("§c올바른 금액을 입력하세요.");
        break;
}
```

**Lua 스크립트 내부 동작:**

```lua
-- arc:economy:withdraw Lua 스크립트
local key = KEYS[1]          -- "arc:economy:balance:<uuid>"
local amount = tonumber(ARGV[1])
local current = tonumber(redis.call("GET", key) or "0")

if current < amount then
    return -1  -- 잔액 부족
end

redis.call("DECRBY", key, amount)
return 1  -- 성공
```

### 이체 (원자적)

두 플레이어 간 이체도 Lua 스크립트로 처리된다. 출금 실패 시 입금이 일어나지 않는다.

```java
UUID sender = player.getUniqueId();
UUID receiver = targetPlayer.getUniqueId();

EconomyResult result = eco.transfer(sender, receiver, 300L);

switch (result) {
    case SUCCESS:
        player.sendMessage("§a300코인을 전송했습니다.");
        targetPlayer.sendMessage("§a300코인을 받았습니다.");
        break;
    case INSUFFICIENT_FUNDS:
        player.sendMessage("§c잔액이 부족합니다.");
        break;
    case SELF_TRANSFER:
        player.sendMessage("§c자신에게 이체할 수 없습니다.");
        break;
}
```

### 잔액 설정 (관리자용)

```java
// 잔액 직접 설정 (관리자 전용, 원자성 보장 없음)
eco.setBalance(uuid, 10000L);

// 잔액 초기화
eco.setBalance(uuid, 0L);
```

---

## Redis 키 구조

```
arc:economy:balance:<uuid>   (String) = "1500"
arc:economy:total            (String) = 서버 전체 통화량 (선택적 추적)
```

---

## 장단점

### 장점

- **즉시 반영**: 어느 서버에서 변경해도 Redis를 통해 모든 서버에 즉시 반영된다. 캐시 동기화 문제가 없다.
- **원자적 출금/이체**: Lua 스크립트로 경쟁 조건 없이 안전하게 처리된다. 분산 환경에서 이중 지출이 불가능하다.
- **단순한 구조**: 별도 데이터베이스 연결 없이 Redis 하나로 운영된다.

### 단점

- **Redis 의존**: Redis 장애 시 경제 기능이 완전히 마비된다. Redis HA 구성을 강력히 권장한다.
- **정수 타입(Long)**: 소수점 잔액을 지원하지 않는다. 소수점이 필요하다면 단위를 변환해야 한다.

  ```java
  // 1.00코인 = 100 (센트 단위로 저장)
  long storedBalance = eco.getBalance(uuid);
  double displayBalance = storedBalance / 100.0; // 표시용
  String formatted = String.format("%.2f코인", displayBalance);
  ```

- **히스토리 없음**: 기본적으로 잔액 변경 이력이 저장되지 않는다. 감사 기능이 필요하면 ArcAuditLog와 함께 사용하라.

---

## Vault 연동

`ArcGlobalEconomy`는 Vault Economy 인터페이스 구현체로 등록할 수 있어, 기존 Vault 기반 플러그인과 호환된다.

```yaml
# arc-network.yml
economy:
  vault-integration: true   # Vault Economy provider로 등록
```

활성화하면 Vault를 사용하는 모든 플러그인(Essentials, ShopGUI+, JobsReborn 등)이 자동으로 Arc 글로벌 경제를 사용한다.

```java
// Vault Economy API를 통한 직접 접근 (기존 플러그인 코드 변경 없음)
Economy economy = getServer().getServicesManager()
    .getRegistration(Economy.class).getProvider();

economy.depositPlayer(player, 1000.0); // 내부적으로 ArcGlobalEconomy.deposit() 호출
double balance = economy.getBalance(player); // 내부적으로 ArcGlobalEconomy.getBalance() 호출
```

{: .note }
Vault는 `double` 타입을 사용하지만 Arc 글로벌 경제는 `long` 타입을 사용한다. Vault 연동 시 내부적으로 `(long) amount` 변환이 일어나 소수점 이하가 버려진다. 이 동작이 문제라면 단위 변환 설정을 검토하라.

---

## 마이그레이션

기존 경제 플러그인에서 Arc 글로벌 경제로 마이그레이션하는 경우:

```java
// 1. 기존 플러그인에서 잔액 읽기
Map<UUID, Double> balances = existingPlugin.getAllBalances();

// 2. Arc 글로벌 경제에 일괄 입금
ArcGlobalEconomy eco = ArcGlobalEconomy.getInstance();
for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
    long amount = (long) entry.getValue().doubleValue();
    eco.setBalance(entry.getKey(), amount);
}

Bukkit.getLogger().info("마이그레이션 완료: " + balances.size() + "개 계정");
```

{: .warning }
마이그레이션 전 Redis 백업(`BGSAVE` 또는 RDB 스냅샷)을 반드시 수행하라.
