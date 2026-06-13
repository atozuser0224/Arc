# Custom Effect Registry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the prototype custom effect manager with a tested, Kotlin-first virtual effect registry for Arc plugins.

**Architecture:** A Bukkit-independent state engine owns deterministic effect transitions. A plugin-scoped Bukkit adapter owns definitions, scheduling, callbacks, visual potion effects, and lifecycle cleanup; a focused DSL builds immutable definitions.

**Tech Stack:** Kotlin 2.x, Bukkit/Paper API, Kotlin `Duration`, JUnit 5 through `kotlin.test`, Gradle.

---

## File Structure

- Create `arc-api/src/main/kotlin/dev/arc/api/effect/CustomEffectEngine.kt`: generic active-state engine and transition/result types.
- Replace `arc-api/src/main/kotlin/dev/arc/api/effect/CustomEffect.kt`: immutable definition, builder DSL, plugin registry, scheduler adapter, compatibility extensions.
- Create `arc-api/src/test/kotlin/dev/arc/api/effect/CustomEffectEngineTest.kt`: deterministic state and reapplication tests.
- Modify `arc-api/README.md`: public API overview and usage.
- Modify `ARC_IMPLEMENTATION.md`: dated implementation record, behavior, verification, and limitation.

### Task 1: State Engine

**Files:**
- Create: `arc-api/src/test/kotlin/dev/arc/api/effect/CustomEffectEngineTest.kt`
- Create: `arc-api/src/main/kotlin/dev/arc/api/effect/CustomEffectEngine.kt`

- [ ] **Step 1: Write failing engine tests**

Add tests that instantiate `CustomEffectEngine<String, String>()` and verify:

```kotlin
val first = engine.apply("player", "arc:haste", 100, 1, EffectReapplyPolicy.KEEP_STRONGER)
assertEquals(EffectApplyResult.APPLIED, first.result)
assertEquals(100, engine.get("player", "arc:haste")?.remainingTicks)
```

Cover `KEEP_STRONGER` with weaker, stronger, and equal applications; `REPLACE`;
`EXTEND`; `IGNORE`; expiration; explicit removal; subject clearing; effect
clearing; immutable snapshots; and rejection of non-positive durations,
negative amplifiers, and non-positive tick intervals.

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
rtk .\gradlew.bat :arc-api:test --tests dev.arc.api.effect.CustomEffectEngineTest --no-daemon
```

Expected: compilation fails because `CustomEffectEngine` and its result types do
not exist.

- [ ] **Step 3: Implement the engine**

Define:

```kotlin
public enum class EffectReapplyPolicy { KEEP_STRONGER, REPLACE, EXTEND, IGNORE }
public enum class EffectApplyResult { APPLIED, REPLACED, REFRESHED, EXTENDED, IGNORED }
public enum class EffectRemovalReason { EXPIRED, REMOVED, REPLACED, CLEARED, UNREGISTERED, SHUTDOWN }

public data class ActiveCustomEffect<K>(
    val effectKey: K,
    val remainingTicks: Int,
    val amplifier: Int,
    val elapsedTicks: Long,
    val tickInterval: Int,
)

public class CustomEffectEngine<S : Any, K : Any> {
    public fun apply(...): EffectApplication<K>
    public fun get(subject: S, effectKey: K): ActiveCustomEffect<K>?
    public fun snapshot(subject: S): Map<K, ActiveCustomEffect<K>>
    public fun snapshotAll(): Map<S, Map<K, ActiveCustomEffect<K>>>
    public fun remove(...): EffectRemoval<K>?
    public fun clearSubject(...): List<EffectRemoval<K>>
    public fun clearEffect(...): List<SubjectEffectRemoval<S, K>>
    public fun tick(): EffectTickBatch<S, K>
}
```

Use copy-on-read immutable data objects and `LinkedHashMap` for deterministic
transition ordering.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the focused command from Step 2. Expected: all engine tests pass.

### Task 2: Definition DSL and Registry Adapter

**Files:**
- Replace: `arc-api/src/main/kotlin/dev/arc/api/effect/CustomEffect.kt`
- Test: `arc-api/src/test/kotlin/dev/arc/api/effect/CustomEffectEngineTest.kt`

- [ ] **Step 1: Add failing builder validation tests**

Test the internal pure builder conversion helpers for positive default duration,
non-negative amplifier, positive tick interval, and ceiling conversion from
`Duration` to ticks:

```kotlin
assertEquals(2, 51.milliseconds.toEffectTicks())
assertFailsWith<IllegalArgumentException> { Duration.ZERO.toEffectTicks() }
```

- [ ] **Step 2: Run focused tests and verify RED**

Expected: compilation fails because `toEffectTicks` and builder validation are
not implemented.

- [ ] **Step 3: Implement immutable definitions and Bukkit registry**

Implement these public entry points:

```kotlin
public fun Plugin.customEffects(block: CustomEffectRegistry.() -> Unit): CustomEffectRegistry
public fun Plugin.customEffectRegistry(): CustomEffectRegistry
public fun CustomEffectRegistry.effects(block: CustomEffectRegistry.() -> Unit)
public fun CustomEffectRegistry.effect(id: String, block: CustomEffectBuilder.() -> Unit): CustomEffect
public operator fun CustomEffectRegistry.get(id: String): CustomEffect
```

`CustomEffectBuilder` exposes `visual`, defaults, policy, `tickInterval`, and
callbacks. `CustomEffectRegistry` validates duplicate keys, requires the primary
server thread for mutation, owns one `BukkitTask`, isolates callback failures,
reconciles shared visual effect ownership, and implements idempotent `close()`.

Keep compatibility overloads for `define`, integer tick application, and player
extensions that accept an explicit registry.

- [ ] **Step 4: Run focused tests and compile**

Run:

```powershell
rtk .\gradlew.bat :arc-api:test --tests dev.arc.api.effect.CustomEffectEngineTest :arc-api:compileKotlin --no-daemon
```

Expected: tests and Kotlin compilation pass.

### Task 3: Documentation and Full Verification

**Files:**
- Modify: `arc-api/README.md`
- Modify: `ARC_IMPLEMENTATION.md`

- [ ] **Step 1: Document the API**

Add a `Custom effects` row and example to `arc-api/README.md`. Add a dated
section to `ARC_IMPLEMENTATION.md` listing the DSL, policies, lifecycle,
test coverage, and the limitation that independently applied potion effects
cannot be restored reliably.

- [ ] **Step 2: Run formatting checks**

Run:

```powershell
rtk git diff --check
```

Expected: no whitespace errors.

- [ ] **Step 3: Run full verification**

Run:

```powershell
rtk .\gradlew.bat :arc-api:test :arc-api:compileKotlin --no-daemon
```

Expected: `BUILD SUCCESSFUL` with zero failing tests.

- [ ] **Step 4: Review the final diff**

Run:

```powershell
rtk git diff -- arc-api/src/main/kotlin/dev/arc/api/effect arc-api/src/test/kotlin/dev/arc/api/effect arc-api/README.md ARC_IMPLEMENTATION.md
```

Confirm the diff contains only the designed registry work and preserves
unrelated untracked files.
