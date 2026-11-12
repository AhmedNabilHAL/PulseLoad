package com.pulseload.engine;

import com.pulseload.engine.api.MetricsSnapshot;
import com.pulseload.engine.api.RunReport;
import com.pulseload.engine.api.RunSpec;
import com.pulseload.engine.executor.ProtocolExecutor;
import com.pulseload.engine.logging.RunIdentifiable;
import com.pulseload.engine.logging.RunMdcScope;
import com.pulseload.engine.metrics.MetricsRecorder;
import com.pulseload.engine.metrics.RunSummaryCalculator;
import com.pulseload.engine.schedule.WorkloadSchedule;
import com.pulseload.engine.strategy.LoadStrategy;
import com.pulseload.engine.strategy.LoadStrategyFactory;
import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Drives one run end to end: runs the {@link LoadStrategy} built for it to
 * completion on a
 * dedicated thread, and separately ticks a 1-second reporting loop that turns
 * {@link MetricsRecorder} readings into {@link MetricsSnapshot}s for the
 * listener.
 *
 * <p>
 * A prototype-scoped Spring bean: a fresh instance (and a fresh CGLIB proxy) is
 * created per
 * run via {@code ObjectProvider<EngineRun>.getObject(...)} in
 * {@link InProcessEngine}, with every
 * constructor argument supplied explicitly. Being a real proxied bean is what
 * lets
 * {@link RunMdcScope @RunMdcScope} apply to
 * {@link #runToCompletion()}/{@link #emitSnapshot()}
 * instead of each wrapping its own body in an explicit MDC helper.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
class EngineRun implements RunIdentifiable {

    private static final Logger log = LoggerFactory.getLogger(EngineRun.class);
    private static final long SNAPSHOT_INTERVAL_MILLIS = 1000;

    private final RunSpec spec;
    private final RunListener listener;
    private final ProtocolExecutor executor;
    private final MetricsRecorder metricsRecorder = new MetricsRecorder();
    private final RunSummaryCalculator summaryCalculator = new RunSummaryCalculator();
    private final WorkloadSchedule schedule;
    private final LoadStrategy strategy;
    private final ScheduledExecutorService snapshotTicker = Executors.newSingleThreadScheduledExecutor();
    private final CountDownLatch strategyFinished = new CountDownLatch(1);
    private final long startNanos = System.nanoTime();
    private volatile boolean stopRequested = false;

    EngineRun(RunSpec spec, ProtocolExecutor executor, RunListener listener, LoadStrategyFactory strategyFactory) {
        this.spec = spec;
        this.executor = executor;
        this.listener = listener;
        this.schedule = WorkloadSchedule.of(spec.startTarget(), spec.stages());
        this.strategy = strategyFactory.create(spec, schedule, executor, metricsRecorder);
    }

    @Override
    public UUID runId() {
        return spec.runId();
    }

    /**
     * Blocks until the run completes, fails, or is stopped. Safe to call from any
     * thread.
     */
    @RunMdcScope
    public void runToCompletion() {
        try {
            // Must be resolved here, on this thread: AopContext.currentProxy() throws on
            // any
            // other thread. Scheduling the ticker through `self` rather than
            // `this::emitSnapshot`
            // is what lets the ticker's own thread go through the proxy and get re-advised
            // with
            // the right runId on each tick, instead of calling the real object directly and
            // skipping the aspect entirely (a plain self-invocation bypasses the proxy).
            EngineRun self = (EngineRun) AopContext.currentProxy();

            log.info("Run starting: startTarget={} maxConcurrency={} stages={}",
                    spec.startTarget(), spec.maxConcurrency(), spec.stages().size());

            snapshotTicker.scheduleAtFixedRate(self::emitSnapshot, SNAPSHOT_INTERVAL_MILLIS, SNAPSHOT_INTERVAL_MILLIS,
                    TimeUnit.MILLISECONDS);
            strategy.run();
            emitSnapshot(); // plain self-call, on purpose: already inside this method's advised
                            // scope on this same thread, so runId is already set correctly
            double elapsed = elapsedSeconds();
            // The average requested target over the whole run (cumulativeAt(elapsed) /
            // elapsed),
            // not the instantaneous final-tick target.
            double averageRequestedRps = elapsed > 0 ? schedule.cumulativeAt(elapsed) / elapsed
                    : schedule.targetAt(elapsed);
            RunReport report = summaryCalculator.finish(metricsRecorder, elapsed, averageRequestedRps);

            if (stopRequested) {
                log.info("Run cancelled after {}s: completed={} dropped={}", String.format("%.1f", elapsed),
                        report.totalCompleted(), report.totalDropped());
            } else {
                log.info("Run completed after {}s: completed={} dropped={} actualRps={}",
                        String.format("%.1f", elapsed), report.totalCompleted(), report.totalDropped(),
                        String.format("%.1f", report.actualRps()));
            }
            listener.onCompleted(report, stopRequested);
        } catch (Exception e) {
            log.error("Run failed", e);
            listener.onFailed(e);
        } finally {
            snapshotTicker.shutdownNow();
            executor.close();
            strategyFinished.countDown();
        }
    }

    public void stop() {
        stopRequested = true;
        strategy.stop();
    }

    /** Blocks the calling thread until {@link #runToCompletion} has returned. */
    public void awaitFinished() throws InterruptedException {
        strategyFinished.await();
    }

    @RunMdcScope
    public void emitSnapshot() {
        try {
            double elapsed = elapsedSeconds();
            Histogram latencyInterval = metricsRecorder.intervalLatencyHistogram();
            summaryCalculator.accumulate(latencyInterval);

            long completedCount = metricsRecorder.completedCount();
            double completedRps = elapsed > 0 ? completedCount / elapsed : 0;

            MetricsSnapshot snapshot = new MetricsSnapshot(
                    elapsed,
                    schedule.targetAt(elapsed),
                    completedRps,
                    completedCount,
                    metricsRecorder.droppedCount(),
                    metricsRecorder.activeRequests(),
                    metricsRecorder.outcomeCountsSnapshot());
            log.info("Progress: elapsed={}s requested={} actual={} active={} dropped={}",
                    String.format("%.0f", elapsed), String.format("%.0f", snapshot.requestedTarget()),
                    String.format("%.1f", completedRps), snapshot.activeRequests(), snapshot.droppedCount());
            listener.onSnapshot(snapshot);
        } catch (Exception e) {
            // A listener failure (e.g. a transient DB write error) must not kill the run;
            // the next tick will simply emit the next snapshot.
        }
    }

    private double elapsedSeconds() {
        return (System.nanoTime() - startNanos) / 1_000_000_000.0;
    }
}
