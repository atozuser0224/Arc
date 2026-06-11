@file:Suppress("UnusedVariable", "unused")

package dev.arc.api.registry.sample

import dev.arc.api.command.CommandBuilder
import dev.arc.api.command.CommandContext
import dev.arc.api.command.command as arcCommand
import dev.arc.api.conversation.form
import dev.arc.api.coroutine.launch
import dev.arc.api.data.data
import dev.arc.api.data.registerPlayerData
import dev.arc.api.event.listenSuspend
import dev.arc.api.registry.arcRegistries
import dev.arc.api.service.provideService
import org.bukkit.NamespacedKey
import org.bukkit.SoundCategory
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin

// ============================================================
//  샘플: 마법 시스템 플러그인
//  — 커스텀 레지스트리(사운드, 어트리뷰트) + 플레이어 데이터 + 커맨드 DSL
// ============================================================

// ── 플레이어 데이터 모델 ───────────────────────────────────────
data class ManaData(
    var current: Double = 100.0,
    var maxBonus: Double = 0.0,
    var regenBonus: Double = 0.0,
)

// ── 서비스 인터페이스 ──────────────────────────────────────────
interface ManaService {
    fun mana(player: Player): Double
    fun maxMana(player: Player): Double
    fun consume(player: Player, amount: Double): Boolean  // false = 마나 부족
    fun restore(player: Player, amount: Double)
}

// ── 메인 플러그인 ──────────────────────────────────────────────
class MagicPlugin : JavaPlugin() {

    // 등록된 키를 필드로 보관해두면 어디서든 참조 가능
    lateinit var soundSpellCast: NamespacedKey
    lateinit var soundSpellFail: NamespacedKey
    lateinit var soundManaRegen: NamespacedKey
    lateinit var soundBossRoar:  NamespacedKey

    lateinit var attrMaxMana:    NamespacedKey
    lateinit var attrSpellPower: NamespacedKey
    lateinit var attrManaRegen:  NamespacedKey

