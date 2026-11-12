package com.pulseload.workshop;

import com.pulseload.TestcontainersConfiguration;
import com.pulseload.feature.runs.dto.TestRunDto;
import com.pulseload.feature.runs.service.TestRunService;
import com.pulseload.feature.tests.data.TestRepository;
import com.pulseload.model.HttpMethod;
import com.pulseload.model.LoadModel;
import com.pulseload.model.Stage;
import com.pulseload.model.TestRunStatus;
import com.pulseload.model.WorkloadProfile;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance test for the transactions topic. Starting a run publishes a {@code RunCreatedEvent}
 * that an {@code @TransactionalEventListener(AFTER_COMMIT)} consumes to launch the engine — so the
 * engine only runs against a durably committed row. That listener only fires when {@code startRun}
 * executes inside a transaction; without {@code @Transactional}, the event is dropped and the run
 * is stranded in QUEUED. Fails until {@code startRun} is transactional.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
@Tag("acceptance")
class TransactionAcceptanceTest {

    @Autowired
    private TestRepository testRepository;

    @Autowired
    private TestRunService testRunService;

    private HttpServer server;
    private int serverPort;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/ok", exchange -> {
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
    void startedRunProgressesBeyondQueued() throws InterruptedException {
        com.pulseload.model.Test test = persistTest();

        TestRunDto started = testRunService.startRun(test.getId());

        boolean progressed = false;
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
        while (System.currentTimeMillis() < deadline) {
            if (testRunService.getRunById(started.id()).status() != TestRunStatus.QUEUED) {
                progressed = true;
                break;
            }
            Thread.sleep(100);
        }

        assertThat(progressed)
                .as("a started run must begin executing once its creating transaction commits")
                .isTrue();
    }

    private com.pulseload.model.Test persistTest() {
        WorkloadProfile profile = new WorkloadProfile();
        profile.setLoadModel(LoadModel.CLOSED);
        profile.setStartTarget(2);
        profile.setMaxConcurrency(2);
        profile.setStages(List.of(new Stage(1, 2)));

        com.pulseload.model.Test test = new com.pulseload.model.Test();
        test.setTestName("Transaction acceptance target");
        test.setTargetUrl("http://localhost:" + serverPort + "/ok");
        test.setHttpMethod(HttpMethod.GET);
        test.setWorkloadProfile(profile);
        return testRepository.saveAndFlush(test);
    }
}
