package dev.arc.api.network

import java.util.UUID

/**
 * Global cooldown across all servers via Redis.
 */
object ArcGlobalCooldown {

    fun set(playerUuid: UUID, key: String, seconds: Long): Boolean {
        return ArcRelayClient.setCooldown("${playerUuid}:$key", seconds)
    }

    fun isActive(playerUuid: UUID, key: String): Boolean {
        return ArcRelayClient.hasCooldown("${playerUuid}:$key")
    }

    fun remaining(playerUuid: UUID, key: String): Long {
        return ArcRelayClient.cooldownTtl("${playerUuid}:$key")
    }

    fun clear(playerUuid: UUID, key: String) {
        ArcRelayClient.clearCooldown("${playerUuid}:$key")
    }
}
