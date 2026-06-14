package dev.arc.api.content.pack

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.arc.api.content.ContentDiagnostic
import dev.arc.api.content.ContentId
import dev.arc.api.content.ContentPack
import dev.arc.api.content.DiagnosticSeverity
import dev.arc.api.content.asset.AssetCollector
import dev.arc.api.content.asset.AssetPolicy
import dev.arc.api.content.asset.ContentAsset
import dev.arc.api.content.contentPack
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.io.path.readText

public data class LoadedFileContentPack(
    public val pack: ContentPack?,
    public val assets: List<ContentAsset>,
    public val diagnostics: List<ContentDiagnostic>,
)

public class FileContentPackLoader(
    private val assetPolicy: AssetPolicy = AssetPolicy(),
) {
    public fun load(root: Path): LoadedFileContentPack {
        val diagnostics = mutableListOf<ContentDiagnostic>()
        val metadata = root.resolve("pack.json")
        if (!metadata.exists() || !metadata.isRegularFile(LinkOption.NOFOLLOW_LINKS)) {
            diagnostics += diagnostic("pack.json", "Missing regular pack.json")
            return LoadedFileContentPack(null, emptyList(), diagnostics)
        }

        val pack = runCatching { parsePack(metadata.readText()) }
            .onFailure { diagnostics += diagnostic("pack.json", it.message ?: "Invalid pack metadata") }
            .getOrNull()
            ?: return LoadedFileContentPack(null, emptyList(), diagnostics)

        val collector = AssetCollector(assetPolicy)
        val assetsRoot = root.resolve("assets")
        if (assetsRoot.exists()) {
            Files.walk(assetsRoot).use { paths ->
                paths.filter { it != assetsRoot }.sorted().forEach { path ->
                    when {
                        Files.isSymbolicLink(path) ->
                            diagnostics += diagnostic("assets", "Symbolic links are not allowed: $path")
                        path.isRegularFile(LinkOption.NOFOLLOW_LINKS) -> {
                            val relative = assetsRoot.relativize(path).joinToString("/") { it.toString() }
                            val validation = collector.add(relative, path.readBytes())
                            if (!validation.accepted) {
                                diagnostics += diagnostic(relative, validation.reason ?: "Asset rejected")
                            }
                        }
                    }
                }
            }
        }
        return LoadedFileContentPack(pack, collector.assets, diagnostics)
    }

    private fun parsePack(json: String): ContentPack {
        val root = JsonParser.parseString(json).asJsonObject
        val namespace = root.requiredString("namespace")
        val version = root.requiredString("version")
        return contentPack(namespace, version) {
            root.getAsJsonArray("items")?.forEach { element ->
                val item = element.asJsonObject
                item(item.requiredString("id")) {
                    item.string("fallback")?.let { fallback = it }
                    item.int("maxStackSize")?.let { maxStackSize = it }
                    item.int("durability")?.let { durability = it }
                    item.boolean("required")?.let { required = it }
                    item.int("order")?.let { order = it }
                }
            }
            root.getAsJsonArray("blocks")?.forEach { element ->
                val block = element.asJsonObject
                block(block.requiredString("id")) {
                    block.string("fallback")?.let { fallback = it }
                    block.float("hardness")?.let { hardness = it }
                    block.float("blastResistance")?.let { blastResistance = it }
                    block.boolean("required")?.let { required = it }
                    block.int("order")?.let { order = it }
                }
            }
            root.getAsJsonArray("furniture")?.forEach { element ->
                val furniture = element.asJsonObject
                furniture(furniture.requiredString("id")) {
                    furniture.string("fallback")?.let { fallback = it }
                    furniture.int("seats")?.let { seats = it }
                    furniture.int("width")?.let { width = it }
                    furniture.int("depth")?.let { depth = it }
                    furniture.boolean("required")?.let { required = it }
                    furniture.int("order")?.let { order = it }
                }
            }
            root.getAsJsonArray("recipes")?.forEach { element ->
                val recipe = element.asJsonObject
                recipe(recipe.requiredString("id")) {
                    result = ContentId.parse(recipe.requiredString("result"))
                    recipe.getAsJsonArray("ingredients")?.forEach {
                        ingredients += ContentId.parse(it.asString)
                    }
                    recipe.boolean("required")?.let { required = it }
                    recipe.int("order")?.let { order = it }
                }
            }
        }
    }

    private fun diagnostic(field: String, message: String): ContentDiagnostic =
        ContentDiagnostic(DiagnosticSeverity.ERROR, message, field = field)

    private fun JsonObject.requiredString(name: String): String =
        string(name)?.takeIf { it.isNotBlank() } ?: error("Missing string field: $name")

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString

    private fun JsonObject.int(name: String): Int? =
        get(name)?.takeUnless { it.isJsonNull }?.asInt

    private fun JsonObject.float(name: String): Float? =
        get(name)?.takeUnless { it.isJsonNull }?.asFloat

    private fun JsonObject.boolean(name: String): Boolean? =
        get(name)?.takeUnless { it.isJsonNull }?.asBoolean
}
