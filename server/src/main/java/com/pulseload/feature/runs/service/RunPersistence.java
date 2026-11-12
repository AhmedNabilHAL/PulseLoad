package com.pulseload.feature.runs.service;

import com.pulseload.engine.api.MetricsSnapshot;
import com.pulseload.engine.api.RunReport;
import com.pulseload.model.RunSummary;
import com.pulseload.model.TestRun;
import com.pulseload.model.TestRunMetric;
import com.pulseload.model.TestRunStatus;
import com.pulseload.feature.runs.data.TestRunMetricRepository;
import com.pulseload.feature.runs.data.TestRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes run state to the database from the engine's own reporting thread. Every method here is
 * {@code REQUIRES_NEW} so a slow write never shares (or gets blocked by) the transaction that
 * created the run, and so a failure writing one snapshot can't roll back a previous one. Called
 * from {@link EngineRunListener}, which itself runs on a dedicated single-thread executor rather
 * than the engine's pacing or ticker thread, so a slow or blocked write never perturbs pacing
 * accuracy.
 */
@Component
@RequiredArgsConstructor
class RunPersistence {

    private final TestRunRepository testRunRepository;
    private final TestRunMetricRepository testRunMetricRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markRunning(UUID runId) {
        testRunRepository.findById(runId).ifPresent(run -> {
            run.setStatus(TestRunStatus.RUNNING);
            run.setStartedAt(Instant.now());
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void saveSnapshot(UUID runId, MetricsSnapshot snapshot) {
        TestRun run = testRunRepository.findById(runId).orElse(null);
        if (run == null) {
            return;
        }

        TestRunMetric metric = new TestRunMetric();
        metric.setTestRun(run);
        metric.setElapsedSeconds(snapshot.elapsedSeconds());
        metric.setRequestedTarget(snapshot.requestedTarget());
        metric.setCompletedRps(snapshot.completedRps());
        metric.setCompletedCount(snapshot.completedCount());
        metric.setDroppedCount(snapshot.droppedCount());
        metric.setActiveRequests(snapshot.activeRequests());
        metric.setBacklog(snapshot.backlog());
        metric.setOutcomeCounts(snapshot.outcomeCounts());
        metric.setCpuLoad(snapshot.cpuLoad() >= 0 ? snapshot.cpuLoad() : null);
        metric.setHeapUsedBytes(snapshot.heapUsedBytes());
        testRunMetricRepository.save(metric);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void saveCompletion(UUID runId, RunReport report, boolean cancelled) {
        testRunRepository.findById(runId).ifPresent(run -> {
            run.setStatus(cancelled ? TestRunStatus.CANCELLED : TestRunStatus.COMPLETED);
            run.setFinishedAt(Instant.now());
            run.setSummary(toSummary(report));
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void saveFailure(UUID runId, Throwable cause) {
        testRunRepository.findById(runId).ifPresent(run -> {
            run.setStatus(TestRunStatus.FAILED);
            run.setFinishedAt(Instant.now());
        });
    }

    private static RunSummary toSummary(RunReport report) {
        RunSummary summary = new RunSummary();
        summary.setTotalDispatched(report.totalDispatched());
        summary.setTotalCompleted(report.totalCompleted());
        summary.setTotalDropped(report.totalDropped());
        summary.setOutcomeCounts(report.outcomeCounts());
        summary.setServiceTimeStats(report.serviceTime());
        summary.setResponseTimeStats(report.responseTime());
        summary.setRequestedRps(report.requestedRps());
        summary.setActualRps(report.actualRps());
        summary.setPeakCpuLoad(report.peakCpuLoad() >= 0 ? report.peakCpuLoad() : null);
        summary.setPeakHeapUsedBytes(report.peakHeapUsedBytes());
        summary.setSaturated(report.saturated());
        summary.setSaturationReasons(report.saturationReasons());
        return summary;
    }
}
