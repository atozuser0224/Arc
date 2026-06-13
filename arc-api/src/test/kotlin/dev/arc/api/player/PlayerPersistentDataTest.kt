package dev.arc.api.player

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerPersistentDataTest {

    @Test
    fun `validates writes and supports defaults mutation and increment`() {
        val plugin = plugin("ArcTest")
        val player = player()
        val coins = plugin.playerStore("coins", PersistentDataType.INTEGER) {
            default { 0 }
            validate("coins must be non-negative") { it >= 0 }
        }

        assertEquals(0, player.getOrDefault(coins))
        assertEquals(5, player.mutate(coins, 0) { it + 5 })
        assertEquals(7, player.increment(coins, 2))
        assertTrue(coins in player)

        assertFailsWith<IllegalArgumentException> { player[coins] = -1 }
        assertEquals(7, player[coins])

        player -= coins
        assertFalse(coins in player)
    }

    @Test
    fun `lazily migrates legacy values and current key wins`() {
        val plugin = plugin("ArcTest")
        val player = player()
        val legacy = NamespacedKey(plugin, "balance")
        val coins = plugin.playerStore("coins", PersistentDataType.INTEGER) {
            migrateFrom("balance")
        }

        player.persistentDataContainer.set(legacy, PersistentDataType.INTEGER, 12)
        assertEquals(12, player[coins])
        assertFalse(player.persistentDataContainer.has(legacy, PersistentDataType.INTEGER))
        assertEquals(12, player.persistentDataContainer.get(coins.key, coins.type))

        player.persistentDataContainer.set(legacy, PersistentDataType.INTEGER, 99)
        player[coins] = 20
        assertEquals(20, player[coins])
        assertTrue(player.persistentDataContainer.has(legacy, PersistentDataType.INTEGER))
    }

    @Test
    fun `schema rejects duplicate names and exposes definitions`() {
        val plugin = plugin("ArcTest")
        lateinit var coins: PlayerStore<Int, Int>

        val schema = plugin.playerDataSchema {
            coins = int("coins") { default { 0 } }
            string("rank") { default { "member" } }
            boolean("tutorial_complete")
        }

        assertEquals(3, schema.size)
        assertEquals(coins, schema["coins"])
        assertFailsWith<IllegalArgumentException> {
            plugin.playerDataSchema {
                int("duplicate")
                int("duplicate")
            }
        }
    }

    private fun plugin(name: String): Plugin =
        Proxy.newProxyInstance(
            Plugin::class.java.classLoader,
            arrayOf(Plugin::class.java),
        ) { proxy, method, _ ->
            when (method.name) {
                "getName" -> name
                "toString" -> "Plugin($name)"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> false
                else -> defaultValue(method.returnType)
            }
        } as Plugin

    private fun player(): Player {
        val values = LinkedHashMap<NamespacedKey, Any>()
        val container = Proxy.newProxyInstance(
            PersistentDataContainer::class.java.classLoader,
            arrayOf(PersistentDataContainer::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "set" -> {
                    values[args!![0] as NamespacedKey] = args[2]!!
                    null
                }
                "get" -> values[args!![0] as NamespacedKey]
                "getOrDefault" -> values[args!![0] as NamespacedKey] ?: args[2]
                "has" -> values.containsKey(args!![0] as NamespacedKey)
                "remove" -> {
                    values.remove(args!![0] as NamespacedKey)
                    null
                }
                "getKeys" -> values.keys.toSet()
                "isEmpty" -> values.isEmpty()
                "toString" -> values.toString()
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> false
                else -> defaultValue(method.returnType)
            }
        } as PersistentDataContainer

        return Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { proxy, method, _ ->
            when (method.name) {
                "getPersistentDataContainer" -> container
                "toString" -> "TestPlayer"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> false
                else -> defaultValue(method.returnType)
            }
        } as Player
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }
}
