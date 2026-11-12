package com.pulseload.engine.saturation;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SaturationDetectorTest {

    private final SaturationDetector detector = new SaturationDetector();

    @Test
    void reportsNoSaturationWhenActualThroughputMeetsTheRequestedTarget() {
        SaturationInput input = new SaturationInput(
                1000, 980, 0.95, 900_000_000L, 1_000_000_000L, false, 0, 1_000_000L);

        SaturationResult result = detector.evaluate(input);

        assertThat(result.saturated()).isFalse();
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    void highCpuDuringAShortfallIsGeneratorSaturation() {
        // Requested 5000, actual 4120 — exactly the US 2.4 example — with the generator pegged at 95% CPU.
        SaturationInput input = new SaturationInput(
                5000, 4120, 0.95, 200_000_000L, 2_000_000_000L, false, 0, 50_000_000L);

        SaturationResult result = detector.evaluate(input);

        assertThat(result.saturated()).isTrue();
        assertThat(result.reasons()).containsExactly(SaturationReason.HIGH_CPU);
    }

    @Test
    void highHeapUsageDuringAShortfallIsGeneratorSaturation() {
        SaturationInput input = new SaturationInput(
                1000, 700, 0.3, 1_900_000_000L, 2_000_000_000L, false, 0, 10_000_000L);

        SaturationResult result = detector.evaluate(input);

        assertThat(result.saturated()).isTrue();
        assertThat(result.reasons()).containsExactly(SaturationReason.HIGH_HEAP);
    }

    @Test
    void frequentDispatchTickOverrunsAreGeneratorSaturationEvenWithLowCpu() {
        SaturationInput input = new SaturationInput(
                1000, 700, 0.3, 500_000_000L, 2_000_000_000L, false, 50, 10_000_000L);

        SaturationResult result = detector.evaluate(input);

        assertThat(result.saturated()).isTrue();
        assertThat(result.reasons()).containsExactly(SaturationReason.DISPATCH_LAG);
    }

    @Test
    void droppedRequestsWithLowGeneratorResourceUseAreConcurrencyCapOnlyNotGeneratorSaturation() {
        // The semaphore filled up (requests dropped), but CPU/heap are low and the tick loop
        // kept up — the target's own latency is the bottleneck, not the generator's resources.
        SaturationInput input = new SaturationInput(
                1000, 600, 0.2, 300_000_000L, 2_000_000_000L, true, 0, TimeUnit.MILLISECONDS.toNanos(400));

        SaturationResult result = detector.evaluate(input);

        assertThat(result.saturated()).isFalse();
        assertThat(result.reasons()).containsExactlyInAnyOrder(
                SaturationReason.CONCURRENCY_CAP_REACHED,
                SaturationReason.TARGET_LATENCY_LIMITED
        );
    }

    @Test
    void droppedRequestsWithLowLatencyAreJustConcurrencyCapReached() {
        // Dropped requests but the target answers fast — the configured cap itself is just too
        // low for the requested rate, not a sign the target is slow.
        SaturationInput input = new SaturationInput(
                1000, 600, 0.2, 300_000_000L, 2_000_000_000L, true, 0, TimeUnit.MILLISECONDS.toNanos(5));

        SaturationResult result = detector.evaluate(input);

        assertThat(result.saturated()).isFalse();
        assertThat(result.reasons()).containsExactly(SaturationReason.CONCURRENCY_CAP_REACHED);
    }

    @Test
    void bothGeneratorAndCapReasonsCanBeReportedTogether() {
        SaturationInput input = new SaturationInput(
                1000, 600, 0.95, 300_000_000L, 2_000_000_000L, true, 0, TimeUnit.MILLISECONDS.toNanos(5));

        SaturationResult result = detector.evaluate(input);

        assertThat(result.saturated()).isTrue();
        assertThat(result.reasons()).contains(SaturationReason.HIGH_CPU, SaturationReason.CONCURRENCY_CAP_REACHED);
    }

    @Test
    void aSmallShortfallWithinToleranceIsNotFlaggedAtAll() {
        // 97% of target — within the 95% tolerance band, not a real shortfall worth explaining.
        SaturationInput input = new SaturationInput(
                1000, 970, 0.95, 1_900_000_000L, 2_000_000_000L, true, 100, 1_000_000_000L);

        SaturationResult result = detector.evaluate(input);

        assertThat(result.saturated()).isFalse();
        assertThat(result.reasons()).isEmpty();
    }
}
