package com.pulseload.feature.runs.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gates how many runs may execute at once, independent of which {@code LoadGenerator} adapter is
 * actually running them. This is the control-plane analogue of a Kubernetes Job controller
 * enforcing a concurrency policy — one coordinator for the whole application, not one per run.
 */
@Component
@RequiredArgsConstructor
public class RunCoordinator {

    @Value("${app.runs.max-concurrent:1}")
    private final int maxConcurrentRuns;
    private final AtomicInteger activeRunCount = new AtomicInteger();

    /** Atomically reserves a concurrency slot. Returns false if the limit is already reached. */
    public boolean tryReserve() {
        return activeRunCount.getAndUpdate(current -> current < maxConcurrentRuns ? current + 1 : current) < maxConcurrentRuns;
    }

    public void release() {
        activeRunCount.updateAndGet(current -> Math.max(0, current - 1));
    }
}
