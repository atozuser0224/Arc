# Arc Bucket Rebrand Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebrand the distributable server as Arc Bucket and repair command tab completion across Arc's built-in and DSL commands.

**Architecture:** Keep upstream packages and legacy identifiers as compatibility
aliases while changing product metadata and all user-visible surfaces at their
sources. Centralize completion behavior in a testable resolver, centralize
startup branding in an Arc bootstrap utility, and verify with a real Paperclip
boot.

**Tech Stack:** Kotlin 2.0, Java 21, Bukkit/Paper command APIs, Paperweight,
JUnit 5, Gradle Kotlin DSL.

---

### Task 1: Command Completion Resolver

**Files:**
- Modify: `arc-api/src/main/kotlin/dev/arc/api/command/Commands.kt`
- Create: `arc-api/src/test/kotlin/dev/arc/api/command/CommandCompletionTest.kt`

- [ ] Write tests proving root handlers work beyond argument one, subcommands
  filter by permission, and filtering is case-insensitive.
- [ ] Run the focused test and confirm the current resolver fails.
- [ ] Extract one internal completion resolver used by both command registration
  overloads.
- [ ] Run the focused tests and the Arc control completion tests.

### Task 2: Built-In Arc Command Integration

**Files:**
- Modify: `arc-api/src/main/java/dev/arc/api/control/ArcControlCommand.kt`
- Modify: `arc-api/src/main/kotlin/dev/arc/api/ops/ArcOpsCommand.kt`
- Modify: `sample-pack/showcase-plugin/src/main/kotlin/dev/arc/sample/ArcShowcasePlugin.kt`

- [ ] Add focused tests for `/arc plugin`, `/arc config`, and mixed-case input.
- [ ] Normalize completion dispatch to lowercase without losing the typed prefix.
- [ ] Add explicit showcase command candidates.
- [ ] Verify the focused command suite.

### Task 3: Arc Build Identity And Startup Banner

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `leaf-server/build.gradle.kts`
- Modify: `paper-server/src/main/java/io/papermc/paper/ServerBuildInfoImpl.java`
- Modify: `paper-server/src/main/java/io/papermc/paper/PaperBootstrap.java`
- Create: `paper-server/src/main/java/io/papermc/paper/ArcBranding.java`
- Modify corresponding patch files under `leaf-api/paper-patches` and
  `leaf-server/paper-patches`.

- [ ] Add tests for Arc brand constants and banner content.
- [ ] Change manifest identity to `Arc`, `arc:arc`, and Arc vendor metadata.
- [ ] Print the colored Arc Bucket banner before normal bootstrap output.
- [ ] Ensure Paperclip and Bundler files use `arc-*` names.

### Task 4: Runtime User-Visible Rebrand

**Files:**
- Modify product strings in `paper-server/src/main/java`
- Modify product strings in `leaf-server/src/main/java`
- Modify matching Paperweight patch files

- [ ] Replace console title, version fetcher, watchdog, metrics, replay metadata,
  built-in command descriptions, and runtime thread/logger names with Arc.
- [ ] Register `/arc` as the product command and `/leaf` as a compatibility
  alias without overriding Arc's existing command.
- [ ] Preserve upstream class and package names.
- [ ] Add or update tests around command registration and build info.

### Task 5: Configuration Compatibility

**Files:**
- Modify: `leaf-server/src/main/java/org/dreeam/leaf/config/LeafConfig.java`
- Modify: `leaf-server/src/main/java/org/dreeam/leaf/config/LeafGlobalConfig.java`
- Modify relevant config command/messages and patches

- [ ] Add migration tests for legacy Leaf configuration files.
- [ ] Use Arc names and headers for new configuration.
- [ ] Accept legacy Leaf paths as migration inputs and aliases.
- [ ] Verify no existing config data is overwritten.

### Task 6: README, CI, And Distribution

**Files:**
- Rewrite: `README.md`
- Modify: `arc-api/README.md`
- Modify: `arc-server/README.md`
- Modify: `ARC_IMPLEMENTATION.md`
- Modify: `ARC_NETWORK.md`
- Modify: `.github/workflows/*.yml`
- Modify: `.github/ISSUE_TEMPLATE/*.yml`
- Modify: `sample-pack/server/README.md`

- [ ] Rewrite public documentation around Arc Bucket.
- [ ] Rename workflow, artifact, release, and upload labels to Arc.
- [ ] Keep upstream projects in credits and technical compatibility notes.
- [ ] Scan public files for stale Leaf product branding.

### Task 7: Full Build And Boot Verification

**Files:**
- Update: `sample-pack/server/server.jar`
- Update: `sample-pack/server/plugins/ArcShowcase.jar`

- [ ] Run focused command and branding tests.
- [ ] Build the Arc-named Mojmap Paperclip JAR.
- [ ] Assemble the sample server pack from fresh outputs.
- [ ] Boot the server, capture the Arc ASCII banner and Arc version lines,
  exercise command completion, then stop cleanly.
- [ ] Run a final stale-brand scan and document compatibility-only matches.
