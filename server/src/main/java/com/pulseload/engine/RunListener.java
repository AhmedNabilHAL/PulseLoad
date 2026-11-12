package com.pulseload.engine;

import com.pulseload.engine.api.MetricsSnapshot;
import com.pulseload.engine.api.RunReport;

/**
 * Observer notified as a run progresses. Implementations persist snapshots/results; they must
 * not block the engine's pacing or reporting thread (hand work to a separate executor), and must
 * not throw — an exception here must not interrupt the run it's observing.
 */
public interface RunListener {

    void onSnapshot(MetricsSnapshot snapshot);

    /** @param cancelled true if the run finished because {@code stop()} was called, rather than reaching the schedule's end naturally */
    void onCompleted(RunReport report, boolean cancelled);

    void onFailed(Throwable cause);
}
