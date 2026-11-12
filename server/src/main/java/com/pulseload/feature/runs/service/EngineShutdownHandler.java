package com.pulseload.feature.runs.service;

import com.pulseload.feature.runs.execution.inprocess.InProcessLoadGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Requests every active run to stop and waits for them to actually finish before the application
 * context closes, so a graceful shutdown never leaves a run stuck in {@code RUNNING} the way a
 * hard kill would ({@link OrphanRunRecovery} handles that harder case on the next startup).
 */
@Component
@RequiredArgsConstructor
class EngineShutdownHandler implements SmartLifecycle {

    private final InProcessLoadGenerator loadGenerator;
    private volatile boolean running = false;

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        loadGenerator.stopAll();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
