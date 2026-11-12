package com.pulseload.engine.schedule;

import com.pulseload.model.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure piecewise-linear ramp over a sequence of stages, starting at {@code startTarget}.
 * Within each stage the target value ramps linearly from the previous stage's end value
 * (or {@code startTarget} for the first stage) to that stage's own target, over its duration.
 *
 * <p>{@code targetAt} gives the instantaneous target (RPS or VUs, depending on the load model).
 * {@code cumulativeAt} integrates that target over time, which for the OPEN model is exactly
 * the number of requests that should have been dispatched by a given elapsed time — the basis
 * for both pacing and the intended-send-time calculation used to avoid coordinated omission.
 */
public final class WorkloadSchedule {

    private record Segment(double startSeconds, double endSeconds,
                            double startValue, double endValue, double cumulativeAtStart) {

        double durationSeconds() {
            return endSeconds - startSeconds;
        }

        double valueDelta() {
            return endValue - startValue;
        }

        double targetAt(double elapsedSeconds) {
            double s = elapsedSeconds - startSeconds;
            return startValue + valueDelta() * s / durationSeconds();
        }

        double cumulativeAt(double elapsedSeconds) {
            double s = elapsedSeconds - startSeconds;
            return cumulativeAtStart + startValue * s + valueDelta() * s * s / (2 * durationSeconds());
        }

        double cumulativeAtEnd() {
            return cumulativeAtStart + startValue * durationSeconds() + valueDelta() * durationSeconds() / 2;
        }

        /** Elapsed time (relative to run start) at which this segment's cumulative reaches {@code target}. */
        double elapsedAtCumulative(double target) {
            double c = target - cumulativeAtStart;
            if (valueDelta() == 0) {
                // Constant-rate segment: cumulative grows linearly, c = startValue * s.
                return startSeconds + (startValue == 0 ? 0 : c / startValue);
            }
            // c = startValue * s + valueDelta * s^2 / (2 * duration)  =>  quadratic in s.
            double a = valueDelta() / (2 * durationSeconds());
            double b = startValue;
            double discriminant = b * b + 4 * a * c;
            double s = (-b + Math.sqrt(Math.max(0, discriminant))) / (2 * a);
            return startSeconds + s;
        }
    }

    private final List<Segment> segments;
    private final double totalDurationSeconds;
    private final double finalValue;
    private final double totalCumulative;

    private WorkloadSchedule(List<Segment> segments, double totalDurationSeconds,
                              double finalValue, double totalCumulative) {
        this.segments = segments;
        this.totalDurationSeconds = totalDurationSeconds;
        this.finalValue = finalValue;
        this.totalCumulative = totalCumulative;
    }

    public static WorkloadSchedule of(int startTarget, List<Stage> stages) {
        if (stages == null || stages.isEmpty()) {
            throw new IllegalArgumentException("A workload schedule needs at least one stage");
        }

        List<Segment> segments = new ArrayList<>(stages.size());
        double t = 0;
        double v = startTarget;
        double cumulative = 0;

        for (Stage stage : stages) {
            int duration = stage.durationSeconds();
            int target = stage.target();
            if (duration > 0) {
                Segment segment = new Segment(t, t + duration, v, target, cumulative);
                segments.add(segment);
                cumulative = segment.cumulativeAtEnd();
                t += duration;
            }
            // A zero-duration stage is an instant jump: it updates v without adding a segment.
            v = target;
        }

        if (segments.isEmpty()) {
            throw new IllegalArgumentException("All stages have zero duration; nothing to schedule");
        }

        return new WorkloadSchedule(segments, t, v, cumulative);
    }

    /** Instantaneous target at the given elapsed time, clamped to the schedule's bounds. */
    public double targetAt(double elapsedSeconds) {
        if (elapsedSeconds <= 0) {
            return segments.getFirst().startValue();
        }
        if (elapsedSeconds >= totalDurationSeconds) {
            return finalValue;
        }
        return segmentAt(elapsedSeconds).targetAt(elapsedSeconds);
    }

    /** Integral of {@code targetAt} from 0 to the given elapsed time, clamped to the schedule's bounds. */
    public double cumulativeAt(double elapsedSeconds) {
        if (elapsedSeconds <= 0) {
            return 0;
        }
        if (elapsedSeconds >= totalDurationSeconds) {
            return totalCumulative;
        }
        return segmentAt(elapsedSeconds).cumulativeAt(elapsedSeconds);
    }

    /**
     * Inverse of {@code cumulativeAt}: the elapsed time at which the cumulative target reaches
     * {@code cumulativeTarget}. Used to compute the intended send time of the n-th request under
     * the open model, independent of scheduler tick granularity or dispatch delay.
     */
    public double elapsedAtCumulative(double cumulativeTarget) {
        if (cumulativeTarget <= 0) {
            return 0;
        }
        if (cumulativeTarget >= totalCumulative) {
            return totalDurationSeconds;
        }
        for (Segment segment : segments) {
            if (cumulativeTarget <= segment.cumulativeAtEnd()) {
                return segment.elapsedAtCumulative(cumulativeTarget);
            }
        }
        return totalDurationSeconds;
    }

    public double totalDurationSeconds() {
        return totalDurationSeconds;
    }

    private Segment segmentAt(double elapsedSeconds) {
        for (Segment segment : segments) {
            if (elapsedSeconds <= segment.endSeconds()) {
                return segment;
            }
        }
        return segments.getLast();
    }
}
