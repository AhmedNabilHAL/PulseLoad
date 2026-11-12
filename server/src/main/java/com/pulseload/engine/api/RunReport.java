package com.pulseload.engine.api;

import com.pulseload.engine.executor.ExecutionOutcome;

import java.util.Map;

/**
 * Final result of a completed run: request totals, per-outcome counts,
 * end-to-end request
 * latency, and the requested-vs-actual throughput the run achieved.
 */
public record RunReport(
                long totalDispatched,
                long totalCompleted,
                long totalDropped,
                Map<ExecutionOutcome, Long> outcomeCounts,
                LatencyStats latency,
                double requestedRps,
                double actualRps) {
}
