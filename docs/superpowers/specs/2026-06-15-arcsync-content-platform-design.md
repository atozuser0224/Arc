# ArcSync Content Platform Design

## Purpose

ArcSync turns Arc Bucket into a server-driven content platform. A player installs
one Arc client mod and can join any Arc server. Each server supplies its own
items, blocks, furniture, assets, translations, recipes, GUI descriptions, and
animations without distributing executable client code.

The platform must also support unmodified clients. They receive generated
resource-pack assets and vanilla fallback representations while the server keeps
the authoritative custom content identity and behavior.

## Product Principles

- Content is identified by stable `namespace:path` IDs.
- Arc servers work without manual ArcSync configuration.
- Registered content automatically appears in a server-specific creative tab.
- Server content never downloads JVM bytecode, native libraries, or executable
  mod JARs to clients.
- Plugin API definitions and data-pack-style JSON/YAML definitions compile to
  the same immutable runtime model.
- Unknown or temporarily unavailable content remains preserved in world data.
- Arc client capabilities improve presentation, not server authority.
- Expensive content work is measured, budgeted, and kept off the tick thread
  where Minecraft thread rules permit it.

## Platform Layers

### Content Core

`ContentRegistry` owns all active content definitions. Plugins register content
through Kotlin/Java APIs, while file packs are discovered from an Arc content
directory. Both inputs are validated and compiled into `CompiledContentPack`.

The first content types are:

- items
- blocks
- furniture
- recipes
- creative tabs
- client assets

Mobs, biomes, and dimensions are later extensions because they require broader
registry lifecycle and world-generation guarantees.

Each definition declares its vanilla fallback. Missing optional presentation
data uses a visible Arc missing-content model. Missing behavioral dependencies
disable only the affected definition unless the pack marks it as required.

### NMS Platform

Public API types never expose version-specific NMS classes. `arc-api` defines
small backend contracts; `arc-server` provides the Minecraft 1.21.4
implementations.

The platform contracts cover:

- connection capability discovery
- custom payload transport
- vanilla packet representation rewriting
- chunk custom-data attachment
- item stack identity encoding
- block collision, interaction, and state hooks
- creative inventory synchronization

Backends publish explicit capability flags. Unsupported operations fail during
content compilation instead of failing after a player joins.

### ArcSync Protocol

During configuration/login, the client sends:

- protocol version
- supported feature flags
- cached server identity
- cached manifest hash

The server responds with a signed manifest containing pack IDs, content hashes,
asset hashes, sizes, required features, and fallback policy. Only missing or
changed blobs are transferred. Blobs are content-addressed and may be resumed.

After validation, the client activates a server-isolated content session and
builds the creative catalog. Leaving the server deactivates that session.
Joining another server activates that server's cached content only after its
manifest is verified.

### Vanilla Client Adapter

Arc identifies clients that did not negotiate ArcSync and selects vanilla
fallback representations.

- custom items use vanilla base items plus Arc identity data and generated
  resource-pack model mappings
- custom blocks are rewritten to declared vanilla block states
- furniture uses an anchor plus vanilla display/entity representations
- unsupported custom GUI descriptions fall back to Bukkit inventory UI

Fallbacks never replace authoritative server state. Incoming interaction packets
are resolved against Arc content data before behavior runs.

## Zero-Configuration Discovery

Arc starts the content platform automatically. No feature toggle is required for
the core registry and catalog.

Plugins may register content during their normal enable lifecycle. Arc also
scans `arc-content/` for packs. The registry automatically generates:

- a server creative tab
- plugin or pack groupings
- searchable item and block entries
- recipe-book display metadata
- `/give` identifiers and completion data
- ArcSync manifest entries
- vanilla resource-pack entries

Operators may override names, icons, ordering, size limits, and connection
policy, but defaults are complete and usable.

## Content Model

All definitions contain:

- stable namespaced ID
- owning pack ID and version
- content type
- schema version
- required/optional status
- fallback representation
- translatable display metadata
- asset references
- deterministic content hash

Item definitions additionally describe stack size, durability, tool and combat
properties, food properties, and behavior bindings.

Block definitions describe hardness, blast resistance, tool requirements,
collision and outline shapes, luminance, state properties, placement rules,
drops, and behavior bindings.

Furniture definitions describe anchor strategy, orientation, seats, interaction
boxes, render parts, and optional multi-block occupancy.

Behavior is selected from Arc-owned declarative actions or implemented on the
server by a plugin callback. Clients receive presentation metadata only. They
never receive authoritative behavior code.

## Creative Catalog

