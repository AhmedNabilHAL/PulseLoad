package com.pulseload.engine.api;

import com.pulseload.model.HttpMethod;
import com.pulseload.model.LoadModel;
import com.pulseload.model.Stage;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Immutable instruction set handed to a {@code LoadGenerator} to execute one run.
 * Fields mirror what a future gRPC {@code StartTest} message would carry.
 */
public record RunSpec(
        UUID runId,
        String targetUrl,
        HttpMethod httpMethod,
        LoadModel loadModel,
        int startTarget,
        int maxConcurrency,
        List<Stage> stages,
        Duration requestTimeout
) {
}
