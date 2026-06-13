package dev.arc.api.network

import java.io.File
import org.bukkit.configuration.file.YamlConfiguration

/**
 * Loads `arc-network.yml` — all multi-server configuration.
 * Safe to call before relay connection is established.
 */
object ArcNetworkConfig {

    data class RelayConfig(
        val type: String = "redis",
        val host: String = "127.0.0.1",
        val port: Int = 6379,
        val password: String = "",
        val database: Int = 0,
        val timeoutMs: Int = 5000,
        val poolSize: Int = 8,
        val ssl: Boolean = false,
    )

    data class StorageConfig(
        val type: String = "postgresql",
        val host: String = "127.0.0.1",
        val port: Int = 5432,
        val database: String = "arc_network",
        val username: String = "arc",
        val password: String = "",
        val poolSize: Int = 10,
        val ssl: Boolean = true,
    )

    data class HeartbeatConfig(
        val intervalSeconds: Int = 2,
        val ttlSeconds: Int = 10,
        val staleThresholdSeconds: Int = 30,
        val statusPublish: Boolean = true,
    )

    data class TransferConfig(
        val enabled: Boolean = true,
        val saveBeforeTransfer: Boolean = true,
        val requirePermission: Boolean = true,
        val permissionPrefix: String = "arc.transfer",
        val logAllTransfers: Boolean = true,
    )

    data class QueueConfig(
        val enabled: Boolean = true,
        val priorityPermission: String = "arc.queue.priority",
        val vipPermission: String = "arc.queue.vip",
        val keepOnDisconnectSeconds: Int = 300,
        val transferCheckIntervalSeconds: Int = 5,
        val maxQueueSize: Int = 1000,
        val positionMessageInterval: Int = 30,
    )

    data class GlobalVaultConfig(
        val enabled: Boolean = false,
        val defaultSlots: Int = 9,
        val maxSlots: Int = 54,
        val perGroup: Boolean = true,
        val slotPermissions: Map<String, Int> = mapOf("arc.vault.plus9" to 9, "arc.vault.plus18" to 18, "arc.vault.plus27" to 27),
    )

    data class InventoryTransferConfig(
        val enabled: Boolean = false,
        val requireSameMinecraftVersion: Boolean = true,
        val requireSameArcItemFormat: Boolean = true,
        val allowedGroups: List<String> = emptyList(),
        val blockedGroups: List<String> = listOf("lobby", "creative"),
        val maxInventorySlots: Int = 41,
        val saveBeforeTransfer: Boolean = true,
    )

    data class AuditConfig(
        val enabled: Boolean = true,
        val logAllAdminActions: Boolean = true,
        val logAllTransfers: Boolean = true,
        val logAllVault: Boolean = true,
        val retentionDays: Int = 90,
        val sensitiveDataMasking: Boolean = true,
    )

    data class RemoteCommandConfig(
        val enabled: Boolean = false,
        val requireConfirm: Boolean = true,
        val rateLimit: Int = 3,
        val allowlist: List<String> = listOf("say", "save-all"),
        val blocklist: List<String> = listOf("op", "deop", "stop", "reload", "restart", "ban", "kick", "whitelist"),
    )

    data class ServerGroupConfig(
        val servers: List<String>,
        val defaultHub: Boolean = false,
        val allowTransfersFrom: List<String> = listOf("*"),
        val queueEnabled: Boolean = false,
        val globalVault: Boolean = false,
    )

    // ---- top-level config ----
    @Volatile var enabled: Boolean = false
    @Volatile var serverId: String = "unknown"
    @Volatile var serverGroup: String = "default"
    @Volatile var registrationToken: String = ""
    @Volatile var tags: List<String> = emptyList()

    var relay: RelayConfig = RelayConfig()
    var storage: StorageConfig = StorageConfig()
    var heartbeat: HeartbeatConfig = HeartbeatConfig()
    var transfer: TransferConfig = TransferConfig()
    var queue: QueueConfig = QueueConfig()
    var globalVault: GlobalVaultConfig = GlobalVaultConfig()
    var inventoryTransfer: InventoryTransferConfig = InventoryTransferConfig()
    var audit: AuditConfig = AuditConfig()
    var remoteCommand: RemoteCommandConfig = RemoteCommandConfig()
    var lockDefaultTtlSeconds: Int = 10
    var lockMaxTtlSeconds: Int = 60
    var cooldownEnabled: Boolean = true
    var cooldownRedisPrefix: String = "arc:cooldown"
    var configSyncEnabled: Boolean = false
    var pluginDeployEnabled: Boolean = false
    var proxyType: String = "velocity"
    var forwardingSecret: String = ""
    var modernForwarding: Boolean = true
    var maintenanceDefaultMessage: String = "&cServer under maintenance"
    var maintenanceKickExisting: Boolean = false

