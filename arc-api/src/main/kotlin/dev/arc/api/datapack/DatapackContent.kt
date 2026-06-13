package dev.arc.api.datapack

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeLines
import kotlin.io.path.writeText

private val DATAPACK_GSON = Gson()
private val NAMESPACE_PATTERN = Regex("[a-z0-9._-]+")
private val RESOURCE_PATH_PATTERN = Regex("[a-z0-9/._-]+")
private val PACK_NAME_PATTERN = Regex("[a-z0-9._-]+")
private const val MANIFEST_FILE = ".arc-manifest"

/** Validated Minecraft resource location. */
public data class ResourceId(public val namespace: String, public val path: String) {
    init {
        require(namespace.matches(NAMESPACE_PATTERN)) { "Invalid resource namespace: $namespace" }
        require(path.matches(RESOURCE_PATH_PATTERN)) { "Invalid resource path: $path" }
        require(!path.startsWith('/') && !path.endsWith('/')) { "Resource path cannot start or end with '/': $path" }
        require(path.split('/').none { it == "." || it == ".." }) { "Resource path cannot traverse directories: $path" }
    }

    override fun toString(): String = "$namespace:$path"

    public companion object {
        @JvmStatic
        public fun parse(value: String): ResourceId {
            val colon = value.indexOf(':')
            require(colon > 0 && colon < value.lastIndex) {
                "Resource ID must be 'namespace:path', got: $value"
            }
            require(value.indexOf(':', colon + 1) == -1) { "Resource ID contains multiple colons: $value" }
            return ResourceId(value.substring(0, colon), value.substring(colon + 1))
        }
    }
}

@DslMarker
public annotation class DatapackDsl

/**
 * Bukkit-independent in-memory Minecraft datapack.
 *
 * Resources are immutable strings once inserted; duplicate paths fail fast.
 */
