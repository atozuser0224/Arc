package dev.arc.test.extra

import org.mockbukkit.mockbukkit.ServerMock
import org.bukkit.command.Command

/**
 * Scans all registered commands for permission configuration issues.
 *
 * ```kotlin
 * val issues = auditPermissions()
 * check(issues.none { it.startsWith("[ERROR]") }) {
 *     "Permission audit failed:\n${issues.joinToString("\n")}"
 * }
 * ```
 */
object PermissionAudit {

    data class PermissionIssue(
        val commandName: String,
        val severity: Severity,
        val description: String,
    ) {
        enum class Severity { WARN, ERROR }
        override fun toString() = "[${severity.name}] /$commandName — $description"
    }

    fun scan(server: ServerMock): List<String> {
        val issues = mutableListOf<PermissionIssue>()
        server.commandMap.commands.forEach { cmd -> checkCommand(cmd, issues) }
        return issues.map { it.toString() }
    }

    private fun checkCommand(cmd: Command, issues: MutableList<PermissionIssue>) {
        if (cmd.permission.isNullOrBlank()) {
            issues += PermissionIssue(
                cmd.name,
                PermissionIssue.Severity.WARN,
                "no permission node set — anyone can run this command",
            )
        }
        if (cmd.description.isNullOrBlank()) {
            issues += PermissionIssue(cmd.name, PermissionIssue.Severity.WARN, "no description set")
        }
        if (cmd.permission != null && cmd.permissionMessage == null) {
            issues += PermissionIssue(
                cmd.name,
                PermissionIssue.Severity.WARN,
                "permission '${cmd.permission}' set but no permissionMessage",
            )
        }
    }
}
