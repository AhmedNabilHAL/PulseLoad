package com.pulseload.engine.strategy;

import com.pulseload.engine.concurrent.TaskDispatcher;
import com.pulseload.engine.executor.ExecutionResult;
import com.pulseload.engine.executor.ProtocolExecutor;
import com.pulseload.engine.metrics.MetricsRecorder;
import com.pulseload.engine.schedule.WorkloadSchedule;
import com.pulseload.engine.time.NanoClock;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrency pacing: keeps a target number of virtual users looping — each VU sends a request,
 * waits for the response, then immediately sends the next one. Each tick adjusts the running VU
 * count to {@link WorkloadSchedule#targetAt}, starting new loops or signalling excess ones to
 * stop after their current iteration (never mid-request).
 *
 * <p>Coordinated omission is inherent to this model by construction — a VU can't send its next
 * request any sooner than its last one returned, so there is no "intended send time" independent
 * of actual response times to correct against (unlike the open model). {@code serviceTimeNanos}
 * and {@code responseTimeNanos} are recorded as the same value; this is a property of the model
 * that US 2.3 is teaching, not a bug to fix here.
 *
 * <p>Each VU loop is minted with a stable index when started ({@code runningVus} doubles as the
 * index source, since it always reflects the current live count). A loop keeps running only
 * while its own index is within the current target; when the target drops, the VUs with the
 * highest indices — the most recently started ones — are the ones that wind down, never mid-request.
 * This avoids every VU racing to re-read a single shared "should I stop" flag, which
 * would make all of them stop at once instead of just the excess.
 */
public final class ClosedModelStrategy extends AbstractLoadStrategy {

    private final ProtocolExecutor executor;
    private final TaskDispatcher dispatcher;
    private final AtomicInteger targetVus = new AtomicInteger();
    private final AtomicInteger runningVus = new AtomicInteger();

    public ClosedModelStrategy(WorkloadSchedule schedule,
                                ProtocolExecutor executor,
                                MetricsRecorder metricsRecorder,
                                TaskDispatcher dispatcher,
                                NanoClock clock) {
        super(schedule, metricsRecorder, clock);
        this.executor = executor;
        this.dispatcher = dispatcher;
    }

    @Override
    public void tick(double elapsedSeconds) {
        int target = (int) Math.round(schedule.targetAt(elapsedSeconds));
        targetVus.set(target);

        int toStart = target - runningVus.get();
        for (int i = 0; i < toStart; i++) {
            int myIndex = runningVus.incrementAndGet();
            dispatcher.dispatch(() -> vuLoop(myIndex));
        }
        // Excess VUs (those with the highest indices) notice their index exceeds the new,
        // lower targetVus and stop themselves at the end of their current iteration.
    }

    @Override
    protected void onScheduleComplete() {
        targetVus.set(0);
    }

    private void vuLoop(int myIndex) {
        try {
            while (isRunning() && myIndex <= targetVus.get()) {
                metricsRecorder.recordDispatch();
                ExecutionResult result = executor.execute();
                metricsRecorder.recordCompletion(result.outcome(), result.serviceTimeNanos(), result.serviceTimeNanos());
            }
        } finally {
            runningVus.decrementAndGet();
        }
    }

    /** Always 0: the closed model has no dispatch queue, only a target VU count. */
    @Override
    public long backlog() {
        return 0;
    }
}
