package com.pulseload.feature.runs.dto;

import com.pulseload.engine.api.LatencyStats;
import com.pulseload.engine.executor.ExecutionOutcome;

import java.util.Map;

public record RunSummaryDto(
                Long totalDispatched,
                Long totalCompleted,
                Long totalDropped,
                Map<ExecutionOutcome, Long> outcomeCounts,
                LatencyStats latencyStats,
                Double requestedRps,
                Double actualRps) {
}
