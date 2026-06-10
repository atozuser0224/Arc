package dev.arc.api.ops.async

import dev.arc.api.ops.scheduler.ArcSchedulers
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Safe async helper for **I/O and pure computation only** — never for Bukkit
 * world/entity/block access.
 *
 * ```java
 * ArcAsync.runBlockingIO(() -> userRepository.load(uuid))
 *         .thenSync(data -> player.sendMessage("Loaded"));   // back on main thread
 * ```
 *
 * Contract (the caller MUST honour this — it is not auto-enforced):
 *  - the [Supplier] body may touch files, sockets, JDBC, caches, math
 *  - it may NOT touch World/Entity/Block/Inventory/Scheduler-bound state
 *  - the result is handed back on the **main thread** in [AsyncResult.thenSync]
 *
 * The continuation is dispatched through Bukkit's main-thread scheduler, so it
 * is correct on Paper today and on a future Folia-style server via the
 * global-region scheduler fallback in [ArcSchedulers].
 */
object ArcAsync {

    @Volatile private var plugin: Plugin? = null

    private val io = Executors.newCachedThreadPool(object : ThreadFactory {
        private val n = AtomicInteger()
        override fun newThread(r: Runnable) = Thread(r, "arc-io-${n.incrementAndGet()}").apply { isDaemon = true }
    })

    fun init(plugin: Plugin) { this.plugin = plugin }

    fun shutdown() { io.shutdownNow() }

    private fun owner(): Plugin = plugin ?: error("ArcAsync not initialised — call ArcAsync.init(plugin) at startup")

    /** Run [supplier] on the I/O pool. */
    @JvmStatic
    fun <T> runBlockingIO(supplier: Supplier<T>): AsyncResult<T> =
        AsyncResult(CompletableFuture.supplyAsync(supplier, io), owner())

    /** Kotlin-friendly overload. */
    fun <T> runBlockingIO(block: () -> T): AsyncResult<T> = runBlockingIO(Supplier(block))

    class AsyncResult<T> internal constructor(
        private val future: CompletableFuture<T>,
        private val plugin: Plugin,
    ) {
        /** Chain another async stage on the I/O pool. */
        fun <R> thenIO(fn: java.util.function.Function<T, R>): AsyncResult<R> =
            AsyncResult(future.thenApplyAsync(fn), plugin)

        /** Deliver the result on the main server thread. */
        fun thenSync(consumer: Consumer<T>): AsyncResult<T> {
            future.whenComplete { value, error ->
                if (error != null) {
                    plugin.logger.warning("[ArcAsync] async task failed: ${error.message}")
                    return@whenComplete
                }
                runSync { consumer.accept(value) }
            }
            return this
        }

        /** Handle a failure on the main thread. */
        fun exceptionallySync(handler: Consumer<Throwable>): AsyncResult<T> {
            future.whenComplete { _, error -> if (error != null) runSync { handler.accept(error) } }
            return this
        }

        fun toFuture(): CompletableFuture<T> = future

        private fun runSync(action: () -> Unit) {
            if (Bukkit.isPrimaryThread()) { action(); return }
            // Prefer the Folia-compat global scheduler; fall back to Bukkit scheduler.
            if (!ArcSchedulers.runGlobal(plugin, action)) {
                Bukkit.getScheduler().runTask(plugin, action)
            }
        }
    }
}
