package com.pulseload.engine.api;

import com.pulseload.engine.executor.ExecutionOutcome;

import java.util.Map;

/**
 * Point-in-time view of a run, emitted roughly once a second via {@code RunListener.onSnapshot}.
 * {@code requestedTarget} and {@code sentCount} are supplied by the pacing strategy, which is the
 * only component that knows the schedule's current target and how many requests it has dispatched;
 * everything else comes from the {@code MetricsRecorder}.
 */
public record MetricsSnapshot(
        double elapsedSeconds,
        double requestedTarget,
        double completedRps,
        long completedCount,
        long droppedCount,
        int activeRequests,
        long backlog,
        Map<ExecutionOutcome, Long> outcomeCounts,
        double cpuLoad,
        long heapUsedBytes
) {
}
