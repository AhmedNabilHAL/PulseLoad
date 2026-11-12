package com.pulseload.engine.strategy;

/**
 * Drives a single run's pacing: {@code ClosedModelStrategy} keeps a target
 * number of virtual
 * users looping. The contract is kept small so the caller doesn't need to know
 * pacing details.
 */
public interface LoadStrategy {

    /**
     * Advances the strategy to the given elapsed time: starts or stops virtual user
     * loops to match
     * the schedule's current target. Exposed directly so tests can drive it with
     * explicit elapsed
     * times instead of waiting on real time; {@link #run()} is the production entry
     * point.
     */
    void tick(double elapsedSeconds);

    /**
     * Runs {@link #tick} in a loop against real time until the schedule completes
     * or {@link #stop} is called. Blocks the calling thread.
     */
    void run();

    /** Requests the run loop in {@link #run()} to exit after its current tick. */
    void stop();
}
