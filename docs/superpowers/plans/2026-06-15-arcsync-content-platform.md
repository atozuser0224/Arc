# ArcSync Content Platform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the server-driven Arc content foundation, secure synchronization protocol, persistent custom-block palette, vanilla fallback contracts, and Arc client integration path.

**Architecture:** Pure Kotlin models in `arc-api` compile plugin or file-pack definitions into immutable revisions. ArcSync signs deterministic manifests and computes content-addressed transfer plans. ArcPalette persists namespaced block identities independently of vanilla numeric registries; version-specific `arc-server` backends later attach these contracts to Minecraft networking and chunks.

**Tech Stack:** Kotlin/JVM, Java 21 crypto, Gson, Bukkit/Paper API, JUnit 5, Gradle

---

### Task 1: Content identity, definitions, and diagnostics

**Files:**
- Create: `arc-api/src/main/kotlin/dev/arc/api/content/ContentId.kt`
- Create: `arc-api/src/main/kotlin/dev/arc/api/content/ContentDefinition.kt`
- Create: `arc-api/src/main/kotlin/dev/arc/api/content/ContentDiagnostic.kt`
- Test: `arc-api/src/test/kotlin/dev/arc/api/content/ContentModelTest.kt`

- [ ] **Step 1: Write failing identity and validation tests**

```kotlin
@Test
fun `content ids normalize and reject unsafe values`() {
    assertEquals("arc:ruby_sword", ContentId.parse("arc:ruby_sword").toString())
    assertFailsWith<IllegalArgumentException> { ContentId.parse("Arc:ruby") }
    assertFailsWith<IllegalArgumentException> { ContentId.parse("arc:../ruby") }
}

@Test
fun `block fallback and item limits are validated`() {
    val invalid = ContentPackBuilder(ContentId.parse("arc:core"), "1").apply {
        item("oversized") { maxStackSize = 65 }
        block("ruby") { hardness = -1f; fallback = "minecraft:redstone_block" }
    }.build()
    val result = ContentCompiler.compile(listOf(invalid))
    assertTrue(result.diagnostics.count { it.severity == DiagnosticSeverity.ERROR } >= 2)
}
```

- [ ] **Step 2: Run the focused tests and confirm missing symbols fail**

Run: `.\gradlew.bat :arc-api:test --tests "dev.arc.api.content.ContentModelTest" --no-daemon`

Expected: FAIL because `ContentId` and content definitions do not exist.

- [ ] **Step 3: Implement strict IDs and immutable definitions**

Implement `ContentId(namespace, path)` with lowercase Minecraft-compatible
validation, `ContentType`, `ContentDefinition`, `ItemDefinition`,
`BlockDefinition`, `FurnitureDefinition`, `RecipeDefinition`, fallback models,
and structured diagnostics.

- [ ] **Step 4: Run focused tests**

Run: `.\gradlew.bat :arc-api:test --tests "dev.arc.api.content.ContentModelTest" --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add arc-api/src/main/kotlin/dev/arc/api/content arc-api/src/test/kotlin/dev/arc/api/content
git commit -m "feat(content): add content model"
```

### Task 2: Builder API, deterministic compiler, and creative catalog

**Files:**
- Create: `arc-api/src/main/kotlin/dev/arc/api/content/ContentPack.kt`
- Create: `arc-api/src/main/kotlin/dev/arc/api/content/ContentCompiler.kt`
- Create: `arc-api/src/main/kotlin/dev/arc/api/content/CreativeCatalog.kt`
- Create: `arc-api/src/main/kotlin/dev/arc/api/content/ContentRegistry.kt`
- Test: `arc-api/src/test/kotlin/dev/arc/api/content/ContentCompilerTest.kt`

- [ ] **Step 1: Write failing compilation tests**

```kotlin
@Test
fun `compiler is deterministic and generates default creative groups`() {
    val pack = contentPack("magic", "1.0.0") {
        item("wand") { fallback = "minecraft:blaze_rod"; order = 20 }
        block("altar") { fallback = "minecraft:enchanting_table"; order = 10 }
    }
    val first = ContentCompiler.compile(listOf(pack)).revision!!
    val second = ContentCompiler.compile(listOf(pack)).revision!!
    assertEquals(first.hash, second.hash)
    assertEquals(listOf("magic:altar", "magic:wand"), first.catalog.entries.map { it.id.toString() })
}

@Test
fun `required duplicate ids reject revision transactionally`() {
    val a = contentPack("a", "1") { item("shared") { id = ContentId.parse("arc:shared") } }
    val b = contentPack("b", "1") { item("shared") { id = ContentId.parse("arc:shared") } }
    assertNull(ContentCompiler.compile(listOf(a, b)).revision)
}
```

