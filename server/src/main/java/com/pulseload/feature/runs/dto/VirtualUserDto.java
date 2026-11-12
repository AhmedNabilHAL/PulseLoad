package com.pulseload.feature.runs.dto;

import java.time.Instant;

/**
 * A single virtual user's observable state for the run-observability UI.
 * {@code state} is the
 * VU's lifecycle activity (IDLE, REQUESTING, COMPLETED, ERROR);
 * {@code lastOutcome} is the result
 * of its most recent HTTP request, or null before its first. Carries only
 * non-sensitive lifecycle
 * facts — never request headers, bodies, or credentials.
 */
public record VirtualUserDto(
    int id,
    String state,
    long requestCount,
    String lastOutcome,
    Instant lastActivityAt) {
}
