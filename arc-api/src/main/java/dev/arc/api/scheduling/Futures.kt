package dev.arc.api.scheduling

import dev.arc.api.Arc
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * Execute [block] on Bukkit's legacy primary scheduler and expose its result
 * without blocking. Prefer [callGlobal], [callEntity], or [callRegion] on Folia.
 */
fun <T> Plugin.callSync(block: () -> T): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    val task = Runnable {
        if (!isEnabled) {
            future.cancel(false)
            return@Runnable
        }
        runCatching(block).fold(future::complete, future::completeExceptionally)
    }
    if (Bukkit.isPrimaryThread()) {
        task.run()
    } else {
        runCatching { Bukkit.getScheduler().runTask(this, task) }
            .onFailure(future::completeExceptionally)
    }
    return future
}

/**
 * Execute CPU or blocking work on Bukkit's async executor. Bukkit and NMS
 * objects must not be read or mutated inside [block].
 */
fun <T> Plugin.callAsync(block: () -> T): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    val task = Runnable {
        if (!isEnabled) {
            future.cancel(false)
            return@Runnable
        }
        runCatching(block).fold(future::complete, future::completeExceptionally)
    }
    runCatching { Bukkit.getScheduler().runTaskAsynchronously(this, task) }
        .onFailure(future::completeExceptionally)
    return future
}

/** Execute [block] on Paper's global region thread. */
fun <T> Plugin.callGlobal(block: () -> T): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    val task = Runnable {
        if (!isEnabled) {
            future.cancel(false)
            return@Runnable
        }
        runCatching(block).fold(future::complete, future::completeExceptionally)
    }
    if (Bukkit.isGlobalTickThread()) {
        task.run()
    } else {
        runCatching { Bukkit.getGlobalRegionScheduler().execute(this, task) }
            .onFailure(future::completeExceptionally)
    }
    return future
}

/** Execute [block] on the tick thread that currently owns [entity]. */
fun <T> Plugin.callEntity(entity: Entity, block: () -> T): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    val expectedWorld = entity.world
    val run = Runnable {
        if (!isEnabled || !entity.isValid || entity.world !== expectedWorld) {
            future.cancel(false)
            return@Runnable
        }
        runCatching(block).fold(future::complete, future::completeExceptionally)
    }
    val ownerScheduler = Arc.threadScheduler
    if (ownerScheduler != null) {
        val scheduled = runCatching { ownerScheduler.executeEntity(this, entity, run) }
            .getOrElse {
                future.completeExceptionally(it)
                false
            }
        if (!scheduled && !future.isDone) future.cancel(false)
        return future
    }
    val retired = Runnable { future.cancel(false) }
    val scheduled = runCatching { entity.scheduler.execute(this, run, retired, 1L) }
        .getOrElse {
            future.completeExceptionally(it)
            false
        }
    if (!scheduled) future.cancel(false)
    return future
}

/** Execute [block] on the region thread that owns [location]. */
fun <T> Plugin.callRegion(location: Location, block: () -> T): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    val world = location.world
    if (world == null) {
        future.completeExceptionally(IllegalArgumentException("Location has no world"))
        return future
    }
    val task = Runnable {
        if (!isEnabled) {
            future.cancel(false)
            return@Runnable
        }
        runCatching(block).fold(future::complete, future::completeExceptionally)
    }
    val ownerScheduler = Arc.threadScheduler
    if (ownerScheduler != null) {
        val scheduled = runCatching { ownerScheduler.executeWorld(this, world, task) }
            .getOrElse {
                future.completeExceptionally(it)
                false
            }
        if (!scheduled && !future.isDone) future.cancel(false)
        return future
    }
    runCatching { Bukkit.getRegionScheduler().execute(this, location, task) }
        .onFailure(future::completeExceptionally)
    return future
}

/** Continue this stage on Bukkit's legacy primary scheduler. */
fun <T, R> CompletionStage<T>.thenSync(
    plugin: Plugin,
    block: (T) -> R,
): CompletableFuture<R> = thenCompose { value -> plugin.callSync { block(value) } }.toCompletableFuture()

/** Continue this stage on Bukkit's async executor. */
fun <T, R> CompletionStage<T>.thenAsync(
    plugin: Plugin,
    block: (T) -> R,
): CompletableFuture<R> = thenCompose { value -> plugin.callAsync { block(value) } }.toCompletableFuture()

/** Continue this stage on Paper's global region thread. */
fun <T, R> CompletionStage<T>.thenGlobal(
    plugin: Plugin,
    block: (T) -> R,
): CompletableFuture<R> = thenCompose { value -> plugin.callGlobal { block(value) } }.toCompletableFuture()

/** Continue this stage on the tick thread that owns [entity]. */
fun <T, R> CompletionStage<T>.thenEntity(
    plugin: Plugin,
    entity: Entity,
    block: (T) -> R,
): CompletableFuture<R> = thenCompose { value ->
    plugin.callEntity(entity) { block(value) }
}.toCompletableFuture()

/** Continue this stage on the region thread that owns [location]. */
fun <T, R> CompletionStage<T>.thenRegion(
    plugin: Plugin,
    location: Location,
    block: (T) -> R,
): CompletableFuture<R> = thenCompose { value ->
    plugin.callRegion(location) { block(value) }
}.toCompletableFuture()
