@file:JvmName("Datapacks")

package dev.arc.api.datapack

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.io.File

/**
 * Bukkit adapter for [DatapackContent].
 *
 * Content generation and filesystem safety remain testable without a server;
 * this type only selects Bukkit's datapack directory and performs activation.
 */
public class DatapackMaker(
    public val plugin: Plugin,
    name: String,
) : DatapackContent(name) {

    /**
     * Write this pack below the server world container's `datapacks` folder.
     * The pack is not activated until [enable] or [deployAndReload] is called.
     */
    public fun deploy(): File =
        deployTo(Bukkit.getWorldContainer().toPath().resolve("datapacks")).toFile()

    /** Enable the generated file pack through the vanilla datapack command. */
    public fun enable() {
        Bukkit.dispatchCommand(
            Bukkit.getConsoleSender(),
            "datapack enable \"file/$name\"",
        )
    }

    /**
     * Deploy, enable, and reload runtime-reloadable datapack resources.
     *
     * World-generation resources still require deployment before world load or
     * a full server restart.
     */
    public fun deployAndReload(plugin: Plugin = this.plugin) {
        deploy()
        enable()
        Bukkit.getScheduler().runTask(plugin, Runnable(Bukkit::reloadData))
    }
}

/** Create and configure a Bukkit-backed datapack. */
public fun Plugin.datapackMaker(
    name: String,
    block: DatapackMaker.() -> Unit,
): DatapackMaker = DatapackMaker(this, name).apply(block)
