@file:JvmName("Permissions")

package dev.arc.api.permission

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
import org.bukkit.plugin.Plugin

/** True if this player has ALL of the given permissions. */
public fun Player.hasAllPermissions(vararg nodes: String): Boolean =
    nodes.all { hasPermission(it) }

/** True if this player has ANY of the given permissions. */
public fun Player.hasAnyPermission(vararg nodes: String): Boolean =
    nodes.any { hasPermission(it) }

/** Register a permission node if it hasn't been registered yet. */
public fun Plugin.registerPermission(
    node: String,
    description: String = "",
    default: PermissionDefault = PermissionDefault.OP,
): Permission {
    val existing = Bukkit.getPluginManager().getPermission(node)
    if (existing != null) return existing
    val perm = Permission(node, description, default)
    Bukkit.getPluginManager().addPermission(perm)
    return perm
}

/** Temporarily grant a permission to a player for the duration of [block]. */
public inline fun <T> Player.withPermission(node: String, block: () -> T): T {
    val attachment = addAttachment(server.pluginManager.plugins.first())
    attachment.setPermission(node, true)
    return try {
        block()
    } finally {
        removeAttachment(attachment)
    }
}
