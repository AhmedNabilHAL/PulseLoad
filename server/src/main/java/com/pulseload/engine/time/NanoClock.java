package com.pulseload.engine.time;

/**
 * Seam over {@link System#nanoTime()} so pacing logic can be driven by a fake
 * clock in tests instead of waiting on real time.
 */
public interface NanoClock {

    NanoClock SYSTEM = System::nanoTime;

    long nanoTime();
}
