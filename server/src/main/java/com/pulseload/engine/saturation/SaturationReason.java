package com.pulseload.engine.saturation;

/**
 * Diagnoses why a run's actual throughput fell short of its requested target. The first group
 * below indicts the generator itself and is what sets {@code RunReport.saturated = true}; the
 * second indicts the concurrency cap or the target's own latency (by Little's Law, needed
 * concurrency = RPS x latency) and is surfaced as a warning without implying generator
 * saturation. Populated by {@link SaturationDetector}.
 */
public enum SaturationReason {
    /** GENERATOR: process CPU usage was high while the run was behind schedule. */
    HIGH_CPU,
    /** GENERATOR: JVM heap usage was high while the run was behind schedule. */
    HIGH_HEAP,
    /** GENERATOR: the pacing tick itself was running behind, independent of the concurrency cap. */
    DISPATCH_LAG,
    /** CONCURRENCY_CAP/TARGET: maxConcurrency was exhausted; does not alone imply generator saturation. */
    CONCURRENCY_CAP_REACHED,
    /** CONCURRENCY_CAP/TARGET: the target's own response latency, not the generator, is limiting throughput. */
    TARGET_LATENCY_LIMITED
}
