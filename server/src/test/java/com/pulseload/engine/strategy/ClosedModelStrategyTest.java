package com.pulseload.engine.strategy;

import com.pulseload.engine.concurrent.TaskDispatcher;
import com.pulseload.engine.metrics.MetricsRecorder;
import com.pulseload.engine.schedule.WorkloadSchedule;
import com.pulseload.engine.time.NanoClock;
import com.pulseload.model.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ClosedModelStrategyTest {

    /**
     * Every strategy created by a test is stopped here so its VU loops exit their {@code while}
     * condition instead of spinning forever once the test's executor gate is released — without
     * this, leaked tight-looping virtual threads from one test starve the next test's VUs of
     * carrier threads within the assertion timeout.
     */
    private final List<ClosedModelStrategy> createdStrategies = new ArrayList<>();

    private final List<BlockingExecutor> createdExecutors = new ArrayList<>();

    @AfterEach
    void stopAllStrategies() {
        createdStrategies.forEach(ClosedModelStrategy::stop);
        // Also release every executor's gate so any VU parked inside execute() can observe
        // stop() and exit its loop, instead of blocking forever and leaking into later tests.
        createdExecutors.forEach(BlockingExecutor::release);
    }

    private ClosedModelStrategy newStrategy(WorkloadSchedule schedule, BlockingExecutor executor, MetricsRecorder metricsRecorder) {
        ClosedModelStrategy strategy = new ClosedModelStrategy(
                schedule, executor, metricsRecorder, TaskDispatcher.VIRTUAL_THREADS, NanoClock.SYSTEM);
        createdStrategies.add(strategy);
        return strategy;
    }

    private BlockingExecutor newBlockingExecutor() {
        BlockingExecutor executor = new BlockingExecutor();
        executor.block();
        createdExecutors.add(executor);
        return executor;
    }

    /**
     * A VU "request" that blocks until released, so the test can observe exactly how many VU
     * loops are mid-iteration at a given moment instead of racing real completions.
     */
    private static final class BlockingExecutor implements com.pulseload.engine.executor.ProtocolExecutor {
        private final AtomicInteger inFlight = new AtomicInteger();
        private volatile CountDownLatch gate = new CountDownLatch(0);

        void block() {
            gate = new CountDownLatch(1);
        }

        void release() {
            gate.countDown();
        }

        @Override
        public com.pulseload.engine.executor.ExecutionResult execute() {
            inFlight.incrementAndGet();
            try {
                gate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
            }
            return new com.pulseload.engine.executor.ExecutionResult(
                    com.pulseload.engine.executor.ExecutionOutcome.SUCCESS_2XX, 200, 1_000_000L);
        }

        int inFlight() {
            return inFlight.get();
        }

        @Override
        public void close() {
        }
    }

    @Test
    void startsVuLoopsUpToTheTargetAtAGivenElapsedTime() throws InterruptedException {
        // Ramp 0 -> 10 VUs over 10s; at t=5, target should be 5.
        WorkloadSchedule schedule = WorkloadSchedule.of(0, List.of(new Stage(10, 10)));
        BlockingExecutor executor = newBlockingExecutor();
        MetricsRecorder metricsRecorder = new MetricsRecorder();
        ClosedModelStrategy strategy = newStrategy(schedule, executor, metricsRecorder);

        strategy.tick(5.0);
        awaitInFlight(executor, 5);

        assertThat(executor.inFlight()).isEqualTo(5);
        assertThat(metricsRecorder.activeRequests()).isEqualTo(5);
    }

    @Test
    void doesNotStartMoreVuLoopsThanTheCurrentTargetOnRepeatedTicksAtTheSameTime() throws InterruptedException {
        WorkloadSchedule schedule = WorkloadSchedule.of(0, List.of(new Stage(10, 10)));
        BlockingExecutor executor = newBlockingExecutor();
        MetricsRecorder metricsRecorder = new MetricsRecorder();
        ClosedModelStrategy strategy = newStrategy(schedule, executor, metricsRecorder);

        strategy.tick(5.0);
        awaitInFlight(executor, 5);
        strategy.tick(5.0);
        strategy.tick(5.0);
        Thread.sleep(50);

        assertThat(executor.inFlight()).isEqualTo(5);
    }

    @Test
    void ticksUpVuCountAsTheRampProgresses() throws InterruptedException {
        WorkloadSchedule schedule = WorkloadSchedule.of(0, List.of(new Stage(10, 10)));
        BlockingExecutor executor = newBlockingExecutor();
        MetricsRecorder metricsRecorder = new MetricsRecorder();
        ClosedModelStrategy strategy = newStrategy(schedule, executor, metricsRecorder);

        strategy.tick(2.0);
        awaitInFlight(executor, 2);
        assertThat(executor.inFlight()).isEqualTo(2);

        strategy.tick(8.0);
        awaitInFlight(executor, 8);
        assertThat(executor.inFlight()).isEqualTo(8);
    }

    private static void awaitInFlight(BlockingExecutor executor, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(2);
        while (executor.inFlight() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
    }
}
