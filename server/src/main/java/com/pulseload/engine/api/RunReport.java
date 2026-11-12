package com.pulseload.engine.api;

import com.pulseload.engine.executor.ExecutionOutcome;
import com.pulseload.engine.saturation.SaturationReason;

import java.util.List;
import java.util.Map;

/**
 * Final result of a completed run. {@code serviceTime} measures actual send-to-response latency;
 * {@code responseTime} measures intended-send-to-response latency (see {@code MetricsRecorder}) and
 * is the one that should be shown to users under the open model, since it reflects coordinated
 * omission rather than hiding it. {@code saturated}/{@code saturationReasons} come from {@code
 * SaturationDetector} (US 2.4) — {@code saturated} is true only when the shortfall between
 * {@code requestedRps} and {@code actualRps} is explained by the generator's own resources, not
 * merely by a full concurrency cap or a slow target.
 */
public record RunReport(
        long totalDispatched,
        long totalCompleted,
        long totalDropped,
        Map<ExecutionOutcome, Long> outcomeCounts,
        LatencyStats serviceTime,
        LatencyStats responseTime,
        double requestedRps,
        double actualRps,
        double peakCpuLoad,
        long peakHeapUsedBytes,
        boolean saturated,
        List<SaturationReason> saturationReasons
) {
}
