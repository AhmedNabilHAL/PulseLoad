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
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance tests for the asynchronous-execution topic.
 *
 * <p>
 * 3A (background execution): starting a run must return promptly rather than
 * blocking the
 * caller for the run's whole duration — the run executes asynchronously on a
 * background thread.
 *
 * <p>
 * 3B (virtual-thread concurrency): every virtual user must run concurrently, so
 * a run with N
 * users drives up to N simultaneous in-flight requests, rather than being
 * throttled by a small
 * platform-thread pool. Each fails until the correct execution mechanism is
 * used.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
@Tag("acceptance")
class AsyncExecutionAcceptanceTest {

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
      try {
        // A deliberate per-request delay so concurrent virtual users overlap
        // observably.
        Thread.sleep(150);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
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
  void startRunReturnsPromptlyWithoutBlockingForTheRunDuration() throws InterruptedException {
    com.pulseload.model.Test test = persistTest(2, 4);

    long startNanos = System.nanoTime();
    TestRunDto started = testRunService.startRun(test.getId());
    long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

    try {
      assertThat(elapsedMillis)
          .as("startRun must not block for the run's duration")
          .isLessThan(2000);
    } finally {
      testRunService.stopRun(started.id());
      awaitTerminal(started.id());
    }
  }

  @Test
  void virtualUsersExecuteConcurrently() throws InterruptedException {
    com.pulseload.model.Test test = persistTest(8, 2);

    TestRunDto started = testRunService.startRun(test.getId());

    long peakRequesting = 0;
    long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(6);
    while (System.currentTimeMillis() < deadline) {
      long requesting = testRunService.getVirtualUsers(started.id()).stream()
          .filter(vu -> "REQUESTING".equals(vu.state()))
          .count();
      peakRequesting = Math.max(peakRequesting, requesting);
      if (isTerminal(testRunService.getRunById(started.id()).status())) {
        break;
      }
      Thread.sleep(100);
    }

    assertThat(peakRequesting)
        .as("all 8 virtual users should run concurrently on virtual threads")
        .isGreaterThanOrEqualTo(6);
  }

  private com.pulseload.model.Test persistTest(int virtualUsers, int durationSeconds) {
    WorkloadProfile profile = new WorkloadProfile();
    profile.setLoadModel(LoadModel.CLOSED);
    profile.setStartTarget(virtualUsers);
    profile.setMaxConcurrency(virtualUsers);
    profile.setStages(List.of(new Stage(durationSeconds, virtualUsers)));

    com.pulseload.model.Test test = new com.pulseload.model.Test();
    test.setTestName("Async acceptance target");
    test.setTargetUrl("http://localhost:" + serverPort + "/ok");
    test.setHttpMethod(HttpMethod.GET);
    test.setWorkloadProfile(profile);
    return testRepository.saveAndFlush(test);
  }

  private void awaitTerminal(UUID runId) throws InterruptedException {
    long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(15);
    while (System.currentTimeMillis() < deadline) {
      if (isTerminal(testRunService.getRunById(runId).status())) {
        return;
      }
      Thread.sleep(100);
    }
  }

  private static boolean isTerminal(TestRunStatus status) {
    return status == TestRunStatus.COMPLETED || status == TestRunStatus.FAILED || status == TestRunStatus.CANCELLED;
  }
}
