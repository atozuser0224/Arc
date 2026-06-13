# Datapack DSL Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a safe, testable Minecraft 1.21.4 datapack Kotlin DSL with deterministic deployment.

**Architecture:** A pure content model handles validation, resource generation, and filesystem deployment. A thin Bukkit adapter handles world-container selection, enabling, and reload scheduling.

**Tech Stack:** Kotlin, Gson, Bukkit/Paper API, JUnit 5 through `kotlin.test`, Gradle.

---

### Task 1: Resource Model and JSON Builders

**Files:**
- Create: `arc-api/src/test/kotlin/dev/arc/api/datapack/DatapackContentTest.kt`
- Replace: `arc-api/src/main/kotlin/dev/arc/api/datapack/DatapackMaker.kt`

- [ ] Write failing tests for resource IDs, escaping, duplicate paths, 1.21.4
  tag paths, function output, and invalid builder ranges.
- [ ] Run focused tests and verify missing model/API failures.
- [ ] Implement `ResourceId`, `DatapackContent`, Gson-based biome, loot, and tag
  builders plus raw recipe, advancement, predicate, and function resources.
- [ ] Run focused tests and verify all model tests pass.

### Task 2: Safe Deployment

**Files:**
- Test: `arc-api/src/test/kotlin/dev/arc/api/datapack/DatapackContentTest.kt`
- Modify: `arc-api/src/main/kotlin/dev/arc/api/datapack/DatapackMaker.kt`

- [ ] Write failing temporary-directory tests for pack layout, traversal
  rejection, stale generated-file cleanup, and unrelated-file preservation.
- [ ] Implement `deployTo`, UTF-8 writes, normalized containment checks, and
  `.arc-manifest`.
- [ ] Run focused tests and verify deployment behavior passes.

### Task 3: Bukkit Adapter and Documentation

**Files:**
- Modify: `arc-api/src/main/kotlin/dev/arc/api/datapack/DatapackMaker.kt`
- Modify: `arc-api/README.md`
- Modify: `ARC_IMPLEMENTATION.md`

- [ ] Preserve `Plugin.datapackMaker`, `deploy`, `enable`, and
  `deployAndReload` with the pure model underneath.
- [ ] Document examples, supported resource types, reload behavior, and safety.
- [ ] Run focused tests, integrated `arc-api` compilation, and diff checks.
