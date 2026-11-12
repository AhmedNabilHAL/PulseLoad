package com.pulseload.feature.runs;

import com.pulseload.TestcontainersConfiguration;
import com.pulseload.model.HttpMethod;
import com.pulseload.model.LoadModel;
import com.pulseload.model.Stage;
import com.pulseload.model.TestRunStatus;
import com.pulseload.model.WorkloadProfile;
import com.pulseload.feature.runs.dto.TestRunDto;
import com.pulseload.feature.runs.dto.TestRunMetricDto;
import com.pulseload.feature.runs.service.TestRunService;
import com.pulseload.feature.tests.data.TestRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real {@code TestRunService} end to end: persisted {@code Test} -> start a run
 * via the service (exactly as {@code RunController} would) -> real engine strategy dispatching
 * real HTTP requests against a local stub server -> poll until the DB row reflects completion.
 * Proves Phases 2-6 (schedule, executor, metrics, strategies, persistence, coordinator, service)
 * actually work together end to end, not just each in isolation.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class RunLifecycleIntegrationTest {

    @Autowired
    private TestRepository testRepository;

    @Autowired
    private TestRunService testRunService;

    private HttpServer server;
    private int serverPort;
    private final AtomicInteger requestCount = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        requestCount.set(0);
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/ok", exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        serverPort = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void openModelRunGoesThroughTheFullLifecycleAndProducesASummary() throws InterruptedException {
        com.pulseload.model.Test test = persistTest(LoadModel.OPEN, 20, 100, List.of(new Stage(1, 20)));

        TestRunDto started = testRunService.startRun(test.getId());
        assertThat(started.status()).isEqualTo(TestRunStatus.PENDING);

        TestRunDto finished = awaitTerminalStatus(started.id());

        assertThat(finished.status()).isEqualTo(TestRunStatus.COMPLETED);
        assertThat(finished.startedAt()).isNotNull();
        assertThat(finished.finishedAt()).isNotNull();
        assertThat(finished.summary()).isNotNull();
        assertThat(finished.summary().totalCompleted()).isGreaterThan(0);
        assertThat(requestCount.get()).isEqualTo(finished.summary().totalCompleted().intValue());

        List<TestRunMetricDto> metrics = testRunService.getMetrics(started.id());
        assertThat(metrics).isNotEmpty();
    }

    @Test
    void closedModelRunGoesThroughTheFullLifecycleAndProducesASummary() throws InterruptedException {
        com.pulseload.model.Test test = persistTest(LoadModel.CLOSED, 3, 10, List.of(new Stage(1, 3)));

        TestRunDto started = testRunService.startRun(test.getId());
        TestRunDto finished = awaitTerminalStatus(started.id());

        assertThat(finished.status()).isEqualTo(TestRunStatus.COMPLETED);
        assertThat(finished.summary().totalCompleted()).isGreaterThan(0);
    }

    private com.pulseload.model.Test persistTest(LoadModel loadModel, int startTarget, int maxConcurrency, List<Stage> stages) {
        WorkloadProfile profile = new WorkloadProfile();
        profile.setLoadModel(loadModel);
        profile.setStartTarget(startTarget);
        profile.setMaxConcurrency(maxConcurrency);
        profile.setStages(stages);

        com.pulseload.model.Test test = new com.pulseload.model.Test();
        test.setTestName("Integration test target");
        test.setTargetUrl("http://localhost:" + serverPort + "/ok");
        test.setHttpMethod(HttpMethod.GET);
        test.setWorkloadProfile(profile);

        return testRepository.saveAndFlush(test);
    }

    private TestRunDto awaitTerminalStatus(UUID runId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(15);
        TestRunDto run;
        do {
            Thread.sleep(100);
            run = testRunService.getRunById(runId);
        } while (isNonTerminal(run.status()) && System.currentTimeMillis() < deadline);
        return run;
    }

    private static boolean isNonTerminal(TestRunStatus status) {
        return status == TestRunStatus.PENDING || status == TestRunStatus.RUNNING;
    }
}