- [ ] **Step 2: Run tests and confirm failure**

Run: `.\gradlew.bat :arc-api:test --tests "dev.arc.api.content.ContentCompilerTest" --no-daemon`

Expected: FAIL because compiler and catalog do not exist.

- [ ] **Step 3: Implement builder, canonical hashing, and atomic registry**

Use sorted IDs and explicit UTF-8 canonical fields for SHA-256 hashing. Build
the default server creative tab and per-pack groups. `ContentRegistry.publish`
must retain the previous revision when compilation reports required errors.

- [ ] **Step 4: Run content tests**

Run: `.\gradlew.bat :arc-api:test --tests "dev.arc.api.content.*" --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add arc-api/src/main/kotlin/dev/arc/api/content arc-api/src/test/kotlin/dev/arc/api/content
git commit -m "feat(content): compile content revisions"
```

### Task 3: Secure asset validation and file-pack discovery

**Files:**
- Create: `arc-api/src/main/kotlin/dev/arc/api/content/asset/ContentAsset.kt`
- Create: `arc-api/src/main/kotlin/dev/arc/api/content/asset/AssetPolicy.kt`
- Create: `arc-api/src/main/kotlin/dev/arc/api/content/pack/FileContentPackLoader.kt`
- Test: `arc-api/src/test/kotlin/dev/arc/api/content/asset/AssetPolicyTest.kt`
- Test: `arc-api/src/test/kotlin/dev/arc/api/content/pack/FileContentPackLoaderTest.kt`

- [ ] **Step 1: Write failing security tests**

```kotlin
@Test
fun `policy rejects traversal executable and oversized assets`() {
    val policy = AssetPolicy(maxAssetBytes = 16, maxPackBytes = 32)
    assertFalse(policy.validate("../secret.png", byteArrayOf()).accepted)
    assertFalse(policy.validate("mods/payload.jar", byteArrayOf()).accepted)
    assertFalse(policy.validate("textures/large.png", ByteArray(17)).accepted)
}
```

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `.\gradlew.bat :arc-api:test --tests "dev.arc.api.content.asset.*" --no-daemon`

Expected: FAIL because asset policy does not exist.

- [ ] **Step 3: Implement allowlist, path containment, quotas, and JSON loader**

Allow `.png`, `.ogg`, `.json`, `.mcmeta`, and language files. Resolve paths
against the pack root and reject any path outside it. Parse `pack.json` with
Gson into the same builder model used by plugins.

- [ ] **Step 4: Run asset and loader tests**

Run: `.\gradlew.bat :arc-api:test --tests "dev.arc.api.content.asset.*" --tests "dev.arc.api.content.pack.*" --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add arc-api/src/main/kotlin/dev/arc/api/content arc-api/src/test/kotlin/dev/arc/api/content
git commit -m "feat(content): load secure file packs"
```

### Task 4: ArcSync manifest, signing, and transfer planning

**Files:**
- Create: `arc-api/src/main/kotlin/dev/arc/api/sync/ArcSyncProtocol.kt`
- Create: `arc-api/src/main/kotlin/dev/arc/api/sync/ArcSyncManifest.kt`
- Create: `arc-api/src/main/kotlin/dev/arc/api/sync/ManifestSigner.kt`
- Create: `arc-api/src/main/kotlin/dev/arc/api/sync/TransferPlan.kt`
- Test: `arc-api/src/test/kotlin/dev/arc/api/sync/ArcSyncManifestTest.kt`

- [ ] **Step 1: Write failing deterministic signing and diff tests**

```kotlin
@Test
fun `manifest signature verifies and tampering fails`() {
    val keys = ManifestSigner.generate()
    val signed = ManifestSigner.sign(sampleManifest(), keys.private)
    assertTrue(ManifestSigner.verify(signed, keys.public))
    assertFalse(ManifestSigner.verify(signed.copy(manifest = signed.manifest.copy(revision = "tampered")), keys.public))
}

@Test
fun `transfer plan includes only missing hashes`() {
    val plan = TransferPlan.create(sampleManifest(), setOf("already-cached"))
    assertEquals(setOf("missing-hash"), plan.blobs.map { it.sha256 }.toSet())
}
```

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `.\gradlew.bat :arc-api:test --tests "dev.arc.api.sync.*" --no-daemon`

