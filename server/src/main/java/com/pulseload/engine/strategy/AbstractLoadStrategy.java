package com.pulseload.engine.strategy;

import com.pulseload.engine.metrics.MetricsRecorder;
import com.pulseload.engine.schedule.WorkloadSchedule;
import com.pulseload.engine.time.NanoClock;

/**
 * Shared {@code run()}/{@code stop()} loop for the load model. Subclasses only
 * implement
 * {@link #tick} (and {@link #onScheduleComplete} to stop VU loops).
 *
 * <p>
 * {@code run()} drives {@code tick} against real elapsed time, ticking roughly
 * every
 * {@value #TICK_INTERVAL_MILLIS}ms until the schedule's duration is reached or
 * {@link #stop} is
 * called externally. Tests bypass this entirely and call {@link #tick} directly
 * with explicit
 * elapsed times, so pacing assertions don't depend on real wall-clock time.
 */
abstract class AbstractLoadStrategy implements LoadStrategy {

    private static final long TICK_INTERVAL_MILLIS = 5;

    protected final WorkloadSchedule schedule;
    protected final MetricsRecorder metricsRecorder;
    private final NanoClock clock;

    private volatile boolean running = true;

    protected AbstractLoadStrategy(WorkloadSchedule schedule, MetricsRecorder metricsRecorder, NanoClock clock) {
        this.schedule = schedule;
        this.metricsRecorder = metricsRecorder;
        this.clock = clock;
    }

    @Override
    public final void run() {
        long startNanos = clock.nanoTime();
        double elapsed = 0;
        while (running && elapsed < schedule.totalDurationSeconds()) {
            long now = clock.nanoTime();
            elapsed = (now - startNanos) / 1_000_000_000.0;
            tick(elapsed);
            if (running && elapsed < schedule.totalDurationSeconds()) {
                sleepTickInterval();
            }
        }
        // Reached here either because the schedule completed or stop() was called;
        // either way,
        // VU loops in the closed model need to see isRunning() == false to wind down.
        running = false;
        onScheduleComplete();
        awaitDrain();
    }

    @Override
    public final void stop() {
        running = false;
    }

    protected final boolean isRunning() {
        return running;
    }

    /**
     * Called once, after the tick loop in {@link #run()} exits for any reason.
     * No-op by default.
     */
    protected void onScheduleComplete() {
    }

    private void sleepTickInterval() {
        try {
            Thread.sleep(TICK_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    /**
     * Blocks until every in-flight request this strategy dispatched has completed.
     */
    private void awaitDrain() {
        while (metricsRecorder.activeRequests() > 0) {
            try {
                Thread.sleep(TICK_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
