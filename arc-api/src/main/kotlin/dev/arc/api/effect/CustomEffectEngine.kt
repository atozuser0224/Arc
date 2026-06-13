package dev.arc.api.effect

/** Controls how an application interacts with an already-active effect. */
public enum class EffectReapplyPolicy {
    KEEP_STRONGER,
    REPLACE,
    EXTEND,
    IGNORE,
}

/** Describes the state transition produced by an application. */
public enum class EffectApplyResult {
    APPLIED,
    REPLACED,
    REFRESHED,
    EXTENDED,
    IGNORED,
}

/** Explains why an active effect left the engine. */
public enum class EffectRemovalReason {
    EXPIRED,
    REMOVED,
    REPLACED,
    CLEARED,
    UNREGISTERED,
    SHUTDOWN,
}

/** Immutable view of one active effect. */
public data class ActiveCustomEffect<K : Any>(
    val effectKey: K,
    val remainingTicks: Int,
    val amplifier: Int,
    val elapsedTicks: Long,
    val tickInterval: Int,
)

/** Result of applying or reapplying an effect. */
public data class EffectApplication<K : Any>(
    val result: EffectApplyResult,
    val current: ActiveCustomEffect<K>,
    val previous: ActiveCustomEffect<K>? = null,
)

/** One effect removed from a subject. */
public data class EffectRemoval<K : Any>(
    val effect: ActiveCustomEffect<K>,
    val reason: EffectRemovalReason,
)

/** A removal paired with the subject that owned the effect. */
public data class SubjectEffectRemoval<S : Any, K : Any>(
    val subject: S,
    val removal: EffectRemoval<K>,
)

/** A periodic callback that is due for an active effect. */
public data class DueEffectTick<S : Any, K : Any>(
    val subject: S,
    val effect: ActiveCustomEffect<K>,
)

/** All transitions emitted by one logical engine tick. */
public data class EffectTickBatch<S : Any, K : Any>(
    val due: List<DueEffectTick<S, K>>,
    val expired: List<SubjectEffectRemoval<S, K>>,
)

/**
 * Bukkit-independent active-effect state machine.
 *
 * This type is deliberately not thread-safe. Its owner decides the threading
 * model and can therefore keep all transitions deterministic and allocation
 * costs visible.
 */
public class CustomEffectEngine<S : Any, K : Any> {
    private val active = LinkedHashMap<S, LinkedHashMap<K, ActiveCustomEffect<K>>>()

    public fun apply(
        subject: S,
        effectKey: K,
        durationTicks: Int,
        amplifier: Int = 0,
        tickInterval: Int = 1,
        policy: EffectReapplyPolicy = EffectReapplyPolicy.KEEP_STRONGER,
    ): EffectApplication<K> {
        require(durationTicks > 0) { "durationTicks must be positive" }
        require(amplifier >= 0) { "amplifier must not be negative" }
        require(tickInterval > 0) { "tickInterval must be positive" }

        val effects = active.getOrPut(subject) { LinkedHashMap() }
        val previous = effects[effectKey]
        val fresh = ActiveCustomEffect(
            effectKey = effectKey,
            remainingTicks = durationTicks,
            amplifier = amplifier,
            elapsedTicks = 0,
            tickInterval = tickInterval,
        )
        if (previous == null) {
            effects[effectKey] = fresh
            return EffectApplication(EffectApplyResult.APPLIED, fresh)
        }

        return when (policy) {
            EffectReapplyPolicy.IGNORE ->
                EffectApplication(EffectApplyResult.IGNORED, previous, previous)

            EffectReapplyPolicy.REPLACE -> {
                effects[effectKey] = fresh
                EffectApplication(EffectApplyResult.REPLACED, fresh, previous)
            }

            EffectReapplyPolicy.EXTEND -> {
                val extended = previous.copy(
                    remainingTicks = Math.addExact(previous.remainingTicks, durationTicks),
                    amplifier = maxOf(previous.amplifier, amplifier),
                    tickInterval = tickInterval,
                )
                effects[effectKey] = extended
                EffectApplication(EffectApplyResult.EXTENDED, extended, previous)
            }

            EffectReapplyPolicy.KEEP_STRONGER -> when {
                amplifier > previous.amplifier -> {
                    effects[effectKey] = fresh
                    EffectApplication(EffectApplyResult.REPLACED, fresh, previous)
                }

                amplifier == previous.amplifier && durationTicks > previous.remainingTicks -> {
                    effects[effectKey] = fresh
                    EffectApplication(EffectApplyResult.REFRESHED, fresh, previous)
                }

                else -> EffectApplication(EffectApplyResult.IGNORED, previous, previous)
            }
        }
    }

    public fun get(subject: S, effectKey: K): ActiveCustomEffect<K>? =
        active[subject]?.get(effectKey)

    public fun snapshot(subject: S): Map<K, ActiveCustomEffect<K>> =
        active[subject]?.toMap().orEmpty()

    public fun snapshotAll(): Map<S, Map<K, ActiveCustomEffect<K>>> =
        active.mapValuesTo(LinkedHashMap()) { (_, effects) -> effects.toMap() }

    public fun remove(
        subject: S,
        effectKey: K,
        reason: EffectRemovalReason = EffectRemovalReason.REMOVED,
    ): EffectRemoval<K>? {
        val effects = active[subject] ?: return null
        val removed = effects.remove(effectKey) ?: return null
        if (effects.isEmpty()) active.remove(subject)
        return EffectRemoval(removed, reason)
    }

    public fun clearSubject(
        subject: S,
        reason: EffectRemovalReason = EffectRemovalReason.CLEARED,
    ): List<EffectRemoval<K>> =
        active.remove(subject)?.values?.map { EffectRemoval(it, reason) }.orEmpty()

    public fun clearEffect(
        effectKey: K,
        reason: EffectRemovalReason = EffectRemovalReason.UNREGISTERED,
    ): List<SubjectEffectRemoval<S, K>> {
        val removals = ArrayList<SubjectEffectRemoval<S, K>>()
        val subjects = active.keys.toList()
        for (subject in subjects) {
            remove(subject, effectKey, reason)?.let {
                removals += SubjectEffectRemoval(subject, it)
            }
        }
        return removals
    }

    public fun tick(): EffectTickBatch<S, K> {
        val due = ArrayList<DueEffectTick<S, K>>()
        val expired = ArrayList<SubjectEffectRemoval<S, K>>()
        val subjects = active.keys.toList()

        for (subject in subjects) {
            val effects = active[subject] ?: continue
            val keys = effects.keys.toList()
            for (effectKey in keys) {
                val current = effects[effectKey] ?: continue
                val next = current.copy(
                    remainingTicks = current.remainingTicks - 1,
                    elapsedTicks = current.elapsedTicks + 1,
                )
                if (next.elapsedTicks % next.tickInterval == 0L) {
                    due += DueEffectTick(subject, next)
                }
                if (next.remainingTicks <= 0) {
                    effects.remove(effectKey)
                    expired += SubjectEffectRemoval(
                        subject,
                        EffectRemoval(next, EffectRemovalReason.EXPIRED),
                    )
                } else {
                    effects[effectKey] = next
                }
            }
            if (effects.isEmpty()) active.remove(subject)
        }

        return EffectTickBatch(due, expired)
    }
}
