# Client World State Sessions

## Problem

Per-player time and weather require client packets that Paper does not expose
with complete intensity control. Existing experiments import CraftBukkit and NMS
types directly from `arc-api`, coupling plugin source to one mapped server build.

## Design

`arc-api` exposes immutable `ClientWorldState` values and a closeable
`ClientWorldStateSession`. Packet construction lives behind
`ClientWorldStateBackend`, installed by `arc-server`.

Sessions are stacked per player:

- opening a session applies its normalized state;
- updating the top session applies the replacement state;
- closing the top session reapplies the previous session;
- closing a covered session only removes it from the stack;
- closing the final session asks the backend to restore real world state;
- close is idempotent.

This makes independently owned effects composable without requiring plugins to
coordinate restoration order.

## API

```kotlin
val cinematic = player.clientWorldState {
    time(18_000)
    storm(rain = 1.0f, thunder = 0.8f)
}

cinematic.update {
    time(6_000)
    clearWeather()
}

cinematic.close()
```

Time is normalized into `0..23999`. Rain and thunder are clamped into `0f..1f`.
Unset properties are not overridden. The server backend restores all three
properties from the player's current world when the final session closes.

## Boundaries

- `arc-api` contains no `net.minecraft` or CraftBukkit imports.
- `arc-server` constructs packets reflectively to preserve mapping isolation.
- Session bookkeeping is thread-safe, but backend calls must still obey the
  server's player ownership rules.
- The API reports backend application success so callers can detect an
  unavailable or incompatible server bridge.
