package com.pulseload.feature.runs.service;

import com.pulseload.engine.RunListener;
import com.pulseload.engine.api.MetricsSnapshot;
import com.pulseload.engine.api.RunReport;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Bridges the engine's {@link RunListener} callbacks to {@link RunPersistence}, always via the
 * given single-thread executor rather than the calling (engine) thread — so a slow or blocked
 * database write can never perturb the engine's own pacing or reporting ticks. One instance is
 * created per run by {@link TestRunServiceImpl}.
 */
class EngineRunListener implements RunListener {

    private final UUID runId;
    private final RunPersistence persistence;
    private final ExecutorService persistenceExecutor;
    private final RunCoordinator runCoordinator;

    EngineRunListener(UUID runId, RunPersistence persistence, ExecutorService persistenceExecutor, RunCoordinator runCoordinator) {
        this.runId = runId;
        this.persistence = persistence;
        this.persistenceExecutor = persistenceExecutor;
        this.runCoordinator = runCoordinator;
        persistenceExecutor.execute(() -> persistence.markRunning(runId));
    }

    @Override
    public void onSnapshot(MetricsSnapshot snapshot) {
        persistenceExecutor.execute(() -> persistence.saveSnapshot(runId, snapshot));
    }

    @Override
    public void onCompleted(RunReport report, boolean cancelled) {
        persistenceExecutor.execute(() -> {
            persistence.saveCompletion(runId, report, cancelled);
            runCoordinator.release();
        });
        persistenceExecutor.shutdown();
    }

    @Override
    public void onFailed(Throwable cause) {
        persistenceExecutor.execute(() -> {
            persistence.saveFailure(runId, cause);
            runCoordinator.release();
        });
        persistenceExecutor.shutdown();
    }
}
