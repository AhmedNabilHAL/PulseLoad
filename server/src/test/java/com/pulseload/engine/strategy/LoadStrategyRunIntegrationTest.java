package com.pulseload.engine.strategy;

import com.pulseload.engine.concurrent.TaskDispatcher;
import com.pulseload.engine.executor.HttpExecutor;
import com.pulseload.engine.executor.ProtocolExecutor;
import com.pulseload.engine.metrics.MetricsRecorder;
import com.pulseload.engine.schedule.WorkloadSchedule;
import com.pulseload.engine.time.NanoClock;
import com.pulseload.model.HttpMethod;
import com.pulseload.model.Stage;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Exercises {@code run()} itself (not just direct {@code tick()} calls) against real elapsed
 * time and a real HTTP target, proving the strategies, {@code HttpExecutor} and
 * {@code MetricsRecorder} from Phases 2-4 work together, not just in isolation.
 */
class LoadStrategyRunIntegrationTest {

    private HttpServer server;
    private URI targetUri;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/ok", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        targetUri = URI.create("http://localhost:" + server.getAddress().getPort() + "/ok");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void openModelRunCompletesAndDispatchesRoughlyTheScheduledRequestCount() {
        // Flat 50 RPS for 1 real second => ~50 requests expected.
        WorkloadSchedule schedule = WorkloadSchedule.of(50, List.of(new Stage(1, 50)));
        MetricsRecorder metricsRecorder = new MetricsRecorder();

        try (ProtocolExecutor executor = new HttpExecutor(targetUri, HttpMethod.GET, Duration.ofSeconds(2))) {
            OpenModelStrategy strategy = new OpenModelStrategy(
                    schedule, executor, metricsRecorder, TaskDispatcher.VIRTUAL_THREADS, 100, NanoClock.SYSTEM);

            strategy.run();

            assertThat(metricsRecorder.activeRequests()).isZero();
            assertThat(metricsRecorder.completedCount()).isCloseTo(50, within(15L));
            assertThat(metricsRecorder.droppedCount()).isZero();
        }
    }

    @Test
    void closedModelRunCompletesAndStopsAllVuLoopsBySchedulesEnd() {
        // Flat 5 VUs for 1 real second.
        WorkloadSchedule schedule = WorkloadSchedule.of(5, List.of(new Stage(1, 5)));
        MetricsRecorder metricsRecorder = new MetricsRecorder();

        try (ProtocolExecutor executor = new HttpExecutor(targetUri, HttpMethod.GET, Duration.ofSeconds(2))) {
            ClosedModelStrategy strategy = new ClosedModelStrategy(
                    schedule, executor, metricsRecorder, TaskDispatcher.VIRTUAL_THREADS, NanoClock.SYSTEM);

            strategy.run();

            assertThat(metricsRecorder.activeRequests()).isZero();
            assertThat(metricsRecorder.completedCount()).isGreaterThan(0);
        }
    }
}
