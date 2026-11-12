package com.pulseload.engine.metrics;

import com.pulseload.engine.api.LatencyStats;
import com.pulseload.engine.api.RunReport;
import com.pulseload.engine.saturation.SaturationDetector;
import com.pulseload.engine.saturation.SaturationInput;
import com.pulseload.engine.saturation.SaturationResult;
import org.HdrHistogram.Histogram;

/**
 * Accumulates interval histograms pulled from a {@code MetricsRecorder} over the life of a run
 * into a single run-total {@link RunReport}. HdrHistogram's interval histograms are mergeable,
 * so percentiles computed from the accumulated totals are mathematically sound — unlike averaging
 * per-interval percentiles, which would not be.
 */
public final class RunSummaryCalculator {

    private final Histogram serviceTimeTotal = new Histogram(3);
    private final Histogram responseTimeTotal = new Histogram(3);
    private final SaturationDetector saturationDetector = new SaturationDetector();

    public void accumulate(Histogram serviceTimeInterval, Histogram responseTimeInterval) {
        serviceTimeTotal.add(serviceTimeInterval);
        responseTimeTotal.add(responseTimeInterval);
    }

    public RunReport finish(MetricsRecorder recorder, double elapsedSeconds, double requestedRps,
                             double peakCpuLoad, long peakHeapUsedBytes, long maxHeapBytes,
                             long tickOverrunCount) {
        long completed = recorder.completedCount();
        double actualRps = elapsedSeconds > 0 ? completed / elapsedSeconds : 0;
        LatencyStats responseTimeStats = MetricsRecorder.statsOf(responseTimeTotal);

        SaturationResult saturationResult = saturationDetector.evaluate(new SaturationInput(
                requestedRps,
                actualRps,
                peakCpuLoad,
                peakHeapUsedBytes,
                maxHeapBytes,
                recorder.droppedCount() > 0,
                tickOverrunCount,
                responseTimeStats.p95Nanos()
        ));

        return new RunReport(
                recorder.dispatchedCount(),
                completed,
                recorder.droppedCount(),
                recorder.outcomeCountsSnapshot(),
                MetricsRecorder.statsOf(serviceTimeTotal),
                responseTimeStats,
                requestedRps,
                actualRps,
                peakCpuLoad,
                peakHeapUsedBytes,
                saturationResult.saturated(),
                saturationResult.reasons()
        );
    }
}
