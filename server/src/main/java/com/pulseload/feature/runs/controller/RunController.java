package com.pulseload.feature.runs.controller;

import com.pulseload.feature.runs.dto.TestRunDto;
import com.pulseload.feature.runs.dto.TestRunMetricDto;
import com.pulseload.feature.runs.service.TestRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RunController {

    private final TestRunService testRunService;

    @PostMapping("/tests/{testId}/runs")
    public ResponseEntity<TestRunDto> startRun(@PathVariable UUID testId) {
        TestRunDto run = testRunService.startRun(testId);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/runs/{runId}")
                .buildAndExpand(run.id())
                .toUri();
        return ResponseEntity.accepted().location(location).body(run);
    }

    @GetMapping("/tests/{testId}/runs")
    public ResponseEntity<List<TestRunDto>> getRunsForTest(@PathVariable UUID testId) {
        return ResponseEntity.ok(testRunService.getRunsForTest(testId));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<TestRunDto> getRunById(@PathVariable UUID runId) {
        return ResponseEntity.ok(testRunService.getRunById(runId));
    }

    @GetMapping("/runs/{runId}/metrics")
    public ResponseEntity<List<TestRunMetricDto>> getMetrics(@PathVariable UUID runId) {
        return ResponseEntity.ok(testRunService.getMetrics(runId));
    }

    @PostMapping("/runs/{runId}/stop")
    public ResponseEntity<Void> stopRun(@PathVariable UUID runId) {
        testRunService.stopRun(runId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