    override fun onEnable() {

        // ── 1. 레지스트리 등록 ──────────────────────────────────
        arcRegistries {

            sounds {
                soundSpellCast = +"spell_cast"
                soundSpellFail = +"spell_fail"
                soundManaRegen = +"mana_regen"

                soundBossRoar = "boss_roar" {
                    fixedRange = 128f          // 128블록 고정 감쇠
                }
            }

            attributes {
                attrMaxMana = "max_mana" {
                    description = "magic.attribute.max_mana"
                    default     = 100.0
                    range       = 0.0..10_000.0
                    syncable    = true          // 클라이언트 동기화
                }
                attrSpellPower = "spell_power" {
                    description = "magic.attribute.spell_power"
                    default     = 10.0
                    range       = 0.0..1_000.0
                }
                attrManaRegen = "mana_regen" {
                    description = "magic.attribute.mana_regen"
                    default     = 1.0
                    range       = 0.0..100.0
                    syncable    = true
                }
            }
        }

        // ── 2. 플레이어 데이터 등록 ─────────────────────────────
        registerPlayerData { ManaData() }

        // ── 3. 서비스 등록 ──────────────────────────────────────
        provideService<ManaService>(ManaServiceImpl(this))

        // ── 4. 이벤트 ───────────────────────────────────────────
        listenSuspend<PlayerJoinEvent> { e ->
            val data = e.player.data<ManaData>()
            e.player.sendMessage("§b마나: §f${data.current.toInt()} / ${maxMana(e.player).toInt()}")
        }

        // ── 5. 커맨드 ───────────────────────────────────────────
        arcCommand("mana") {
            description("마나 시스템 커맨드")
            permission("magic.mana")

            sub("info") {
                playerOnly()
                execute { ctx ->
                    val data = ctx.player.data<ManaData>()
                    val max  = maxMana(ctx.player)
                    ctx.player.sendMessage(buildString {
                        appendLine("§6━━━━━ §e마나 정보 §6━━━━━")
                        appendLine("§7현재 마나 §f${data.current.toInt()} §7/ §f${max.toInt()}")
                        appendLine("§7주문 위력 §f${spellPower(ctx.player).toInt()}")
                        appendLine("§7마나 재생 §f${manaRegen(ctx.player)}/초")
                    })
                }
            }

            sub("set") {
                permission("magic.admin")
                execute { ctx ->
                    val target = ctx.args.getOrNull(0)?.let { server.getPlayer(it) }
                        ?: return@execute ctx.sender.sendMessage("§c플레이어를 찾을 수 없습니다.")
                    val amount = ctx.args.getOrNull(1)?.toDoubleOrNull()
                        ?: return@execute ctx.sender.sendMessage("§c사용법: /mana set <플레이어> <양>")
                    target.data<ManaData>().current = amount.coerceIn(0.0, maxMana(target))
                    ctx.sender.sendMessage("§a${target.name}의 마나를 ${amount.toInt()}으로 설정했습니다.")
                }
                complete { ctx ->
                    when (ctx.args.size) {
                        1    -> server.onlinePlayers.map { it.name }
                        else -> emptyList()
                    }
                }
            }

            sub("give") {
                permission("magic.admin")
                playerOnly()
                execute { ctx ->
                    val amount = ctx.args.getOrNull(0)?.toDoubleOrNull()
                        ?: return@execute ctx.sender.sendMessage("§c사용법: /mana give <양>")
                    ctx.player.data<ManaData>().let { data ->
                        data.current = (data.current + amount).coerceAtMost(maxMana(ctx.player))
                    }
                    ctx.player.playSound(ctx.player.location, soundManaRegen.toString(),
                        SoundCategory.PLAYERS, 0.8f, 1.2f)
                    ctx.player.sendMessage("§b마나 §f+${amount.toInt()} §b회복!")
                }
            }

            sub("setup") {
                playerOnly()
                description("대화형 마나 설정")
                execute { ctx ->
                    launch {
                        val (bonus, regen) = ctx.player.form(this@MagicPlugin) {
                            val bonus = askInt(
                                "§e추가 최대 마나를 입력하세요 §7(0~5000)",
                                errorMessage = "§c0~5000 사이의 숫자를 입력해주세요."
                            ) { it in 0..5000 }
                            val regen = askDouble(
                                "§e초당 마나 재생량을 입력하세요 §7(0.1~50.0)",
                                errorMessage = "§c0.1~50.0 사이의 숫자를 입력해주세요."
                            ) { it in 0.1..50.0 }
                            bonus to regen
                        }
                        ctx.player.data<ManaData>().also {
                            it.maxBonus   = bonus.toDouble()
                            it.regenBonus = regen
                        }
                        ctx.player.sendMessage("§a설정 완료! §7최대 마나: §f${maxMana(ctx.player).toInt()}, §7재생: §f$regen/초")
                    }
                }
            }
        }
    }

    // ── 헬퍼 ────────────────────────────────────────────────────
    private fun maxMana(p: Player): Double {
        val base = p.getAttribute(Attribute.valueOf(attrMaxMana.toString()))?.value ?: 100.0
        return base + p.data<ManaData>().maxBonus
    }

    private fun spellPower(p: Player): Double =
        p.getAttribute(Attribute.valueOf(attrSpellPower.toString()))?.value ?: 10.0

    private fun manaRegen(p: Player): Double {
        val base = p.getAttribute(Attribute.valueOf(attrManaRegen.toString()))?.value ?: 1.0
        return base + p.data<ManaData>().regenBonus
    }
}

// ── 서비스 구현 ────────────────────────────────────────────────
private class ManaServiceImpl(private val plugin: MagicPlugin) : ManaService {

    override fun mana(player: Player): Double = player.data<ManaData>().current

    override fun maxMana(player: Player): Double {
        val base = player.getAttribute(Attribute.valueOf(plugin.attrMaxMana.toString()))?.value ?: 100.0
        return base + player.data<ManaData>().maxBonus
    }

    override fun consume(player: Player, amount: Double): Boolean {
        val data = player.data<ManaData>()
        if (data.current < amount) {
            player.playSound(player.location, plugin.soundSpellFail.toString(),
                SoundCategory.PLAYERS, 0.7f, 0.8f)
            player.sendMessage("§c마나가 부족합니다! (§f${data.current.toInt()}§c/§f${amount.toInt()}§c)")
            return false
        }
        data.current -= amount
        player.playSound(player.location, plugin.soundSpellCast.toString(),
            SoundCategory.PLAYERS, 1.0f, 1.0f)
        return true
    }

    override fun restore(player: Player, amount: Double) {
        val data = player.data<ManaData>()
        data.current = (data.current + amount).coerceAtMost(maxMana(player))
    }
}