Expected: FAIL because ArcSync types do not exist.

- [ ] **Step 3: Implement protocol negotiation and Ed25519 signatures**

Use Java 21 `KeyPairGenerator.getInstance("Ed25519")` and
`Signature.getInstance("Ed25519")`. Sign canonical manifest bytes, not Gson
object output. Define protocol version, feature flags, client hello, server
hello, blob descriptors, and bounded transfer chunks.

- [ ] **Step 4: Run sync tests**

Run: `.\gradlew.bat :arc-api:test --tests "dev.arc.api.sync.*" --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add arc-api/src/main/kotlin/dev/arc/api/sync arc-api/src/test/kotlin/dev/arc/api/sync
git commit -m "feat(sync): add signed manifest protocol"
```

### Task 5: ArcSync packet codec and bounded session state

**Files:**
- Create: `arc-api/src/main/kotlin/dev/arc/api/sync/ArcSyncCodec.kt`
- Create: `arc-api/src/main/kotlin/dev/arc/api/sync/ArcSyncSession.kt`
- Modify: `arc-api/src/main/kotlin/dev/arc/api/channel/ModPacketBuffer.kt`
- Test: `arc-api/src/test/kotlin/dev/arc/api/sync/ArcSyncCodecTest.kt`
- Test: `arc-api/src/test/kotlin/dev/arc/api/sync/ArcSyncSessionTest.kt`

- [ ] **Step 1: Write failing codec and quota tests**

```kotlin
@Test
fun `codec round trips client hello with bounded collections`() {
    val hello = ClientHello(ArcSyncProtocol.VERSION, setOf(ArcSyncFeature.CREATIVE_TABS), null)
    assertEquals(hello, ArcSyncCodec.decodeClientHello(ArcSyncCodec.encode(hello)))
}

@Test
fun `session refuses chunks beyond declared blob size`() {
    val session = ArcSyncSession(maxInFlightBytes = 32)
    assertFailsWith<IllegalStateException> { session.accept("hash", 0, ByteArray(33), declaredSize = 33) }
}
```

- [ ] **Step 2: Run sync tests and confirm failure**

Run: `.\gradlew.bat :arc-api:test --tests "dev.arc.api.sync.*" --no-daemon`

Expected: FAIL because codec and session do not exist.

- [ ] **Step 3: Implement codecs and bounded state machine**

Extend `ModPacketBuffer` only with bounded enum/set helpers needed by ArcSync.
Reject unknown packet kinds, duplicate completion, negative offsets, excess
in-flight bytes, and chunks outside declared bounds.

- [ ] **Step 4: Run channel and sync tests**

Run: `.\gradlew.bat :arc-api:test --tests "dev.arc.api.channel.*" --tests "dev.arc.api.sync.*" --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add arc-api/src/main/kotlin/dev/arc/api/channel arc-api/src/main/kotlin/dev/arc/api/sync arc-api/src/test/kotlin/dev/arc/api
git commit -m "feat(sync): add bounded transfer sessions"
```

### Task 6: ArcPalette codec and dormant-content preservation

**Files:**
- Create: `arc-api/src/main/kotlin/dev/arc/api/palette/ArcBlockState.kt`
- Create: `arc-api/src/main/kotlin/dev/arc/api/palette/ArcPalette.kt`
- Create: `arc-api/src/main/kotlin/dev/arc/api/palette/ArcPaletteCodec.kt`
- Test: `arc-api/src/test/kotlin/dev/arc/api/palette/ArcPaletteTest.kt`

- [ ] **Step 1: Write failing round-trip and unknown-ID tests**

```kotlin
@Test
fun `palette round trip preserves ids states and unknown bytes`() {
    val palette = ArcPalette().apply {
        set(1, 64, 2, ArcBlockState(ContentId.parse("magic:altar"), byteArrayOf(3, 9)))
    }
    val restored = ArcPaletteCodec.decode(ArcPaletteCodec.encode(palette))
    assertContentEquals(byteArrayOf(3, 9), restored.get(1, 64, 2)!!.state)
}
```

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `.\gradlew.bat :arc-api:test --tests "dev.arc.api.palette.*" --no-daemon`

