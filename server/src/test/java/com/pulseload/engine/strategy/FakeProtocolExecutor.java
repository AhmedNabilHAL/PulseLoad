package com.pulseload.engine.strategy;

import com.pulseload.engine.executor.ExecutionOutcome;
import com.pulseload.engine.executor.ExecutionResult;
import com.pulseload.engine.executor.ProtocolExecutor;

import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic, instantaneous stand-in for a real target: always succeeds, takes a fixed service time. */
final class FakeProtocolExecutor implements ProtocolExecutor {

    private final long serviceTimeNanos;
    private final AtomicInteger callCount = new AtomicInteger();

    FakeProtocolExecutor(long serviceTimeNanos) {
        this.serviceTimeNanos = serviceTimeNanos;
    }

    @Override
    public ExecutionResult execute() {
        callCount.incrementAndGet();
        return new ExecutionResult(ExecutionOutcome.SUCCESS_2XX, 200, serviceTimeNanos);
    }

    int callCount() {
        return callCount.get();
    }

    @Override
    public void close() {
    }
}
