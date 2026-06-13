# PDC Schema DSL Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add reusable, validated, type-safe PDC field schemas for all Bukkit persistent data holders.

**Architecture:** Immutable field definitions and a pure schema registry handle validation and defaults. Thin extension operators delegate storage to Bukkit's `PersistentDataContainer`.

**Tech Stack:** Kotlin, Bukkit/Paper PDC API, JUnit 5 through `kotlin.test`, Gradle.

---

### Task 1: Field Model and DSL

**Files:**
- Create: `arc-api/src/test/kotlin/dev/arc/api/pdc/PdcSchemaTest.kt`
- Create: `arc-api/src/main/kotlin/dev/arc/api/pdc/PdcSchema.kt`

- [ ] Write failing tests for built-in fields, duplicate names, defaults,
  validation, and schema lookup.
- [ ] Run focused tests and confirm missing API failures.
- [ ] Implement `PdcField`, `PdcFieldBuilder`, `PdcSchema`, built-in field
  factories, custom types, and plugin/namespace entry points.
- [ ] Run focused tests and confirm all tests pass.

### Task 2: Holder Operations and Documentation

**Files:**
- Modify: `arc-api/src/main/kotlin/dev/arc/api/pdc/PdcSchema.kt`
- Modify: `arc-api/README.md`
- Modify: `ARC_IMPLEMENTATION.md`

- [ ] Add holder get/set/remove/contains/getOrPut/mutate operations.
- [ ] Document schema usage and compatibility.
- [ ] Run focused tests, integrated arc-api compilation, and diff checks.
