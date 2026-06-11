package dev.arc.api.ops.safe

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender

/**
 * `/arc safe-mode enable|disable` and `/arc maintenance on|off|allow <player>|status`.
 */
object SafeModeCommands {

    @Volatile var safeModeRequested = false
    @Volatile var maintenanceEnabled = false
    @Volatile var maintenanceKickExisting = false
    @Volatile var maintenanceMessage = "§cServer is under maintenance.\n§7Please try again later."
    private val allowedPlayers = LinkedHashSet<String>()

    fun complete(args: Array<out String>): List<String> = when (args.size) {
        2 -> listOf("enable", "disable", "on", "off", "allow", "status")
        else -> emptyList()
    }

    fun dispatch(sender: CommandSender, args: Array<out String>): Boolean {
        when (args.getOrNull(1)?.lowercase()) {
            "enable" -> {
                safeModeRequested = true
                sender.sendMessage("[Arc] §eSafe mode will be active on next start. Use /arc safe-mode disable to cancel.")
            }
            "disable" -> {
                safeModeRequested = false
                sender.sendMessage("[Arc] Safe mode cancelled. Next boot will be normal.")
            }
            "on" -> {
                maintenanceEnabled = true
                sender.sendMessage("[Arc] §eMaintenance mode: ON. Allowed: ${allowedPlayers.joinToString()}")
            }
            "off" -> {
                maintenanceEnabled = false
                sender.sendMessage("[Arc] Maintenance mode: OFF")
            }
            "allow" -> {
                val name = args.getOrNull(2)
                if (name == null) { sender.sendMessage("/arc maintenance allow <player>"); return true }
                allowedPlayers.add(name.lowercase())
                sender.sendMessage("[Arc] Allowed in maintenance: §e$name")
            }
            "status" -> {
                sender.sendMessage("[Arc] Maintenance: ${if (maintenanceEnabled) "§eON" else "§aOFF"}")
                sender.sendMessage("[Arc] Safe mode next boot: ${if (safeModeRequested) "§eYES" else "§aNO"}")
                if (maintenanceEnabled) {
                    sender.sendMessage("  message: $maintenanceMessage")
                    sender.sendMessage("  allowed: ${allowedPlayers.joinToString()}")
                    sender.sendMessage("  kick existing: $maintenanceKickExisting")
                }
            }
            else -> { sender.sendMessage("/arc <safe-mode <enable|disable>|maintenance <on|off|allow <p>|status>>"); return true }
        }
        return true
    }

    fun isMaintenanceAllowed(name: String): Boolean {
        return !maintenanceEnabled || allowedPlayers.contains(name.lowercase())
    }

    fun isSafeMode(): Boolean = safeModeRequested
}
