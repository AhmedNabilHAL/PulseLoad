package com.pulseload.engine.strategy;

/**
 * Drives a single run's pacing: {@code OpenModelStrategy} dispatches requests to hit a target
 * RPS, {@code ClosedModelStrategy} keeps a target number of virtual users looping. Both share
 * this contract so the caller doesn't need to know which model a run uses.
 */
public interface LoadStrategy {

    /**
     * Advances the strategy to the given elapsed time: dispatches newly-owed requests (open
     * model) or starts/stops virtual user loops (closed model). Exposed directly so tests can
     * drive it with explicit elapsed times instead of waiting on real time; {@link #run()} is
     * the production entry point.
     */
    void tick(double elapsedSeconds);

    /** Runs {@link #tick} in a loop against real time until the schedule completes or {@link #stop} is called. Blocks the calling thread. */
    void run();

    /** Requests the run loop in {@link #run()} to exit after its current tick. */
    void stop();

    /**
     * Requests owed by the schedule but not yet resolved (dispatched or dropped). Meaningful
     * only for the open model; the closed model has no analogous queue.
     */
    long backlog();

    /** Number of pacing ticks that ran significantly behind their intended interval — a generator-side signal for {@code SaturationDetector}. */
    long tickOverrunCount();
}
