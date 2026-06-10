package dev.arc.api.perf

/**
 * Source of live server-load telemetry for [ServerLoad]. arc-api ships a Bukkit-API default (100-tick
 * average MSPT + TPS); arc-server registers a reflective backend that can report the *instantaneous*
 * last-tick MSPT, letting the [AdaptiveGovernor] react to spikes a beat sooner than the smoothed average.
 */
public interface PerfBackend {

    /** Current milliseconds-per-tick. Lower is better; >50 means the server is behind. */
    public val mspt: Double

    /** Current ticks-per-second (capped at 20). */
    public val tps: Double
}
