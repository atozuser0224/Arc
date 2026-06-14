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
            "broadcast", "hub", "cooldown", "maintenance", "find", "audit", "remote-cmd",
            "ban", "unban", "mute", "unmute", "economy", "globalconfig")
        3 -> when (args.getOrNull(1)?.lowercase()) {
            "server" -> ArcServerRegistry.onlineServerIds()
            "send" -> Bukkit.getOnlinePlayers().map { it.name }
            "sendall" -> ArcServerRegistry.onlineServerIds()
            "route", "find" -> Bukkit.getOnlinePlayers().map { it.name }
            "queue" -> listOf("join", "leave", "status", "pause", "resume")
            "maintenance" -> listOf("server", "group", "network", "status")
            "cooldown" -> listOf("check", "clear")
            "evacuate" -> ArcServerRegistry.onlineServerIds()
            "remote-cmd" -> ArcServerRegistry.onlineServerIds()
            "audit" -> listOf("last", "player", "action")
            "ban", "mute" -> Bukkit.getOnlinePlayers().map { it.name }
            "unban", "unmute" -> Bukkit.getOnlinePlayers().map { it.name }
            "economy" -> listOf("balance", "give", "take", "set", "transfer")
            "globalconfig" -> listOf("get", "set", "list", "del")
            else -> emptyList()
        }
        4 -> when (args.getOrNull(1)?.lowercase()) {
            "send" -> ArcServerRegistry.onlineServerIds()
            "queue" -> when (args.getOrNull(2)?.lowercase()) {
                "join", "pause", "resume" -> ArcServerRegistry.onlineServerIds()
                else -> emptyList()
            }
            "sendall" -> ArcServerRegistry.onlineServerIds()
            "maintenance" -> when (args.getOrNull(2)?.lowercase()) {
                "server" -> ArcServerRegistry.onlineServerIds()
                "group" -> ArcNetworkConfig.serverGroups.keys.toList()
                "network" -> listOf("on", "off")
                else -> emptyList()
            }
            "cooldown" -> Bukkit.getOnlinePlayers().map { it.name }
            else -> emptyList()
        }
        5 -> when (args.getOrNull(1)?.lowercase()) {
            "maintenance" -> if (args.getOrNull(2)?.lowercase() in listOf("server", "group")) {
                listOf("on", "off")
            } else {
                emptyList()
            }
            else -> emptyList()
        }
        else -> emptyList()
    }

    fun dispatch(sender: CommandSender, args: Array<out String>): Boolean {
        val sub = args.getOrNull(1)?.lowercase() ?: run {
            sender.sendMessage("/arc network <servers|server|players|send|queue|maintenance|broadcast|evacuate|find|audit|cooldown>")
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
                if (target == null) { sender.sendMessage("/arc network sendall <server>"); return true }
                sender.sendMessage(ArcPlayerTransfer.sendAll(sender, ArcNetworkConfig.serverId, target))
            }
            "hub" -> sender.sendMessage(ArcPlayerTransfer.hub(sender))

            // Queue
            "queue" -> queue(sender, args)

            // Maintenance
            "maintenance" -> maintenance(sender, args)
            "evacuate" -> {
                val target = args.getOrNull(2)
                if (target == null) { sender.sendMessage("/arc network evacuate <server>"); return true }
                sender.sendMessage(ArcEvacuation.evacuate(sender, ArcNetworkConfig.serverId, target))
            }

            // Broadcast
            "broadcast" -> {
                val msg = args.drop(2).joinToString(" ")
                if (msg.isEmpty()) { sender.sendMessage("/arc network broadcast <message>"); return true }
                ArcNetworkBroadcast.networkBroadcast(sender, msg)
                sender.sendMessage("§aBroadcast sent")
            }

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
                    sender.sendMessage("  §7[${e.traceId}] §e${e.action} §7by ${e.actorName} — ${e.metadata.entries.joinToString { "${it.key}=${it.value}" }}")
                }
            }

            // Remote command
            "remote-cmd" -> remoteCmd(sender, args)

            // Cooldown
            "cooldown" -> cooldown(sender, args)

            // Ban / Mute
            "ban" -> ban(sender, args)
            "unban" -> {
                val name = args.getOrNull(2) ?: run { sender.sendMessage("/arc network unban <player>"); return true }
                ArcGlobalBan.unban(Bukkit.getOfflinePlayer(name).uniqueId)
                sender.sendMessage("§aUnbanned $name")
            }
            "mute" -> mute(sender, args)
            "unmute" -> {
                val name = args.getOrNull(2) ?: run { sender.sendMessage("/arc network unmute <player>"); return true }
                ArcGlobalBan.unmute(Bukkit.getOfflinePlayer(name).uniqueId)
                sender.sendMessage("§aUnmuted $name")
            }

            // Economy
            "economy" -> economy(sender, args)

            // Global config
            "globalconfig" -> globalConfig(sender, args)

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
                val on = parseToggle(sender, args.getOrNull(4), "/arc network maintenance server <id> on|off") ?: return
                ArcNetworkMaintenance.setServerMaintenance(name, on)
                sender.sendMessage("§aMaintenance ${if (on) "ON" else "OFF"} for $name")
            }
            "group" -> {
                val grp = args.getOrNull(3) ?: run { sender.sendMessage("/arc network maintenance group <name> on|off"); return }
                val on = parseToggle(sender, args.getOrNull(4), "/arc network maintenance group <name> on|off") ?: return
                ArcNetworkMaintenance.setGroupMaintenance(grp, on)
                sender.sendMessage("§aMaintenance ${if (on) "ON" else "OFF"} for group $grp")
            }
            "network" -> {
                val on = parseToggle(sender, args.getOrNull(3), "/arc network maintenance network on|off") ?: return
                ArcNetworkMaintenance.setNetworkMaintenance(on)
                sender.sendMessage("§aNetwork-wide maintenance ${if (on) "ON" else "OFF"}")
            }
            "status" -> sender.sendMessage(ArcNetworkMaintenance.status())
            else -> sender.sendMessage("/arc network maintenance <server|group|network|status>")
        }
    }

    private fun parseToggle(sender: CommandSender, raw: String?, usage: String): Boolean? {
        return when (raw?.lowercase()) {
            "on" -> true
            "off" -> false
            else -> null.also { sender.sendMessage(usage) }
        }
    }

    private fun remoteCmd(sender: CommandSender, args: Array<out String>): Boolean {
        if (!ArcNetworkConfig.remoteCommand.enabled) {
            sender.sendMessage("[Arc] §cRemote command disabled — set arc-network.remote-command.enabled: true")
            return true
        }
        val target = args.getOrNull(2) ?: run {
            sender.sendMessage("/arc network remote-cmd <server> <command...>"); return true
        }
        val cmd = args.drop(3).joinToString(" ")
        if (cmd.isEmpty()) { sender.sendMessage("/arc network remote-cmd <server> <command...>"); return true }
        if (ArcServerRegistry.getServer(target) == null) {
            sender.sendMessage("[Arc] §cServer '§e$target§c' not found or offline"); return true
        }
        ArcNetworkInbox.sendRemoteCommand(target, cmd, "${sender.name}@${ArcNetworkConfig.serverId}")
        ArcNetworkAudit.log("remote-command.send", mapOf("target" to target, "command" to cmd, "actor" to sender.name))
        sender.sendMessage("[Arc] §aDispatched to §e$target§a: §7$cmd")
        return true
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

    private fun ban(sender: CommandSender, args: Array<out String>) {
        val name = args.getOrNull(2) ?: run { sender.sendMessage("/arc network ban <player> [duration_seconds] [reason...]"); return }
        val player = Bukkit.getOfflinePlayer(name)
        val duration = args.getOrNull(3)?.toLongOrNull()
        val reason = if (duration != null) args.drop(4).joinToString(" ").ifEmpty { "Banned by network admin" }
                     else args.drop(3).joinToString(" ").ifEmpty { "Banned by network admin" }
        val entry = ArcGlobalBan.ban(player.uniqueId, name, reason, sender.name, duration)
        Bukkit.getPlayer(player.uniqueId)?.kickPlayer("§cYou have been banned.\n§7Reason: §f$reason")
        val expStr = entry.expiry?.let { "${duration}s" } ?: "permanent"
        sender.sendMessage("§aBanned §e$name §a($expStr): §7$reason")
    }

    private fun mute(sender: CommandSender, args: Array<out String>) {
        val name = args.getOrNull(2) ?: run { sender.sendMessage("/arc network mute <player> [duration_seconds] [reason...]"); return }
        val player = Bukkit.getOfflinePlayer(name)
        val duration = args.getOrNull(3)?.toLongOrNull()
        val reason = if (duration != null) args.drop(4).joinToString(" ").ifEmpty { "Muted by network admin" }
                     else args.drop(3).joinToString(" ").ifEmpty { "Muted by network admin" }
        ArcGlobalBan.mute(player.uniqueId, name, reason, sender.name, duration)
        val expStr = duration?.let { "${it}s" } ?: "permanent"
        sender.sendMessage("§aMuted §e$name §a($expStr): §7$reason")
    }

    private fun economy(sender: CommandSender, args: Array<out String>) {
        when (args.getOrNull(2)?.lowercase()) {
            "balance" -> {
                val name = args.getOrNull(3) ?: run { sender.sendMessage("/arc network economy balance <player>"); return }
                val player = Bukkit.getOfflinePlayer(name)
                val bal = ArcGlobalEconomy.getBalance(player.uniqueId)
                sender.sendMessage("§e$name §7balance: §a$bal")
            }
            "give" -> {
                val name = args.getOrNull(3) ?: run { sender.sendMessage("/arc network economy give <player> <amount>"); return }
                val amount = args.getOrNull(4)?.toLongOrNull() ?: run { sender.sendMessage("§cInvalid amount"); return }
                val player = Bukkit.getOfflinePlayer(name)
                val newBal = ArcGlobalEconomy.deposit(player.uniqueId, amount)
                sender.sendMessage("§aGave §e$amount §ato §e$name§a. New balance: §f$newBal")
            }
            "take" -> {
                val name = args.getOrNull(3) ?: run { sender.sendMessage("/arc network economy take <player> <amount>"); return }
                val amount = args.getOrNull(4)?.toLongOrNull() ?: run { sender.sendMessage("§cInvalid amount"); return }
                val player = Bukkit.getOfflinePlayer(name)
                val success = ArcGlobalEconomy.withdraw(player.uniqueId, amount)
                if (success) sender.sendMessage("§aTook §e$amount §afrom §e$name§a. New balance: §f${ArcGlobalEconomy.getBalance(player.uniqueId)}")
                else sender.sendMessage("§c$name has insufficient balance (${ArcGlobalEconomy.getBalance(player.uniqueId)})")
            }
            "set" -> {
                val name = args.getOrNull(3) ?: run { sender.sendMessage("/arc network economy set <player> <amount>"); return }
                val amount = args.getOrNull(4)?.toLongOrNull() ?: run { sender.sendMessage("§cInvalid amount"); return }
                val player = Bukkit.getOfflinePlayer(name)
                ArcGlobalEconomy.setBalance(player.uniqueId, amount)
                sender.sendMessage("§aSet §e$name §abalance to §f$amount")
            }
            "transfer" -> {
                val from = args.getOrNull(3) ?: run { sender.sendMessage("/arc network economy transfer <from> <to> <amount>"); return }
                val to = args.getOrNull(4) ?: run { sender.sendMessage("/arc network economy transfer <from> <to> <amount>"); return }
                val amount = args.getOrNull(5)?.toLongOrNull() ?: run { sender.sendMessage("§cInvalid amount"); return }
                val fromPlayer = Bukkit.getOfflinePlayer(from)
                val toPlayer = Bukkit.getOfflinePlayer(to)
                val success = ArcGlobalEconomy.transfer(fromPlayer.uniqueId, toPlayer.uniqueId, amount)
                if (success) sender.sendMessage("§aTransferred §e$amount §afrom §e$from §ato §e$to")
                else sender.sendMessage("§cTransfer failed — $from has insufficient balance")
            }
            else -> sender.sendMessage("/arc network economy <balance|give|take|set|transfer>")
        }
    }

    private fun globalConfig(sender: CommandSender, args: Array<out String>) {
        when (args.getOrNull(2)?.lowercase()) {
            "get" -> {
                val key = args.getOrNull(3) ?: run { sender.sendMessage("/arc network globalconfig get <key>"); return }
                val value = ArcGlobalConfig.get(key)
                if (value == null) sender.sendMessage("§7$key §c(not set)")
                else sender.sendMessage("§7$key §8= §e$value")
            }
            "set" -> {
                val key = args.getOrNull(3) ?: run { sender.sendMessage("/arc network globalconfig set <key> <value>"); return }
                val value = args.drop(4).joinToString(" ").ifEmpty { run { sender.sendMessage("/arc network globalconfig set <key> <value>"); return } }
                ArcGlobalConfig.set(key, value)
                sender.sendMessage("§aSet §7$key §8= §e$value")
            }
            "del" -> {
                val key = args.getOrNull(3) ?: run { sender.sendMessage("/arc network globalconfig del <key>"); return }
                ArcGlobalConfig.del(key)
                sender.sendMessage("§aDeleted §7$key")
            }
            "list" -> {
                val all = ArcGlobalConfig.getAll()
                if (all.isEmpty()) { sender.sendMessage("§7Global config is empty"); return }
                sender.sendMessage("[Arc] Global Config (${all.size} entries):")
                all.entries.sortedBy { it.key }.forEach { (k, v) ->
                    sender.sendMessage("  §7$k §8= §e$v")
                }
            }
            else -> sender.sendMessage("/arc network globalconfig <get|set|del|list>")
        }
    }
}
