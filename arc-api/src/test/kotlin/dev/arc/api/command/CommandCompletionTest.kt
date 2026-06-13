package dev.arc.api.command

import org.bukkit.command.CommandSender
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

class CommandCompletionTest {

    @Test
    fun `root completer handles arguments beyond the first token`() {
        val builder = CommandBuilder("arc").apply {
            complete { ctx ->
                when (ctx.args.size) {
                    1 -> listOf("plugin", "config")
                    2 -> if (ctx.args[0].equals("plugin", true)) listOf("list", "info") else emptyList()
                    else -> emptyList()
                }
            }
        }

        assertEquals(listOf("list", "info"), builder.completeFor(sender(), "arc", arrayOf("plugin", "")))
        assertEquals(listOf("info"), builder.completeFor(sender(), "arc", arrayOf("PLUGIN", "i")))
    }

    @Test
    fun `subcommands and candidates are permission filtered and deduplicated`() {
        val builder = CommandBuilder("test").apply {
            sub("Open") {
                complete { listOf("Alpha", "alpha", "Beta") }
            }
            sub("Admin") {
                permission("test.admin")
            }
        }
        val sender = sender(permissions = emptySet())

        assertEquals(listOf("Open"), builder.completeFor(sender, "test", arrayOf("")))
        assertEquals(listOf("Alpha"), builder.completeFor(sender, "test", arrayOf("open", "a")))
    }

    private fun sender(permissions: Set<String> = setOf("*")): CommandSender =
        Proxy.newProxyInstance(
            CommandSender::class.java.classLoader,
            arrayOf(CommandSender::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "hasPermission" -> "*" in permissions || args?.get(0) in permissions
                "isPermissionSet" -> "*" in permissions || args?.get(0) in permissions
                "getName" -> "test"
                "spigot" -> null
                "equals" -> proxy === args?.get(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "TestCommandSender"
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    else -> null
                }
            }
        } as CommandSender
}
