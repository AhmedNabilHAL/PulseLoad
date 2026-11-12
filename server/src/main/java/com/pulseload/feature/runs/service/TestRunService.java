package com.pulseload.feature.runs.service;

import com.pulseload.feature.runs.dto.TestRunDto;
import com.pulseload.feature.runs.dto.TestRunMetricDto;

import java.util.List;
import java.util.UUID;

public interface TestRunService {

    TestRunDto startRun(UUID testId);

    List<TestRunDto> getRunsForTest(UUID testId);

    TestRunDto getRunById(UUID runId);

    List<TestRunMetricDto> getMetrics(UUID runId);

    void stopRun(UUID runId);
}
