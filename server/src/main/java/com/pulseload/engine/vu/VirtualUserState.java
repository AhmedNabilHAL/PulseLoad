package com.pulseload.engine.vu;

import com.pulseload.engine.executor.ExecutionOutcome;

import java.time.Instant;

/**
 * Immutable snapshot of one virtual user's observable state, read by the
 * control plane to show
 * participants what each VU is doing. {@code id} is the VU's stable index
 * within its run;
 * {@code lastOutcome} is the result of its most recent HTTP request (null
 * before the first).
 */
public record VirtualUserState(
    int id,
    VirtualUserActivity activity,
    long requestCount,
    ExecutionOutcome lastOutcome,
    Instant lastActivityAt) {
}