Arc generates one server tab by default, named from the server brand. Content is
grouped by owning pack and sorted by explicit order followed by namespaced ID.

Players in creative mode can take entries exactly as they do from a normal
modded creative tab. No OP-only restriction is added. Survival acquisition is
still controlled by recipes, drops, commands, and plugin logic.

Catalog revisions are immutable and hash-addressed. A content reload builds a
new revision and swaps it atomically after validation.

## World Persistence

Custom blocks are not inserted directly into vanilla block-state numeric IDs.
Doing so would make vanilla clients unable to decode chunk packets and would
make saves fragile across registry changes.

Instead, each chunk stores an `ArcPalette` attachment:

- a palette maps compact local integers to namespaced content IDs
- occupied positions store local ID plus compact state data
- unknown IDs and raw state bytes are preserved
- dirty sections are serialized independently
- content removal creates dormant entries rather than deleting world data
- re-registering the same ID restores behavior and presentation

The vanilla block state at each occupied position is a server-selected carrier
or fallback state. Arc game logic resolves placement, breaking, collision,
explosion, piston, fluid, lighting, and drops through the palette entry.

Furniture persistence uses the same stable IDs plus an instance record for
orientation, seats, render parts, and occupied positions.

## Security

Server-supplied executable code is prohibited.

Accepted assets are allowlisted formats such as PNG, OGG, JSON, and language
files. Validation rejects:

- absolute paths and parent traversal
- symbolic links
- archive recursion
- executable and native formats
- individual or aggregate size excess
- decompression ratio excess
- hash or signature mismatch
- unsupported schema or protocol versions

Each server has an isolated cache with a configurable quota and LRU cleanup.
The server creates an Ed25519 signing key on first start. Clients pin the public
key to the server identity and require a trust decision when it changes.

## Error Handling

Compilation returns structured diagnostics with pack ID, content ID, field path,
severity, and message. Invalid optional definitions are quarantined. Invalid
required definitions reject the pack revision.

Required synchronization failure rejects login with an actionable message.
Optional asset failure activates the missing-content presentation. Interrupted
downloads remain resumable only after their received chunks pass hash checks.

Content reload is transactional:

1. discover inputs
2. parse and validate
3. compile runtime and client artifacts
4. build catalog and manifest
5. atomically publish revision
6. notify compatible clients

The previous revision remains active if any required step fails.

## Performance Core

Manifest building, hashing, compression, file IO, and palette serialization run
off the tick thread. Applying Minecraft world mutations remains on the owning
server thread.

The platform includes:

- content-addressed immutable asset cache
- manifest and packet representation caches
- chunk-section dirty tracking
- compact palette/state encoding
- bounded transfer queues and per-player bandwidth limits
- per-content tick budgets
- slow-handler metrics and automatic throttling
- revision-level counters for compilation, transfer, and runtime cost

Optimization is evidence-driven. Arc records timings and allocation estimates
before enabling throttling or changing update frequency.

## Mod Bridge Evolution

The Arc client mod initially implements ArcSync transport, asset activation,
custom rendering, creative tabs, and declarative UI. Future client features use
negotiated capability flags and remain backward compatible.

The protocol can later expose declarative particles, shaders, HUD widgets, and
animations. Server-specific executable mods remain outside ArcSync.

## Testing

Unit tests cover:

- namespaced ID and schema validation
- deterministic compilation and hashing
- duplicate ownership and priority resolution
- asset path and size security
- manifest diff calculation
- signature creation and verification
- palette encoding, unknown-ID preservation, and round trips
- catalog grouping and deterministic ordering

Integration tests cover:

- plugin registration and file-pack discovery
- transactional reload failure
- client capability negotiation
- interrupted transfer resume
- vanilla fallback conversion
- chunk save, restart, plugin removal, and restoration

Server QA covers placement, breaking, recipes, creative acquisition, explosions,
pistons, fluids, furniture interaction, reconnects, and mixed Arc/vanilla
clients. Benchmarks cover large manifests, mass block placement, chunk
serialization, and concurrent joins.

## Delivery Sequence

1. Content model, compiler, diagnostics, and automatic creative catalog
2. ArcSync protocol model, manifest signing, diffing, and bounded transport
3. Item identity and vanilla fallback adapter
4. ArcPalette persistence and custom block lifecycle
5. Furniture instances and declarative GUI/animation descriptors
6. Arc client mod implementation
7. Runtime budgets, metrics, and adaptive throttling

Each stage ships with a stable public contract and tests. Later stages extend
capabilities without changing stored namespaced identities.
