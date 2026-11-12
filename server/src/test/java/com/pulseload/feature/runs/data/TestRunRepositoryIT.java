package com.pulseload.feature.runs.data;

import com.pulseload.TestcontainersConfiguration;
import com.pulseload.engine.api.LatencyStats;
import com.pulseload.engine.executor.ExecutionOutcome;
import com.pulseload.engine.saturation.SaturationReason;
import com.pulseload.model.HttpMethod;
import com.pulseload.model.LoadModel;
import com.pulseload.model.RunSummary;
import com.pulseload.model.Stage;
import com.pulseload.model.TestRun;
import com.pulseload.model.TestRunMetric;
import com.pulseload.model.TestRunStatus;
import com.pulseload.model.WorkloadProfile;
import com.pulseload.feature.tests.data.TestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class TestRunRepositoryIT {

    @Autowired
    private TestRepository testRepository;

    @Autowired
    private TestRunRepository testRunRepository;

    @Autowired
    private TestRunMetricRepository testRunMetricRepository;

    @Test
    void persistsAndReloadsAFinishedRunWithItsConfigSnapshotAndSummary() {
        com.pulseload.model.Test test = persistedTest();

        TestRun run = new TestRun();
        run.setTest(test);
        run.setStatus(TestRunStatus.COMPLETED);
        run.setConfigSnapshot(workloadProfile());
        run.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        run.setStartedAt(Instant.parse("2026-01-01T00:00:01Z"));
        run.setFinishedAt(Instant.parse("2026-01-01T00:01:01Z"));

        RunSummary summary = new RunSummary();
        summary.setTotalDispatched(500L);
        summary.setTotalCompleted(498L);
        summary.setTotalDropped(2L);
        summary.setOutcomeCounts(Map.of(ExecutionOutcome.SUCCESS_2XX, 490L, ExecutionOutcome.SERVER_ERROR_5XX, 8L));
        summary.setServiceTimeStats(new LatencyStats(1_000_000L, 5_000_000.0, 50_000_000L, 4_000_000L, 8_000_000L, 12_000_000L, 20_000_000L));
        summary.setResponseTimeStats(new LatencyStats(1_500_000L, 7_000_000.0, 80_000_000L, 5_000_000L, 10_000_000L, 18_000_000L, 40_000_000L));
        summary.setRequestedRps(100.0);
        summary.setActualRps(98.5);
        summary.setPeakCpuLoad(0.42);
        summary.setPeakHeapUsedBytes(536_870_912L);
        summary.setSaturated(false);
        summary.setSaturationReasons(List.of(SaturationReason.CONCURRENCY_CAP_REACHED));
        run.setSummary(summary);

        TestRun saved = testRunRepository.saveAndFlush(run);

        TestRun reloaded = testRunRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getStatus()).isEqualTo(TestRunStatus.COMPLETED);
        assertThat(reloaded.getConfigSnapshot().getLoadModel()).isEqualTo(LoadModel.OPEN);
        assertThat(reloaded.getConfigSnapshot().getStartTarget()).isEqualTo(10);
        assertThat(reloaded.getConfigSnapshot().getMaxConcurrency()).isEqualTo(200);
        assertThat(reloaded.getConfigSnapshot().getStages()).containsExactly(new Stage(60, 100));
        assertThat(reloaded.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));

        RunSummary reloadedSummary = reloaded.getSummary();
        assertThat(reloadedSummary.getTotalDispatched()).isEqualTo(500L);
        assertThat(reloadedSummary.getOutcomeCounts())
                .containsEntry(ExecutionOutcome.SUCCESS_2XX, 490L)
                .containsEntry(ExecutionOutcome.SERVER_ERROR_5XX, 8L);
        assertThat(reloadedSummary.getServiceTimeStats().p99Nanos()).isEqualTo(20_000_000L);
        assertThat(reloadedSummary.getResponseTimeStats().p99Nanos()).isEqualTo(40_000_000L);
        assertThat(reloadedSummary.getSaturated()).isFalse();
        assertThat(reloadedSummary.getSaturationReasons()).containsExactly(SaturationReason.CONCURRENCY_CAP_REACHED);
    }

    @Test
    void findByStatusInReturnsOnlyMatchingRuns() {
        com.pulseload.model.Test test = persistedTest();

        TestRun pending = newRun(test, TestRunStatus.PENDING);
        TestRun running = newRun(test, TestRunStatus.RUNNING);
        TestRun completed = newRun(test, TestRunStatus.COMPLETED);
        testRunRepository.saveAllAndFlush(List.of(pending, running, completed));

        List<TestRun> orphanCandidates = testRunRepository.findByStatusIn(List.of(TestRunStatus.PENDING, TestRunStatus.RUNNING));

        assertThat(orphanCandidates).extracting(TestRun::getStatus)
                .containsExactlyInAnyOrder(TestRunStatus.PENDING, TestRunStatus.RUNNING);
    }

    @Test
    void persistsMetricsLinkedToARunAndOrdersThemByElapsedSeconds() {
        com.pulseload.model.Test test = persistedTest();
        TestRun run = testRunRepository.saveAndFlush(newRun(test, TestRunStatus.RUNNING));

        testRunMetricRepository.saveAllAndFlush(List.of(
                metricAt(run, 2.0),
                metricAt(run, 1.0),
                metricAt(run, 3.0)
        ));

        List<TestRunMetric> metrics = testRunMetricRepository.findByTestRunOrderByElapsedSecondsAsc(run);

        assertThat(metrics).extracting(TestRunMetric::getElapsedSeconds).containsExactly(1.0, 2.0, 3.0);
        assertThat(metrics.getFirst().getOutcomeCounts()).containsEntry(ExecutionOutcome.SUCCESS_2XX, 10L);
    }

    private com.pulseload.model.Test persistedTest() {
        com.pulseload.model.Test test = new com.pulseload.model.Test();
        test.setTestName("Checkout API ramp");
        test.setTargetUrl("https://example.com/checkout");
        test.setHttpMethod(HttpMethod.POST);
        test.setWorkloadProfile(workloadProfile());
        return testRepository.saveAndFlush(test);
    }

    private static WorkloadProfile workloadProfile() {
        WorkloadProfile profile = new WorkloadProfile();
        profile.setLoadModel(LoadModel.OPEN);
        profile.setStartTarget(10);
        profile.setMaxConcurrency(200);
        profile.setStages(List.of(new Stage(60, 100)));
        return profile;
    }

    private static TestRun newRun(com.pulseload.model.Test test, TestRunStatus status) {
        TestRun run = new TestRun();
        run.setTest(test);
        run.setStatus(status);
        run.setConfigSnapshot(workloadProfile());
        run.setCreatedAt(Instant.now());
        return run;
    }

    private static TestRunMetric metricAt(TestRun run, double elapsedSeconds) {
        TestRunMetric metric = new TestRunMetric();
        metric.setTestRun(run);
        metric.setElapsedSeconds(elapsedSeconds);
        metric.setRequestedTarget(100.0);
        metric.setCompletedRps(95.0);
        metric.setCompletedCount(95L);
        metric.setDroppedCount(0L);
        metric.setActiveRequests(5);
        metric.setBacklog(0L);
        metric.setOutcomeCounts(Map.of(ExecutionOutcome.SUCCESS_2XX, 10L));
        metric.setCpuLoad(0.3);
        metric.setHeapUsedBytes(100_000_000L);
        return metric;
    }
}
