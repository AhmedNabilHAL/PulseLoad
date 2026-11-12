package com.pulseload.engine.strategy;

import com.pulseload.engine.concurrent.TaskDispatcher;
import com.pulseload.engine.metrics.MetricsRecorder;
import com.pulseload.engine.schedule.WorkloadSchedule;
import com.pulseload.engine.time.NanoClock;
import com.pulseload.model.Stage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OpenModelStrategyTest {

    /** Runs dispatched work inline on the calling thread, making dispatch counts exact and deterministic. */
    private static final TaskDispatcher SYNCHRONOUS = Runnable::run;

    @Test
    void dispatchesExactlyTheCumulativeTargetInASingleTick() {
        // Ramp 0 -> 100 RPS over 10s; cumulativeAt(10) = area of the triangle = 500 requests.
        WorkloadSchedule schedule = WorkloadSchedule.of(0, List.of(new Stage(10, 100)));
        FakeProtocolExecutor executor = new FakeProtocolExecutor(1_000_000L);
        MetricsRecorder metricsRecorder = new MetricsRecorder();

        OpenModelStrategy strategy = new OpenModelStrategy(
                schedule, executor, metricsRecorder, SYNCHRONOUS, 1000, NanoClock.SYSTEM);

        strategy.tick(10.0);

        assertThat(executor.callCount()).isEqualTo(500);
        assertThat(metricsRecorder.completedCount()).isEqualTo(500);
        assertThat(metricsRecorder.droppedCount()).isZero();
    }

    @Test
    void accumulatesCorrectlyAcrossManySmallTicksLikeTheRealRunLoopWould() {
        WorkloadSchedule schedule = WorkloadSchedule.of(0, List.of(new Stage(10, 100)));
        FakeProtocolExecutor executor = new FakeProtocolExecutor(1_000_000L);
        MetricsRecorder metricsRecorder = new MetricsRecorder();

        OpenModelStrategy strategy = new OpenModelStrategy(
                schedule, executor, metricsRecorder, SYNCHRONOUS, 1000, NanoClock.SYSTEM);

        for (double elapsed = 0.5; elapsed <= 10.0; elapsed += 0.5) {
            strategy.tick(elapsed);
        }

        assertThat(executor.callCount()).isEqualTo(500);
    }

    @Test
    void dropsRequestsInsteadOfQueueingThemWhenConcurrencyCapIsExceeded() throws InterruptedException {
        // Instant jump to a flat 50 RPS, dispatched concurrently (real virtual threads) against
        // a target that holds each permit for a while, so the concurrency cap actually binds
        // within one tick instead of draining immediately.
        WorkloadSchedule schedule = WorkloadSchedule.of(0, List.of(new Stage(0, 50), new Stage(1, 50)));
        FakeProtocolExecutor slowExecutor = new FakeProtocolExecutor(TimeUnit.MILLISECONDS.toNanos(200));
        MetricsRecorder metricsRecorder = new MetricsRecorder();

        OpenModelStrategy strategy = new OpenModelStrategy(
                schedule, slowExecutor, metricsRecorder, TaskDispatcher.VIRTUAL_THREADS, 1, NanoClock.SYSTEM);

        strategy.tick(1.0);

        // All 50 owed requests are resolved (dispatched or dropped) within this single tick call.
        assertThat(metricsRecorder.dispatchedCount() + metricsRecorder.droppedCount()).isEqualTo(50);
        assertThat(metricsRecorder.droppedCount()).isGreaterThan(0);

        Thread.sleep(300); // let the one admitted request finish so it doesn't leak into other tests
    }
}
