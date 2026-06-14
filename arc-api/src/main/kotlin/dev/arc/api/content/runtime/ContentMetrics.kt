package dev.arc.api.content.runtime

import java.util.concurrent.atomic.AtomicLong

public data class ContentMetricsSnapshot(
    public val compileNanos: Long,
    public val revisionBytes: Long,
    public val cacheHits: Long,
    public val cacheMisses: Long,
    public val transferredBytes: Long,
    public val paletteEncodeNanos: Long,
)

public class ContentMetrics {
    private val compileNanos = AtomicLong()
    private val revisionBytes = AtomicLong()
    private val cacheHits = AtomicLong()
    private val cacheMisses = AtomicLong()
    private val transferredBytes = AtomicLong()
    private val paletteEncodeNanos = AtomicLong()

    public fun recordCompile(elapsedNanos: Long, bytes: Long) {
        require(elapsedNanos >= 0 && bytes >= 0) { "Compile metrics must not be negative" }
        compileNanos.set(elapsedNanos)
        revisionBytes.set(bytes)
    }

    public fun recordCache(hit: Boolean) {
        (if (hit) cacheHits else cacheMisses).incrementAndGet()
    }

    public fun recordTransfer(bytes: Long) {
        require(bytes >= 0) { "Transferred bytes must not be negative" }
        transferredBytes.addAndGet(bytes)
    }

    public fun recordPaletteEncode(elapsedNanos: Long) {
        require(elapsedNanos >= 0) { "Palette encode time must not be negative" }
        paletteEncodeNanos.addAndGet(elapsedNanos)
    }

    public fun snapshot(): ContentMetricsSnapshot =
        ContentMetricsSnapshot(
            compileNanos.get(),
            revisionBytes.get(),
            cacheHits.get(),
            cacheMisses.get(),
            transferredBytes.get(),
            paletteEncodeNanos.get(),
        )
}
