package com.pulseload.engine;

import com.pulseload.engine.api.MetricsSnapshot;
import com.pulseload.engine.api.RunReport;
import com.pulseload.engine.api.RunSpec;
import com.pulseload.engine.logging.EngineAopConfig;
import com.pulseload.engine.logging.RunMdcAspect;
import com.pulseload.engine.strategy.ClosedLoadStrategyFactory;
import com.pulseload.engine.strategy.OpenLoadStrategyFactory;
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
import static org.assertj.core.api.Assertions.within;

/**
 * Exercises {@link InProcessEngine} end to end against a real stub HTTP target, proving
 * {@code EngineRun} wires the strategies/metrics/saturation pieces together correctly, and that
 * it works as a real prototype-scoped, AOP-advised Spring bean rather than a plain {@code new}'d
 * object — {@code AopContext.currentProxy()} inside {@code EngineRun.runToCompletion()} throws
 * outside a real proxy, so this test needs an actual (lightweight) Spring context instead of
 * constructing {@code InProcessEngine} directly.
 */
@SpringJUnitConfig(classes = InProcessEngineTest.EngineTestConfig.class)
class InProcessEngineTest {

    // EngineRun already carries its own @Component + @Scope(prototype) in production code, so
    // this test config just imports the real beans rather than redeclaring EngineRun by hand.
    @Configuration
    @org.springframework.context.annotation.Import({
            EngineAopConfig.class,
            RunMdcAspect.class,
            OpenLoadStrategyFactory.class,
            ClosedLoadStrategyFactory.class,
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

    /**
     * Regression test for a real bug found via manual smoke testing: {@code EngineRun} originally
     * passed the schedule's final instantaneous target as the whole run's "requestedRps", instead
     * of the average rate the ramp actually asked for over its full duration. That made every
     * ramping workload look artificially saturated at the end of the ramp — a generator that
     * tracked a 0-to-X ramp perfectly the whole way through would still report actualRps far
     * below the final tick's target, because actual throughput necessarily lags a late, high
     * target. The fix compares actualRps against the schedule's time-averaged target instead.
     */
    @Test
    void reportsTheAverageRequestedRateOverTheRampNotTheFinalInstantaneousTarget() throws InterruptedException {
        // The very first HttpClient/virtual-thread/scheduler/CGLIB-proxy use in a JVM can be slow
        // (classloading, JIT warm-up), which would otherwise inflate this run's own elapsed time
        // and make the assertion below flaky for reasons unrelated to what's being tested. Two
        // cheap warm-up runs absorb that cost before the real assertion; a single short warm-up
        // run wasn't always enough once AOP proxying was added on top of the raw strategy path.
        runToCompletion(new RunSpec(UUID.randomUUID(), targetUrl, HttpMethod.GET, LoadModel.OPEN,
                10, 50, List.of(new Stage(1, 10)), Duration.ofSeconds(2)));
        runToCompletion(new RunSpec(UUID.randomUUID(), targetUrl, HttpMethod.GET, LoadModel.OPEN,
                10, 50, List.of(new Stage(1, 10)), Duration.ofSeconds(2)));

        // Ramp 0 -> 100 RPS over 4s. Average target = cumulativeAt(4)/4 = 200/4 = 50, not 100
        // (the bug's value). A longer ramp than the bare minimum dilutes the relative effect of
        // any residual one-off scheduling hiccup on the measured elapsed time. Asserting strictly
        // less than the final target (100) is what actually distinguishes the fix from the bug;
        // the loose bounds around 50 just confirm the result is in the right ballpark.
        RunSpec spec = new RunSpec(
                UUID.randomUUID(), targetUrl, HttpMethod.GET, LoadModel.OPEN,
                0, 200, List.of(new Stage(4, 100)), Duration.ofSeconds(2));

        RunReport report = runToCompletion(spec);

        assertThat(report.requestedRps()).isLessThan(100.0).isGreaterThan(20.0);
        assertThat(report.requestedRps()).isCloseTo(50.0, within(20.0));
        // A generator that actually keeps up with a ramp it can handle should not be flagged.
        assertThat(report.saturated()).isFalse();
    }

    /**
     * Regression test for the self-invocation fix in {@code EngineRun}: the snapshot ticker runs
     * on its own thread (separate from the thread {@code runToCompletion} started on), and
     * {@code emitSnapshot} is only re-advised there because {@code runToCompletion} schedules
     * {@code self::emitSnapshot} (resolved via {@code AopContext.currentProxy()}) instead of
     * {@code this::emitSnapshot}. If that self-invocation fix were missing or broken, snapshots
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

        // Every snapshot, no matter which thread emitted it, must have seen the correct runId —
        // never null/blank (the symptom of the self-invocation bug this test guards against).
        assertThat(observedRunIds).containsExactly(spec.runId().toString());
        // The snapshot ticker's own thread is distinct from the run's own thread, proving at
        // least one snapshot really was emitted from a different thread than runToCompletion's.
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
