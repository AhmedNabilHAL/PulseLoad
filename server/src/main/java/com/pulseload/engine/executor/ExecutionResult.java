package com.pulseload.engine.executor;

public record ExecutionResult(ExecutionOutcome outcome, int statusCode, long serviceTimeNanos) {
}
