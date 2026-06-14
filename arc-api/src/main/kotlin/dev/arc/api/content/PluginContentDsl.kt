package dev.arc.api.content

import org.bukkit.plugin.Plugin

public fun contentNamespace(pluginName: String): String {
    val normalized = pluginName.lowercase()
        .replace(Regex("[^a-z0-9_.-]+"), "_")
        .trim('_', '.', '-')
    return normalized.takeIf { it.any(Char::isLetterOrDigit) } ?: "plugin"
}

public fun Plugin.arcContent(
    namespace: String = contentNamespace(name),
    version: String = pluginMeta.version,
    block: ContentPackBuilder.() -> Unit,
): ContentPublishResult {
    val pack = contentPack(namespace, version, block)
    return ArcContent.registry.install("plugin:$name", pack)
}

public fun Plugin.removeArcContent(): ContentPublishResult =
    ArcContent.registry.uninstall("plugin:$name")
