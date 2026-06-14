---
title: Global Vault
parent: Network Layer
nav_order: 5
---

# Global Vault

어느 서버에서든 같은 아이템 창고(Vault)에 접근할 수 있는 기능이다. Redis SET NX EX를 이용한 분산 잠금으로 동시 접근을 차단해 아이템 복제를 방지한다.

{: .warning }
`vault.enabled: false`가 기본값이다. 잠금 구현을 충분히 검토하고 아이템 복제 여부를 테스트한 뒤에 활성화하라. 특히 서버 비정상 종료 시나리오를 반드시 검증하라.

## 활성화

```yaml
# arc-network.yml
network:
  vault:
    enabled: true
    lock-ttl: 300       # 분산 잠금 만료 시간 (초, 기본 5분)
    size: 54            # Vault 크기 (슬롯 수, 기본 54)
    groups:             # 그룹별 별도 Vault (선택)
      - "survival"
      - "creative"
```

---

## API: ArcGlobalVault

```java
import dev.arc.network.vault.ArcGlobalVault;
import dev.arc.network.vault.VaultOpenResult;
import java.util.UUID;

ArcGlobalVault vault = ArcGlobalVault.getInstance();
```

### 기본 Vault 열기

```java
// 플레이어의 기본 Vault 열기
VaultOpenResult result = vault.open(player);

switch (result) {
    case SUCCESS:
        // Vault GUI가 자동으로 열림
        break;
    case LOCKED_BY_OTHER_SERVER:
        player.sendMessage("§c이미 다른 서버에서 Vault를 사용 중입니다. 잠시 후 다시 시도하세요.");
        break;
    case REDIS_UNAVAILABLE:
        player.sendMessage("§c현재 Vault 서비스를 사용할 수 없습니다.");
        break;
    case ALREADY_OPEN:
        player.sendMessage("§e이미 Vault가 열려 있습니다.");
        break;
}
```

### 그룹 Vault 열기

```java
// 특정 그룹의 Vault 열기
VaultOpenResult result = vault.open(player, "survival");

// 플레이어 UUID 기준으로 관리자가 다른 플레이어의 Vault 열기
VaultOpenResult result = vault.openFor(adminPlayer, targetUuid);
```

---

## 분산 잠금 메커니즘

Vault를 열 때 Redis `SET NX EX` 명령으로 잠금 키를 생성한다. 이미 잠금이 걸려 있으면 열기에 실패한다.

```
[Vault 열기 요청 - 서버 A]
  └─▶ SET arc:vault:lock:<uuid> "server-A" NX EX 300
      ├─ OK   → 잠금 획득 성공, Vault GUI 열기
      └─ nil  → 이미 잠겨 있음 → LOCKED_BY_OTHER_SERVER 반환

[Vault 닫기 - 서버 A]
  └─▶ GET arc:vault:lock:<uuid> → "server-A" 확인 (내 잠금인지 검증)
  └─▶ DEL arc:vault:lock:<uuid>
  └─▶ 변경된 아이템 저장: HSET arc:vault:items:<uuid> ...
```

**잠금 소유권 검증**: 잠금 해제 시 현재 서버 ID가 잠금 값과 일치하는지 확인한다. 다른 서버가 실수로 잠금을 해제하는 것을 방지한다.

```lua
-- 원자적 잠금 해제 Lua 스크립트
local key = KEYS[1]
local expected = ARGV[1]  -- 현재 서버 ID
local current = redis.call("GET", key)

if current == expected then
    redis.call("DEL", key)
    return 1  -- 성공
else
    return 0  -- 잠금 소유자가 아님
end
```

---

## Redis 키 구조

```
arc:vault:lock:<uuid>           (String, TTL = lock-ttl)  = "server-A"
arc:vault:lock:<uuid>:<group>   (String, TTL = lock-ttl)  = "server-A"

arc:vault:items:<uuid>          (Hash)
  slot-0  → Base64 인코딩된 ItemStack
  slot-1  → Base64 인코딩된 ItemStack
  ...
  slot-53 → Base64 인코딩된 ItemStack

arc:vault:items:<uuid>:<group>  (Hash)  ← 그룹 Vault
```

