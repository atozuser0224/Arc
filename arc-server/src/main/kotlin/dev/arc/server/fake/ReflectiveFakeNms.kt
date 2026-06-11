package dev.arc.server.fake

import dev.arc.api.nms.ArcFakeNms
import dev.arc.server.nms.NmsThreadGuard
import dev.arc.server.nms.Reflect
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.Event
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.entity.EntityTeleportEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Reflection-backed [ArcFakeNms]. Creates real NMS entities so every Bukkit event
 * fires through the same code paths as a genuine player or mob.
 */
internal object ReflectiveFakeNms : ArcFakeNms {

    private val FAKE_PLAYER_TAG = NamespacedKey("arc", "fake_player")
    private val FAKE_MOB_TAG    = NamespacedKey("arc", "fake_mob")
    private val trackedPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    // ── Player lifecycle ──────────────────────────────────────────────────────

    override fun spawnFakePlayer(world: World, name: String, location: Location): Player {
        NmsThreadGuard.requireOwned(Bukkit.isGlobalTickThread(), "fake.spawnFakePlayer")

        val nmsServer = Reflect.invoke(Bukkit.getServer(), "getServer")
            ?: error("[Arc/Fake] Cannot obtain NMS MinecraftServer")
        val serverLevel = Reflect.handleOf(world)
            ?: error("[Arc/Fake] Cannot obtain ServerLevel for world '${world.name}'")

        val uuid = UUID.randomUUID()
        val gameProfile = buildGameProfile(uuid, name)
            ?: error("[Arc/Fake] Cannot construct GameProfile for '$name'")
        val clientInfo = buildClientInfo()

        val serverPlayerCls = Reflect.cls("net.minecraft.server.level.ServerPlayer")
            ?: error("[Arc/Fake] net.minecraft.server.level.ServerPlayer not found")

        val nmsPlayer =
            Reflect.constructByArity(serverPlayerCls, 4, nmsServer, serverLevel, gameProfile, clientInfo)
                ?: Reflect.constructByArity(serverPlayerCls, 3, nmsServer, serverLevel, gameProfile)
                ?: error("[Arc/Fake] Cannot construct ServerPlayer '$name'")

        installFakeConnection(nmsServer, nmsPlayer)
        Reflect.invoke(nmsPlayer, "setPos", location.x, location.y, location.z)

        val added = Reflect.invoke(serverLevel, "addNewPlayer", nmsPlayer) != null
            || Reflect.invoke(serverLevel, "addFreshEntity", nmsPlayer) != null
        if (!added) error("[Arc/Fake] Failed to add fake player '$name' to world '${world.name}'")

        val craftPlayer = Reflect.invoke(nmsPlayer, "getBukkitEntity") as? Player
            ?: error("[Arc/Fake] getBukkitEntity() did not return a Player for '$name'")

        craftPlayer.persistentDataContainer.set(FAKE_PLAYER_TAG, PersistentDataType.BYTE, 1)
        trackedPlayers += uuid
        return craftPlayer
    }

    override fun removeFakePlayer(player: Player) {
        if (!isFakePlayer(player)) return
        trackedPlayers -= player.uniqueId
        val handle = Reflect.handleOf(player)
        if (handle != null) {
            Reflect.invoke(handle, "discard")
                ?: Reflect.invoke(handle, "remove", removalReason("DISCARDED"))
        } else {
            player.remove()
        }
    }

    override fun isFakePlayer(entity: Entity): Boolean {
        if (entity !is Player) return false
        return entity.uniqueId in trackedPlayers ||
            entity.persistentDataContainer.has(FAKE_PLAYER_TAG, PersistentDataType.BYTE)
    }

    // ── Mob lifecycle ─────────────────────────────────────────────────────────

    override fun spawnFakeMob(world: World, entityType: EntityType, location: Location): LivingEntity {
        NmsThreadGuard.requireOwned(Bukkit.isGlobalTickThread(), "fake.spawnFakeMob")

        @Suppress("UNCHECKED_CAST")
        val entityClass = entityType.entityClass?.asSubclass(LivingEntity::class.java)
            ?: error("[Arc/Fake] EntityType ${entityType.name} is not a LivingEntity")

        val entity = world.spawn(location, entityClass)
        entity.persistentDataContainer.set(FAKE_MOB_TAG, PersistentDataType.BYTE, 1)
        return entity
    }

