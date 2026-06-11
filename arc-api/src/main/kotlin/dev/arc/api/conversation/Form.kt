@file:JvmName("Forms")

package dev.arc.api.conversation

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/**
 * Coroutine-based multi-step chat form. Each [ask] / [askInt] / [askUntil] call suspends until the
 * player submits valid input; invalid input sends an error message and re-prompts automatically.
 *
 * ```kotlin
 * plugin.launch {
 *     val (name, level) = player.form(plugin) {
 *         val name  = ask("캐릭터 이름을 입력하세요")
 *         val level = askInt("레벨을 입력하세요 (1~100)") { it in 1..100 }
 *         name to level
 *     }
 *     player.sendMessage("생성 완료: $name Lv.$level")
 * }
 * ```
 */
public class FormScope(public val player: Player, private val plugin: Plugin) {

    /** Prompt and await any non-blank chat input. */
    public suspend fun ask(prompt: String): String {
        while (true) {
            player.sendMessage(prompt)
            val input = player.awaitChatText(plugin).trim()
            if (input.isNotEmpty()) return input
            player.sendMessage("§c입력값이 비어있습니다.")
        }
    }

    /** Prompt and await an [Int] that satisfies [validate]. */
    public suspend fun askInt(
        prompt: String,
        errorMessage: String = "§c숫자를 입력해주세요.",
        validate: (Int) -> Boolean = { true },
    ): Int {
        while (true) {
            player.sendMessage(prompt)
            val input = player.awaitChatText(plugin).trim()
            val value = input.toIntOrNull()
            if (value != null && validate(value)) return value
            player.sendMessage(errorMessage)
        }
    }

    /** Prompt and await a [Double] that satisfies [validate]. */
    public suspend fun askDouble(
        prompt: String,
        errorMessage: String = "§c숫자를 입력해주세요.",
        validate: (Double) -> Boolean = { true },
    ): Double {
        while (true) {
            player.sendMessage(prompt)
            val input = player.awaitChatText(plugin).trim()
            val value = input.toDoubleOrNull()
            if (value != null && validate(value)) return value
            player.sendMessage(errorMessage)
        }
    }

    /**
     * Prompt and await input that [parse] can convert to a non-null [T].
     * Re-prompts with [errorMessage] when [parse] returns null.
     */
    public suspend fun <T> askUntil(
        prompt: String,
        errorMessage: String = "§c올바르지 않은 입력입니다.",
        parse: (String) -> T?,
    ): T {
        while (true) {
            player.sendMessage(prompt)
            val input = player.awaitChatText(plugin).trim()
            val value = parse(input)
            if (value != null) return value
            player.sendMessage(errorMessage)
        }
    }

    /** Prompt a yes/no question — accepts y/yes/n/no (case-insensitive). */
    public suspend fun confirm(
        prompt: String,
        errorMessage: String = "§cy/yes 또는 n/no 로 답해주세요.",
    ): Boolean = askUntil(prompt, errorMessage) { input ->
        when (input.lowercase()) {
            "y", "yes", "예", "응" -> true
            "n", "no", "아니", "노" -> false
            else -> null
        }
    }
}

/**
 * Open a coroutine-based chat form for this player.
 * The [block] receives a [FormScope] whose methods each suspend for user input.
 * Returns whatever [block] returns.
 */
public suspend fun <T> Player.form(plugin: Plugin, block: suspend FormScope.() -> T): T =
    FormScope(this, plugin).block()