---

## 아이템 저장 및 복원

```java
// Vault GUI 닫힘 이벤트 처리 (내부 구현 참고용)
@EventHandler
public void onInventoryClose(InventoryCloseEvent event) {
    if (!(event.getInventory().getHolder() instanceof ArcVaultHolder)) return;

    ArcVaultHolder holder = (ArcVaultHolder) event.getInventory().getHolder();
    UUID ownerUuid = holder.getOwnerUuid();
    String group = holder.getGroup(); // null이면 기본 Vault

    // 아이템 직렬화 후 Redis에 저장
    vault.saveAndClose(ownerUuid, group, event.getInventory());
}
```

---

## 장단점

### 장점

- **어디서든 접근**: 어느 서버에서나 동일한 창고에 접근 가능. 서버 이동 후 아이템을 찾을 필요가 없다.
- **복제 방지**: `SET NX EX`로 동시에 두 서버에서 같은 Vault를 여는 것을 차단한다. 아이템 복제 벡터를 원천 차단한다.
- **그룹 분리**: 생존, 창작 등 게임 모드별로 독립적인 창고를 제공할 수 있다.

### 단점

- **Redis 장애 시 접근 불가**: Redis가 다운되면 Vault를 열 수 없다. `REDIS_UNAVAILABLE` 결과를 반환한다.
- **잠금 만료 대기**: 서버가 잠금을 획득한 채로 비정상 종료되면 TTL(기본 5분)이 만료될 때까지 해당 플레이어의 Vault를 열 수 없다. TTL을 너무 길게 설정하면 불편함이 증가하고, 너무 짧게 설정하면 아이템 저장 전에 잠금이 만료될 수 있다.
- **아이템 NBT 호환성**: Base64 직렬화는 버전 간 NBT 구조 변경에 취약하다. Minecraft 버전 업그레이드 전 Vault 데이터 마이그레이션 테스트가 필요하다.

---

## 비정상 종료 시나리오

| 시나리오 | 결과 | 대응 |
|---------|------|------|
| Vault 열기 중 클라이언트 끊김 | 잠금 TTL 후 자동 해제, 마지막 저장 상태 유지 | 없음 (자동 처리) |
| Vault 저장 중 서버 크래시 | 마지막 저장 이전 상태 유지 (열기 시점의 아이템) | 아이템 변경분 손실 가능 |
| Redis 연결 끊김 후 회복 | 잠금 TTL이 남아 있으면 그대로 유지 | TTL 만료 대기 |
| 잠금 TTL 만료 중 서버가 살아 있는 경우 | 아직 Vault가 열려 있어도 다른 서버에서 열 수 있음 → 위험 | lock-ttl을 세션 시간보다 충분히 크게 설정 |

{: .warning }
`lock-ttl`은 플레이어가 Vault를 열어 둘 수 있는 최대 시간이다. 이 시간 안에 저장이 완료되어야 안전하다. 기본 300초(5분)는 일반적인 사용에 충분하지만, 네트워크가 느리거나 아이템 수가 많은 환경에서는 늘리는 것을 검토하라.

---

## 관리자 명령어

| 명령어 | 권한 | 설명 |
|--------|------|------|
| `/arc vault open <player>` | `arc.vault.admin` | 다른 플레이어의 Vault 열기 |
| `/arc vault unlock <player>` | `arc.vault.admin` | 잠금 강제 해제 (비상시) |
| `/arc vault info <player>` | `arc.vault.admin` | 잠금 상태 및 저장 슬롯 수 조회 |

{: .note }
`/arc vault unlock`은 비상 상황에서만 사용하라. Vault가 다른 서버에서 실제로 열려 있는 상태에서 강제 해제하면 아이템이 중복 저장되거나 변경사항이 덮어쓰일 수 있다.