Expected: FAIL because palette types do not exist.

- [ ] **Step 3: Implement compact deterministic palette encoding**

Validate local X/Z `0..15`, Y bounds supplied by caller, unique palette IDs,
bounded state bytes, and codec schema version. Preserve raw state bytes without
requiring a currently registered definition.

- [ ] **Step 4: Run palette tests**

Run: `.\gradlew.bat :arc-api:test --tests "dev.arc.api.palette.*" --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add arc-api/src/main/kotlin/dev/arc/api/palette arc-api/src/test/kotlin/dev/arc/api/palette
git commit -m "feat(content): add ArcPalette codec"
```

### Task 7: Item identity and vanilla fallback contracts

**Files:**
- Create: `arc-api/src/main/kotlin/dev/arc/api/content/runtime/ContentRuntimeBackend.kt`
- Create: `arc-api/src/main/kotlin/dev/arc/api/content/runtime/VanillaFallback.kt`
- Create: `arc-api/src/main/kotlin/dev/arc/api/content/runtime/ContentItemIdentity.kt`
- Test: `arc-api/src/test/kotlin/dev/arc/api/content/runtime/VanillaFallbackTest.kt`

- [ ] **Step 1: Write failing fallback tests**

```kotlin
@Test
fun `item identity survives fallback conversion`() {
    val encoded = ContentItemIdentity.encode(ContentId.parse("magic:wand"), mapOf("charge" to "3"))
    val decoded = ContentItemIdentity.decode(encoded)
    assertEquals(ContentId.parse("magic:wand"), decoded.id)
    assertEquals("3", decoded.properties["charge"])
}
```

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `.\gradlew.bat :arc-api:test --tests "dev.arc.api.content.runtime.*" --no-daemon`

Expected: FAIL because runtime contracts do not exist.

- [ ] **Step 3: Implement stable identity envelope and backend capabilities**

Define `ContentRuntimeBackend`, `ContentCapability`, client kind detection,
creative sync, item conversion, block representation, and palette persistence
hooks. Identity serialization must be versioned and length bounded.

- [ ] **Step 4: Run runtime tests**

Run: `.\gradlew.bat :arc-api:test --tests "dev.arc.api.content.runtime.*" --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add arc-api/src/main/kotlin/dev/arc/api/content/runtime arc-api/src/test/kotlin/dev/arc/api/content/runtime
git commit -m "feat(content): add runtime fallback contracts"
```

### Task 8: Bukkit service and automatic startup

**Files:**
- Create: `arc-api/src/main/kotlin/dev/arc/api/content/ArcContent.kt`
- Create: `arc-api/src/main/kotlin/dev/arc/api/content/PluginContentDsl.kt`
- Modify: Arc bootstrap location discovered from `arc-api` startup wiring
- Test: `arc-api/src/test/kotlin/dev/arc/api/content/ContentRegistryTest.kt`

- [ ] **Step 1: Write failing transactional registry tests**

```kotlin
@Test
fun `failed reload retains previous revision`() {
    val registry = ContentRegistry()
    assertTrue(registry.publish(listOf(validPack())).accepted)
    val previous = registry.current
    assertFalse(registry.publish(listOf(invalidRequiredPack())).accepted)
    assertSame(previous, registry.current)
}
```

- [ ] **Step 2: Run test and confirm failure**

Run: `.\gradlew.bat :arc-api:test --tests "dev.arc.api.content.ContentRegistryTest" --no-daemon`

Expected: FAIL until lifecycle service exists.

- [ ] **Step 3: Implement global service and plugin DSL**

Expose `Plugin.arcContent {}` and a global read-only current revision. Discover
`arc-content/` at server startup, publish a revision automatically, and retain
operator overrides as optional configuration.

- [ ] **Step 4: Run all arc-api tests**

Run: `.\gradlew.bat :arc-api:test --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add arc-api
git commit -m "feat(content): start content service automatically"
```

### Task 9: Minecraft 1.21.4 backend integration