    override fun removeFakeMob(entity: LivingEntity) {
        if (!isFakeMob(entity)) return
        val handle = Reflect.handleOf(entity)
        if (handle != null) {
            Reflect.invoke(handle, "discard")
                ?: Reflect.invoke(handle, "remove", removalReason("DISCARDED"))
        } else {
            entity.remove()
        }
    }

    override fun isFakeMob(entity: Entity): Boolean =
        entity !is Player &&
            entity.persistentDataContainer.has(FAKE_MOB_TAG, PersistentDataType.BYTE)

    // ── Combat ────────────────────────────────────────────────────────────────

    override fun performAttack(attacker: LivingEntity, target: LivingEntity, weapon: ItemStack?): Boolean {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(attacker), "fake.performAttack")

        val attackerHandle = Reflect.handleOf(attacker) ?: return false
        val targetHandle   = Reflect.handleOf(target)   ?: return false

        // ServerPlayer.attack(Entity) → full Player pipeline (cooldown, crit, sweep, knockback)
        if (Reflect.invoke(attackerHandle, "attack", targetHandle) != null) return true

        // Mob.doHurtTarget(Entity) → mob-specific damage attributes + knockback
        if (Reflect.invoke(attackerHandle, "doHurtTarget", targetHandle) != null) return true

