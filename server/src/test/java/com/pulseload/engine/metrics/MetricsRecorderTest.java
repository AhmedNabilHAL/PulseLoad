package com.pulseload.engine.metrics;

import com.pulseload.engine.api.RunReport;
import com.pulseload.engine.executor.ExecutionOutcome;
import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsRecorderTest {

    @Test
    void tracksCompletedCountsPerOutcomeAndActiveRequests() {
        MetricsRecorder recorder = new MetricsRecorder();

        recorder.recordDispatch();
        recorder.recordDispatch();
        assertThat(recorder.activeRequests()).isEqualTo(2);

        recorder.recordCompletion(ExecutionOutcome.SUCCESS_2XX, 10_000_000L, 10_000_000L);
        assertThat(recorder.activeRequests()).isEqualTo(1);

        recorder.recordCompletion(ExecutionOutcome.SERVER_ERROR_5XX, 5_000_000L, 5_000_000L);
        assertThat(recorder.activeRequests()).isZero();

        recorder.recordDropped();

        assertThat(recorder.completedCount()).isEqualTo(2);
        assertThat(recorder.droppedCount()).isEqualTo(1);
        assertThat(recorder.dispatchedCount()).isEqualTo(2);
        assertThat(recorder.outcomeCountsSnapshot())
                .containsEntry(ExecutionOutcome.SUCCESS_2XX, 1L)
                .containsEntry(ExecutionOutcome.SERVER_ERROR_5XX, 1L);
    }

    @Test
    void intervalHistogramsResetAfterEachRead() {
        MetricsRecorder recorder = new MetricsRecorder();
        recorder.recordCompletion(ExecutionOutcome.SUCCESS_2XX, 100, 100);

        Histogram first = recorder.intervalServiceTimeHistogram();
        assertThat(first.getTotalCount()).isEqualTo(1);

        Histogram second = recorder.intervalServiceTimeHistogram();
        assertThat(second.getTotalCount()).isZero();
    }

    @Test
    void responseTimeCanDivergeFromServiceTimeUnderCoordinatedOmission() {
        MetricsRecorder recorder = new MetricsRecorder();

        // The generator fell behind schedule: the request was sent late, so even though the
        // target answered quickly (short service time), the user-perceived wait was much longer.
        long serviceTimeNanos = TimeUnit.MILLISECONDS.toNanos(5);
        long responseTimeNanos = TimeUnit.MILLISECONDS.toNanos(500);
        recorder.recordCompletion(ExecutionOutcome.SUCCESS_2XX, serviceTimeNanos, responseTimeNanos);

        Histogram serviceTime = recorder.intervalServiceTimeHistogram();
        Histogram responseTime = recorder.intervalResponseTimeHistogram();

        assertThat(serviceTime.getValueAtPercentile(50)).isLessThan(responseTime.getValueAtPercentile(50));
    }

    @Test
    void runSummaryCalculatorAccumulatesIntervalHistogramsIntoRunTotals() {
        MetricsRecorder recorder = new MetricsRecorder();
        RunSummaryCalculator calculator = new RunSummaryCalculator();

        recorder.recordDispatch();
        recorder.recordCompletion(ExecutionOutcome.SUCCESS_2XX, TimeUnit.MILLISECONDS.toNanos(10), TimeUnit.MILLISECONDS.toNanos(10));
        calculator.accumulate(recorder.intervalServiceTimeHistogram(), recorder.intervalResponseTimeHistogram());

        recorder.recordDispatch();
        recorder.recordCompletion(ExecutionOutcome.SUCCESS_2XX, TimeUnit.MILLISECONDS.toNanos(20), TimeUnit.MILLISECONDS.toNanos(20));
        calculator.accumulate(recorder.intervalServiceTimeHistogram(), recorder.intervalResponseTimeHistogram());

        RunReport report = calculator.finish(recorder, 1.0, 100.0, 0.5, 100_000_000L, 1_000_000_000L, 0);

        assertThat(report.totalCompleted()).isEqualTo(2);
        assertThat(report.totalDispatched()).isEqualTo(2);
        assertThat(report.serviceTime().minNanos()).isCloseTo(TimeUnit.MILLISECONDS.toNanos(10), org.assertj.core.data.Offset.offset(1_000_000L));
        assertThat(report.serviceTime().maxNanos()).isCloseTo(TimeUnit.MILLISECONDS.toNanos(20), org.assertj.core.data.Offset.offset(1_000_000L));
        assertThat(report.actualRps()).isEqualTo(2.0);
    }
}