    val serverGroups: MutableMap<String, ServerGroupConfig> = LinkedHashMap()

    private val configFile = File("arc-network.yml")

    fun load() {
        if (!configFile.exists()) {
            saveDefaults()
            return
        }
        val yml = YamlConfiguration.loadConfiguration(configFile)

        enabled = yml.getBoolean("arc-network.enabled", false)
        serverId = yml.getString("arc-network.server.id", "unknown") ?: "unknown"
        serverGroup = yml.getString("arc-network.server.group", "default") ?: "default"
        registrationToken = resolveEnv(yml.getString("arc-network.server.registration-token", ""))
        tags = yml.getStringList("arc-network.server.tags")

        relay = RelayConfig(
            type = yml.getString("arc-network.relay.type", "redis") ?: "redis",
            host = yml.getString("arc-network.relay.redis.host", "127.0.0.1") ?: "127.0.0.1",
            port = yml.getInt("arc-network.relay.redis.port", 6379),
            password = resolveEnv(yml.getString("arc-network.relay.redis.password", "")),
            database = yml.getInt("arc-network.relay.redis.database", 0),
            timeoutMs = yml.getInt("arc-network.relay.redis.timeout-ms", 5000),
            poolSize = yml.getInt("arc-network.relay.redis.pool-size", 8),
            ssl = yml.getBoolean("arc-network.relay.redis.ssl", false),
        )

        storage = StorageConfig(
            type = string(yml, "storage.type", "postgresql"),
            host = string(yml, "storage.host", "127.0.0.1"),
            port = yml.getInt(key("storage.port"), 5432),
            database = string(yml, "storage.database", "arc_network"),
            username = string(yml, "storage.username", "arc"),
            password = resolveEnv(yml.getString(key("storage.password"), "")),
            poolSize = yml.getInt(key("storage.pool-size"), 10),
            ssl = yml.getBoolean(key("storage.ssl"), true),
        )
        heartbeat = HeartbeatConfig(
            intervalSeconds = yml.getInt(key("heartbeat.interval-seconds"), 2),
            ttlSeconds = yml.getInt(key("heartbeat.ttl-seconds"), 10),
            staleThresholdSeconds = yml.getInt(key("heartbeat.stale-threshold-seconds"), 30),
            statusPublish = yml.getBoolean(key("heartbeat.status-publish"), true),
        )
        transfer = TransferConfig(
            enabled = yml.getBoolean(key("transfer.enabled"), true),
            saveBeforeTransfer = yml.getBoolean(key("transfer.save-before-transfer"), true),
            requirePermission = yml.getBoolean(key("transfer.require-permission"), true),
            permissionPrefix = string(yml, "transfer.permission-prefix", "arc.transfer"),
            logAllTransfers = yml.getBoolean(key("transfer.log-all-transfers"), true),
        )
        queue = QueueConfig(
            enabled = yml.getBoolean(key("queue.enabled"), true),
            priorityPermission = string(yml, "queue.priority-permission", "arc.queue.priority"),
            vipPermission = string(yml, "queue.vip-permission", "arc.queue.vip"),
            keepOnDisconnectSeconds = yml.getInt(key("queue.keep-on-disconnect-seconds"), 300),
            transferCheckIntervalSeconds = yml.getInt(key("queue.transfer-check-interval-seconds"), 5),
            maxQueueSize = yml.getInt(key("queue.max-queue-size"), 1000),
            positionMessageInterval = yml.getInt(key("queue.position-message-interval"), 30),
        )
        globalVault = GlobalVaultConfig(
            enabled = yml.getBoolean(key("global-vault.enabled"), false),
            defaultSlots = yml.getInt(key("global-vault.default-slots"), 9),
            maxSlots = yml.getInt(key("global-vault.max-slots"), 54),
            perGroup = yml.getBoolean(key("global-vault.per-group"), true),
            slotPermissions = readIntMap(yml, "global-vault.slot-permissions", GlobalVaultConfig().slotPermissions),
        )
        inventoryTransfer = InventoryTransferConfig(
            enabled = yml.getBoolean(key("inventory-transfer.enabled"), false),
            requireSameMinecraftVersion = yml.getBoolean(key("inventory-transfer.require-same-minecraft-version"), true),
            requireSameArcItemFormat = yml.getBoolean(key("inventory-transfer.require-same-arc-item-format"), true),
            allowedGroups = yml.getStringList(key("inventory-transfer.allowed-groups")),
            blockedGroups = yml.getStringList(key("inventory-transfer.blocked-groups")).ifEmpty {
                InventoryTransferConfig().blockedGroups
            },
            maxInventorySlots = yml.getInt(key("inventory-transfer.max-inventory-slots"), 41),
            saveBeforeTransfer = yml.getBoolean(key("inventory-transfer.save-before-transfer"), true),
        )
        audit = AuditConfig(
            enabled = yml.getBoolean(key("audit.enabled"), true),
            logAllAdminActions = yml.getBoolean(key("audit.log-all-admin-actions"), true),
            logAllTransfers = yml.getBoolean(key("audit.log-all-transfers"), true),
            logAllVault = yml.getBoolean(key("audit.log-all-vault"), true),
            retentionDays = yml.getInt(key("audit.retention-days"), 90),
            sensitiveDataMasking = yml.getBoolean(key("audit.sensitive-data-masking"), true),
        )
        remoteCommand = RemoteCommandConfig(
            enabled = yml.getBoolean(key("remote-command.enabled"), false),
            requireConfirm = yml.getBoolean(key("remote-command.require-confirm"), true),
            rateLimit = yml.getInt(key("remote-command.rate-limit"), 3),
            allowlist = yml.getStringList(key("remote-command.allowlist")).ifEmpty {
                RemoteCommandConfig().allowlist
            },
            blocklist = yml.getStringList(key("remote-command.blocklist")).ifEmpty {
                RemoteCommandConfig().blocklist
            },
        )
        lockDefaultTtlSeconds = yml.getInt(key("lock.default-ttl-seconds"), 10)
        lockMaxTtlSeconds = yml.getInt(key("lock.max-ttl-seconds"), 60)
        cooldownEnabled = yml.getBoolean(key("cooldown.enabled"), true)
        cooldownRedisPrefix = string(yml, "cooldown.redis-prefix", "arc:cooldown")
        configSyncEnabled = yml.getBoolean(key("config-sync.enabled"), false)
        pluginDeployEnabled = yml.getBoolean(key("plugin-deploy.enabled"), false)
        proxyType = string(yml, "proxy.type", "velocity")
        forwardingSecret = resolveEnv(yml.getString(key("proxy.forwarding-secret"), ""))
        modernForwarding = yml.getBoolean(key("proxy.modern-forwarding"), true)
        maintenanceDefaultMessage = string(yml, "maintenance.default-message", "&cServer under maintenance")
        maintenanceKickExisting = yml.getBoolean(key("maintenance.kick-existing"), false)

        loadGroups(yml)
    }

