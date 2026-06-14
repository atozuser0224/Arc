package dev.arc.api.content

import java.security.MessageDigest

public data class ContentCompileResult(
    public val diagnostics: List<ContentDiagnostic>,
    public val revision: CompiledContentRevision? = null,
)

public data class CompiledContentRevision(
    public val hash: String,
    public val packs: List<ContentPack>,
    public val definitions: Map<ContentId, ContentDefinition>,
    public val catalog: CreativeCatalog,
)

public object ContentCompiler {

    @JvmStatic
    public fun compile(packs: List<ContentPack>): ContentCompileResult {
        val diagnostics = mutableListOf<ContentDiagnostic>()
        val definitions = linkedMapOf<ContentId, Pair<ContentPack, ContentDefinition>>()
        for (pack in packs) {
            for (definition in pack.definitions) {
                val previous = definitions.putIfAbsent(definition.id, pack to definition)
                if (previous != null) {
                    diagnostics += ContentDiagnostic(
                        severity = DiagnosticSeverity.ERROR,
                        message = "Content ID is already owned by ${previous.first.id}",
                        packId = pack.id,
                        contentId = definition.id,
                        field = "duplicate",
                    )
                    continue
                }
                validate(pack, definition, diagnostics)
            }
        }
        if (diagnostics.any { it.severity == DiagnosticSeverity.ERROR }) {
            return ContentCompileResult(diagnostics)
        }

        val sortedPacks = packs.sortedBy { it.id }
        val sortedDefinitions = definitions.entries
            .sortedBy { it.key }
            .associateTo(linkedMapOf()) { it.key to it.value.second }
        val catalogEntries = definitions.values
            .map { (pack, definition) ->
                CreativeCatalogEntry(definition.id, definition.type, pack.id, definition.order)
            }
            .sortedWith(compareBy(CreativeCatalogEntry::order, CreativeCatalogEntry::id))
        val namespaces = sortedPacks.map { it.id.namespace }.distinct()
        val title = if (namespaces.size == 1) "Arc: ${namespaces.single()}" else "Arc"
        val revision = CompiledContentRevision(
            hash = hash(sortedPacks, sortedDefinitions.values),
            packs = sortedPacks,
            definitions = sortedDefinitions,
            catalog = CreativeCatalog(title, catalogEntries),
        )
        return ContentCompileResult(diagnostics, revision)
    }

    private fun validate(
        pack: ContentPack,
        definition: ContentDefinition,
        diagnostics: MutableList<ContentDiagnostic>,
    ) {
        when (definition) {
            is ItemDefinition -> {
                if (definition.maxStackSize !in 1..64) {
                    diagnostics += error(pack, definition, "maxStackSize", "must be in 1..64")
                }
                if (definition.durability < 0) {
                    diagnostics += error(pack, definition, "durability", "must not be negative")
                }
            }
            is BlockDefinition -> {
                if (definition.hardness < 0.0f) {
                    diagnostics += error(pack, definition, "hardness", "must not be negative")
                }
                if (definition.blastResistance < 0.0f) {
                    diagnostics += error(pack, definition, "blastResistance", "must not be negative")
                }
            }
        }
    }

    private fun error(
        pack: ContentPack,
        definition: ContentDefinition,
        field: String,
        message: String,
    ): ContentDiagnostic = ContentDiagnostic(
        severity = DiagnosticSeverity.ERROR,
        message = message,
        packId = pack.id,
        contentId = definition.id,
        field = field,
    )

    private fun hash(
        packs: List<ContentPack>,
        definitions: Collection<ContentDefinition>,
    ): String {
        val canonical = buildString {
            packs.forEach { append("pack|").append(it.id).append('|').append(it.version).append('\n') }
            definitions.forEach { definition ->
                append(definition.type).append('|')
                append(definition.id).append('|')
                append(definition.fallback).append('|')
                append(definition.required).append('|')
                append(definition.order)
                when (definition) {
                    is ItemDefinition -> append('|').append(definition.maxStackSize)
                        .append('|').append(definition.durability)
                    is BlockDefinition -> append('|').append(definition.hardness)
                        .append('|').append(definition.blastResistance)
                }
                append('\n')
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
