package com.pulseload.engine;

import com.pulseload.config.AsyncConfig;
import com.pulseload.engine.api.MetricsSnapshot;
import com.pulseload.engine.api.RunReport;
import com.pulseload.engine.api.RunSpec;
import com.pulseload.engine.logging.EngineAopConfig;
import com.pulseload.engine.logging.RunMdcAspect;
import com.pulseload.engine.strategy.ClosedLoadStrategyFactory;
import com.pulseload.engine.vu.VirtualUserRegistry;
import com.pulseload.model.HttpMethod;
import com.pulseload.model.LoadModel;
import com.pulseload.model.Stage;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link InProcessEngine} end to end against a real stub HTTP target,
 * proving
 * {@code EngineRun} wires the strategy/metrics pieces together correctly, that
 * it works as a real
 * prototype-scoped, AOP-advised Spring bean (so
 * {@code AopContext.currentProxy()} inside
 * {@code EngineRun.runToCompletion()} resolves), and that {@code @Async}
 * launches the run on a
 * background thread.
 */
@SpringJUnitConfig(classes = InProcessEngineTest.EngineTestConfig.class)
class InProcessEngineTest {

    // EngineRun already carries its own @Component + @Scope(prototype) in
    // production code, so
    // this test config just imports the real beans rather than redeclaring
    // EngineRun by hand.
    @Configuration
    @org.springframework.context.annotation.Import({
            AsyncConfig.class,
            EngineAopConfig.class,
            RunMdcAspect.class,
            ClosedLoadStrategyFactory.class,
            VirtualUserRegistry.class,
            AsyncRunLauncher.class,
            EngineRun.class,
            InProcessEngine.class
    })
    static class EngineTestConfig {
    }

    @Autowired
    private InProcessEngine engine;

    private HttpServer server;
    private String targetUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/ok", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        targetUrl = "http://localhost:" + server.getAddress().getPort() + "/ok";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        MDC.clear();
    }

    @Test
    void runCompletesAndReportsCompletedRequests() throws InterruptedException {
        RunSpec spec = new RunSpec(UUID.randomUUID(), targetUrl, HttpMethod.GET, LoadModel.CLOSED,
                3, 10, List.of(new Stage(1, 3)), Duration.ofSeconds(2));

        RunReport report = runToCompletion(spec);

        assertThat(report.totalCompleted()).isGreaterThan(0);
        assertThat(report.actualRps()).isGreaterThan(0.0);
    }

    /**
     * Regression test for the self-invocation fix in {@code EngineRun}: the
     * snapshot ticker runs
     * on its own thread (separate from the thread {@code runToCompletion} started
     * on), and
     * {@code emitSnapshot} is only re-advised there because {@code runToCompletion}
     * schedules
     * {@code self::emitSnapshot} (resolved via {@code AopContext.currentProxy()})
     * instead of
     * {@code this::emitSnapshot}. If that self-invocation fix were missing or
     * broken, snapshots
     * emitted from the ticker thread would see a blank {@code runId} in MDC.
     */
    @Test
    void propagatesRunIdIntoMdcOnTheTickerThreadNotJustTheRunThread() throws InterruptedException {
        RunSpec spec = new RunSpec(UUID.randomUUID(), targetUrl, HttpMethod.GET, LoadModel.CLOSED,
                2, 10, List.of(new Stage(2, 2)), Duration.ofSeconds(2));

        Set<String> observedRunIds = ConcurrentHashMap.newKeySet();
        Set<String> observedThreadNames = ConcurrentHashMap.newKeySet();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<RunReport> reportRef = new AtomicReference<>();

        engine.start(spec, new RunListener() {
            @Override
            public void onSnapshot(MetricsSnapshot snapshot) {
                observedRunIds.add(MDC.get("runId"));
                observedThreadNames.add(Thread.currentThread().getName());
            }

            @Override
            public void onCompleted(RunReport report, boolean cancelled) {
                reportRef.set(report);
                completed.countDown();
            }

            @Override
            public void onFailed(Throwable cause) {
                completed.countDown();
            }
        });

        assertThat(completed.await(10, TimeUnit.SECONDS)).as("run should finish within the timeout").isTrue();
        assertThat(reportRef.get()).as("run should complete successfully, not fail").isNotNull();

        // Every snapshot, no matter which thread emitted it, must have seen the correct
        // runId —
        // never null/blank (the symptom of the self-invocation bug this test guards
        // against).
        assertThat(observedRunIds).containsExactly(spec.runId().toString());
        assertThat(observedThreadNames.size()).isGreaterThanOrEqualTo(1);
    }

    private RunReport runToCompletion(RunSpec spec) throws InterruptedException {
        AtomicReference<RunReport> reportRef = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);

        engine.start(spec, new RunListener() {
            @Override
            public void onSnapshot(MetricsSnapshot snapshot) {
            }

            @Override
            public void onCompleted(RunReport report, boolean cancelled) {
                reportRef.set(report);
                completed.countDown();
            }

            @Override
            public void onFailed(Throwable cause) {
                completed.countDown();
            }
        });

        boolean finished = completed.await(10, TimeUnit.SECONDS);
        assertThat(finished).as("run should finish within the timeout").isTrue();
        assertThat(reportRef.get()).as("run should complete successfully, not fail").isNotNull();
        return reportRef.get();
    }
}
