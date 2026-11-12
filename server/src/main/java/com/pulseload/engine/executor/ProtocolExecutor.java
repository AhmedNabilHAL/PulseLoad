package com.pulseload.engine.executor;

/**
 * Fires one request against the run's target and reports the outcome. An instance is bound to
 * a single run (target, method, timeout) and is safe to call concurrently from many virtual
 * threads. {@code HttpExecutor} is the only implementation today; a {@code GrpcExecutor} is
 * expected in Sprint 3, sharing this contract.
 */
public interface ProtocolExecutor extends AutoCloseable {

    ExecutionResult execute();

    @Override
    void close();
}
