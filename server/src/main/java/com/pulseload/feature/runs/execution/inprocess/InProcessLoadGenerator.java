package com.pulseload.feature.runs.execution.inprocess;

import com.pulseload.engine.InProcessEngine;
import com.pulseload.engine.RunListener;
import com.pulseload.engine.api.RunSpec;
import com.pulseload.feature.runs.execution.LoadGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InProcessLoadGenerator implements LoadGenerator {

    private final InProcessEngine engine;

    @Override
    public void start(RunSpec spec, RunListener listener) {
        engine.start(spec, listener);
    }

    @Override
    public void stop(UUID runId) {
        engine.stop(runId);
    }

    /** Requests every active run to stop and blocks until each has finished. Called on shutdown. */
    public void stopAll() {
        engine.stopAll();
    }
}
