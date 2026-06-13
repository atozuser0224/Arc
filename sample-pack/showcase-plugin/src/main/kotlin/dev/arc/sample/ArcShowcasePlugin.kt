package dev.arc.sample

import dev.arc.api.command.command
import dev.arc.api.datapack.datapackMaker
import dev.arc.api.effect.CustomEffectRegistry
import dev.arc.api.effect.EffectReapplyPolicy
import dev.arc.api.effect.customEffects
import dev.arc.api.event.listen
import dev.arc.api.item.ItemVerification
import dev.arc.api.item.itemAuthenticator
import dev.arc.api.item.item
import dev.arc.api.player.PlayerStore
import dev.arc.api.player.getOrDefault
import dev.arc.api.player.increment
import dev.arc.api.player.playerDataSchema
import dev.arc.api.player.send
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import kotlin.time.Duration.Companion.seconds

/**
 * A guided tour of the Arc developer API. Every block below maps to one
 * flagship feature documented in arc-api/README.md. Arc itself is installed
 * by the server at boot (CraftServer -> ArcBootstrap.install()), so a plugin
 * only needs to call the arc-api extension functions — no bootstrap, no
 * Listener classes, and (for the showcase commands) no plugin.yml command block.
 */
class ArcShowcasePlugin : JavaPlugin() {

    // --- Player PDC schema: typed, validated, migration-aware persistent fields ---
    private lateinit var commandsRun: PlayerStore<Int, Int>

    // --- Custom effect registry (closed in onDisable) ---
    private lateinit var effects: CustomEffectRegistry

    // --- Item authenticator: HMAC-SHA256 signed items, fixed demo key ---
    private val authenticator = itemAuthenticator {
        primaryKey("v1", DEMO_SECRET)
    }

    override fun onEnable() {
        effects = customEffects {
            effect("mana_surge") {
                visual(org.bukkit.potion.PotionEffectType.REGENERATION)
                defaultDuration = 15.seconds
                reapplyPolicy = EffectReapplyPolicy.KEEP_STRONGER

                onApply { player -> player.send("<aqua>Mana surge <white>begins!") }
                onTick(interval = 20) { player, instance ->
                    player.send("<dark_aqua>mana +${instance.amplifier + 1} <gray>(${instance.remainingTicks / 20}s left)")
                }
                onRemove { player, _, reason ->
                    player.send("<gray>Mana surge ended: <yellow>$reason")
                }
            }
        }

        // 1) Player PDC schema — declare fields once, with default + validation + legacy migration.
        val schema = playerDataSchema {
            commandsRun = int("commands_run") {
                default { 0 }
                validate("commands_run must be non-negative") { it >= 0 }
                migrateFrom("cmd_count") // older key name, lazily migrated on first read
            }
            string("rank") { default { "guest" } }
        }
        logger.info("PDC schema fields: ${schema.names}")

        // 2) Datapack DSL — generate + deploy a validated 1.21.4 datapack at startup.
        val showcasePack = datapackMaker("arc_showcase") {
            lootTable("arc_showcase:chests/welcome") {
                pool(rolls = 1..2) {
                    entry("minecraft:diamond", weight = 1, count = 1..2)
                    entry("minecraft:bread", weight = 4, count = 1..3)
                    empty(weight = 2)
                }
            }
            tag("blocks", "arc_showcase:valuable_ores") {
                add("minecraft:diamond_ore")
                add("minecraft:emerald_ore")
            }
            function("arc_showcase:load") {
                +"say [ArcShowcase] datapack loaded"
            }
        }
        val datapacksDirectory = checkNotNull(Bukkit.getWorlds().firstOrNull()) {
            "ArcShowcase requires a loaded primary world"
        }.worldFolder.toPath().resolve("datapacks")
        showcasePack.deployTo(datapacksDirectory)
        Bukkit.getScheduler().runTask(this, Runnable {
            Bukkit.reloadData()
            logger.info("Deployed datapack 'arc_showcase'")
        })

        // 3) Events — no Listener class, no annotations, auto-unregistered on disable.
        listen<PlayerJoinEvent> { event ->
            val player = event.player
            val runs = player.getOrDefault(commandsRun)
            player.send("<green>Welcome! <gray>You have run <white>$runs<gray> showcase commands.")
        }

        // 4) Command DSL — runtime registration straight onto the command map.
        command("showcase") {
            complete { listOf("effect", "give", "verify", "stats") }
            execute { ctx ->
                val sender = ctx.sender
                if (ctx.isPlayer) ctx.player.increment(commandsRun, 1)
                when (ctx.arg(0)) {
                    "effect" -> {
                        val player = ctx.player
                        effects.apply(player, effects["mana_surge"], duration = 15.seconds, amplifier = 1)
                    }
                    "give" -> {
                        val player = ctx.player
                        val gift = item(Material.DIAMOND_SWORD, build = {
                            name("<gradient:#00aaff:#aa00ff>Arc Blade</gradient>".mmComponent())
                            lore(
                                "<gray>Forged by the showcase".mmComponent(),
                                "<dark_gray>Right-click to feel fancy".mmComponent(),
                            )
                        })
                        player.inventory.addItem(authenticator.sign(gift))
                        player.send("<green>Signed Arc Blade added to your inventory.")
                    }
                    "verify" -> {
                        val player = ctx.player
                        val held = player.inventory.itemInMainHand
                        val result = authenticator.verify(held)
                        val color = if (result == ItemVerification.VALID) "<green>" else "<red>"
                        player.send("Held item authenticity: $color$result")
                    }
                    "stats" -> {
                        if (ctx.isPlayer) {
                            val runs = ctx.player.getOrDefault(commandsRun)
                            ctx.player.send("<aqua>You have run <white>$runs<aqua> showcase commands.")
                        } else {
                            sender.sendMessage("stats are per-player")
                        }
                    }
                    else -> sender.send(
                        "<yellow>/showcase <effect|give|verify|stats> <gray>— Arc API demo",
                    )
                }
            }
        }

        logger.info("ArcShowcase enabled — try /showcase effect|give|verify|stats")
    }

    override fun onDisable() {
        effects.close()
    }

    /** Local MiniMessage helper so the item lore stays readable. */
    private fun String.mmComponent() = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(this)

    companion object {
        /** Demo HMAC secret. In production load this from config, never hard-code. */
        private val DEMO_SECRET = ByteArray(32) { (it * 7 + 13).toByte() }
    }
}
