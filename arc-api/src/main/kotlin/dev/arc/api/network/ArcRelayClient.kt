package dev.arc.api.network

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Arc Relay client — pub/sub, lock, cooldown via Redis.
 * Abstracts the messaging layer so different backends can be plugged.
 */
object ArcRelayClient {

    private var jedisPool: Any? = null // JedisPool — runtime dependency; loaded reflectively
    @Volatile var connected: Boolean = false
    private var config: ArcNetworkConfig? = null

    fun connect(cfg: ArcNetworkConfig) {
        this.config = cfg
        connected = false
        if (!cfg.enabled) return
        try {
            // Reflectively create JedisPool to avoid hard dependency
            val poolClass = Class.forName("redis.clients.jedis.JedisPool")
            val configClass = Class.forName("redis.clients.jedis.JedisPoolConfig")
            val configInst = configClass.getDeclaredConstructor().newInstance()
            configClass.getMethod("setMaxTotal", Int::class.javaPrimitiveType).invoke(configInst, cfg.relay.poolSize)
            configClass.getMethod("setMaxIdle", Int::class.javaPrimitiveType).invoke(configInst, cfg.relay.poolSize / 2)

            val host = cfg.relay.host
            val port = cfg.relay.port
            val timeout = cfg.relay.timeoutMs
            val password = cfg.relay.password.ifBlank { null }

            val pool = if (password != null) {
                poolClass.getConstructor(configClass, String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
                    .newInstance(configInst, host, port, timeout, password)
            } else {
                poolClass.getConstructor(configClass, String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .newInstance(configInst, host, port, timeout)
            }
            jedisPool = pool
            val jedis = pool.javaClass.getMethod("getResource").invoke(pool)
            try {
                jedis.javaClass.getMethod("ping").invoke(jedis)
                connected = true
            } finally {
                jedis.javaClass.getMethod("close").invoke(jedis)
            }
        } catch (e: ClassNotFoundException) {
            Bukkit.getLogger().warning("[Arc-Network] Jedis not found on classpath — relay disabled")
        } catch (e: Exception) {
            runCatching { jedisPool?.javaClass?.getMethod("close")?.invoke(jedisPool) }
            jedisPool = null
            connected = false
            Bukkit.getLogger().warning("[Arc-Network] Redis connection failed: ${e.message}")
        }
    }

    fun disconnect() {
        connected = false
        runCatching {
            jedisPool?.javaClass?.getMethod("close")?.invoke(jedisPool)
        }
        jedisPool = null
    }

    fun reconnect() {
        disconnect()
        config?.let { connect(it) }
    }

    // ---- Key-Value ----
    fun get(key: String): String? {
        return jedisOp { jedis -> jedis.javaClass.getMethod("get", String::class.java).invoke(jedis, key) as? String }
    }

    fun setex(key: String, ttl: Int, value: String) {
        jedisOp { jedis ->
            jedis.javaClass.getMethod("setex", String::class.java, Int::class.javaPrimitiveType, String::class.java)
                .invoke(jedis, key, ttl, value)
        }
    }

    fun del(key: String) {
        jedisOp { jedis -> jedis.javaClass.getMethod("del", String::class.java).invoke(jedis, key) }
    }

    // ---- Sets ----
    fun sadd(key: String, member: String) {
        jedisOp { jedis -> jedis.javaClass.getMethod("sadd", String::class.java, String::class.java).invoke(jedis, key, member) }
    }

    fun srem(key: String, member: String) {
        jedisOp { jedis -> jedis.javaClass.getMethod("srem", String::class.java, String::class.java).invoke(jedis, key, member) }
    }

    fun smembers(key: String): List<String> {
        return jedisOp { jedis ->
            @Suppress("UNCHECKED_CAST")
            (jedis.javaClass.getMethod("smembers", String::class.java).invoke(jedis, key) as? java.util.Set<String>)?.toList() ?: emptyList()
        } ?: emptyList()
    }

    // ---- Sorted Sets ----
    fun zadd(key: String, score: Double, member: String) {
        jedisOp { jedis ->
            jedis.javaClass.getMethod("zadd", String::class.java, Double::class.javaPrimitiveType, String::class.java)
                .invoke(jedis, key, score, member)
        }
    }

    fun zpopmin(key: String, count: Int = 1): List<String> {
        return jedisOp { jedis ->
            @Suppress("UNCHECKED_CAST")
            val result = jedis.javaClass.getMethod("zpopmin", String::class.java, Int::class.javaPrimitiveType)
                .invoke(jedis, key, count) as? java.util.Set<*> ?: emptySet<Any>()
            result.map { it.toString() }
        } ?: emptyList()
    }

    fun zrem(key: String, member: String) {
        jedisOp { jedis ->
            jedis.javaClass.getMethod("zrem", String::class.java, String::class.java)
                .invoke(jedis, key, member)
        }
    }

    fun zcard(key: String): Long {
        return jedisOp { jedis ->
            jedis.javaClass.getMethod("zcard", String::class.java).invoke(jedis, key) as? Long ?: 0L
        } ?: 0L
    }

    fun zrank(key: String, member: String): Long? {
        return jedisOp { jedis ->
            jedis.javaClass.getMethod("zrank", String::class.java, String::class.java)
                .invoke(jedis, key, member) as? Long
        }
    }

    // ---- Pub/Sub ----
    fun publish(channel: String, message: String) {
        jedisOp { jedis ->
            jedis.javaClass.getMethod("publish", String::class.java, String::class.java)
                .invoke(jedis, channel, message)
        }
    }

    // ---- Distributed Lock ----
    fun acquireLock(lockKey: String, ttlSeconds: Long): String? {
        val fullKey = "arc:lock:$lockKey"
        val token = UUID.randomUUID().toString()
        return jedisOp { jedis ->
            val setParamsClass = Class.forName("redis.clients.jedis.params.SetParams")
            // SetParams uses a builder — each call returns a new instance; chain them
            var setParams = setParamsClass.getMethod("setParams").invoke(null)
            setParams = setParamsClass.getMethod("nx").invoke(setParams)
            setParams = setParamsClass.getMethod("ex", Long::class.javaPrimitiveType).invoke(setParams, ttlSeconds)
            val result = jedis.javaClass.getMethod("set", String::class.java, String::class.java, setParamsClass)
                .invoke(jedis, fullKey, token, setParams) as? String
            if (result == "OK") token else null
        }
    }

    fun releaseLock(lockKey: String, token: String): Boolean {
        val fullKey = "arc:lock:$lockKey"
        val script = "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end"
        return jedisOp { jedis ->
            val result = jedis.javaClass.getMethod("eval", String::class.java, Int::class.javaPrimitiveType,
                Array<String>::class.java)
                .invoke(jedis, script, 1, arrayOf(fullKey, token)) as? Long
            result == 1L
        } ?: false
    }

    // ---- Cooldown ----
    fun setCooldown(key: String, ttlSeconds: Long): Boolean {
        return jedisOp { jedis ->
            val setParamsClass = Class.forName("redis.clients.jedis.params.SetParams")
            var setParams = setParamsClass.getMethod("setParams").invoke(null)
            setParams = setParamsClass.getMethod("nx").invoke(setParams)
            setParams = setParamsClass.getMethod("ex", Long::class.javaPrimitiveType).invoke(setParams, ttlSeconds)
            val result = jedis.javaClass.getMethod("set", String::class.java, String::class.java, setParamsClass)
                .invoke(jedis, "arc:cooldown:$key", "1", setParams) as? String
            result == "OK"
        } ?: false
    }

    fun hasCooldown(key: String): Boolean {
        return jedisOp { jedis ->
            jedis.javaClass.getMethod("exists", String::class.java).invoke(jedis, "arc:cooldown:$key") as? Boolean ?: false
        } ?: false
    }

    fun cooldownTtl(key: String): Long {
        return jedisOp { jedis ->
            (jedis.javaClass.getMethod("ttl", String::class.java).invoke(jedis, "arc:cooldown:$key") as? Long) ?: -2
        } ?: -2
    }

    fun clearCooldown(key: String) {
        jedisOp { jedis -> jedis.javaClass.getMethod("del", String::class.java).invoke(jedis, "arc:cooldown:$key") }
    }

    // ---- Internal ----
    private fun <T> jedisOp(op: (Any) -> T): T? {
        if (!connected || jedisPool == null) return null
        return try {
            val pool = jedisPool!!
            val jedis = pool.javaClass.getMethod("getResource").invoke(pool)
            try {
                op(jedis)
            } finally {
                jedis.javaClass.getMethod("close").invoke(jedis)
            }
        } catch (e: Exception) {
            null
        }
    }
}
