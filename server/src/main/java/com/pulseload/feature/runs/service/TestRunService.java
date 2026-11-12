package com.pulseload.feature.runs.service;

import com.pulseload.feature.runs.dto.TestRunDto;
import com.pulseload.feature.runs.dto.TestRunMetricDto;
import com.pulseload.feature.runs.dto.VirtualUserDto;

import java.util.List;
import java.util.UUID;

public interface TestRunService {

    TestRunDto startRun(UUID testId);

    List<TestRunDto> getRunsForTest(UUID testId);

    TestRunDto getRunById(UUID runId);

    List<TestRunMetricDto> getMetrics(UUID runId);

    List<VirtualUserDto> getVirtualUsers(UUID runId);

    void stopRun(UUID runId);
}
