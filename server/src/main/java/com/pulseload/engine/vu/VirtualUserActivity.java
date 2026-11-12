package com.pulseload.engine.vu;

/**
 * Lifecycle of a single load-testing virtual user — a logical actor in a run,
 * distinct from the
 * JVM virtual thread it happens to execute on. A VU is {@code IDLE} between
 * requests, {@code
 * REQUESTING} while an HTTP call is in flight, {@code COMPLETED} once its loop
 * winds down normally,
 * or {@code ERROR} if its loop itself failed.
 */
public enum VirtualUserActivity {
  IDLE,
  REQUESTING,
  COMPLETED,
  ERROR
}
