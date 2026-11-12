package com.pulseload.feature.runs.dto;

import com.pulseload.engine.executor.ExecutionOutcome;

import java.util.Map;

public record TestRunMetricDto(
        Double elapsedSeconds,
        Double requestedTarget,
        Double completedRps,
        Long completedCount,
        Long droppedCount,
        Integer activeRequests,
        Long backlog,
        Map<ExecutionOutcome, Long> outcomeCounts,
        Double cpuLoad,
        Long heapUsedBytes
) {
}
