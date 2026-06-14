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

    // ---- Lists ----
    fun lpush(key: String, value: String) {
        jedisOp { jedis ->
            jedis.javaClass.getMethod("lpush", String::class.java, Array<String>::class.java)
                .invoke(jedis, key, arrayOf(value))
        }
    }

    fun rpopBatch(key: String, max: Int = 50): List<String> {
        return jedisOp { jedis ->
            try {
                // Jedis 4+: rpop(key, count)
                @Suppress("UNCHECKED_CAST")
                (jedis.javaClass.getMethod("rpop", String::class.java, Int::class.javaPrimitiveType)
                    .invoke(jedis, key, max) as? List<String>) ?: emptyList()
            } catch (_: NoSuchMethodException) {
                // Jedis 3: rpop one at a time
                val result = mutableListOf<String>()
                val rpop1 = jedis.javaClass.getMethod("rpop", String::class.java)
                for (i in 0 until max) {
                    val msg = rpop1.invoke(jedis, key) as? String ?: break
                    result += msg
                }
                result
            }
        } ?: emptyList()
    }

    // ---- Sorted Sets (extended) ----
    fun zrange(key: String, start: Long, stop: Long): List<String> {
        return jedisOp { jedis ->
            @Suppress("UNCHECKED_CAST")
            (jedis.javaClass.getMethod("zrange", String::class.java, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)
                .invoke(jedis, key, start, stop) as? Collection<String>)?.toList() ?: emptyList()
        } ?: emptyList()
    }

    fun exists(key: String): Boolean {
        return jedisOp { jedis ->
            // Jedis 3 returns Boolean; Jedis 4 returns Long (count of matching keys)
            when (val r = jedis.javaClass.getMethod("exists", String::class.java).invoke(jedis, key)) {
                is Boolean -> r
                is Number -> r.toLong() > 0L
                else -> false
            }
        } ?: false
    }

    fun zremrangebyscore(key: String, min: Double, max: Double): Long {
        return jedisOp { jedis ->
            jedis.javaClass.getMethod("zremrangeByScore", String::class.java, Double::class.javaPrimitiveType, Double::class.javaPrimitiveType)
                .invoke(jedis, key, min, max) as? Long ?: 0L
        } ?: 0L
    }

    fun zrevrange(key: String, start: Long, stop: Long): List<String> {
        return jedisOp { jedis ->
            @Suppress("UNCHECKED_CAST")
            (jedis.javaClass.getMethod("zrevrange", String::class.java, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)
                .invoke(jedis, key, start, stop) as? Collection<String>)?.toList() ?: emptyList()
        } ?: emptyList()
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
            when (val r = jedis.javaClass.getMethod("exists", String::class.java).invoke(jedis, "arc:cooldown:$key")) {
                is Boolean -> r
                is Number -> r.toLong() > 0L
                else -> false
            }
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

    // ---- Score queries ----
    fun zscore(key: String, member: String): Double? {
        return jedisOp { jedis ->
            jedis.javaClass.getMethod("zscore", String::class.java, String::class.java)
                .invoke(jedis, key, member) as? Double
        }
    }

    fun zrevrank(key: String, member: String): Long? {
        return jedisOp { jedis ->
            jedis.javaClass.getMethod("zrevrank", String::class.java, String::class.java)
                .invoke(jedis, key, member) as? Long
        }
    }

    // ---- Plain SET (no TTL) ----
    fun set(key: String, value: String) {
        jedisOp { jedis ->
            jedis.javaClass.getMethod("set", String::class.java, String::class.java).invoke(jedis, key, value)
        }
    }

    // ---- Atomic increment ----
    fun incrBy(key: String, amount: Long): Long {
        return jedisOp { jedis ->
            jedis.javaClass.getMethod("incrBy", String::class.java, Long::class.javaPrimitiveType)
                .invoke(jedis, key, amount) as? Long ?: 0L
        } ?: 0L
    }

    // ---- Lua eval ----
    fun eval(script: String, keys: List<String>, args: List<String>): Long {
        return jedisOp { jedis ->
            val result = jedis.javaClass.getMethod(
                "eval", String::class.java, java.util.List::class.java, java.util.List::class.java
            ).invoke(jedis, script, keys, args)
            when (result) {
                is Long -> result
                is Number -> result.toLong()
                else -> 0L
            }
        } ?: 0L
    }

    // ---- Hash ----
    fun hget(key: String, field: String): String? {
        return jedisOp { jedis ->
            jedis.javaClass.getMethod("hget", String::class.java, String::class.java)
                .invoke(jedis, key, field) as? String
        }
    }

    fun hset(key: String, field: String, value: String) {
        jedisOp { jedis ->
            jedis.javaClass.getMethod("hset", String::class.java, String::class.java, String::class.java)
                .invoke(jedis, key, field, value)
        }
    }

    fun hdel(key: String, field: String) {
        jedisOp { jedis ->
            jedis.javaClass.getMethod("hdel", String::class.java, Array<String>::class.java)
                .invoke(jedis, key, arrayOf(field))
        }
    }

    fun hgetAll(key: String): Map<String, String> {
        return jedisOp { jedis ->
            @Suppress("UNCHECKED_CAST")
            (jedis.javaClass.getMethod("hgetAll", String::class.java).invoke(jedis, key) as? Map<String, String>)
                ?: emptyMap()
        } ?: emptyMap()
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
