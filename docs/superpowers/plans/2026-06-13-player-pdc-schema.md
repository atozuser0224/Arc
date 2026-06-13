# Player PDC Schema DSL Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend typed player PDC handles with Kotlin schema declarations, validation, defaults, and lazy key migration.

**Architecture:** Immutable `PlayerStore` definitions contain policy metadata. Existing Player extension functions enforce policy while delegating storage to Bukkit's PDC.

**Tech Stack:** Kotlin, Bukkit/Paper PDC API, JUnit 5 through `kotlin.test`, Java dynamic proxies.

---

### Task 1: Policy and Migration

**Files:**
- Create: `arc-api/src/test/kotlin/dev/arc/api/player/PlayerPersistentDataTest.kt`
- Modify: `arc-api/src/main/kotlin/dev/arc/api/player/PlayerPersistentData.kt`

- [ ] Write failing tests for validation, defaults, operators, mutation,
  increment, migration, and current-key precedence.
- [ ] Run focused tests and verify the richer API is missing.
- [ ] Implement immutable policy fields and migration-aware read/write helpers.
- [ ] Run focused tests and verify behavior.

### Task 2: Schema DSL and Documentation

**Files:**
- Modify: `arc-api/src/main/kotlin/dev/arc/api/player/PlayerPersistentData.kt`
- Modify: `arc-api/README.md`
- Modify: `ARC_IMPLEMENTATION.md`

- [ ] Add `playerDataSchema` plus typed int, long, double, string, boolean, and
  byte-array declarations with duplicate rejection.
- [ ] Add README usage and implementation notes.
- [ ] Run focused tests, integrated compilation, and diff checks.
