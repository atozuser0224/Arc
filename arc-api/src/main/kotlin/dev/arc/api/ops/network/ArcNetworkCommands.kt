package dev.arc.api.ops.network

import dev.arc.api.network.*
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * `/arc network *` commands — multi-server controls.
 * Wired into ArcOpsCommand.
 */
object ArcNetworkCommands {

    fun complete(args: Array<out String>): List<String> = when (args.size) {
        2 -> listOf("servers", "server", "players", "route", "send", "sendall", "queue", "evacuate",
            "broadcast", "hub", "itemmail", "cooldown", "maintenance", "find", "audit")
        3 -> when (args.getOrNull(1)?.lowercase()) {
            "server" -> ArcServerRegistry.onlineServerIds()
            "send" -> Bukkit.getOnlinePlayers().map { it.name }
            "sendall" -> ArcServerRegistry.onlineServerIds()
            "route", "find" -> Bukkit.getOnlinePlayers().map { it.name }
            "queue" -> listOf("join", "leave", "status", "pause", "resume")
            "itemmail" -> listOf("send", "inbox", "claim", "cancel", "history")
            "maintenance" -> listOf("server", "group", "network", "status")
            "cooldown" -> listOf("check", "clear")
            "evacuate" -> ArcServerRegistry.onlineServerIds()
            "audit" -> listOf("last", "player", "action")
            else -> emptyList()
        }
        4 -> when (args.getOrNull(1)?.lowercase()) {
            "queue" -> when (args.getOrNull(2)?.lowercase()) {
                "join", "pause", "resume" -> ArcServerRegistry.onlineServerIds()
                "check" -> Bukkit.getOnlinePlayers().map { it.name }
                else -> emptyList()
            }
            "sendall" -> ArcServerRegistry.onlineServerIds()
            "maintenance" -> when (args.getOrNull(2)?.lowercase()) {
                "server" -> ArcServerRegistry.onlineServerIds()
                "group" -> ArcNetworkConfig.serverGroups.keys.toList()
                else -> emptyList()
            }
            else -> emptyList()
        }
        else -> emptyList()
    }

    fun dispatch(sender: CommandSender, args: Array<out String>): Boolean {
        val sub = args.getOrNull(1)?.lowercase() ?: run {
            sender.sendMessage("/arc network <servers|server|players|send|queue|maintenance|broadcast|itemmail|evacuate|find|audit|cooldown>")
            return true
        }

        if (!ArcNetworkConfig.enabled) {
            sender.sendMessage("§cArc Network is not enabled. Set arc-network.enabled: true in arc-network.yml")
            return true
        }

        when (sub) {
            // Dashboard
            "servers" -> networkServers(sender)
            "server" -> networkServer(sender, args.getOrNull(2))
            "players" -> networkPlayers(sender)

            // Transfer
            "send" -> {
                val player = args.getOrNull(2)
                val target = args.getOrNull(3)
                if (player == null || target == null) { sender.sendMessage("/arc network send <player> <server>"); return true }
                sender.sendMessage(ArcPlayerTransfer.send(sender, player, target))
            }
            "sendall" -> {
                val target = args.getOrNull(2)
                if (target == null) { sender.sendMessage("/arc network sendall <fromServer> <toServer>"); return true }
                sender.sendMessage(ArcPlayerTransfer.sendAll(sender, ArcNetworkConfig.serverId, target))
            }
            "hub" -> sender.sendMessage(ArcPlayerTransfer.hub(sender))

            // Queue
            "queue" -> queue(sender, args)

            // Maintenance
            "maintenance" -> maintenance(sender, args)
            "evacuate" -> {
                val target = args.getOrNull(2)
                if (target == null) { sender.sendMessage("/arc network evacuate <fromServer> <toServer>"); return true }
                sender.sendMessage(ArcEvacuation.evacuate(sender, ArcNetworkConfig.serverId, target))
            }

            // Broadcast
            "broadcast" -> {
                val msg = args.drop(2).joinToString(" ")
                if (msg.isEmpty()) { sender.sendMessage("/arc network broadcast <message>"); return true }
                ArcNetworkBroadcast.networkBroadcast(sender, msg)
                sender.sendMessage("§aBroadcast sent")
            }

            // Item mail
            "itemmail" -> itemmail(sender, args)

            // Lookup
            "find", "route" -> {
                val name = args.getOrNull(2) ?: run { sender.sendMessage("/arc network $sub <player>"); return true }
                sender.sendMessage(ArcGlobalPlayerLookup.find(name))
            }

            // Audit
            "audit" -> {
                val recent = ArcNetworkAudit.recent(20)
                if (recent.isEmpty()) { sender.sendMessage("[Arc] Network audit is empty"); return true }
                sender.sendMessage("[Arc] Network Audit (last 20):")
                recent.forEach { e ->
                    sender.sendMessage("  §7${e.timestamp} ${e.action}: ${e.metadata}")
                }
            }

            // Cooldown
            "cooldown" -> cooldown(sender, args)

            else -> false
        }
        return true
    }