        // Final fallback: swing + raw hurt via DamageSource
        val mainHand = nmsInteractionHand("MAIN_HAND")
        if (mainHand != null) Reflect.invoke(attackerHandle, "swing", mainHand)
        val dmgSource = buildMobAttackDamageSource(attacker) ?: return false
        return Reflect.invoke(targetHandle, "hurt", dmgSource, 1.0f) != null
    }

    override fun applyDamage(target: LivingEntity, amount: Double, source: Entity?, cause: DamageCause): Double {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(target), "fake.applyDamage")
        val hpBefore = target.health
        if (source != null) target.damage(amount, source) else target.damage(amount)
        val hpAfter = if (target.isValid) target.health else 0.0
        return (hpBefore - hpAfter).coerceAtLeast(0.0)
    }

    override fun launchProjectile(shooter: LivingEntity, target: LivingEntity, entityClass: String): Entity? {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(shooter), "fake.launchProjectile")
        val type = runCatching {
            @Suppress("UNCHECKED_CAST")
            Class.forName("org.bukkit.entity.$entityClass").asSubclass(Projectile::class.java)
        }.getOrNull() ?: return null
        val projectile = shooter.launchProjectile(type)
        val dir = target.eyeLocation.toVector()
            .subtract(shooter.eyeLocation.toVector())
            .normalize()
        projectile.velocity = dir.multiply(2.5)
        return projectile
    }

    // ── Player interaction ────────────────────────────────────────────────────

    override fun performBlockInteract(actor: Player, block: Block, face: BlockFace, hand: EquipmentSlot) {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(actor), "fake.performBlockInteract")
        val event = PlayerInteractEvent(
            actor, Action.RIGHT_CLICK_BLOCK, actor.inventory.getItem(hand), block, face, hand,
        )
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled || event.useInteractedBlock() == Event.Result.DENY) return

        val actorHandle      = Reflect.handleOf(actor)       ?: return
        val level            = Reflect.handleOf(block.world)  ?: return
        val blockPos         = buildBlockPos(block)            ?: return
        val interactionHand  = nmsInteractionHand(hand)        ?: return
        val hitResult        = buildBlockHitResult(block, face) ?: return
        val gameMode         = Reflect.invoke(actorHandle, "gameMode") ?: return
        Reflect.invoke(gameMode, "useItemOn", actorHandle, level, null, interactionHand, hitResult)
    }

    override fun performEntityInteract(actor: Player, target: Entity, hand: EquipmentSlot) {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(actor), "fake.performEntityInteract")
        val event = PlayerInteractEntityEvent(actor, target, hand)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return

        val actorHandle     = Reflect.handleOf(actor)  ?: return
        val targetHandle    = Reflect.handleOf(target) ?: return
        val interactionHand = nmsInteractionHand(hand)  ?: return
        Reflect.invoke(actorHandle, "interact", targetHandle, interactionHand)
    }

    // ── Movement ──────────────────────────────────────────────────────────────

    override fun simulateMove(player: Player, to: Location): Boolean {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(player), "fake.simulateMove")
        val event = PlayerMoveEvent(player, player.location.clone(), to)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return false
        player.teleport(event.to ?: to)
        return true
    }

    override fun simulateMobMove(mob: Mob, to: Location): Boolean {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(mob), "fake.simulateMobMove")
        val event = EntityTeleportEvent(mob, mob.location.clone(), to)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return false
        mob.teleport(event.to ?: to)
        return true
    }

    override fun pathfindMobTo(mob: Mob, location: Location, speed: Double) {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(mob), "fake.pathfindMobTo")
        val handle = Reflect.handleOf(mob) ?: run { mob.teleport(location); return }

        // PathNavigation.moveTo(double x, double y, double z, double speed)
        val navigation = Reflect.invoke(handle, "getNavigation") ?: run {
            mob.teleport(location); return
        }
        Reflect.invoke(navigation, "moveTo", location.x, location.y, location.z, speed)
    }

    override fun pathfindMobToEntity(mob: Mob, target: LivingEntity, speed: Double) {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(mob), "fake.pathfindMobToEntity")
        val handle       = Reflect.handleOf(mob)    ?: run { mob.teleport(target.location); return }
        val targetHandle = Reflect.handleOf(target) ?: run { mob.teleport(target.location); return }

        // PathNavigation.moveTo(Entity target, double speed)
        val navigation = Reflect.invoke(handle, "getNavigation") ?: run {
            mob.teleport(target.location); return
        }
        val moved = Reflect.invoke(navigation, "moveTo", targetHandle, speed)
        // Fallback: moveTo(double x, y, z, speed) aimed at entity feet
        if (moved == null) {
            val loc = target.location
            Reflect.invoke(navigation, "moveTo", loc.x, loc.y, loc.z, speed)
        }
    }

    // ── NMS helpers ───────────────────────────────────────────────────────────

    private fun buildGameProfile(uuid: UUID, name: String): Any? {
        val cls = Reflect.cls("com.mojang.authlib.GameProfile") ?: return null
        return Reflect.construct(cls, arrayOf(UUID::class.java, String::class.java), uuid, name)
    }

    private fun buildClientInfo(): Any? {
        val cls = Reflect.cls("net.minecraft.server.level.ClientInformation") ?: return null
        return Reflect.invokeStatic(cls, "createDefault")
            ?: Reflect.invokeStatic(cls, "initial")
            ?: run {
                val chatVisCls  = Reflect.cls("net.minecraft.world.entity.player.ChatVisiblity")
                val armCls      = Reflect.cls("net.minecraft.world.entity.HumanoidArm")
                val particleCls = Reflect.cls("net.minecraft.world.entity.player.ParticleStatus")
                val chatVis  = chatVisCls?.let  { Reflect.invokeStatic(it, "valueOf", "FULL") }
                val arm      = armCls?.let      { Reflect.invokeStatic(it, "valueOf", "RIGHT") }
                val particles = particleCls?.let { Reflect.invokeStatic(it, "valueOf", "ALL") }
                Reflect.constructByArity(cls, 9, "en_us", 10, chatVis, true, 127, arm, false, false, particles)
                    ?: Reflect.constructByArity(cls, 8, "en_us", 10, chatVis, true, 127, arm, false, false)
                    ?: Reflect.constructByArity(cls, 7, "en_us", 10, chatVis, true, 127, arm, false)
            }
    }

    private fun installFakeConnection(nmsServer: Any, nmsPlayer: Any) {
        val embeddedCls = Reflect.cls("io.netty.channel.embedded.EmbeddedChannel") ?: return
        val channel = Reflect.constructByArity(embeddedCls, 0) ?: return

        val connectionCls = Reflect.cls("net.minecraft.network.Connection")        ?: return
        val flowCls       = Reflect.cls("net.minecraft.network.protocol.PacketFlow") ?: return
        val clientbound   = Reflect.invokeStatic(flowCls, "valueOf", "CLIENTBOUND")  ?: return
        val connection    = Reflect.construct(connectionCls, arrayOf(flowCls), clientbound) ?: return
        Reflect.field(connection.javaClass, "channel")?.set(connection, channel)

        val listenerCls = Reflect.cls("net.minecraft.server.network.ServerGamePacketListenerImpl") ?: return
        val cookieCls   = Reflect.cls("net.minecraft.server.network.CommonListenerCookie")

        val listener = if (cookieCls != null) {
            val profile    = Reflect.invoke(nmsPlayer, "getGameProfile")
            val clientInfo = buildClientInfo()
            val cookie = Reflect.constructByArity(cookieCls, 4, profile, 769, clientInfo, false)
                ?: Reflect.constructByArity(cookieCls, 3, profile, 769, clientInfo)
                ?: Reflect.constructByArity(cookieCls, 2, profile, 769)
            Reflect.constructByArity(listenerCls, 4, nmsServer, connection, nmsPlayer, cookie)
                ?: Reflect.constructByArity(listenerCls, 3, nmsServer, connection, nmsPlayer)
        } else {
            Reflect.constructByArity(listenerCls, 3, nmsServer, connection, nmsPlayer)
        } ?: return

        Reflect.field(nmsPlayer.javaClass, "connection")?.set(nmsPlayer, listener)
    }

    private fun buildMobAttackDamageSource(attacker: LivingEntity): Any? {
        val attackerHandle = Reflect.handleOf(attacker) ?: return null
        val level          = Reflect.handleOf(attacker.world) ?: return null
        val damageSources  = Reflect.invoke(level, "damageSources") ?: return null
        return Reflect.invoke(damageSources, "mobAttack", attackerHandle)
            ?: Reflect.invoke(damageSources, "playerAttack", attackerHandle)
    }

    private fun removalReason(name: String): Any? {
        val cls = Reflect.cls("net.minecraft.world.entity.Entity\$RemovalReason") ?: return null
        return Reflect.invokeStatic(cls, "valueOf", name)
    }

    private fun buildBlockPos(block: Block): Any? {
        val cls = Reflect.cls("net.minecraft.core.BlockPos") ?: return null
        return Reflect.construct(cls, arrayOf(Integer.TYPE, Integer.TYPE, Integer.TYPE), block.x, block.y, block.z)
    }

    private fun nmsInteractionHand(slot: EquipmentSlot): Any? =
        nmsInteractionHand(if (slot == EquipmentSlot.OFF_HAND) "OFF_HAND" else "MAIN_HAND")

    private fun nmsInteractionHand(name: String): Any? {
        val cls = Reflect.cls("net.minecraft.world.InteractionHand") ?: return null
        return Reflect.invokeStatic(cls, "valueOf", name)
    }

    private fun buildBlockHitResult(block: Block, face: BlockFace): Any? {
        val hitResultCls = Reflect.cls("net.minecraft.world.phys.BlockHitResult") ?: return null
        val vec3Cls      = Reflect.cls("net.minecraft.world.phys.Vec3")            ?: return null
        val dirCls       = Reflect.cls("net.minecraft.core.Direction")             ?: return null

        val loc = block.location.add(0.5, 0.5, 0.5)
        val vec      = Reflect.constructByArity(vec3Cls, 3, loc.x, loc.y, loc.z)          ?: return null
        val blockPos = buildBlockPos(block)                                                  ?: return null
        val dir      = Reflect.invokeStatic(dirCls, "valueOf", face.name.uppercase())       ?: return null
        return Reflect.constructByArity(hitResultCls, 4, vec, dir, blockPos, false)
            ?: Reflect.constructByArity(hitResultCls, 3, vec, dir, blockPos)
    }
}
