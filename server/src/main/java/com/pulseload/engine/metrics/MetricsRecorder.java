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
 * Collects per-request outcomes for a single run. Two histograms are kept deliberately separate:
 * {@code serviceTime} is actual send-to-response latency, while {@code responseTime} is measured
 * from the request's <em>intended</em> send time (per the workload schedule) to response. Under
 * the open model, serviceTime alone would hide coordinated omission — a generator that falls
 * behind schedule still reports fast service times for the requests it does get out, even though
 * users experienced much longer waits for the ones it was late to send.
 *
 * <p>Thread-safe: {@code recordCompletion} is called concurrently from many virtual threads, while
 * {@code intervalServiceTimeHistogram}/{@code intervalResponseTimeHistogram} are called periodically
 * from a single reporting thread. HdrHistogram's {@link Recorder} is built for exactly this pattern.
 */
public final class MetricsRecorder {

    private final Recorder serviceTimeRecorder = new Recorder(3);
    private final Recorder responseTimeRecorder = new Recorder(3);
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

    /**
     * @param serviceTimeNanos  actual send-to-response latency
     * @param responseTimeNanos intended-send-to-response latency; equal to serviceTimeNanos under
     *                          the closed model, where coordinated omission is inherent by design
     */
    public void recordCompletion(ExecutionOutcome outcome, long serviceTimeNanos, long responseTimeNanos) {
        activeRequests.decrementAndGet();
        outcomeCounts.get(outcome).increment();
        serviceTimeRecorder.recordValue(serviceTimeNanos);
        responseTimeRecorder.recordValue(responseTimeNanos);
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

    /** Snapshot-and-reset of service time recorded since the last call; intended for periodic reporting. */
    public Histogram intervalServiceTimeHistogram() {
        return serviceTimeRecorder.getIntervalHistogram();
    }

    /** Snapshot-and-reset of response time recorded since the last call; intended for periodic reporting. */
    public Histogram intervalResponseTimeHistogram() {
        return responseTimeRecorder.getIntervalHistogram();
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
                histogram.getValueAtPercentile(99)
        );
    }
}
