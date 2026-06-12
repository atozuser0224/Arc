package dev.arc.test.extra

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dev.arc.test.ArcTestScope
import dev.arc.test.virtual.VirtualPlayer
import java.io.File
import java.time.Instant

// ---------------------------------------------------------------------------
//  Data model
// ---------------------------------------------------------------------------

data class RecordedAction(
    val tick: Long,
    val player: String,
    val type: ActionType,
    val payload: String,
) {
    enum class ActionType { CHAT, COMMAND, MOVE, INTERACT_LEFT, INTERACT_RIGHT }
}

data class RecordedScenario(
    val name: String,
    val recordedAt: String = Instant.now().toString(),
    val actions: List<RecordedAction>,
)

// ---------------------------------------------------------------------------
//  Recorder
// ---------------------------------------------------------------------------

/**
 * Records a sequence of player actions into a JSON file that can be replayed
 * in future tests — great for capturing real gameplay regressions.
 *
 * ```kotlin
 * val rec = ScenarioRecorder("shop-buy-flow")
 * rec.record(player, RecordedAction.ActionType.COMMAND, "shop buy sword")
 * rec.save(File("src/test/resources/scenarios/shop-buy-flow.json"))
 * ```
 */
class ScenarioRecorder(val name: String) {
    private val actions = mutableListOf<RecordedAction>()
    private var currentTick = 0L

    fun advanceTick(ticks: Int = 1) { currentTick += ticks }

    fun record(player: VirtualPlayer, type: RecordedAction.ActionType, payload: String = "") {
        actions += RecordedAction(currentTick, player.name, type, payload)
    }

    fun save(file: File) {
        val scenario = RecordedScenario(name, actions = actions.toList())
        file.parentFile?.mkdirs()
        file.writeText(gson.toJson(scenario))
    }

    fun scenario(): RecordedScenario = RecordedScenario(name, actions = actions.toList())

    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(file: File): RecordedScenario = gson.fromJson(file.readText(), RecordedScenario::class.java)
    }
}

// ---------------------------------------------------------------------------
//  Replayer
// ---------------------------------------------------------------------------

/**
 * Replays a [RecordedScenario] inside an [ArcTestScope].
 *
 * ```kotlin
 * @Test
 * fun `replay shop buy flow`() = arcTest {
 *     val scenario = ScenarioRecorder.load(File("src/test/resources/scenarios/shop-buy-flow.json"))
 *     val player = withPlayer("Steve")
 *     replayScenario(scenario, mapOf("Steve" to player))
 * }
 * ```
 */
fun ArcTestScope.replayScenario(
    scenario: RecordedScenario,
    playerMap: Map<String, VirtualPlayer>,
) {
    var lastTick = 0L
    for (action in scenario.actions) {
        val delta = (action.tick - lastTick).toInt()
        if (delta > 0) tick(delta)
        lastTick = action.tick

        val player = playerMap[action.player]
            ?: error("Scenario references player '${action.player}' but not found in playerMap")

        when (action.type) {
            RecordedAction.ActionType.CHAT            -> player.chat(action.payload)
            RecordedAction.ActionType.COMMAND         -> player.performCommand(action.payload)
            RecordedAction.ActionType.INTERACT_LEFT   -> {
                val (x, y, z) = action.payload.split(",").map { it.trim().toInt() }
                player.interactLeft(x, y, z)
            }
            RecordedAction.ActionType.INTERACT_RIGHT  -> {
                val (x, y, z) = action.payload.split(",").map { it.trim().toInt() }
                player.interactRight(x, y, z)
            }
            RecordedAction.ActionType.MOVE            -> {
                val (x, y, z) = action.payload.split(",").map { it.trim().toDouble() }
                player.move(x, y, z)
            }
        }
    }
}