**Files:**
- Create: `arc-server/src/main/kotlin/dev/arc/server/content/ArcContentBackend.kt`
- Create: `arc-server/src/main/kotlin/dev/arc/server/content/ArcClientTracker.kt`
- Create: `arc-server/src/main/resources/META-INF/services/dev.arc.api.content.runtime.ContentRuntimeBackend`
- Modify: relevant Paper patch or bootstrap source located during implementation
- Test: `arc-server/src/test/kotlin/dev/arc/server/content/ArcContentBackendTest.kt`

- [ ] **Step 1: Add backend capability and lifecycle tests**

Test ServiceLoader discovery, vanilla fallback selection, ArcSync channel
negotiation, and safe no-op behavior when a player connection closes.

- [ ] **Step 2: Run tests and confirm failure**

Run: `.\gradlew.bat :arc-server:test --tests "dev.arc.server.content.*" --no-daemon`

Expected: FAIL before backend implementation.

- [ ] **Step 3: Implement backend and NMS hook points**

Use explicit 1.21.4 mappings where available and isolate reflection in existing
`Reflect` helpers. Install client tracking during connection setup, expose
creative catalog synchronization, and attach palette bytes to chunk persistent
data without changing vanilla registry numeric IDs.

- [ ] **Step 4: Run server tests and compile**

Run: `.\gradlew.bat :arc-server:test :arc-server:classes --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add arc-server
git commit -m "feat(server): integrate Arc content backend"
```

### Task 10: Arc client mod module

**Files:**
- Create: `arc-client/settings.gradle.kts`
- Create: `arc-client/build.gradle.kts`
- Create: `arc-client/src/main/kotlin/dev/arc/client/ArcClient.kt`
- Create: `arc-client/src/main/kotlin/dev/arc/client/sync/ArcSyncClient.kt`
- Create: `arc-client/src/main/kotlin/dev/arc/client/content/ClientContentSession.kt`
- Create: `arc-client/src/main/kotlin/dev/arc/client/content/ArcCreativeTab.kt`
- Create: `arc-client/src/main/resources/fabric.mod.json`
- Test: `arc-client/src/test/kotlin/dev/arc/client/sync/ArcSyncClientTest.kt`

- [ ] **Step 1: Add protocol fixture tests shared with server**

Use fixed binary fixtures for client hello, signed manifest, transfer request,
chunk, completion, and failure packets. Both modules must decode the same bytes.

- [ ] **Step 2: Run client tests and confirm failure**

Run: `arc-client\gradlew.bat test --no-daemon`

Expected: FAIL before client implementation.

- [ ] **Step 3: Implement Fabric client**

Negotiate ArcSync, pin server keys, maintain isolated content-addressed caches,
activate assets for the current connection, generate the server creative tab,
and clear the active session on disconnect. Do not load server code.

- [ ] **Step 4: Run client tests and build**

Run: `arc-client\gradlew.bat test build --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add arc-client
git commit -m "feat(client): add ArcSync client mod"
```

### Task 11: Runtime budgets, metrics, and end-to-end verification

**Files:**
- Create: `arc-api/src/main/kotlin/dev/arc/api/content/runtime/ContentBudget.kt`
- Create: `arc-api/src/main/kotlin/dev/arc/api/content/runtime/ContentMetrics.kt`
- Create: `arc-api/src/test/kotlin/dev/arc/api/content/runtime/ContentBudgetTest.kt`
- Create: `docs/arcsync-content.md`

- [ ] **Step 1: Add budget behavior tests**

Verify rolling handler cost, bounded queues, throttling after repeated budget
excess, recovery after a quiet window, and per-content metric isolation.

- [ ] **Step 2: Implement budgets and metrics**

Metrics include compile duration, revision size, cache hit rate, transferred
bytes, palette encode duration, handler count, handler time, and throttles.

- [ ] **Step 3: Run complete verification**

Run: `.\gradlew.bat :arc-api:test :arc-server:test :arc-server:classes --no-daemon`

Expected: PASS.

Run: `arc-client\gradlew.bat test build --no-daemon`

Expected: PASS.

Run: `rtk git diff --check`

Expected: no output and exit code 0.

- [ ] **Step 4: Document plugin API, file-pack schema, security model, and client install**

Documentation must include a complete item/block example, generated creative
tab behavior, vanilla fallback behavior, key pinning, cache location, limits,
and migration rules for removed content.

- [ ] **Step 5: Commit**

```bash
git add arc-api arc-server arc-client docs
git commit -m "feat(content): complete ArcSync platform"
```
