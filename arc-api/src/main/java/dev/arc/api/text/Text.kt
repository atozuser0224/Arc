package dev.arc.api.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage

private val MM = MiniMessage.miniMessage()

/** Parse a MiniMessage string into a [Component]. */
fun mini(input: String): Component = MM.deserialize(input)

/** `"<red>hello <bold>world".mm()` */
fun String.mm(): Component = MM.deserialize(this)

/** Serialize a [Component] back to a MiniMessage string. */
fun Component.toMini(): String = MM.serialize(this)
