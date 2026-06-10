package dev.arc.api.ops.profiler

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

/**
 * A lightweight statistical profiler for the main server thread — a tiny,
 * dependency-free stand-in for Spark when you just need "where is the tick
 * spending its time".
 *
 * It runs an off-thread sampler that periodically grabs the server thread's
 * stack trace and counts the top (self) frame. Because it samples rather than
 * instruments, its overhead is bounded and it never touches Bukkit state, so it
 * is safe to leave running briefly on a live server.
 *
 * Caveats (be honest with operators):
 *  - self-time only; it does not build inclusive call trees
 *  - a frame's share is statistical — short bursts between samples are missed
 *  - native/blocked frames show up as whatever Java frame called them
 */
object MainThreadProfiler {

    private const val MAIN_THREAD_NAME = "Server thread"

    private val selfCounts = ConcurrentHashMap<String, LongAdder>()
    private val totalSamples = AtomicLong()
    private val missedSamples = AtomicLong()

    @Volatile private var running = false
    @Volatile private var intervalMs = 10L
    @Volatile private var startedAt = 0L
    private var thread: Thread? = null

    val isRunning: Boolean get() = running

    fun start(intervalMs: Long = 10): Boolean {
        if (running) return false
        this.intervalMs = intervalMs.coerceIn(2, 1000)
        selfCounts.clear()
        totalSamples.set(0)
        missedSamples.set(0)
        startedAt = System.currentTimeMillis()
        running = true
        thread = Thread(::loop, "arc-profiler").apply { isDaemon = true; start() }
        return true
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun loop() {
        while (running) {
            val server = serverThread()
            if (server == null) {
                missedSamples.incrementAndGet()
            } else {
                val stack = server.stackTrace
                if (stack.isEmpty()) {
                    missedSamples.incrementAndGet()
                } else {
                    totalSamples.incrementAndGet()
                    val leaf = stack[0]
                    val key = "${leaf.className}.${leaf.methodName}"
                    selfCounts.computeIfAbsent(key) { LongAdder() }.increment()
                }
            }
            try {
                Thread.sleep(intervalMs)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
    }

    private fun serverThread(): Thread? =
        Thread.getAllStackTraces().keys.firstOrNull { it.name == MAIN_THREAD_NAME }

    fun report(limit: Int = 20): String {
        val total = totalSamples.get()
        return buildString {
            appendLine("===== Leaf Profiler (main thread, self-time) =====")
            appendLine("state    : ${if (running) "RUNNING" else "stopped"}  interval=${intervalMs}ms")
            appendLine("duration : ${(System.currentTimeMillis() - startedAt) / 1000}s")
            appendLine("samples  : $total (missed=${missedSamples.get()})")
            if (total == 0L) {
                append("no samples yet — start with /leaf profiler start"); return@buildString
            }
            appendLine("top frames:")
            selfCounts.entries
                .map { it.key to it.value.sum() }
                .sortedByDescending { it.second }
                .take(limit)
                .forEach { (frame, count) ->
                    val pct = count.toDouble() / total * 100
                    appendLine("  ${"%5.1f%%".format(pct)}  ${count.toString().padStart(6)}  $frame")
                }
            append("note: statistical self-time, not an inclusive call tree")
        }
    }
}
