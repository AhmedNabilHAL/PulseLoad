package com.pulseload.feature.runs.dto;

import com.pulseload.engine.api.LatencyStats;
import com.pulseload.engine.executor.ExecutionOutcome;
import com.pulseload.engine.saturation.SaturationReason;

import java.util.List;
import java.util.Map;

public record RunSummaryDto(
        Long totalDispatched,
        Long totalCompleted,
        Long totalDropped,
        Map<ExecutionOutcome, Long> outcomeCounts,
        LatencyStats serviceTimeStats,
        LatencyStats responseTimeStats,
        Double requestedRps,
        Double actualRps,
        Double peakCpuLoad,
        Long peakHeapUsedBytes,
        Boolean saturated,
        List<SaturationReason> saturationReasons
) {
}
