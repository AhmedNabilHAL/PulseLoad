package com.pulseload.engine.schedule;

import com.pulseload.model.Stage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WorkloadScheduleTest {

    @Test
    void ramsLinearlyFromStartTargetOverASingleStage() {
        WorkloadSchedule schedule = WorkloadSchedule.of(0, List.of(new Stage(10, 100)));

        assertThat(schedule.targetAt(0)).isEqualTo(0);
        assertThat(schedule.targetAt(5)).isEqualTo(50);
        assertThat(schedule.targetAt(10)).isEqualTo(100);
    }

    @Test
    void cumulativeIsTheAreaUnderTheRampCurve() {
        WorkloadSchedule schedule = WorkloadSchedule.of(0, List.of(new Stage(10, 100)));

        // Area of the triangle 0..100 RPS over 10s.
        assertThat(schedule.cumulativeAt(10)).isCloseTo(500, within(1e-9));
        // Area of the smaller triangle 0..50 RPS over 5s.
        assertThat(schedule.cumulativeAt(5)).isCloseTo(125, within(1e-9));
        assertThat(schedule.cumulativeAt(0)).isEqualTo(0);
    }

    @Test
    void elapsedAtCumulativeInvertsCumulativeAt() {
        WorkloadSchedule schedule = WorkloadSchedule.of(0, List.of(new Stage(10, 100)));

        assertThat(schedule.elapsedAtCumulative(125)).isCloseTo(5, within(1e-6));
        assertThat(schedule.elapsedAtCumulative(500)).isCloseTo(10, within(1e-6));
    }

    @Test
    void handlesMultipleStagesIncludingAFlatPlateau() {
        // Flat at 50 for 10s, then ramp 50 -> 150 over the next 10s.
        WorkloadSchedule schedule = WorkloadSchedule.of(50, List.of(
                new Stage(10, 50),
                new Stage(10, 150)
        ));

        assertThat(schedule.targetAt(5)).isEqualTo(50);
        assertThat(schedule.targetAt(15)).isEqualTo(100);
        assertThat(schedule.targetAt(20)).isEqualTo(150);

        assertThat(schedule.cumulativeAt(10)).isCloseTo(500, within(1e-9));
        assertThat(schedule.cumulativeAt(15)).isCloseTo(875, within(1e-9));
        assertThat(schedule.cumulativeAt(20)).isCloseTo(1500, within(1e-9));

        assertThat(schedule.elapsedAtCumulative(875)).isCloseTo(15, within(1e-6));
    }

    @Test
    void clampsBeforeStartAndAfterEnd() {
        WorkloadSchedule schedule = WorkloadSchedule.of(10, List.of(new Stage(10, 100)));

        assertThat(schedule.targetAt(-5)).isEqualTo(10);
        assertThat(schedule.targetAt(100)).isEqualTo(100);
        assertThat(schedule.cumulativeAt(-5)).isEqualTo(0);
        assertThat(schedule.cumulativeAt(1000)).isEqualTo(schedule.cumulativeAt(schedule.totalDurationSeconds()));
    }

    @Test
    void zeroDurationStageIsAnInstantJumpNotASeparateSegment() {
        // Jump straight to 200, then ramp down to 0 over 10s.
        WorkloadSchedule schedule = WorkloadSchedule.of(0, List.of(
                new Stage(0, 200),
                new Stage(10, 0)
        ));

        assertThat(schedule.targetAt(0)).isEqualTo(200);
        assertThat(schedule.targetAt(5)).isEqualTo(100);
        assertThat(schedule.targetAt(10)).isEqualTo(0);
    }

    @Test
    void rejectsEmptyStageList() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> WorkloadSchedule.of(0, List.of()));
    }
}
