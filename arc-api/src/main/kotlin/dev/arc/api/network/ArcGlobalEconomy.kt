package dev.arc.api.network

import java.util.UUID

/**
 * Cross-server economy backed by Redis. All operations are atomic via Lua eval.
 * Balances are stored as Long (integer currency units) in arc:economy:balance:<uuid>.
 */
object ArcGlobalEconomy {

    private fun key(uuid: UUID) = "arc:economy:balance:$uuid"

    fun getBalance(uuid: UUID): Long =
        ArcRelayClient.get(key(uuid))?.toLongOrNull() ?: 0L

    fun setBalance(uuid: UUID, amount: Long) {
        require(amount >= 0) { "Balance cannot be negative" }
        ArcRelayClient.set(key(uuid), amount.toString())
    }

    fun deposit(uuid: UUID, amount: Long): Long {
        require(amount > 0) { "Deposit amount must be positive" }
        return ArcRelayClient.incrBy(key(uuid), amount)
    }

    fun withdraw(uuid: UUID, amount: Long): Boolean {
        require(amount > 0) { "Withdraw amount must be positive" }
        val script = """
            local bal = tonumber(redis.call('GET', KEYS[1])) or 0
            if bal >= tonumber(ARGV[1]) then
                redis.call('SET', KEYS[1], tostring(bal - tonumber(ARGV[1])))
                return 1
            end
            return 0
        """.trimIndent()
        return ArcRelayClient.eval(script, listOf(key(uuid)), listOf(amount.toString())) == 1L
    }

    fun transfer(from: UUID, to: UUID, amount: Long): Boolean {
        require(amount > 0) { "Transfer amount must be positive" }
        val script = """
            local from_bal = tonumber(redis.call('GET', KEYS[1])) or 0
            if from_bal >= tonumber(ARGV[1]) then
                redis.call('SET', KEYS[1], tostring(from_bal - tonumber(ARGV[1])))
                local to_bal = tonumber(redis.call('GET', KEYS[2])) or 0
                redis.call('SET', KEYS[2], tostring(to_bal + tonumber(ARGV[1])))
                return 1
            end
            return 0
        """.trimIndent()
        return ArcRelayClient.eval(script, listOf(key(from), key(to)), listOf(amount.toString())) == 1L
    }

    fun hasBalance(uuid: UUID, amount: Long): Boolean = getBalance(uuid) >= amount
}