    private fun networkServers(sender: CommandSender) {
        val servers = ArcServerRegistry.allServers()
        if (servers.isEmpty()) { sender.sendMessage("[Arc] No servers online"); return }
        val localId = ArcNetworkConfig.serverId
        sender.sendMessage("[Arc] Network Servers (${servers.size}):")
        servers.sortedBy { it.group }.forEach { s ->
            val statusTag = when (s.status) {
                ArcServerRegistry.ServerStatus.ONLINE -> "§a●"
                ArcServerRegistry.ServerStatus.DEGRADED -> "§e●"
                ArcServerRegistry.ServerStatus.FULL -> "§6●"
                ArcServerRegistry.ServerStatus.MAINTENANCE -> "§c●"
                else -> "§7●"
            }
            val local = if (s.id == localId) " §7(this)" else ""
            sender.sendMessage("  $statusTag §e${s.id} §7group=${s.group} players=${s.players}/${s.maxPlayers} tps=${"%.1f".format(s.tps[0])}$local")
        }
    }

    private fun networkServer(sender: CommandSender, name: String?) {
        val info = name?.let { ArcServerRegistry.getServer(it) } ?: ArcServerRegistry.localServerInfo
        if (info == null) { sender.sendMessage("[Arc] server not found: $name"); return }
        sender.sendMessage("[Arc] Server §e${info.id}:")
        sender.sendMessage("  group: ${info.group}  status: ${info.status}")
        sender.sendMessage("  players: ${info.players}/${info.maxPlayers}  tps: ${info.tps[0]} mspt: ${info.mspt}")
        sender.sendMessage("  mc: ${info.minecraftVersion}  arc: ${info.arcVersion}")
        sender.sendMessage("  mem: ${info.memoryUsedMb}/${info.memoryMaxMb} MB  cpu: ${"%.0f".format(info.cpuLoad*100)}%")
        sender.sendMessage("  uptime: ${info.uptimeMinutes}min  gc: ${info.gcCount}")
    }

    private fun networkPlayers(sender: CommandSender) {
        sender.sendMessage("[Arc] Online Players:")
        ArcServerRegistry.allServers().forEach { s ->
            if (s.players > 0) sender.sendMessage("  §e${s.id}: §7${s.players} player(s)")
        }
    }

    private fun queue(sender: CommandSender, args: Array<out String>) {
        when (args.getOrNull(2)?.lowercase()) {
            "join" -> {
                val target = args.getOrNull(3) ?: run { sender.sendMessage("/arc network queue join <server>"); return }
                val player = sender as? Player ?: run { sender.sendMessage("Player-only"); return }
                sender.sendMessage(ArcPlayerTransfer.queue(player, target))
            }
            "leave" -> {
                val player = sender as? Player ?: return
                ArcServerRegistry.onlineServerIds().forEach { ArcQueue.removeFromQueue(player.uniqueId, it) }
                sender.sendMessage("§aLeft all queues")
            }
            "status" -> { val p = sender as? Player; if (p != null) sender.sendMessage(ArcQueue.getPosition(p)) }
            "pause" -> {
                val target = args.getOrNull(3) ?: run { sender.sendMessage("/arc network queue pause <server>"); return }
                ArcQueue.pauseQueue(target); sender.sendMessage("§aQueue paused for $target")
            }
            "resume" -> {
                val target = args.getOrNull(3) ?: run { sender.sendMessage("/arc network queue resume <server>"); return }
                ArcQueue.resumeQueue(target); sender.sendMessage("§aQueue resumed for $target")
            }
            else -> sender.sendMessage("/arc network queue <join|leave|status|pause|resume>")
        }
    }