    private fun loadGroups(yml: YamlConfiguration) {
        serverGroups.clear()
        val sec = yml.getConfigurationSection("arc-network.server-groups") ?: return
        for (groupName in sec.getKeys(false)) {
            val gs = sec.getConfigurationSection(groupName) ?: continue
            serverGroups[groupName] = ServerGroupConfig(
                servers = gs.getStringList("servers"),
                defaultHub = gs.getBoolean("default-hub", false),
                allowTransfersFrom = gs.getStringList("allow-transfers-from"),
                queueEnabled = gs.getBoolean("queue-enabled", false),
                globalVault = gs.getBoolean("global-vault", false),
            )
        }
    }

    private fun saveDefaults() {
        configFile.parentFile?.mkdirs()
        val yml = YamlConfiguration()
        yml.options().setHeader(listOf("Arc Network Configuration"))
        yml.set("arc-network.enabled", false)
        yml.set("arc-network.server.id", "server-1")
        yml.set("arc-network.server.group", "default")
        yml.set("arc-network.relay.redis.host", "127.0.0.1")
        yml.set("arc-network.relay.redis.port", 6379)
        yml.set("arc-network.global-vault.enabled", false)
        yml.set("arc-network.inventory-transfer.enabled", false)
        yml.set("arc-network.remote-command.enabled", false)
        yml.set("arc-network.plugin-deploy.enabled", false)
        yml.save(configFile)
    }

    private fun key(path: String) = "arc-network.$path"

    private fun string(yml: YamlConfiguration, path: String, default: String): String =
        yml.getString(key(path), default) ?: default

    private fun readIntMap(
        yml: YamlConfiguration,
        path: String,
        default: Map<String, Int>,
    ): Map<String, Int> {
        val section = yml.getConfigurationSection(key(path)) ?: return default
        return section.getKeys(false).associateWith { section.getInt(it) }
    }

    private fun resolveEnv(value: String?): String {
        if (value == null) return ""
        val envPattern = Regex("\\$\\{(\\w+)}")
        return envPattern.replace(value) { System.getenv(it.groupValues[1]) ?: "" }
    }
}
