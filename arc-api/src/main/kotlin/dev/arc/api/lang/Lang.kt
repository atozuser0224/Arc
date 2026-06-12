@file:JvmName("Lang")

package dev.arc.api.lang

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val mm = MiniMessage.miniMessage()

/**
 * Multi-locale message registry backed by YAML files under `lang/`.
 *
 * Each locale maps to a file: `lang/en.yml`, `lang/ko.yml`, etc.
 * Keys are dot-notation paths that return MiniMessage strings.
 * Unknown keys fall back to the default locale, then to the raw key.
 *
 * ```kotlin
 * val lang = plugin.langRegistry {
 *     defaultLocale("en")
 *     loadLocale("en")
 *     loadLocale("ko")
 * }
 *
 * // In a command:
 * player.sendMessage(lang["shop.buy.success", player.locale, "item" to "Diamond Sword", "price" to 200])
 * ```
 *
 * `lang/en.yml`:
 * ```yaml
 * shop:
 *   buy:
 *     success: "<green>Purchased <yellow><item></yellow> for <gold><price></gold> coins!"
 *     no_funds: "<red>Not enough coins."
 * ```
 */
class LangRegistry internal constructor(private val plugin: Plugin) {

    private val tables = ConcurrentHashMap<String, Map<String, String>>()
    private var default = "en"

    /** Set the fallback locale used when a key is missing from the requested locale. */
    fun defaultLocale(locale: String) { default = locale }

    /**
     * Load a locale from `lang/<locale>.yml` inside the plugin's data folder.
     * If the file doesn't exist and the plugin bundles `lang/<locale>.yml` as a resource,
     * it is extracted automatically.
     */
    fun loadLocale(locale: String, fileName: String = "lang/$locale.yml"): LangRegistry {
        val file = File(plugin.dataFolder, fileName)
        if (!file.exists()) {
            file.parentFile.mkdirs()
            plugin.getResource(fileName)?.use { res -> file.writeBytes(res.readBytes()) }
                ?: file.createNewFile()
        }
        val yaml = YamlConfiguration.loadConfiguration(file)
        tables[locale] = flattenSection(yaml, "")
        return this
    }

    /**
     * Load locale entries programmatically (useful for testing).
     *
     * ```kotlin
     * lang.putLocale("en", mapOf("shop.buy.success" to "<green>Purchased!"))
     * ```
     */
    fun putLocale(locale: String, entries: Map<String, String>): LangRegistry {
        tables[locale] = entries
        return this
    }

    /**
     * Retrieve a message for [key] in [locale], resolved as a [Component].
     * Falls back to [default] locale, then returns the raw key wrapped in red as a missing-key indicator.
     *
     * [placeholders] are MiniMessage tag pairs: `"player" to player.name`, `"amount" to 100`.
     */
    operator fun get(
        key: String,
        locale: String = default,
        vararg placeholders: Pair<String, Any>,
    ): Component {
        val raw = tables[locale]?.get(key)
            ?: tables[default]?.get(key)
            ?: return mm.deserialize("<red>[missing: $key]")
        val resolver = if (placeholders.isEmpty()) TagResolver.empty()
        else TagResolver.resolver(placeholders.map { (name, value) ->
            Placeholder.parsed(name, value.toString())
        })
        return mm.deserialize(raw, resolver)
    }

    /** Convenience: look up [key] using the player's own locale (Minecraft client language). */
    fun forPlayer(player: Player, key: String, vararg placeholders: Pair<String, Any>): Component {
        @Suppress("DEPRECATION")
        val locale = player.locale.substringBefore('_').lowercase()
        return get(key, locale, *placeholders)
    }

    /** Reload all previously-loaded locales from disk. */
    fun reload(): LangRegistry {
        val locales = tables.keys.toList()
        tables.clear()
        locales.forEach { loadLocale(it) }
        return this
    }

    private fun flattenSection(section: org.bukkit.configuration.ConfigurationSection, prefix: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (key in section.getKeys(false)) {
            val full = if (prefix.isEmpty()) key else "$prefix.$key"
            val value = section.get(key)
            when {
                value is org.bukkit.configuration.ConfigurationSection -> map += flattenSection(value, full)
                value != null -> map[full] = value.toString()
            }
        }
        return map
    }
}

/**
 * Create a [LangRegistry] attached to this plugin.
 *
 * ```kotlin
 * val lang = plugin.langRegistry {
 *     defaultLocale("en")
 *     loadLocale("en")
 *     loadLocale("ko")
 * }
 * ```
 */
fun Plugin.langRegistry(block: LangRegistry.() -> Unit = {}): LangRegistry =
    LangRegistry(this).apply(block)

/** Send this player a message looked up from [lang] using their own client locale. */
fun Player.sendLang(lang: LangRegistry, key: String, vararg placeholders: Pair<String, Any>) {
    sendMessage(lang.forPlayer(this, key, *placeholders))
}