    private fun maintenance(sender: CommandSender, args: Array<out String>) {
        when (args.getOrNull(2)?.lowercase()) {
            "server" -> {
                val name = args.getOrNull(3) ?: run { sender.sendMessage("/arc network maintenance server <id> on|off"); return }
                val on = args.getOrNull(4)?.lowercase() == "on"
                ArcNetworkMaintenance.setServerMaintenance(name, on)
                sender.sendMessage("§aMaintenance ${if (on) "ON" else "OFF"} for $name")
            }
            "group" -> {
                val grp = args.getOrNull(3) ?: run { sender.sendMessage("/arc network maintenance group <name> on|off"); return }
                val on = args.getOrNull(4)?.lowercase() == "on"
                ArcNetworkMaintenance.setGroupMaintenance(grp, on)
                sender.sendMessage("§aMaintenance ${if (on) "ON" else "OFF"} for group $grp")
            }
            "network" -> {
                val on = args.getOrNull(3)?.lowercase() == "on"
                ArcNetworkMaintenance.setNetworkMaintenance(on)
                sender.sendMessage("§aNetwork-wide maintenance ${if (on) "ON" else "OFF"}")
            }
            "status" -> sender.sendMessage(ArcNetworkMaintenance.status())
            else -> sender.sendMessage("/arc network maintenance <server|group|network|status>")
        }
    }

    private fun itemmail(sender: CommandSender, args: Array<out String>) {
        when (args.getOrNull(2)?.lowercase()) {
            "send" -> {
                val receiver = args.getOrNull(3)
                if (receiver == null) { sender.sendMessage("/arc network itemmail send <player>"); return }
                val p = sender as? Player ?: return
                sender.sendMessage(ArcItemMail.send(p, receiver))
            }
            "inbox" -> { val p = sender as? Player ?: return; sender.sendMessage(ArcItemMail.inbox(p)) }
            "claim" -> {
                val id = args.getOrNull(3)?.toLongOrNull() ?: run { sender.sendMessage("/arc network itemmail claim <id>"); return }
                val p = sender as? Player ?: return
                sender.sendMessage(ArcItemMail.claim(p, id))
            }
            "cancel" -> {
                val id = args.getOrNull(3)?.toLongOrNull() ?: run { sender.sendMessage("/arc network itemmail cancel <id>"); return }
                sender.sendMessage(ArcItemMail.cancel(sender as? Player ?: return, id))
            }
            "history" -> sender.sendMessage("[Arc] Mail history: (SQL impl required)")
            else -> sender.sendMessage("/arc network itemmail <send|inbox|claim|cancel|history>")
        }
    }

    private fun cooldown(sender: CommandSender, args: Array<out String>) {
        when (args.getOrNull(2)?.lowercase()) {
            "check" -> {
                val player = args.getOrNull(3)?.let { Bukkit.getPlayer(it) }
                val key = args.getOrNull(4)
                if (player == null || key == null) { sender.sendMessage("/arc network cooldown check <player> <key>"); return }
                val active = ArcGlobalCooldown.isActive(player.uniqueId, key)
                val remaining = ArcGlobalCooldown.remaining(player.uniqueId, key)
                sender.sendMessage(if (active) "§eCooldown active: ${remaining}s remaining" else "§aNo cooldown")
            }
            "clear" -> {
                val player = args.getOrNull(3)?.let { Bukkit.getPlayer(it) }
                val key = args.getOrNull(4)
                if (player == null || key == null) { sender.sendMessage("/arc network cooldown clear <player> <key>"); return }
                ArcGlobalCooldown.clear(player.uniqueId, key)
                sender.sendMessage("§aCooldown cleared for $key")
            }
            else -> sender.sendMessage("/arc network cooldown <check|clear> <player> <key>")
        }
    }
}