@DatapackDsl
public open class DatapackContent(
    public val name: String,
    public var packFormat: Int = 61,
) {
    private val entries = LinkedHashMap<String, String>()

    init {
        require(name.matches(PACK_NAME_PATTERN)) {
            "Invalid datapack name '$name'; use lowercase letters, numbers, '.', '_' or '-'"
        }
        require(packFormat > 0) { "packFormat must be positive" }
    }

    public val size: Int get() = entries.size
    public val paths: Set<String> get() = entries.keys.toSet()

    /** Add an arbitrary text resource relative to the pack root. */
    public fun raw(path: String, content: String) {
        put(normalizeRelativePath(path), content)
    }

    /** Add validated raw JSON. */
    public fun rawJson(path: String, json: String) {
        put(normalizeRelativePath(path), normalizeJson(json))
    }

    public fun biome(id: String, block: BiomeBuilder.() -> Unit) {
        val key = ResourceId.parse(id)
        put(resourcePath(key, "worldgen/biome"), BiomeBuilder().apply(block).build())
    }

    public fun lootTable(id: String, block: LootTableBuilder.() -> Unit) {
        val key = ResourceId.parse(id)
        put(resourcePath(key, "loot_table"), LootTableBuilder().apply(block).build())
    }

    public fun tag(registry: String, id: String, block: TagBuilder.() -> Unit) {
        val key = ResourceId.parse(id)
        val registryPath = normalizeTagRegistry(registry)
        put(resourcePath(key, "tags/$registryPath"), TagBuilder().apply(block).build())
    }

    public fun recipe(id: String, json: String): Unit =
        jsonResource("recipe", id, json)

    public fun advancement(id: String, json: String): Unit =
        jsonResource("advancement", id, json)

    public fun predicate(id: String, json: String): Unit =
        jsonResource("predicate", id, json)

    public fun function(id: String, block: FunctionBuilder.() -> Unit) {
        val key = ResourceId.parse(id)
        put(resourcePath(key, "function", ".mcfunction"), FunctionBuilder().apply(block).build())
    }

    public fun contains(path: String): Boolean = normalizeRelativePath(path) in entries

    public fun resource(path: String): String =
        entries[normalizeRelativePath(path)] ?: error("Datapack resource not found: $path")

    public fun resources(): Map<String, String> = entries.toMap()

    /**
     * Deploy into [datapacksDirectory]/[name].
     *
     * Only stale files recorded by a previous Arc manifest are removed.
     */
    public fun deployTo(datapacksDirectory: Path): Path {
        require(packFormat > 0) { "packFormat must be positive" }
        val root = datapacksDirectory.toAbsolutePath().normalize()
        Files.createDirectories(root)
        val packDir = root.resolve(name).normalize()
        require(packDir.startsWith(root)) { "Datapack path escapes deployment root" }
        Files.createDirectories(packDir)

        removeStaleManagedFiles(packDir)
        writePackMetadata(packDir)
        for ((relative, content) in entries) {
            val target = resolveInside(packDir, relative)
            Files.createDirectories(target.parent)
            target.writeText(
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
            )
        }
        packDir.resolve(MANIFEST_FILE).writeLines(
            entries.keys.sorted(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
        return packDir
    }

    private fun jsonResource(directory: String, id: String, json: String) {
        val key = ResourceId.parse(id)
        put(resourcePath(key, directory), normalizeJson(json))
    }

    private fun put(path: String, content: String) {
        require(path !in entries) { "Datapack resource already exists: $path" }
        entries[path] = content
    }

    private fun writePackMetadata(packDir: Path) {
        val pack = JsonObject().apply {
            addProperty("pack_format", packFormat)
            addProperty("description", "Arc datapack: $name")
        }
        val root = JsonObject().apply { add("pack", pack) }
        packDir.resolve("pack.mcmeta").writeText(
            DATAPACK_GSON.toJson(root),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
    }

    private fun removeStaleManagedFiles(packDir: Path) {
        val manifest = packDir.resolve(MANIFEST_FILE)
        if (!manifest.exists()) return
        for (oldPath in manifest.readLines(StandardCharsets.UTF_8)) {
            if (oldPath.isBlank() || oldPath in entries) continue
            val stale = resolveInside(packDir, oldPath)
            stale.deleteIfExists()
            var parent = stale.parent
            while (parent != null && parent != packDir && Files.isDirectory(parent)) {
                val empty = Files.newDirectoryStream(parent).use { !it.iterator().hasNext() }
                if (!empty) break
                if (!parent.toFile().delete()) break
                parent = parent.parent
            }
        }
    }

    private fun resolveInside(root: Path, relative: String): Path {
        val target = root.resolve(normalizeRelativePath(relative)).normalize()
        require(target.startsWith(root)) { "Datapack resource escapes pack root: $relative" }
        return target
    }

    private fun resourcePath(id: ResourceId, directory: String, extension: String = ".json"): String =
        "data/${id.namespace}/$directory/${id.path}$extension"

    private fun normalizeTagRegistry(registry: String): String {
        val normalized = when (registry) {
            "blocks" -> "block"
            "items" -> "item"
            "fluids" -> "fluid"
            "entity_types" -> "entity_type"
            "game_events" -> "game_event"
            "functions" -> "function"
            else -> registry
        }
        require(normalized.matches(RESOURCE_PATH_PATTERN) && '/' !in normalized) {
            "Invalid tag registry: $registry"
        }
        return normalized
    }

    private fun normalizeRelativePath(path: String): String {
        require(path.isNotBlank()) { "Datapack path cannot be blank" }
        require('\\' !in path) { "Datapack path must use '/' separators: $path" }
        require(!path.startsWith('/')) { "Datapack path must be relative: $path" }
        val segments = path.split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." }) {
            "Datapack path cannot traverse directories: $path"
        }
        return segments.joinToString("/")
    }
}

@DatapackDsl
public class BiomeBuilder {
    private var precipitation: Boolean = true
    private var temperature: Float = 0.5f
    private var temperatureModifier: String = "none"
    private var downfall: Float = 0.5f
    private var fogColor: Int = 12_638_463
    private var skyColor: Int = 7_907_327
    private var waterColor: Int = 4_159_204
    private var waterFogColor: Int = 329_011
    private var grassColorModifier: String = "none"
    private var grassColor: Int? = null
    private var foliageColor: Int? = null
    private val spawners = LinkedHashMap<String, MutableList<SpawnerEntry>>()
    private val features = ArrayList<ResourceId>()
    private val carvers = ArrayList<ResourceId>()

    public fun precipitation(enabled: Boolean) { precipitation = enabled }
    public fun temperature(value: Float) { require(value.isFinite()); temperature = value }
    public fun frozenTemperature() { temperature = 0.0f; temperatureModifier = "frozen" }
    public fun downfall(value: Float) { require(value in 0.0f..1.0f); downfall = value }
    public fun fogColor(rgb: Int) { fogColor = validateColor(rgb) }
    public fun skyColor(rgb: Int) { skyColor = validateColor(rgb) }
    public fun waterColor(rgb: Int) { waterColor = validateColor(rgb) }
    public fun waterFogColor(rgb: Int) { waterFogColor = validateColor(rgb) }
    public fun grassColor(rgb: Int) { grassColor = validateColor(rgb) }
    public fun foliageColor(rgb: Int) { foliageColor = validateColor(rgb) }
    public fun swampGrass() { grassColorModifier = "swamp" }
    public fun darkForestGrass() { grassColorModifier = "dark_forest" }

    public fun spawner(
        category: String,
        entityType: String,
        weight: Int = 1,
        minCount: Int = 1,
        maxCount: Int = 4,
    ) {
        require(category.matches(NAMESPACE_PATTERN)) { "Invalid spawn category: $category" }
        require(weight > 0) { "Spawner weight must be positive" }
        require(minCount > 0 && maxCount >= minCount) { "Invalid spawn count range: $minCount..$maxCount" }
        spawners.getOrPut(category) { ArrayList() } +=
            SpawnerEntry(ResourceId.parse(entityType), weight, minCount, maxCount)
    }

    public fun feature(key: String) { features += ResourceId.parse(key) }
    public fun carver(key: String) { carvers += ResourceId.parse(key) }

    internal fun build(): String {
        val effects = JsonObject().apply {
            addProperty("fog_color", fogColor)
            addProperty("sky_color", skyColor)
            addProperty("water_color", waterColor)
            addProperty("water_fog_color", waterFogColor)
            addProperty("grass_color_modifier", grassColorModifier)
            grassColor?.let { addProperty("grass_color", it) }
            foliageColor?.let { addProperty("foliage_color", it) }
        }
        val spawnerGroups = JsonObject()
        for ((category, values) in spawners) {
            spawnerGroups.add(category, JsonArray().apply {
                values.forEach { entry ->
                    add(JsonObject().apply {
                        addProperty("type", entry.type.toString())
                        addProperty("weight", entry.weight)
                        addProperty("minCount", entry.min)
                        addProperty("maxCount", entry.max)
                    })
                }
            })
        }
        val spawnSettings = JsonObject().apply {
            addProperty("creature_spawn_probability", 0.1)
            add("spawners", spawnerGroups)
            add("spawn_costs", JsonObject())
        }
        val featureSteps = JsonArray().apply {
            features.forEach { feature ->
                add(JsonArray().apply { add(feature.toString()) })
            }
        }
        val carverObject = JsonObject().apply {
            add("air", JsonArray().apply { carvers.forEach { add(it.toString()) } })
        }
        return DATAPACK_GSON.toJson(JsonObject().apply {
            addProperty("has_precipitation", precipitation)
            addProperty("temperature", temperature)
            addProperty("temperature_modifier", temperatureModifier)
            addProperty("downfall", downfall)
            add("effects", effects)
            add("mob_spawn_settings", spawnSettings)
            add("carvers", carverObject)
            add("features", featureSteps)
        })
    }

    private fun validateColor(rgb: Int): Int {
        require(rgb in 0..0xFFFFFF) { "RGB color must be in 0x000000..0xFFFFFF" }
        return rgb
    }

    private data class SpawnerEntry(
        val type: ResourceId,
        val weight: Int,
        val min: Int,
        val max: Int,
    )
}

@DatapackDsl
public class LootTableBuilder {
    private val pools = ArrayList<JsonObject>()

    public fun pool(rolls: IntRange = 1..1, block: LootPoolBuilder.() -> Unit) {
        require(!rolls.isEmpty() && rolls.first > 0) { "Loot rolls must be a positive range" }
        pools += LootPoolBuilder(rolls).apply(block).build()
    }

    public fun pool(rolls: Int, block: LootPoolBuilder.() -> Unit): Unit =
        pool(rolls..rolls, block)

    internal fun build(): String = DATAPACK_GSON.toJson(JsonObject().apply {
        addProperty("type", "minecraft:chest")
        add("pools", JsonArray().apply { pools.forEach(::add) })
    })
}

@DatapackDsl
public class LootPoolBuilder internal constructor(private val rolls: IntRange) {
    private val entries = ArrayList<JsonObject>()

    public fun entry(item: String, weight: Int = 1, count: IntRange = 1..1) {
        require(weight > 0) { "Loot entry weight must be positive" }
        require(!count.isEmpty() && count.first > 0) { "Loot count must be a positive range" }
        val itemId = ResourceId.parse(item)
        val countValue = if (count.first == count.last) {
            DATAPACK_GSON.toJsonTree(count.first)
        } else {
            JsonObject().apply {
                addProperty("type", "minecraft:uniform")
                addProperty("min", count.first)
                addProperty("max", count.last)
            }
        }
        entries += JsonObject().apply {
            addProperty("type", "minecraft:item")
            addProperty("name", itemId.toString())
            addProperty("weight", weight)
            add("functions", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("function", "minecraft:set_count")
                    add("count", countValue)
                })
            })
        }
    }

    public fun empty(weight: Int = 1) {
        require(weight > 0) { "Loot entry weight must be positive" }
        entries += JsonObject().apply {
            addProperty("type", "minecraft:empty")
            addProperty("weight", weight)
        }
    }

    internal fun build(): JsonObject {
        require(entries.isNotEmpty()) { "Loot pool must contain at least one entry" }
        val rollValue = if (rolls.first == rolls.last) {
            DATAPACK_GSON.toJsonTree(rolls.first)
        } else {
            JsonObject().apply {
                addProperty("type", "minecraft:uniform")
                addProperty("min", rolls.first)
                addProperty("max", rolls.last)
            }
        }
        return JsonObject().apply {
            add("rolls", rollValue)
            add("entries", JsonArray().apply { entries.forEach(::add) })
        }
    }
}

@DatapackDsl
public class TagBuilder {
    private val values = ArrayList<String>()
    private var replace: Boolean = false

    public fun add(id: String) { values += ResourceId.parse(id).toString() }
    public fun addTag(tagId: String) { values += "#${ResourceId.parse(tagId)}" }
    public fun replace() { replace = true }

    internal fun build(): String = DATAPACK_GSON.toJson(JsonObject().apply {
        addProperty("replace", replace)
        add("values", JsonArray().apply { values.forEach(::add) })
    })
}

@DatapackDsl
public class FunctionBuilder {
    private val commands = ArrayList<String>()

    public fun command(command: String) {
        require(command.isNotBlank()) { "Function command cannot be blank" }
        require('\n' !in command && '\r' !in command) { "Function command cannot contain a newline" }
        commands += command
    }

    public operator fun String.unaryPlus() {
        command(this)
    }

    internal fun build(): String =
        if (commands.isEmpty()) "" else commands.joinToString(separator = "\n", postfix = "\n")
}

private fun normalizeJson(json: String): String =
    DATAPACK_GSON.toJson(JsonParser.parseString(json))
