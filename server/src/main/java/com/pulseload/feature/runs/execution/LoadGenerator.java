package com.pulseload.feature.runs.execution;

import com.pulseload.engine.RunListener;
import com.pulseload.engine.api.RunSpec;

import java.util.UUID;

/**
 * Starts and stops load test runs. Implemented in-process today ({@code InProcessLoadGenerator});
 * Sprint 4 adds a {@code GrpcLoadGenerator} that delegates to a remote worker instead, without
 * this port or its callers changing.
 */
public interface LoadGenerator {

    void start(RunSpec spec, RunListener listener);

    void stop(UUID runId);
}
