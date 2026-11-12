package com.pulseload.feature.runs.dto;

import com.pulseload.model.TestRunStatus;
import com.pulseload.feature.tests.dto.WorkloadProfileDto;

import java.time.Instant;
import java.util.UUID;

public record TestRunDto(
        UUID id,
        UUID testId,
        TestRunStatus status,
        WorkloadProfileDto configSnapshot,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        RunSummaryDto summary
) {
}
