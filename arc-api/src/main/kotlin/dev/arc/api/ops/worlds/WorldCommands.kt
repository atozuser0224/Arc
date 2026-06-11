package dev.arc.api.ops.worlds

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender

/**
 * `/arc world report [<world>]`, `/arc world gamerules <world>`, `/arc world config <world>`.
 */
object WorldCommands {

    fun complete(args: Array<out String>): List<String> = when (args.size) {
        2 -> listOf("report", "gamerules", "config")
        3 -> Bukkit.getWorlds().map { it.name }
        else -> emptyList()
    }

    fun dispatch(sender: CommandSender, args: Array<out String>): Boolean {
        when (args.getOrNull(1)?.lowercase()) {
            "report" -> report(sender, args.getOrNull(2))
            "gamerules" -> gamerules(sender, args.getOrNull(2))
            "config" -> worldConfig(sender, args.getOrNull(2))
            else -> { sender.sendMessage("/arc world <report [world]|gamerules <world>|config <world>>"); return true }
        }
        return true
    }

    private fun report(sender: CommandSender, name: String?) {
        val worlds = if (name != null) {
            val w = Bukkit.getWorld(name)
            if (w == null) { sender.sendMessage("[Arc] world not found: $name"); return }
            listOf(w)
        } else Bukkit.getWorlds().toList()

        for (w in worlds.sortedBy { it.name }) {
            sender.sendMessage("[Arc] World §e${w.name}:")
            sender.sendMessage("  env: ${w.environment} difficulty: ${w.difficulty}")
            sender.sendMessage("  players: ${w.players.size} entities: ${w.entities.size}")
            sender.sendMessage("  chunks loaded: ${w.loadedChunks.size} (forced: ${w.forceLoadedChunks.size})")
            sender.sendMessage("  view-dist: ${w.viewDistance} sim-dist: ${w.simulationDistance}")
            sender.sendMessage("  spawn: ${w.spawnLocation.blockX},${w.spawnLocation.blockY},${w.spawnLocation.blockZ}")
            sender.sendMessage("  border: ${w.worldBorder.size.toInt()} (center: ${w.worldBorder.center.blockX},${w.worldBorder.center.blockZ})")
            sender.sendMessage("  auto-save: ${w.isAutoSave} keep-spawn-loaded: ${w.keepSpawnInMemory}")
            sender.sendMessage("  time: ${w.time} (full: ${w.fullTime}) weather: ${if(w.hasStorm())"storm" else "clear"} thunder: ${w.isThundering}")
        }
    }

    private fun gamerules(sender: CommandSender, name: String?) {
        val world = resolveWorld(sender, name) ?: return
        sender.sendMessage("[Arc] Game rules for §e${world.name}:")
        val rules = listOf(
            "doDaylightCycle", "doWeatherCycle", "doMobSpawning", "doMobLoot",
            "doTileDrops", "doFireTick", "keepInventory", "mobGriefing",
            "naturalRegeneration", "commandBlockOutput", "sendCommandFeedback",
            "randomTickSpeed", "spawnRadius", "maxEntityCramming",
            "playersSleepingPercentage", "disableElytraMovementCheck",
        )
        for (r in rules) {
            val v = runCatching { world.getGameRuleValue(r) }.getOrNull()?.toString() ?: "?"
            sender.sendMessage("  §e$r §7= $v")
        }
    }

    private fun worldConfig(sender: CommandSender, name: String?) {
        val world = resolveWorld(sender, name) ?: return
        sender.sendMessage("[Arc] World config for §e${world.name}:")
        sender.sendMessage("  pvp: ${world.pvp} generate-structures: ${world.canGenerateStructures()}")
        sender.sendMessage("  hardcore: ${world.isHardcore} allow-animals: ${world.allowAnimals} allow-monsters: ${world.allowMonsters}")
        sender.sendMessage("  water-ambient-spawn-limit: ${world.waterAmbientSpawnLimit}")
        sender.sendMessage("  ambient-spawn-limit: ${world.ambientSpawnLimit}")
        sender.sendMessage("  monster-spawn-limit: ${world.monsterSpawnLimit}")
        sender.sendMessage("  animal-spawn-limit: ${world.animalSpawnLimit}")
        sender.sendMessage("  water-animal-spawn-limit: ${world.waterAnimalSpawnLimit}")
        sender.sendMessage("  ticks-per-ambient-spawn: ${world.ticksPerAmbientSpawns}")
        sender.sendMessage("  ticks-per-animal-spawn: ${world.ticksPerAnimalSpawns}")
        sender.sendMessage("  ticks-per-monster-spawn: ${world.ticksPerMonsterSpawns}")
        sender.sendMessage("  ticks-per-water-spawn: ${world.ticksPerWaterSpawns}")
        sender.sendMessage("  ticks-per-water-ambient-spawn: ${world.ticksPerWaterAmbientSpawns}")
    }

    private fun resolveWorld(sender: CommandSender, name: String?) = run {
        val w = if (name != null) Bukkit.getWorld(name) else Bukkit.getWorlds().firstOrNull()
        if (w == null) sender.sendMessage("[Arc] world not found: ${name ?: "<none>"}")
        w
    }
}
