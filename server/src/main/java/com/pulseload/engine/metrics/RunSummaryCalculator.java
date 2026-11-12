package com.pulseload.engine.metrics;

import com.pulseload.engine.api.RunReport;
import org.HdrHistogram.Histogram;

/**
 * Accumulates interval histograms pulled from a {@code MetricsRecorder} over
 * the life of a run
 * into a single run-total {@link RunReport}. HdrHistogram's interval histograms
 * are mergeable,
 * so percentiles computed from the accumulated totals are mathematically sound
 * — unlike averaging
 * per-interval percentiles, which would not be.
 */
public final class RunSummaryCalculator {

    private final Histogram latencyTotal = new Histogram(3);

    public void accumulate(Histogram latencyInterval) {
        latencyTotal.add(latencyInterval);
    }

    public RunReport finish(MetricsRecorder recorder, double elapsedSeconds, double requestedRps) {
        long completed = recorder.completedCount();
        double actualRps = elapsedSeconds > 0 ? completed / elapsedSeconds : 0;

        return new RunReport(
                recorder.dispatchedCount(),
                completed,
                recorder.droppedCount(),
                recorder.outcomeCountsSnapshot(),
                MetricsRecorder.statsOf(latencyTotal),
                requestedRps,
                actualRps);
    }
}
