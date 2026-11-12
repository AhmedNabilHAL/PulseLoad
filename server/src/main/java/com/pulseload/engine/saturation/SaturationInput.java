package com.pulseload.engine.saturation;

/**
 * Plain inputs {@link SaturationDetector} needs to diagnose a run — deliberately primitives only,
 * so the detector has no dependency on {@code engine.metrics}/{@code engine.api} types and stays
 * trivially unit-testable with synthetic values.
 *
 * @param requestedRps     the schedule's target at the end of the run (RPS or VUs)
 * @param actualRps        completed requests per second actually achieved
 * @param peakCpuLoad      highest process CPU load sampled during the run, 0..1, or -1 if never available
 * @param peakHeapUsedBytes highest JVM heap usage sampled during the run, in bytes
 * @param maxHeapBytes     the JVM's maximum heap size, in bytes (for a usage ratio)
 * @param anyRequestsDropped whether the open-model strategy ever had to drop a request because
 *                           {@code maxConcurrency} was exhausted (closed-model runs never drop)
 * @param tickOverrunCount number of pacing ticks that ran significantly slower than their
 *                         intended interval — a sign the generator's own scheduling loop, not
 *                         just request dispatch, is falling behind
 * @param responseTimeP95Nanos p95 response-time latency for the run (service time under the
 *                             closed model, where response/service time are identical)
 */
public record SaturationInput(
        double requestedRps,
        double actualRps,
        double peakCpuLoad,
        long peakHeapUsedBytes,
        long maxHeapBytes,
        boolean anyRequestsDropped,
        long tickOverrunCount,
        long responseTimeP95Nanos
) {
}
