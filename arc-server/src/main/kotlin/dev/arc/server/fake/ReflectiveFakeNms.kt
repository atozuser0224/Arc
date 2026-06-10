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
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.Event
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Reflection-backed [ArcFakeNms]. Creates real NMS [ServerPlayer] instances with
 * a no-op Netty [EmbeddedChannel] so every Bukkit event fires through the same
 * code paths as a genuine player — indistinguishable to event listeners.
 */
internal object ReflectiveFakeNms : ArcFakeNms {

    private val FAKE_TAG = NamespacedKey("arc", "fake_player")
    private val trackedUuids: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

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

        // Add the entity to the world bypassing the full login sequence.
        // addNewPlayer tracks the ServerPlayer in the level's entity sections.
        val added = Reflect.invoke(serverLevel, "addNewPlayer", nmsPlayer) != null
            || Reflect.invoke(serverLevel, "addFreshEntity", nmsPlayer) != null
        if (!added) error("[Arc/Fake] Failed to add ServerPlayer '$name' to world '${world.name}'")

        val craftPlayer = Reflect.invoke(nmsPlayer, "getBukkitEntity") as? Player
            ?: error("[Arc/Fake] getBukkitEntity() did not return a Player for '$name'")

        craftPlayer.persistentDataContainer.set(FAKE_TAG, PersistentDataType.BYTE, 1)
        trackedUuids += uuid
        return craftPlayer
    }

    override fun removeFakePlayer(player: Player) {
        if (!isFakePlayer(player)) return
        trackedUuids -= player.uniqueId
        val handle = Reflect.handleOf(player)
        if (handle != null) {
            // discard() is the clean NMS entity removal path (no PlayerQuitEvent)
            Reflect.invoke(handle, "discard")
                ?: Reflect.invoke(handle, "remove", removalReason("DISCARDED"))
        } else {
            player.remove()
        }
    }

    override fun isFakePlayer(entity: Entity): Boolean {
        if (entity !is Player) return false
        return entity.uniqueId in trackedUuids ||
            entity.persistentDataContainer.has(FAKE_TAG, PersistentDataType.BYTE)
    }

    // ── Combat ────────────────────────────────────────────────────────────────

    override fun performAttack(attacker: LivingEntity, target: LivingEntity, weapon: ItemStack?): Boolean {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(attacker), "fake.performAttack")

        val attackerHandle = Reflect.handleOf(attacker) ?: return false
        val targetHandle = Reflect.handleOf(target) ?: return false

        // Player.attack(Entity) → full vanilla pipeline: cooldown check, crit,
        // enchantment damage, armor reduction, event dispatch, knockback, sweep AOE.
        if (Reflect.invoke(attackerHandle, "attack", targetHandle) != null) return true

        // Fallback for non-ServerPlayer attackers: swing animation + direct hurt
        val mainHand = nmslInteractionHand("MAIN_HAND")
        if (mainHand != null) Reflect.invoke(attackerHandle, "swing", mainHand)
        val damageSource = buildMobAttackDamageSource(attacker) ?: return false
        return Reflect.invoke(targetHandle, "hurt", damageSource, 1.0f) != null
    }

    override fun applyDamage(target: LivingEntity, amount: Double, source: Entity?, cause: DamageCause): Double {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(target), "fake.applyDamage")
        val hpBefore = target.health
        // Bukkit damage() delegates to NMS hurt() through CraftLivingEntity,
        // so EntityDamageEvent fires and armor/effects are applied correctly.
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

    // ── Interaction ───────────────────────────────────────────────────────────

    override fun performBlockInteract(actor: Player, block: Block, face: BlockFace, hand: EquipmentSlot) {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(actor), "fake.performBlockInteract")
        val event = PlayerInteractEvent(
            actor, Action.RIGHT_CLICK_BLOCK, actor.inventory.getItem(hand), block, face, hand,
        )
        Bukkit.getPluginManager().callEvent(event)

        if (event.isCancelled || event.useInteractedBlock() == Event.Result.DENY) return

        // Trigger the block's NMS use action via the player's GameMode
        val actorHandle = Reflect.handleOf(actor) ?: return
        val level = Reflect.handleOf(block.world) ?: return
        val blockPos = buildBlockPos(block) ?: return
        val interactionHand = nmslInteractionHand(hand) ?: return
        val hitResult = buildBlockHitResult(block, face) ?: return

        val gameMode = Reflect.invoke(actorHandle, "gameMode") ?: return
        Reflect.invoke(gameMode, "useItemOn", actorHandle, level, null, interactionHand, hitResult)
    }

    override fun performEntityInteract(actor: Player, target: Entity, hand: EquipmentSlot) {
        NmsThreadGuard.requireOwned(Bukkit.isOwnedByCurrentRegion(actor), "fake.performEntityInteract")
        val event = PlayerInteractEntityEvent(actor, target, hand)
        Bukkit.getPluginManager().callEvent(event)

        if (event.isCancelled) return

        val actorHandle = Reflect.handleOf(actor) ?: return
        val targetHandle = Reflect.handleOf(target) ?: return
        val interactionHand = nmslInteractionHand(hand) ?: return
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

    // ── NMS helpers ───────────────────────────────────────────────────────────

    private fun buildGameProfile(uuid: UUID, name: String): Any? {
        val cls = Reflect.cls("com.mojang.authlib.GameProfile") ?: return null
        return Reflect.construct(cls, arrayOf(UUID::class.java, String::class.java), uuid, name)
    }

    private fun buildClientInfo(): Any? {
        val cls = Reflect.cls("net.minecraft.server.level.ClientInformation") ?: return null
        // Prefer factory methods; fall back to best-fit constructor
        return Reflect.invokeStatic(cls, "createDefault")
            ?: Reflect.invokeStatic(cls, "initial")
            ?: run {
                val chatVisCls = Reflect.cls("net.minecraft.world.entity.player.ChatVisiblity")
                val armCls = Reflect.cls("net.minecraft.world.entity.HumanoidArm")
                val particleCls = Reflect.cls("net.minecraft.world.entity.player.ParticleStatus")
                val chatVis = chatVisCls?.let { Reflect.invokeStatic(it, "valueOf", "FULL") }
                val arm = armCls?.let { Reflect.invokeStatic(it, "valueOf", "RIGHT") }
                val particles = particleCls?.let { Reflect.invokeStatic(it, "valueOf", "ALL") }
                // 1.21.4: language, viewDistance, chatVisibility, chatColors,
                //         modelCustomisation, mainHand, textFiltering, allowsListing, particleStatus
                Reflect.constructByArity(cls, 9, "en_us", 10, chatVis, true, 127, arm, false, false, particles)
                    ?: Reflect.constructByArity(cls, 8, "en_us", 10, chatVis, true, 127, arm, false, false)
                    ?: Reflect.constructByArity(cls, 7, "en_us", 10, chatVis, true, 127, arm, false)
            }
    }

    private fun installFakeConnection(nmsServer: Any, nmsPlayer: Any) {
        // Step 1: EmbeddedChannel — Netty channel that discards all writes
        val embeddedCls = Reflect.cls("io.netty.channel.embedded.EmbeddedChannel") ?: return
        val channel = Reflect.constructByArity(embeddedCls, 0) ?: return

        // Step 2: Connection wrapping the channel
        val connectionCls = Reflect.cls("net.minecraft.network.Connection") ?: return
        val flowCls = Reflect.cls("net.minecraft.network.protocol.PacketFlow") ?: return
        val clientbound = Reflect.invokeStatic(flowCls, "valueOf", "CLIENTBOUND") ?: return
        val connection = Reflect.construct(connectionCls, arrayOf(flowCls), clientbound) ?: return
        Reflect.field(connection.javaClass, "channel")?.set(connection, channel)

        // Step 3: ServerGamePacketListenerImpl — handles all inbound play-state packets.
        //         With EmbeddedChannel the outbound side is silently discarded.
        val listenerCls = Reflect.cls("net.minecraft.server.network.ServerGamePacketListenerImpl") ?: return
        val cookieCls = Reflect.cls("net.minecraft.server.network.CommonListenerCookie")

        val listener = if (cookieCls != null) {
            val profile = Reflect.invoke(nmsPlayer, "getGameProfile")
            val clientInfo = buildClientInfo()
            // CommonListenerCookie(GameProfile, int protocolVersion, ClientInformation, boolean transferred)
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
        val level = Reflect.handleOf(attacker.world) ?: return null
        val damageSources = Reflect.invoke(level, "damageSources") ?: return null
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

    private fun nmslInteractionHand(slot: EquipmentSlot): Any? =
        nmslInteractionHand(if (slot == EquipmentSlot.OFF_HAND) "OFF_HAND" else "MAIN_HAND")

    private fun nmslInteractionHand(name: String): Any? {
        val cls = Reflect.cls("net.minecraft.world.InteractionHand") ?: return null
        return Reflect.invokeStatic(cls, "valueOf", name)
    }

    private fun buildBlockHitResult(block: Block, face: BlockFace): Any? {
        val hitResultCls = Reflect.cls("net.minecraft.world.phys.BlockHitResult") ?: return null
        val vec3Cls = Reflect.cls("net.minecraft.world.phys.Vec3") ?: return null
        val dirCls = Reflect.cls("net.minecraft.core.Direction") ?: return null

        val loc = block.location.add(0.5, 0.5, 0.5)
        val vec = Reflect.constructByArity(vec3Cls, 3, loc.x, loc.y, loc.z) ?: return null
        val blockPos = buildBlockPos(block) ?: return null
        val dir = Reflect.invokeStatic(dirCls, "valueOf", face.name.uppercase()) ?: return null

        return Reflect.constructByArity(hitResultCls, 4, vec, dir, blockPos, false)
            ?: Reflect.constructByArity(hitResultCls, 3, vec, dir, blockPos)
    }
}
