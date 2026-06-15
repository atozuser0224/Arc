package dev.arc.sample

import dev.arc.api.command.command
import dev.arc.api.content.ContentId
import dev.arc.api.content.arcContent
import dev.arc.api.content.removeArcContent
import dev.arc.api.datapack.datapackMaker
import dev.arc.api.effect.CustomEffectRegistry
import dev.arc.api.effect.EffectReapplyPolicy
import dev.arc.api.effect.customEffects
import dev.arc.api.event.listen
import dev.arc.api.item.ItemBuilder
import dev.arc.api.item.ItemVerification
import dev.arc.api.item.defineItem
import dev.arc.api.item.item
import dev.arc.api.item.itemAuthenticator
import dev.arc.api.item.withBehavior
import dev.arc.api.player.PlayerStore
import dev.arc.api.player.getOrDefault
import dev.arc.api.player.increment
import dev.arc.api.player.playerDataSchema
import dev.arc.api.player.send
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import dev.arc.api.plugin.ArcPlugin
import kotlin.time.Duration.Companion.seconds

/**
 * A guided tour of the Arc developer API. Every block below maps to one
 * flagship feature documented in arc-api/README.md. Arc itself is installed
 * by the server at boot (CraftServer -> ArcBootstrap.install()), so a plugin
 * only needs to call the arc-api extension functions — no bootstrap, no
 * Listener classes, and (for the showcase commands) no plugin.yml command block.
 *
 * Extends [ArcPlugin] for typed service dependency delegates:
 *   val economy: Economy by service()
 *   val papi: PlaceholderAPI? by optionalService()
 */
class ArcShowcasePlugin : ArcPlugin() {

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
        val manaCrystalKey = NamespacedKey(this, "mana_crystal")
        val arcBladeKey = NamespacedKey(this, "arc_blade")

        command("showcase") {
            complete { listOf("effect", "give", "verify", "stats", "content") }
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
                    "content" -> {
                        val player = ctx.player
                        when (ctx.arg(1)) {
                            "give" -> when (ctx.arg(2)) {
                                "mana_crystal" -> {
                                    val crystal = contentItem(
                                        "arcshowcase:showcase/mana_crystal",
                                        Material.AMETHYST_SHARD,
                                    ) {
                                        name("<aqua>Mana Crystal</aqua>".mmComponent())
                                        lore("<gray>Right-click to surge with mana.".mmComponent())
                                    }.withBehavior(manaCrystalKey, this@ArcShowcasePlugin)
                                    player.inventory.addItem(crystal)
                                    player.send("<green>Given <aqua>Mana Crystal<green>.")
                                }
                                "arc_blade" -> {
                                    val blade = contentItem(
                                        "arcshowcase:showcase/arc_blade",
                                        Material.DIAMOND_SWORD,
                                    ) {
                                        name("<gradient:#00aaff:#aa00ff>Arc Blade</gradient>".mmComponent())
                                        lore("<gray>Right-click to release a pulse.".mmComponent())
                                        unbreakable()
                                    }.withBehavior(arcBladeKey, this@ArcShowcasePlugin)
                                    player.inventory.addItem(blade)
                                    player.send("<green>Given <gradient:#00aaff:#aa00ff>Arc Blade<green>.")
                                }
                                else -> player.send(
                                    "<yellow>/showcase content give <mana_crystal|arc_blade>",
                                )
                            }
                            else -> player.send(
                                "<yellow>/showcase content give <mana_crystal|arc_blade>",
                            )
                        }
                    }
                    else -> sender.send(
                        "<yellow>/showcase <effect|give|verify|stats|content> <gray>— Arc API demo",
                    )
                }
            }
        }

        // 5) Content pack — Arc client mod users see these in the Arc creative tab.
        //    Textures go in: sample-pack/content/assets/arcshowcase/<path>.png
        val content = arcContent {
            item("showcase/mana_crystal") {
                fallback = "minecraft:amethyst_shard"
                order = 10
            }
            item("showcase/arc_blade") {
                fallback = "minecraft:diamond_sword"
                durability = 1561
                maxStackSize = 1
                order = 20
            }
            block("showcase/mana_ore") {
                fallback = "minecraft:amethyst_block"
                hardness = 3.0f
                blastResistance = 3.0f
                order = 30
            }
            recipe("showcase/mana_crystal_recipe") {
                result = ContentId("arcshowcase", "showcase/mana_crystal")
                ingredients += ContentId("minecraft", "amethyst_shard")
            }
        }
        if (!content.accepted) {
            logger.warning("ArcShowcase content pack rejected: ${content.diagnostics}")
        }

        // 6) Item behaviors — content items with Arc-powered right-click actions.
        defineItem(manaCrystalKey) {
            onRightClick { e ->
                effects.apply(e.player, effects["mana_surge"], duration = 20.seconds, amplifier = 0)
                e.player.send("<aqua>Mana crystal charges your surge.")
            }
        }
        defineItem(arcBladeKey) {
            onRightClick { e ->
                val player = e.player
                val origin = player.location
                player.world.getNearbyEntities(origin, 5.0, 5.0, 5.0)
                    .filter { it != player }
                    .forEach { entity ->
                        val dir = entity.location.toVector()
                            .subtract(origin.toVector())
                            .normalize().multiply(1.8).setY(0.35)
                        entity.velocity = dir
                        (entity as? LivingEntity)?.damage(4.0, player)
                    }
                player.send("<gradient:#aa00ff:#ff00aa>Arc Blade releases a pulse!")
                player.world.strikeLightningEffect(origin)
            }
        }

        // Amethyst blocks have a 30% chance to drop a mana crystal (showcase drop table).
        listen<BlockBreakEvent> { e ->
            if (e.block.type != Material.AMETHYST_BLOCK) return@listen
            if (Math.random() > 0.3) return@listen
            val crystal = contentItem("arcshowcase:showcase/mana_crystal", Material.AMETHYST_SHARD)
                .withBehavior(manaCrystalKey, this)
            e.block.world.dropItemNaturally(e.block.location.add(0.5, 0.0, 0.5), crystal)
        }

        logger.info("ArcShowcase enabled — try /showcase effect|give|verify|stats|content")
    }

    override fun onDisable() {
        effects.close()
        removeArcContent()
    }

    private fun contentItem(
        arcId: String,
        material: Material,
        build: ItemBuilder.() -> Unit = {},
    ): ItemStack {
        val stack = item(material, build = build)
        val meta = stack.itemMeta!!
        meta.persistentDataContainer.set(NamespacedKey("arc", "id"), PersistentDataType.STRING, arcId)
        stack.itemMeta = meta
        return stack
    }

    /** Local MiniMessage helper so the item lore stays readable. */
    private fun String.mmComponent() = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(this)

    companion object {
        /** Demo HMAC secret. In production load this from config, never hard-code. */
        private val DEMO_SECRET = ByteArray(32) { (it * 7 + 13).toByte() }
    }
}
