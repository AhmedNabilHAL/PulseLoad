package com.pulseload.engine.metrics;

import com.pulseload.engine.api.LatencyStats;
import com.pulseload.engine.executor.ExecutionOutcome;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Collects per-request outcomes and end-to-end latency for a single run.
 *
 * <p>
 * Thread-safe: {@code recordCompletion} is called concurrently from many
 * virtual threads, while
 * {@code intervalLatencyHistogram} is called periodically from a single
 * reporting thread.
 * HdrHistogram's {@link Recorder} is built for exactly this pattern.
 */
public final class MetricsRecorder {

    private final Recorder latencyRecorder = new Recorder(3);
    private final Map<ExecutionOutcome, LongAdder> outcomeCounts = new EnumMap<>(ExecutionOutcome.class);
    private final LongAdder droppedCount = new LongAdder();
    private final LongAdder dispatchedCount = new LongAdder();
    private final AtomicInteger activeRequests = new AtomicInteger();

    public MetricsRecorder() {
        for (ExecutionOutcome outcome : ExecutionOutcome.values()) {
            outcomeCounts.put(outcome, new LongAdder());
        }
    }

    public void recordDispatch() {
        dispatchedCount.increment();
        activeRequests.incrementAndGet();
    }

    /** @param latencyNanos end-to-end send-to-response latency for this request */
    public void recordCompletion(ExecutionOutcome outcome, long latencyNanos) {
        activeRequests.decrementAndGet();
        outcomeCounts.get(outcome).increment();
        latencyRecorder.recordValue(latencyNanos);
    }

    public void recordDropped() {
        droppedCount.increment();
    }

    public int activeRequests() {
        return activeRequests.get();
    }

    public long dispatchedCount() {
        return dispatchedCount.sum();
    }

    public long droppedCount() {
        return droppedCount.sum();
    }

    public long completedCount() {
        return outcomeCounts.values().stream().mapToLong(LongAdder::sum).sum();
    }

    public Map<ExecutionOutcome, Long> outcomeCountsSnapshot() {
        Map<ExecutionOutcome, Long> snapshot = new EnumMap<>(ExecutionOutcome.class);
        outcomeCounts.forEach((outcome, adder) -> snapshot.put(outcome, adder.sum()));
        return snapshot;
    }

    /**
     * Snapshot-and-reset of latency recorded since the last call; intended for
     * periodic reporting.
     */
    public Histogram intervalLatencyHistogram() {
        return latencyRecorder.getIntervalHistogram();
    }

    public static LatencyStats statsOf(Histogram histogram) {
        if (histogram.getTotalCount() == 0) {
            return new LatencyStats(0, 0, 0, 0, 0, 0, 0);
        }
        return new LatencyStats(
                histogram.getMinValue(),
                histogram.getMean(),
                histogram.getMaxValue(),
                histogram.getValueAtPercentile(50),
                histogram.getValueAtPercentile(90),
                histogram.getValueAtPercentile(95),
                histogram.getValueAtPercentile(99));
    }
}
