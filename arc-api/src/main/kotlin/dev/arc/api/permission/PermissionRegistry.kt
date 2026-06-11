@file:JvmName("PermissionRegistry")

package dev.arc.api.permission

import org.bukkit.Bukkit
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
import org.bukkit.plugin.Plugin

@DslMarker private annotation class PermDsl

/**
 * Build and register a permission tree for this plugin.
 *
 * ```kotlin
 * plugin.permissions {
 *     "myplugin" {
 *         description("Root permission")
 *         children {
 *             "admin" { description("Full admin access"); default(PermissionDefault.OP) }
 *             "use"   { description("Basic usage");      default(PermissionDefault.TRUE) }
 *             "warp" {
 *                 description("Warp system")
 *                 children {
 *                     "set" { description("Set warps") }
 *                     "go"  { description("Use warps"); default(PermissionDefault.TRUE) }
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 */
public fun Plugin.permissions(block: PermissionTreeScope.() -> Unit) {
    PermissionTreeScope(null).apply(block).registerAll()
}

@PermDsl
public class PermissionTreeScope(private val prefix: String?) {
    internal val nodes = mutableListOf<PermissionNodeBuilder>()

    public operator fun String.invoke(block: PermissionNodeBuilder.() -> Unit) {
        val full = if (prefix != null) "$prefix.$this" else this
        nodes += PermissionNodeBuilder(full).apply(block)
    }

    internal fun registerAll() { nodes.forEach { it.register() } }
}

@PermDsl
public class PermissionNodeBuilder(public val node: String) {
    private var desc: String = ""
    private var default: PermissionDefault = PermissionDefault.OP
    private val childScope = PermissionTreeScope(node)

    public fun description(s: String)          { desc = s }
    public fun default(d: PermissionDefault)   { default = d }
    public fun default(op: Boolean)            { default = if (op) PermissionDefault.OP else PermissionDefault.TRUE }
    public fun children(block: PermissionTreeScope.() -> Unit) { childScope.apply(block) }

    internal fun register() {
        // register children first so their Permission objects exist
        childScope.registerAll()

        if (Bukkit.getPluginManager().getPermission(node) != null) return

        val children = childScope.nodes.associate { it.node to true }
        val perm = Permission(node, desc, default, children)
        runCatching { Bukkit.getPluginManager().addPermission(perm) }
    }
}
