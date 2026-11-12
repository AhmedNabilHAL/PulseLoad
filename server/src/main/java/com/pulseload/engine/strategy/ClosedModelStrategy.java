package com.pulseload.engine.strategy;

import com.pulseload.engine.concurrent.TaskDispatcher;
import com.pulseload.engine.executor.ExecutionResult;
import com.pulseload.engine.executor.ProtocolExecutor;
import com.pulseload.engine.metrics.MetricsRecorder;
import com.pulseload.engine.schedule.WorkloadSchedule;
import com.pulseload.engine.time.NanoClock;
import com.pulseload.engine.vu.VirtualUserRegistry;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrency pacing: keeps a target number of virtual users looping — each VU
 * sends a request,
 * waits for the response, then immediately sends the next one. Each tick
 * adjusts the running VU
 * count to {@link WorkloadSchedule#targetAt}, starting new loops or signalling
 * excess ones to
 * stop after their current iteration (never mid-request).
 *
 * <p>
 * Each VU loop is minted with a stable index when started ({@code runningVus}
 * doubles as the
 * index source, since it always reflects the current live count). A loop keeps
 * running only
 * while its own index is within the current target; when the target drops, the
 * VUs with the
 * highest indices — the most recently started ones — are the ones that wind
 * down, never mid-request.
 * This avoids every VU racing to re-read a single shared "should I stop" flag,
 * which
 * would make all of them stop at once instead of just the excess.
 *
 * <p>
 * Each VU reports its own lifecycle transitions to the
 * {@link VirtualUserRegistry} so the
 * control plane can show participants what every virtual user is doing —
 * distinct from the JVM
 * virtual thread it runs on.
 */
public final class ClosedModelStrategy extends AbstractLoadStrategy {

    private final ProtocolExecutor executor;
    private final TaskDispatcher dispatcher;
    private final VirtualUserRegistry vuRegistry;
    private final UUID runId;
    private final AtomicInteger targetVus = new AtomicInteger();
    private final AtomicInteger runningVus = new AtomicInteger();

    public ClosedModelStrategy(UUID runId,
            WorkloadSchedule schedule,
            ProtocolExecutor executor,
            MetricsRecorder metricsRecorder,
            TaskDispatcher dispatcher,
            VirtualUserRegistry vuRegistry,
            NanoClock clock) {
        super(schedule, metricsRecorder, clock);
        this.runId = runId;
        this.executor = executor;
        this.dispatcher = dispatcher;
        this.vuRegistry = vuRegistry;
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
        // Excess VUs (those with the highest indices) notice their index exceeds the
        // new,
        // lower targetVus and stop themselves at the end of their current iteration.
    }

    @Override
    protected void onScheduleComplete() {
        targetVus.set(0);
    }

    private void vuLoop(int myIndex) {
        vuRegistry.started(runId, myIndex);
        boolean failed = false;
        try {
            while (isRunning() && myIndex <= targetVus.get()) {
                vuRegistry.requestStarted(runId, myIndex);
                metricsRecorder.recordDispatch();
                ExecutionResult result = executor.execute();
                metricsRecorder.recordCompletion(result.outcome(), result.serviceTimeNanos());
                vuRegistry.requestCompleted(runId, myIndex, result.outcome());
            }
        } catch (RuntimeException e) {
            failed = true;
            throw e;
        } finally {
            runningVus.decrementAndGet();
            vuRegistry.finished(runId, myIndex, failed);
        }
    }
}
