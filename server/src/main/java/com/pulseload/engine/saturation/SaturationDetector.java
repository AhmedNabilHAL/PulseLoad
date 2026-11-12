package com.pulseload.engine.saturation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Pure rule evaluation distinguishing "the generator itself is saturated" from "the configured
 * concurrency cap or the target's own latency is the limiting factor" (US 2.4). A shortfall
 * between requested and actual throughput only means generator saturation if the generator's own
 * resources explain it; a full concurrency cap on its own does not, since by Little's Law the
 * concurrency needed to sustain a given RPS at a given latency is {@code RPS x latency} — a slow
 * target can exhaust {@code maxConcurrency} with the generator nowhere near its own limits.
 */
public final class SaturationDetector {

    /** Below this fraction of the requested target, actual throughput counts as a shortfall worth explaining. */
    private static final double SHORTFALL_TOLERANCE = 0.95;
    private static final double HIGH_CPU_LOAD = 0.85;
    private static final double HIGH_HEAP_USAGE_RATIO = 0.85;
    private static final long DISPATCH_LAG_TICK_THRESHOLD = 5;
    private static final long HIGH_LATENCY_NANOS = TimeUnit.MILLISECONDS.toNanos(250);

    public SaturationResult evaluate(SaturationInput input) {
        boolean shortfall = input.requestedRps() > 0
                && input.actualRps() < input.requestedRps() * SHORTFALL_TOLERANCE;

        if (!shortfall) {
            return new SaturationResult(false, List.of());
        }

        List<SaturationReason> reasons = new ArrayList<>();

        boolean highCpu = input.peakCpuLoad() >= HIGH_CPU_LOAD;
        boolean highHeap = input.maxHeapBytes() > 0
                && input.peakHeapUsedBytes() >= input.maxHeapBytes() * HIGH_HEAP_USAGE_RATIO;
        boolean dispatchLag = input.tickOverrunCount() > DISPATCH_LAG_TICK_THRESHOLD;

        if (highCpu) {
            reasons.add(SaturationReason.HIGH_CPU);
        }
        if (highHeap) {
            reasons.add(SaturationReason.HIGH_HEAP);
        }
        if (dispatchLag) {
            reasons.add(SaturationReason.DISPATCH_LAG);
        }

        boolean generatorSaturated = highCpu || highHeap || dispatchLag;

        if (input.anyRequestsDropped()) {
            reasons.add(SaturationReason.CONCURRENCY_CAP_REACHED);
        }
        if (input.anyRequestsDropped() && input.responseTimeP95Nanos() >= HIGH_LATENCY_NANOS) {
            reasons.add(SaturationReason.TARGET_LATENCY_LIMITED);
        }

        return new SaturationResult(generatorSaturated, List.copyOf(reasons));
    }
}
