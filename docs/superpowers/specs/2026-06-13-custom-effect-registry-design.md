# Custom Effect Registry Design

## Goal

Provide a Kotlin-first custom effect API for Arc plugins without requiring a
modded client or relying on unstable runtime mutation of Minecraft's native mob
effect registry. The API must support reusable effect definitions, predictable
reapplication rules, lifecycle callbacks, inspection, and deterministic tests.

## Scope

This increment replaces the prototype `CustomEffectRegistry` implementation
with a production-oriented virtual effect registry. It includes:

- namespaced, immutable effect definitions;
- a Kotlin DSL for bulk registration;
- configurable reapplication policies;
- application, tick, expiration, and removal callbacks;
- registry lookup, snapshots, unregistering, and duplicate validation;
- lifecycle shutdown and offline-player cleanup;
- callback failure isolation and logging;
- a Bukkit-independent state engine with unit tests;
- compatibility helpers for the existing player extension API.

Native registration in `minecraft:mob_effect` is intentionally deferred. A
vanilla client cannot render arbitrary effect IDs without registry and protocol
support, and runtime NMS registration is version-sensitive. Definitions may
still select an existing Bukkit `PotionEffectType` as their client-visible icon.

## Public API

Plugins create one registry and define effects in a cohesive block:

```kotlin
val effects = customEffects {
    effect("mana_regen") {
        visual(PotionEffectType.REGENERATION)
        defaultDuration = 20.seconds
        defaultAmplifier = 0
        reapplyPolicy = EffectReapplyPolicy.KEEP_STRONGER

        onApply { player, instance ->
            player.sendMessage("Mana regeneration started")
        }
        onTick(interval = 20.ticks) { player, instance ->
            restoreMana(player, instance.amplifier + 1)
        }
        onExpire { player, instance ->
            player.sendMessage("Mana regeneration ended")
        }
    }
}

effects.apply(player, effects["mana_regen"], duration = 10.seconds)
player.hasCustomEffect(effects["mana_regen"], effects)
```

Simple names are converted to `NamespacedKey(plugin, name)`. Fully namespaced
keys are accepted by lower-level overloads. Definitions are immutable after
registration so active instances cannot change underneath the tick engine.

## Components

### Effect Definition

`CustomEffect` contains the key, optional visual effect, default duration and
amplifier, default reapplication policy, tick interval, and callbacks. A
builder validates values before producing the immutable definition.

### State Engine

`CustomEffectEngine<K, D>` owns active effect state without Bukkit types. It
accepts a subject key, effect key, duration, amplifier, and reapplication
policy. It returns explicit transition results so the Bukkit layer can decide
which callbacks and visual updates to perform.

The engine supports:

- apply and reapply;
- remove one effect;
- clear one subject;
- unregister one definition's active instances;
- advance one logical tick;
- immutable snapshots for inspection.

Keeping this component independent from Bukkit allows state transitions to be
tested without a running Minecraft server or scheduler mocks.

### Bukkit Registry

`CustomEffectRegistry` owns definitions, the state engine, and one repeating
Bukkit scheduler task. It resolves online players, updates optional visual
effects, invokes callbacks, logs callback failures, and shuts down cleanly.

The registry is not global. Each plugin owns its registry, avoiding key and
lifecycle collisions between plugins. Duplicate definitions within a registry
fail immediately.

### Kotlin DSL

`Plugin.customEffects { ... }` constructs and starts a registry.
`CustomEffectRegistry.effects { ... }` adds definitions later. Builders use
the existing Arc registry DSL marker to prevent accidental receiver leakage.

Duration overloads accept ticks directly and Kotlin `Duration`. Durations are
rounded up to whole ticks and must be positive.

## Reapplication Semantics

`EffectReapplyPolicy` has four values:

- `KEEP_STRONGER`: replace when the new amplifier is higher; for equal
  amplifiers keep the longer remaining duration.
- `REPLACE`: always replace the active instance.
- `EXTEND`: retain the stronger amplifier and add the new duration to the
  remaining duration.
- `IGNORE`: preserve the existing instance unchanged.

The default is `KEEP_STRONGER`, matching the least-surprising potion-like
behavior. An apply call returns a result describing whether the instance was
applied, replaced, extended, refreshed, or ignored.

Replacing an instance invokes the removal callback with reason `REPLACED`
before invoking the new application callback. Natural completion uses
`EXPIRED`; explicit calls use `REMOVED`; registry shutdown uses `SHUTDOWN`;
unregistering uses `UNREGISTERED`.

## Tick and Callback Flow

1. The scheduler advances the engine once per server tick.
2. Active instances whose configured interval is due produce tick transitions.
3. The registry resolves each subject to an online player.
4. Missing players are cleared without callbacks or Bukkit calls.
5. Tick callbacks run before duration decrement reaches expiration.
6. Expired instances are removed, their visual effect is cleared, and their
   expiration callback is invoked.

Every callback is isolated with `runCatching`. A plugin callback failure is
logged with the effect key and player UUID and does not stop other effects or
future scheduler ticks.

## Visual Effect Ownership

Visual effects are optional compatibility hints, not the source of gameplay
state. Applying an effect adds the selected Bukkit potion effect with the
custom instance's duration and amplifier.

When multiple custom effects use the same visual `PotionEffectType`, removing
one must not clear the visual while another active custom effect still owns it.
The registry recomputes the strongest remaining visual owner and reapplies it.
This prevents one custom definition from corrupting another definition's HUD
state.

The registry does not restore potion effects applied independently by other
plugins. That would require ownership metadata unavailable through Bukkit and
is documented as a limitation.

## Threading and Lifecycle

All mutation methods require the server thread. The registry checks this and
throws a descriptive error rather than silently corrupting mutable Bukkit
state. Read-only definition lookup is safe after construction; active-state
inspection is server-thread-only.

`close()` is idempotent. It cancels the scheduler task, removes managed visual
effects, emits shutdown removals for online players, clears active state, and
prevents further mutation.

## Compatibility

Existing names remain available where practical:

- `Plugin.customEffectRegistry()` creates an empty registry.
- `define(id) { ... }` delegates to the new builder.
- player extension functions continue accepting an explicit registry.
- integer tick duration overloads remain supported.

Source-compatible callback adapters are retained for the prototype's
single-parameter `onApply` and `onExpire` forms and two-parameter `onTick`
form. New overloads expose the complete active instance.

## Testing

State-engine tests cover:

- initial application;
- all four reapplication policies;
- stronger/equal/weaker amplifier behavior;
- duration expiration and transition ordering;
- explicit removal, subject clearing, and unregister cleanup;
- immutable snapshots and invalid input rejection.

Builder tests cover key validation, positive durations, tick intervals, and
duplicate definitions without requiring a running server where possible.

The final verification runs the focused tests, the full `arc-api` test task,
and `arc-api` compilation.

## Documentation

`ARC_IMPLEMENTATION.md` receives a dated entry describing the new API,
behavior, tests, and known visual ownership limitation. `arc-api/README.md`
receives a concise usage example and public entry-point summary.
