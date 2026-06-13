package dev.arc.api.effect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class CustomEffectEngineTest {

    @Test
    fun `applies and exposes immutable active state`() {
        val engine = CustomEffectEngine<String, String>()

        val application = engine.apply(
            subject = "player",
            effectKey = "arc:haste",
            durationTicks = 100,
            amplifier = 1,
            tickInterval = 5,
            policy = EffectReapplyPolicy.KEEP_STRONGER,
        )

        assertEquals(EffectApplyResult.APPLIED, application.result)
        assertEquals(100, application.current.remainingTicks)
        assertEquals(1, application.current.amplifier)
        assertEquals(5, application.current.tickInterval)
        assertEquals(application.current, engine.get("player", "arc:haste"))

        val snapshot = engine.snapshot("player")
        engine.remove("player", "arc:haste")
        assertEquals(100, snapshot.getValue("arc:haste").remainingTicks)
        assertNull(engine.get("player", "arc:haste"))
    }

    @Test
    fun `keep stronger ignores weaker and shorter equal applications`() {
        val engine = CustomEffectEngine<String, String>()
        engine.apply("player", "arc:haste", 100, 2, 1, EffectReapplyPolicy.KEEP_STRONGER)

        val weaker = engine.apply("player", "arc:haste", 200, 1, 1, EffectReapplyPolicy.KEEP_STRONGER)
        val shorter = engine.apply("player", "arc:haste", 50, 2, 1, EffectReapplyPolicy.KEEP_STRONGER)

        assertEquals(EffectApplyResult.IGNORED, weaker.result)
        assertEquals(EffectApplyResult.IGNORED, shorter.result)
        assertEquals(100, engine.get("player", "arc:haste")?.remainingTicks)
        assertEquals(2, engine.get("player", "arc:haste")?.amplifier)
    }

    @Test
    fun `keep stronger replaces stronger and refreshes longer equal applications`() {
        val engine = CustomEffectEngine<String, String>()
        engine.apply("player", "arc:haste", 100, 1, 1, EffectReapplyPolicy.KEEP_STRONGER)

        val stronger = engine.apply("player", "arc:haste", 40, 2, 2, EffectReapplyPolicy.KEEP_STRONGER)
        assertEquals(EffectApplyResult.REPLACED, stronger.result)
        assertEquals(1, stronger.previous?.amplifier)

        val refreshed = engine.apply("player", "arc:haste", 80, 2, 3, EffectReapplyPolicy.KEEP_STRONGER)
        assertEquals(EffectApplyResult.REFRESHED, refreshed.result)
        assertEquals(80, refreshed.current.remainingTicks)
        assertEquals(3, refreshed.current.tickInterval)
    }

    @Test
    fun `replace extend and ignore policies are deterministic`() {
        val engine = CustomEffectEngine<String, String>()
        engine.apply("player", "arc:focus", 20, 2, 1, EffectReapplyPolicy.KEEP_STRONGER)

        val replaced = engine.apply("player", "arc:focus", 10, 0, 4, EffectReapplyPolicy.REPLACE)
        assertEquals(EffectApplyResult.REPLACED, replaced.result)
        assertEquals(0, replaced.current.amplifier)

        val extended = engine.apply("player", "arc:focus", 15, 3, 2, EffectReapplyPolicy.EXTEND)
        assertEquals(EffectApplyResult.EXTENDED, extended.result)
        assertEquals(25, extended.current.remainingTicks)
        assertEquals(3, extended.current.amplifier)
        assertEquals(2, extended.current.tickInterval)

        val ignored = engine.apply("player", "arc:focus", 999, 9, 1, EffectReapplyPolicy.IGNORE)
        assertEquals(EffectApplyResult.IGNORED, ignored.result)
        assertEquals(extended.current, engine.get("player", "arc:focus"))
    }

    @Test
    fun `tick emits due callbacks before expiration`() {
        val engine = CustomEffectEngine<String, String>()
        engine.apply("player", "arc:pulse", 3, 0, 2, EffectReapplyPolicy.REPLACE)

        val first = engine.tick()
        assertTrue(first.due.isEmpty())
        assertTrue(first.expired.isEmpty())
        assertEquals(2, engine.get("player", "arc:pulse")?.remainingTicks)

        val second = engine.tick()
        assertEquals(1, second.due.size)
        assertEquals(2, second.due.single().effect.elapsedTicks)
        assertTrue(second.expired.isEmpty())

        val third = engine.tick()
        assertTrue(third.due.isEmpty())
        assertEquals(1, third.expired.size)
        assertEquals(EffectRemovalReason.EXPIRED, third.expired.single().removal.reason)
        assertNull(engine.get("player", "arc:pulse"))
    }

    @Test
    fun `remove clear subject and clear effect report reasons`() {
        val engine = CustomEffectEngine<String, String>()
        engine.apply("one", "arc:a", 20, 0, 1, EffectReapplyPolicy.REPLACE)
        engine.apply("one", "arc:b", 20, 0, 1, EffectReapplyPolicy.REPLACE)
        engine.apply("two", "arc:a", 20, 0, 1, EffectReapplyPolicy.REPLACE)

        val removed = engine.remove("one", "arc:b", EffectRemovalReason.REMOVED)
        assertEquals(EffectRemovalReason.REMOVED, removed?.reason)

        val subject = engine.clearSubject("one", EffectRemovalReason.CLEARED)
        assertEquals(listOf("arc:a"), subject.map { it.effect.effectKey })

        val effect = engine.clearEffect("arc:a", EffectRemovalReason.UNREGISTERED)
        assertEquals(listOf("two"), effect.map { it.subject })
        assertTrue(engine.snapshotAll().isEmpty())
    }

    @Test
    fun `rejects invalid active state inputs`() {
        val engine = CustomEffectEngine<String, String>()

        assertFailsWith<IllegalArgumentException> {
            engine.apply("player", "arc:a", 0, 0, 1, EffectReapplyPolicy.REPLACE)
        }
        assertFailsWith<IllegalArgumentException> {
            engine.apply("player", "arc:a", 1, -1, 1, EffectReapplyPolicy.REPLACE)
        }
        assertFailsWith<IllegalArgumentException> {
            engine.apply("player", "arc:a", 1, 0, 0, EffectReapplyPolicy.REPLACE)
        }
    }

    @Test
    fun `duration conversion rounds up and rejects non-positive values`() {
        assertEquals(1, 1.milliseconds.toEffectTicks())
        assertEquals(1, 50.milliseconds.toEffectTicks())
        assertEquals(2, 51.milliseconds.toEffectTicks())
        assertFailsWith<IllegalArgumentException> { Duration.ZERO.toEffectTicks() }
        assertFailsWith<IllegalArgumentException> { (-1).milliseconds.toEffectTicks() }
    }
}
