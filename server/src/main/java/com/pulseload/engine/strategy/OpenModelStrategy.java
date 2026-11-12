package com.pulseload.engine.strategy;

import com.pulseload.engine.concurrent.TaskDispatcher;
import com.pulseload.engine.executor.ExecutionResult;
import com.pulseload.engine.executor.ProtocolExecutor;
import com.pulseload.engine.metrics.MetricsRecorder;
import com.pulseload.engine.schedule.WorkloadSchedule;
import com.pulseload.engine.time.NanoClock;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Arrival-rate pacing: each tick dispatches however many requests the schedule says should have
 * gone out by now ({@link WorkloadSchedule#cumulativeAt}) but haven't yet, gated by a
 * {@link Semaphore} capping in-flight requests at {@code maxConcurrency}.
 *
 * <p>If no permit is free at a request's intended dispatch time, that request is dropped
 * immediately rather than queued — a full semaphore means the generator (or the target) can't
 * keep up with the schedule, and letting owed requests pile up would let them burst out later,
 * which is not the load the test asked for. Because every owed request is resolved (dispatched
 * or dropped) within the tick that owes it, there is no persistent backlog across ticks; see
 * {@link #backlog()}.
 *
 * <p>Latency is anchored to real wall-clock time via {@link System#nanoTime()} regardless of
 * which {@link NanoClock} drives {@link #run()}'s own tick timing — {@code responseTimeNanos}
 * measures from the request's <em>intended</em> send time (per the schedule) to its actual
 * completion, which is what avoids coordinated omission under this model.
 */
public final class OpenModelStrategy extends AbstractLoadStrategy {

    private final ProtocolExecutor executor;
    private final TaskDispatcher dispatcher;
    private final Semaphore concurrencyLimit;
    private final long runStartNanos = System.nanoTime();

    /** Count of owed requests already resolved (dispatched or dropped), i.e. the 1-based index of the next owed request. */
    private final AtomicLong resolvedCount = new AtomicLong();

    public OpenModelStrategy(WorkloadSchedule schedule,
                              ProtocolExecutor executor,
                              MetricsRecorder metricsRecorder,
                              TaskDispatcher dispatcher,
                              int maxConcurrency,
                              NanoClock clock) {
        super(schedule, metricsRecorder, clock);
        this.executor = executor;
        this.dispatcher = dispatcher;
        this.concurrencyLimit = new Semaphore(maxConcurrency);
    }

    @Override
    public void tick(double elapsedSeconds) {
        long owedByNow = (long) Math.floor(schedule.cumulativeAt(elapsedSeconds));
        long owedThisTick = owedByNow - resolvedCount.get();

        for (long i = 0; i < owedThisTick; i++) {
            long requestIndex = resolvedCount.incrementAndGet();
            if (concurrencyLimit.tryAcquire()) {
                dispatchOne(requestIndex);
            } else {
                metricsRecorder.recordDropped();
            }
        }
    }

    private void dispatchOne(long requestIndex) {
        metricsRecorder.recordDispatch();
        double intendedElapsedSeconds = schedule.elapsedAtCumulative(requestIndex);
        long intendedSendNanos = runStartNanos + (long) (intendedElapsedSeconds * 1_000_000_000.0);

        dispatcher.dispatch(() -> {
            try {
                ExecutionResult result = executor.execute();

                long responseTimeNanos = Math.max(0, System.nanoTime() - intendedSendNanos);
                metricsRecorder.recordCompletion(result.outcome(), result.serviceTimeNanos(), responseTimeNanos);
            } finally {
                concurrencyLimit.release();
            }
        });
    }

    /**
     * Always 0: every request owed by a given tick is resolved (dispatched or dropped) within
     * that same tick, so no owed-but-unresolved backlog persists between ticks by design.
     */
    @Override
    public long backlog() {
        return 0;
    }
}
