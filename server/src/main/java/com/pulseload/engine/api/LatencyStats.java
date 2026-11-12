package com.pulseload.engine.api;

public record LatencyStats(
        long minNanos,
        double meanNanos,
        long maxNanos,
        long p50Nanos,
        long p90Nanos,
        long p95Nanos,
        long p99Nanos
) {
}
