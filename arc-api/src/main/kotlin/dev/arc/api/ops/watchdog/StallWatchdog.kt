package dev.arc.api.ops.watchdog

import dev.arc.api.ops.metrics.TickSampler
import java.io.File
import java.lang.management.ManagementFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * Off-thread watcher that detects a *stalled* main thread — the failure mode
 * the on-thread [dev.arc.api.ops.lagspike.LagSpikeMonitor] cannot see, because
 * if the main thread is frozen its own monitoring task never runs.
 *
 * It compares [TickSampler.heartbeatNanos] against wall-clock time on a daemon
 * thread. If the heartbeat hasn't advanced for longer than [thresholdMs], it
 * captures the main thread's stack (and optionally a full thread dump) to a
 * file — exactly the artefact you want when "the server hung for 10 seconds".
 *
 * It is conservative: one dump per distinct stall (it re-arms only after the
 * server recovers), so a long freeze produces a single report, not a flood.
 */
class StallWatchdog(
    private val thresholdMs: Long,
    private val fullThreadDump: Boolean,
    private val dumpDir: File,
    private val log: Logger,
) {
    @Volatile private var running = false
    @Volatile private var armed = true
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread(::loop, "arc-stall-watchdog").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun loop() {
        while (running) {
            val sinceHeartbeatMs = (System.nanoTime() - TickSampler.heartbeatNanos()) / 1_000_000
            if (sinceHeartbeatMs >= thresholdMs && TickSampler.heartbeatNanos() != 0L) {
                if (armed) {
                    armed = false
                    runCatching { capture(sinceHeartbeatMs) }
                        .onFailure { log.warning("[Arc] stall dump failed: ${it.message}") }
                }
            } else if (sinceHeartbeatMs < thresholdMs / 2) {
                armed = true // recovered; re-arm for the next stall
            }
            try {
                Thread.sleep((thresholdMs / 4).coerceIn(50, 1000))
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
    }

    private fun capture(stalledMs: Long) {
        val now = Instant.now()
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault()).format(now)
        val text = buildString {
            appendLine("===== Arc Stall Watchdog =====")
            appendLine("captured : ${DateTimeFormatter.ISO_INSTANT.format(now)}")
            appendLine("stalled  : ~${stalledMs}ms with no tick heartbeat")
            appendLine()

            val server = Thread.getAllStackTraces().keys.firstOrNull { it.name == "Server thread" }
            if (server != null) {
                appendLine("--- Server thread (${server.state}) ---")
                server.stackTrace.forEach { appendLine("  at $it") }
            } else {
                appendLine("(could not locate 'Server thread')")
            }

            // Deadlock detection — a common cause of a hard stall.
            val mx = ManagementFactory.getThreadMXBean()
            val deadlocked = runCatching { mx.findDeadlockedThreads() }.getOrNull()
            if (deadlocked != null && deadlocked.isNotEmpty()) {
                appendLine()
                appendLine("!!! DEADLOCK DETECTED on ${deadlocked.size} thread(s) !!!")
                mx.getThreadInfo(deadlocked, true, true).filterNotNull().forEach {
                    appendLine("  ${it.threadName}: ${it.threadState}")
                }
            }

            if (fullThreadDump) {
                appendLine()
                appendLine("--- Full thread dump ---")
                for ((t, stack) in Thread.getAllStackTraces()) {
                    if (t.name == "Server thread") continue
                    appendLine("\"${t.name}\" ${t.state}")
                    stack.take(12).forEach { appendLine("  at $it") }
                }
            }
        }

        dumpDir.mkdirs()
        val file = File(dumpDir, "stall-$stamp.txt")
        file.writeText(text)
        log.warning("[Arc] Main thread stalled ~${stalledMs}ms — dump written to ${file.path}")
    }
}
