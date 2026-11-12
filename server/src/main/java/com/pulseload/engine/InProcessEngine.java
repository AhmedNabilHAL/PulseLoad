package com.pulseload.engine;

import com.pulseload.engine.api.RunSpec;
import com.pulseload.engine.executor.HttpExecutor;
import com.pulseload.engine.executor.ProtocolExecutor;
import com.pulseload.engine.strategy.LoadStrategyFactory;
import com.pulseload.model.LoadModel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Runs load tests in the same JVM as the control plane. This is the engine-side half of the
 * seam Sprint 4 replaces with a remote, gRPC-driven worker: {@code service.runs.execution}'s
 * {@code LoadGenerator} port is implemented by an in-process adapter today and a gRPC adapter
 * later, but either way it delegates the actual pacing/execution work to this class (today) or
 * to a remote worker process (later) without the control plane's own code changing.
 *
 * <p>Tracks one {@link EngineRun} per active {@code runId} — the engine-side analogue of
 * {@code RunCoordinator} in the control plane, which tracks active runs for the purpose of
 * enforcing the concurrent-run limit and routing stop requests.
 *
 * <p>Spring autowires every {@link LoadStrategyFactory} bean into {@code strategyFactories}; this
 * is where the OPEN-vs-CLOSED choice that used to be an if/else in {@code EngineRun} now lives.
 * {@code EngineRun} is a prototype-scoped bean, so a fresh instance (and a fresh proxy) is
 * requested per run via {@code engineRuns.getObject(...)}, passing every constructor argument
 * explicitly — Spring does not mix explicitly-passed arguments with autowiring in a single
 * constructor call, so the resolved {@link LoadStrategyFactory} must be supplied here rather than
 * left for {@code EngineRun} to autowire itself.
 */
@Component
@RequiredArgsConstructor
public final class InProcessEngine {

    private final List<LoadStrategyFactory> strategyFactories;
    private final ObjectProvider<EngineRun> engineRuns;
    private final Map<UUID, EngineRun> activeRuns = new ConcurrentHashMap<>();

    public void start(RunSpec spec, RunListener listener) {
        ProtocolExecutor executor = new HttpExecutor(URI.create(spec.targetUrl()), spec.httpMethod(), spec.requestTimeout());
        LoadStrategyFactory strategyFactory = strategyFactoryFor(spec.loadModel());
        EngineRun run = engineRuns.getObject(spec, executor, listener, strategyFactory);
        activeRuns.put(spec.runId(), run);

        Executors.defaultThreadFactory().newThread(() -> {
            try {
                run.runToCompletion();
            } finally {
                activeRuns.remove(spec.runId());
            }
        }).start();
    }

    public void stop(UUID runId) {
        EngineRun run = activeRuns.get(runId);
        if (run != null) {
            run.stop();
        }
    }

    /** Requests every active run to stop and blocks until each has actually finished. Intended for shutdown. */
    public void stopAll() {
        activeRuns.values().forEach(EngineRun::stop);
        activeRuns.values().forEach(run -> {
            try {
                run.awaitFinished();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private LoadStrategyFactory strategyFactoryFor(LoadModel model) {
        return strategyFactories.stream()
                .filter(factory -> factory.loadModel() == model)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No LoadStrategyFactory registered for " + model));
    }
}
