package com.pulseload.engine.api;

import com.pulseload.engine.executor.ExecutionOutcome;

import java.util.Map;

/**
 * Point-in-time view of a run, emitted roughly once a second via
 * {@code RunListener.onSnapshot}.
 * {@code requestedTarget} is supplied by the pacing strategy, which is the only
 * component that
 * knows the schedule's current target; everything else comes from the
 * {@code MetricsRecorder}.
 * {@code activeRequests} is how many virtual users are mid-request at this
 * instant.
 */
public record MetricsSnapshot(
                double elapsedSeconds,
                double requestedTarget,
                double completedRps,
                long completedCount,
                long droppedCount,
                int activeRequests,
                Map<ExecutionOutcome, Long> outcomeCounts) {
}
